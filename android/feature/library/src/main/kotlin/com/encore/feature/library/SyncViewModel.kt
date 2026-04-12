package com.encore.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SyncStatus
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import com.encore.core.data.sync.ContentSyncStatus
import com.encore.core.data.sync.SyncHudState
import com.encore.core.data.sync.SyncProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * ViewModel for all GCS sync operations, background polling, lock management,
 * and conflict resolution.
 *
 * Extracted from LibraryViewModel as part of Track 1 (Foundation Hardening).
 * Owns: triggerGlobalSync, background poller, pullRemoteChanges,
 *       requestEditLock/releaseEditLock, conflict resolution state + handlers.
 *
 * NOTE: uploadSongInBackground and uploadSetToCloud still live in LibraryViewModel
 * as a documented residual until Track 1.4 routes set/song uploads through the
 * repository layer.
 */
class SyncViewModel(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPrefs: UserPreferencesRepository,
    private val syncProvider: SyncProvider?
) : ViewModel() {

    private val _syncHudState = MutableStateFlow<SyncHudState?>(null)
    val syncHudState: StateFlow<SyncHudState?> = _syncHudState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _conflictToResolve = MutableStateFlow<ConflictResolutionState?>(null)
    val conflictToResolve: StateFlow<ConflictResolutionState?> = _conflictToResolve.asStateFlow()

    /** Tracks the `updatedAt` we last processed per set — prevents re-applying stale web data. */
    private val lastSeenSetUpdatedAt = mutableMapOf<Int, Long>()

    /** Consent intents from GcpSyncProvider — collect in UI to launch OAuth2 flow. */
    val syncAuthConsentEvents = songRepository.syncAuthConsentEvents

    init {
        viewModelScope.launch {
            _lastSyncTimestamp.value = userPrefs.getLastSyncTimestamp()
        }
        autoSyncOnStart()
        startRemoteChangePoller()
    }

    /**
     * Startup sync. Skipped if fewer than 60 seconds have passed since the last sync
     * to prevent re-entrancy on rapid restarts.
     */
    private fun autoSyncOnStart() {
        viewModelScope.launch {
            val lastSync = userPrefs.getLastSyncTimestamp()
            val now = System.currentTimeMillis()
            if (now - lastSync < 60_000L) return@launch
            triggerGlobalSync()
        }
    }

    /**
     * Polls GCS every 2 minutes while the app is open.
     * Skipped if a manual sync is already running to avoid collisions.
     * Cancelled automatically when the ViewModel is cleared.
     */
    private fun startRemoteChangePoller() {
        viewModelScope.launch {
            while (true) {
                delay(2 * 60 * 1000L)
                if (_syncHudState.value is SyncHudState.InProgress) continue
                pullRemoteChanges()
            }
        }
    }

    private suspend fun pullRemoteChanges() {
        val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return
        val songs = songRepository.getAllSongsOnce()
        for (song in songs) {
            if (song.lastSyncedHash == null) continue
            val status = songRepository.checkSyncStatus(song.id)
            when (status) {
                is ContentSyncStatus.RemoteAhead -> songRepository.pullSongFromCloud(userId, song.id)
                is ContentSyncStatus.Conflict    -> songRepository.markConflict(song.id)
                else -> Unit
            }
        }
        checkAndApplyWebSetChanges(userId)
    }

    private suspend fun checkAndApplyWebSetChanges(userId: String) {
        val setlistId = try {
            setlistRepository.getOrCreateSetByNumber(1).setlistId
        } catch (e: Exception) {
            Log.w(TAG, "checkAndApplyWebSetChanges — could not resolve default setlist: ${e.message}")
            return
        }
        val sets = setlistRepository.getSetsForSetlist(setlistId).first()
        for (set in sets) {
            try {
                val json = syncProvider?.downloadSetData(userId, set.number) ?: continue
                val obj = JSONObject(json)
                if (obj.optString("source") != "web") continue
                val updatedAt = obj.optLong("updatedAt", 0L)
                if (updatedAt <= (lastSeenSetUpdatedAt[set.number] ?: 0L)) continue
                val arr = obj.getJSONArray("songIds")
                val songIds = (0 until arr.length()).map { arr.getString(it) }
                    .filter { id -> songRepository.getSongById(id) != null }
                setlistRepository.replaceSetContents(set.id, songIds)
                lastSeenSetUpdatedAt[set.number] = updatedAt
                Log.d(TAG, "Applied web set changes for Set ${set.number}: ${songIds.size} songs")
            } catch (e: Exception) {
                Log.w(TAG, "checkAndApplyWebSetChanges(set=${set.number}) failed: ${e.message}")
            }
        }
    }

    /**
     * Run a full library sync pass against the configured SyncProvider.
     * Drives the Sync HUD. Guard: ignored if a sync is already running.
     */
    fun triggerGlobalSync() {
        if (_syncHudState.value is SyncHudState.InProgress) return
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            val songs = songRepository.getAllSongsOnce()
            val total = songs.size
            if (total == 0) return@launch

            var consecutiveUploadFailures = 0
            for ((index, song) in songs.withIndex()) {
                _syncHudState.value = SyncHudState.InProgress(current = index + 1, total = total)

                if (song.lastSyncedHash == null || (song.isDirty && song.syncStatus != SyncStatus.CONFLICT)) {
                    val uploaded = songRepository.uploadSongToCloud(userId, song.id)
                    if (uploaded) {
                        consecutiveUploadFailures = 0
                    } else {
                        consecutiveUploadFailures++
                        if (consecutiveUploadFailures >= 2) {
                            _syncHudState.value = null
                            return@launch
                        }
                    }
                } else if (song.syncStatus != SyncStatus.CONFLICT) {
                    consecutiveUploadFailures = 0
                    val status = songRepository.checkSyncStatus(song.id)
                    when (status) {
                        is ContentSyncStatus.RemoteAhead -> songRepository.pullSongFromCloud(userId, song.id)
                        is ContentSyncStatus.Conflict    -> songRepository.markConflict(song.id)
                        else -> Unit
                    }
                }
            }

            val now = System.currentTimeMillis()
            userPrefs.saveLastSyncTimestamp(now)
            _lastSyncTimestamp.value = now
            _syncHudState.value = SyncHudState.Complete
            delay(3000)
            _syncHudState.value = null
        }
    }

    // ── Conflict Resolution ───────────────────────────────────────────────────

    /**
     * Fetch the remote markdown body for [songId] and open ConflictResolutionDialog.
     */
    fun prepareConflictResolution(songId: String) {
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            val song = songRepository.getSongById(songId) ?: return@launch
            val remoteBody = songRepository.fetchRemoteMarkdownBody(userId, songId) ?: return@launch
            _conflictToResolve.value = ConflictResolutionState(
                songId = songId,
                songTitle = song.title,
                localBody = song.markdownBody,
                remoteBody = remoteBody
            )
        }
    }

    /** Keep local — re-upload local content to cloud and clear conflict status. */
    fun resolveConflictKeepLocal(songId: String) {
        _conflictToResolve.value = null
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            songRepository.uploadSongToCloud(userId, songId)
        }
    }

    /** Keep remote — overwrite local DB with cloud content and clear conflict status. */
    fun resolveConflictKeepRemote(songId: String) {
        _conflictToResolve.value = null
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            songRepository.pullSongFromCloud(userId, songId)
        }
    }

    /** Dismiss the conflict dialog without resolving (Decide Later). */
    fun dismissConflictResolution() {
        _conflictToResolve.value = null
    }

    companion object {
        private const val TAG = "SyncViewModel"
    }
}
