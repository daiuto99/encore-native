package com.encore.core.data.repository

import com.encore.core.data.dao.SongDao
import com.encore.core.data.entities.SongEntity
import android.content.IntentSender
import com.encore.core.data.sync.ContentSyncStatus
import com.encore.core.data.sync.FileHashUtils
import com.encore.core.data.sync.GcpManifest
import com.encore.core.data.sync.LockResult
import com.encore.core.data.sync.SyncProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

import kotlinx.coroutines.withTimeoutOrNull

/**
 * Repository for Song data operations.
 *
 * Provides abstraction over SongDao and handles business logic for song management.
 * Implements offline-first pattern with Flow-based reactive queries.
 */
interface SongRepository {

    /**
     * Hot stream of OAuth2 consent Intents that must be launched by the UI.
     * Delegates to [SyncProvider.authConsentEvents].
     */
    val syncAuthConsentEvents: SharedFlow<IntentSender>

    /**
     * Get all songs ordered by title.
     *
     * @return Flow of all songs for reactive UI updates
     */
    fun getSongs(): Flow<List<SongEntity>>

    /**
     * Search songs by title or artist.
     * If query is empty or blank, returns all songs.
     *
     * @param query Search term (partial match, case-insensitive)
     * @return Flow of matching songs
     */
    fun searchSongs(query: String): Flow<List<SongEntity>>

    /**
     * Search songs in a specific set by title or artist.
     * If query is empty or blank, returns all songs in the set.
     *
     * @param query Search term (partial match, case-insensitive)
     * @param setNumber Set number (1-4)
     * @return Flow of matching songs in the set
     */
    fun searchSongsInSet(query: String, setNumber: Int): Flow<List<SongEntity>>

    /**
     * Get a single song by ID.
     *
     * @param id Song UUID
     * @return Song or null if not found
     */
    suspend fun getSongById(id: String): SongEntity?

    /**
     * Insert or update a song (upsert operation).
     * Used for import flow and editing songs.
     *
     * - If song.id doesn't exist: inserts new song
     * - If song.id exists: updates existing song
     *
     * @param song Song to insert or update
     * @return Result with song ID or error
     */
    suspend fun upsertSong(song: SongEntity): Result<String>

    /**
     * Delete a song.
     * Cascade will remove all SetEntries referencing this song.
     *
     * @param song Song to delete
     * @return Result indicating success or error
     */
    suspend fun deleteSong(song: SongEntity): Result<Unit>

    /**
     * Check if a duplicate song exists (by title and artist).
     * Used during import to detect existing songs.
     *
     * @param title Song title
     * @param artist Artist name
     * @param userId User ID (default "local-user" for Milestone 2)
     * @return Existing song or null
     */
    suspend fun findDuplicate(
        title: String,
        artist: String,
        userId: String = "local-user"
    ): SongEntity?

    /**
     * Get total count of songs in library.
     *
     * @return Number of songs
     */
    suspend fun getSongCount(): Int

    /**
     * Update the zoom level for a specific song.
     * Used in Performance Mode to persist per-song zoom preferences.
     *
     * @param songId Song UUID
     * @param zoomLevel Zoom level (0.5-3.0, where 1.0 = 100%)
     * @return Result indicating success or error
     */
    suspend fun updateZoomLevel(songId: String, zoomLevel: Float): Result<Unit>

    /**
     * Get all songs that have no key parsed yet.
     * Used for backfilling key on previously imported songs.
     *
     * @return List of songs with null displayKey
     */
    suspend fun getSongsWithoutKey(): List<SongEntity>

    /**
     * Get songs in a specific set ordered by position within the set.
     * Used when set filter is active with no search text.
     *
     * @param setNumber Set number (1-4)
     * @return Flow of songs ordered by set position
     */
    fun getSongsInSetOrdered(setNumber: Int): Flow<List<SongEntity>>

    /**
     * Get all songs as a one-shot list for audit scanning.
     */
    suspend fun getAllSongsOnce(): List<SongEntity>

    /**
     * Write the result of an audit scan for a single song.
     */
    suspend fun updateValidationResult(id: String, isVerified: Boolean, errors: String?, timestamp: Long)

    /**
     * Reactive stream of songs that have validation errors.
     * Only includes songs that have been scanned and failed.
     */
    fun getInvalidSongs(): Flow<List<SongEntity>>

    /**
     * Compare local markdownBody hash against the server-side hash to determine
     * whether a sync action is needed.
     *
     * Decision table:
     *  - lastSyncedHash == null                                   → [ContentSyncStatus.NeverSynced]
     *  - remoteHash == null (server echo for SYNCED fake)         → [ContentSyncStatus.UpToDate]
     *  - !isDirty && remoteHash != lastSyncedHash                 → [ContentSyncStatus.RemoteAhead]
     *  - isDirty && remoteHash == lastSyncedHash                  → [ContentSyncStatus.LocalAhead]
     *  - isDirty && remoteHash != lastSyncedHash                  → [ContentSyncStatus.Conflict]
     *  - otherwise                                                → [ContentSyncStatus.UpToDate]
     *
     * Always runs on [kotlinx.coroutines.Dispatchers.IO] internally via [FileHashUtils].
     *
     * @param songId UUID of the song to check
     */
    suspend fun checkSyncStatus(songId: String): ContentSyncStatus

    /**
     * Mark a sync as successful by writing the current content hash into [lastSyncedHash]
     * and clearing [isDirty].
     *
     * @param songId UUID of the song that was just synced
     */
    suspend fun markSynced(songId: String)

    /**
     * Request an exclusive edit lock for [songId] from [FakeSyncProvider].
     *
     * - If the lock is granted: clears [SongEntity.isLockedByOther] and returns [LockResult.Acquired].
     * - If another client holds it: sets [SongEntity.isLockedByOther] = true and returns [LockResult.LockedBy].
     * - Offline escape hatch: if no response within 5 seconds, returns [LockResult.Acquired] silently
     *   so performers are never blocked by a network outage.
     *
     * @param songId UUID of the song to lock
     */
    suspend fun requestEditLock(songId: String): LockResult

    /**
     * Release the edit lock for [songId] and clear [SongEntity.isLockedByOther].
     */
    suspend fun releaseEditLock(songId: String)

    /**
     * Upload the current markdownBody of [songId] to the cloud sync backend
     * and call [markSynced] on success.
     *
     * @param userId  The signed-in user's account ID (used for GCS path scoping).
     * @param songId  UUID of the song to upload.
     * @return true if the upload succeeded, false if offline or not found.
     */
    suspend fun uploadSongToCloud(userId: String, songId: String): Boolean

    /**
     * Download the cloud version of [songId] and apply it to the local Room record.
     *
     * Parses the YAML front matter prepended by [uploadSongToCloud] to update
     * title, artist, displayKey, originalKey, isLeadGuitar, and markdownBody.
     * Marks the song as synced (clears [SongEntity.isDirty], sets [SongEntity.lastSyncedHash]).
     *
     * Used when the web app has edited a song and the remote version is ahead of local.
     *
     * @param userId  Owner's account ID (GCS path scope).
     * @param songId  UUID of the song to pull.
     * @return true if the pull succeeded, false if not found or network unavailable.
     */
    suspend fun pullSongFromCloud(userId: String, songId: String): Boolean
}

/**
 * Implementation of SongRepository using Room DAO.
 */
class SongRepositoryImpl(
    private val songDao: SongDao,
    private val syncProvider: SyncProvider
) : SongRepository {

    override val syncAuthConsentEvents: SharedFlow<IntentSender> = syncProvider.authConsentEvents

    override fun getSongs(): Flow<List<SongEntity>> {
        return songDao.getAllSongs()
    }

    override fun searchSongs(query: String): Flow<List<SongEntity>> {
        // If query is empty or blank, return all songs
        return if (query.isBlank()) {
            songDao.getAllSongs()
        } else {
            songDao.searchSongs(query.trim())
        }
    }

    override fun searchSongsInSet(query: String, setNumber: Int): Flow<List<SongEntity>> {
        // Always use the filtered query for set-specific searches
        return songDao.searchSongsInSet(query.trim(), setNumber)
    }

    override suspend fun getSongById(id: String): SongEntity? {
        return songDao.getById(id)
    }

    override suspend fun upsertSong(song: SongEntity): Result<String> {
        return try {
            val existingSong = songDao.getById(song.id)

            if (existingSong != null) {
                // Update existing song
                songDao.update(song)
            } else {
                // Insert new song
                songDao.insert(song)
            }

            Result.success(song.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteSong(song: SongEntity): Result<Unit> {
        return try {
            songDao.delete(song)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun findDuplicate(
        title: String,
        artist: String,
        userId: String
    ): SongEntity? {
        return songDao.findDuplicate(userId, title, artist)
    }

    override suspend fun getSongCount(): Int {
        return songDao.getCount()
    }

    override suspend fun updateZoomLevel(songId: String, zoomLevel: Float): Result<Unit> {
        return try {
            val song = songDao.getById(songId)
            if (song != null) {
                val updatedSong = song.copy(
                    lastZoomLevel = zoomLevel,
                    localUpdatedAt = System.currentTimeMillis()
                )
                songDao.update(updatedSong)
                Result.success(Unit)
            } else {
                Result.failure(IllegalArgumentException("Song not found: $songId"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSongsWithoutKey(): List<SongEntity> = songDao.getSongsWithoutKey()

    override fun getSongsInSetOrdered(setNumber: Int): Flow<List<SongEntity>> =
        songDao.getSongsInSetOrdered(setNumber)

    override suspend fun getAllSongsOnce(): List<SongEntity> = songDao.getAllSongsOnce()

    override suspend fun updateValidationResult(
        id: String,
        isVerified: Boolean,
        errors: String?,
        timestamp: Long
    ) = songDao.updateValidation(id, isVerified, errors, timestamp)

    override fun getInvalidSongs(): Flow<List<SongEntity>> = songDao.getInvalidSongs()

    override suspend fun checkSyncStatus(songId: String): ContentSyncStatus {
        val song = songDao.getById(songId) ?: return ContentSyncStatus.NeverSynced
        if (song.lastSyncedHash == null) return ContentSyncStatus.NeverSynced

        val remote = syncProvider.getRemoteHash(songId)
        val remoteHash = remote.remoteHash
            ?: return ContentSyncStatus.UpToDate // FakeSyncProvider.SYNCED path

        // Timestamp-based RemoteAhead: web app writes a timestamp hash to the manifest
        // after each save. If that timestamp is newer than our last sync, the remote is ahead
        // regardless of whether the hash strings match (they won't — web uses epoch ms, not MD5).
        val remoteUpdatedAt = remote.serverUpdatedAt
        if (!song.isDirty && remoteUpdatedAt != null && song.lastSyncedAt != null
            && remoteUpdatedAt > song.lastSyncedAt) {
            return ContentSyncStatus.RemoteAhead(remoteHash)
        }

        return when {
            !song.isDirty && remoteHash != song.lastSyncedHash ->
                ContentSyncStatus.RemoteAhead(remoteHash)

            song.isDirty && remoteHash == song.lastSyncedHash ->
                ContentSyncStatus.LocalAhead

            song.isDirty && remoteHash != song.lastSyncedHash -> {
                val localHash = FileHashUtils.hashMarkdownBody(song.markdownBody)
                ContentSyncStatus.Conflict(localHash, remoteHash)
            }

            else -> ContentSyncStatus.UpToDate
        }
    }

    override suspend fun markSynced(songId: String) {
        val song = songDao.getById(songId) ?: return
        val hash = FileHashUtils.hashMarkdownBody(song.markdownBody)
        songDao.update(
            song.copy(
                lastSyncedHash = hash,
                isDirty = false,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun requestEditLock(songId: String): LockResult {
        val result = withTimeoutOrNull(5_000L) {
            syncProvider.requestLock(songId)
        } ?: LockResult.Acquired // offline: grant silently so performers aren't blocked

        val song = songDao.getById(songId) ?: return result
        songDao.update(song.copy(isLockedByOther = result is LockResult.LockedBy))
        return result
    }

    override suspend fun releaseEditLock(songId: String) {
        syncProvider.releaseLock(songId)
        val song = songDao.getById(songId) ?: return
        songDao.update(song.copy(isLockedByOther = false))
    }

    override suspend fun uploadSongToCloud(userId: String, songId: String): Boolean {
        val song = songDao.getById(songId) ?: return false
        return try {
            // Prepend YAML front matter so the web app can read title/artist/key.
            // The web app strips this block before editing and re-attaches on save.
            val yaml = buildString {
                appendLine("---")
                appendLine("title: ${song.title}")
                appendLine("artist: ${song.artist}")
                appendLine("display_key: ${song.displayKey ?: ""}")
                appendLine("original_key: ${song.originalKey ?: ""}")
                appendLine("is_lead_guitar: ${song.isLeadGuitar}")
                append("---")
            }
            val content = "$yaml\n${song.markdownBody}"
            syncProvider.uploadSong(userId, songId, content)
            markSynced(songId)
            true
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun pullSongFromCloud(userId: String, songId: String): Boolean {
        val rawContent = syncProvider.downloadSong(userId, songId) ?: return false
        val existing = songDao.getById(songId) ?: return false
        val (yaml, markdownBody) = parseYamlFrontMatter(rawContent)
        val trimmedBody = markdownBody.trim()
        val hash = FileHashUtils.hashMarkdownBody(trimmedBody)
        val now = System.currentTimeMillis()
        songDao.update(
            existing.copy(
                title          = yaml["title"]?.takeIf { it.isNotBlank() }        ?: existing.title,
                artist         = yaml["artist"]?.takeIf { it.isNotBlank() }       ?: existing.artist,
                displayKey     = yaml["display_key"]?.takeIf { it.isNotBlank() }  ?: existing.displayKey,
                originalKey    = yaml["original_key"]?.takeIf { it.isNotBlank() } ?: existing.originalKey,
                isLeadGuitar   = yaml["is_lead_guitar"]?.lowercase() == "true",
                markdownBody   = trimmedBody,
                isDirty        = false,
                lastSyncedHash = hash,
                lastSyncedAt   = now,
                localUpdatedAt = now,
            )
        )
        return true
    }

    /**
     * Parse YAML front matter from a raw cloud document.
     *
     * Expected format:
     * ```
     * ---
     * key: value
     * ---
     * body content
     * ```
     *
     * @return Pair of (frontMatterMap, bodyContent). If no valid front matter is found,
     *         returns an empty map and the full raw string as body.
     */
    private fun parseYamlFrontMatter(raw: String): Pair<Map<String, String>, String> {
        val normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
        if (!normalized.startsWith("---\n")) return emptyMap<String, String>() to normalized
        val end = normalized.indexOf("\n---\n", 4)
        if (end == -1) return emptyMap<String, String>() to normalized
        val yamlText = normalized.substring(4, end)
        val body     = normalized.substring(end + 5)
        val map = yamlText.lines().mapNotNull { line ->
            val colonIdx = line.indexOf(':')
            if (colonIdx < 1) return@mapNotNull null
            val key   = line.substring(0, colonIdx).trim()
            val value = line.substring(colonIdx + 1).trim().trim('"', '\'')
            key to value
        }.toMap()
        return map to body
    }
}
