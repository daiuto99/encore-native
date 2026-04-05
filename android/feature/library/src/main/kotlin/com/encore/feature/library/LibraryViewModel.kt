package com.encore.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.entities.SyncStatus
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.sync.ContentSyncStatus
import com.encore.core.data.sync.LockResult
import com.encore.core.data.sync.SyncHudState
import com.encore.core.data.sync.SyncProvider
import kotlinx.coroutines.delay
import com.encore.core.data.preferences.AppPreferencesRepository
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.relations.SetEntryWithSong
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * ViewModel for Library Screen.
 *
 * Manages:
 * - Song list from Room database (reactive Flow)
 * - Search query state (global search ignores set filter when text is present)
 * - Set filter state (shows songs in position order when active, no text)
 * - Import flow with SAF
 * - Add/remove songs from sets
 * - Drag-and-drop reordering within a set
 * - Key backfill for previously imported songs
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPrefs: UserPreferencesRepository,
    private val appPrefsRepository: AppPreferencesRepository? = null,
    private val syncProvider: SyncProvider? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _setFilter = MutableStateFlow<Int?>(null)
    val setFilter: StateFlow<Int?> = _setFilter.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private var importJob: Job? = null

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    /** Consent intents from GcpSyncProvider — UI must launch these to unblock OAuth2. */
    val syncAuthConsentEvents = songRepository.syncAuthConsentEvents

    private val _syncHudState = MutableStateFlow<SyncHudState?>(null)
    val syncHudState: StateFlow<SyncHudState?> = _syncHudState.asStateFlow()

    private val _lockState = MutableStateFlow<LockResult?>(null)
    val lockState: StateFlow<LockResult?> = _lockState.asStateFlow()

    /** Unix timestamp (ms) of the last completed global sync. 0L = never. */
    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    /** URI string of the last synced folder — null if no folder has been linked yet. */
    val connectedFolderUri: StateFlow<String?> = userPrefs.connectedFolderUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /** Global display preferences — emits instantly when any setting changes. */
    val appPreferences: StateFlow<AppPreferences> = (appPrefsRepository?.appPreferences
        ?: kotlinx.coroutines.flow.flowOf(AppPreferences()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPreferences()
        )

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    /** Non-null while the ConflictResolutionDialog is open. Cleared on resolution or dismiss. */
    private val _conflictToResolve = MutableStateFlow<ConflictResolutionState?>(null)
    val conflictToResolve: StateFlow<ConflictResolutionState?> = _conflictToResolve.asStateFlow()

    // ── Perform Set ───────────────────────────────────────────────────────────
    // The perform set is Set 1 of the default setlist — the nightly working set.
    private val _performSetId = MutableStateFlow<String?>(null)
    private val _defaultSetlistId = MutableStateFlow<String?>(null)

    /** Ordered entries (with song data) for the current perform set. */
    val performSetEntries: StateFlow<List<SetEntryWithSong>> = _performSetId
        .flatMapLatest { setId ->
            if (setId == null) flowOf(emptyList())
            else setlistRepository.getSongsInSet(setId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All sets in the default setlist, ordered by number. Drives the Sets section chips. */
    val availableSets: StateFlow<List<SetEntity>> = _defaultSetlistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else setlistRepository.getSetsForSetlist(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All named setlists for the Load Set dialog. */
    val setlists: StateFlow<List<SetlistEntity>> = setlistRepository.getSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // One-shot feedback messages (add to set, remove from set, etc.)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /**
     * Songs list with reactive search, set filtering, and sort order.
     *
     * - Non-empty search: global search across all songs by title and artist (ignores set filter)
     * - Empty search + set filter: songs in set ordered by position (sort order ignored)
     * - Empty search + no filter: all songs sorted by [SortOrder]
     */
    val songs: StateFlow<List<SongEntity>> = combine(_searchQuery, _setFilter, _sortOrder) { query, setFilter, sortOrder ->
        Triple(query, setFilter, sortOrder)
    }.flatMapLatest { (query, setFilter, sortOrder) ->
        val base = when {
            query.isNotBlank() -> songRepository.searchSongs(query)
            setFilter != null  -> songRepository.getSongsInSetOrdered(setFilter)
            else               -> songRepository.searchSongs("")
        }
        // Preserve set position order when browsing a set without a search query
        if (setFilter != null && query.isBlank()) {
            base
        } else {
            base.map { list ->
                val sorted = when (sortOrder) {
                    SortOrder.TITLE  -> list.sortedBy { it.title.lowercase() }
                    SortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
                }
                // Smart search: prioritise results where title/artist STARTS WITH the query.
                // Falls back to the full contains-match list if no starts-with hits exist.
                if (query.isNotBlank()) {
                    val q = query.lowercase()
                    val startsWith = sorted.filter {
                        it.title.lowercase().startsWith(q) || it.artist.lowercase().startsWith(q)
                    }
                    if (startsWith.isNotEmpty()) startsWith else sorted
                } else {
                    sorted
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        backfillMissingKeys()
        initPerformSet()
        viewModelScope.launch {
            _lastSyncTimestamp.value = userPrefs.getLastSyncTimestamp()
        }
        autoSyncOnStart()
        startRemoteChangePoller()
    }

    /**
     * Startup sync. Skipped if fewer than 60 seconds have passed since the last sync
     * (prevents re-entrancy on rapid restarts). Uploads dirty songs and pulls remote changes.
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
     * For each clean local song that has a newer version on the server, pulls silently.
     * Skipped entirely if a manual sync is already running to avoid collisions.
     * Cancelled automatically when the ViewModel is cleared (app backgrounded/closed).
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

    private fun initPerformSet() {
        viewModelScope.launch {
            try {
                val set1 = setlistRepository.getOrCreateSetByNumber(1)
                _performSetId.value = set1.id
                _defaultSetlistId.value = set1.setlistId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize perform set", e)
            }
        }
    }

    /**
     * Clear all sets back to a blank slate: empties Set 1 and deletes Sets 2+.
     * Resets the active set filter. Songs in the library are unaffected.
     */
    fun clearAllSets() {
        val setlistId = _defaultSetlistId.value ?: return
        viewModelScope.launch {
            setlistRepository.clearAllSets(setlistId)
            _setFilter.value = null
            uploadSetToCloud(1) // Set 1 is now empty; sets 2+ were deleted
        }
    }

    /**
     * Delete a set and renumber subsequent sets (e.g., deleting Set 3 makes Set 4 → Set 3).
     * Clears the active set filter if the deleted set was selected.
     * Set 1 (the perform set) cannot be deleted.
     */
    fun deleteSet(set: SetEntity) {
        if (set.id == _performSetId.value) return  // protect the working perform set
        viewModelScope.launch {
            try {
                setlistRepository.deleteSetAndRenumber(set)
                if (_setFilter.value == set.number) _setFilter.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete set ${set.number}", e)
                _statusMessage.value = "Could not delete set"
            }
        }
        uploadAllSetsInBackground()
    }

    /** Creates the next numbered set in the default setlist. */
    fun createNewSet() {
        val setlistId = _defaultSetlistId.value ?: return
        viewModelScope.launch {
            try {
                setlistRepository.addSetToSetlist(setlistId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create new set", e)
                _statusMessage.value = "Could not create set"
            }
        }
    }

    /**
     * Scans all songs with null displayKey and re-parses their markdown body to extract the key.
     * Runs once on ViewModel creation to repair previously imported songs.
     */
    private fun backfillMissingKeys() {
        viewModelScope.launch {
            try {
                val songsWithoutKey = songRepository.getSongsWithoutKey()
                songsWithoutKey.forEach { song ->
                    val key = parseKey(song.markdownBody)
                    if (key != null) {
                        val now = System.currentTimeMillis()
                        songRepository.upsertSong(
                            song.copy(displayKey = key, updatedAt = now, localUpdatedAt = now)
                        )
                        Log.d(TAG, "Backfilled key for: ${song.title} → $key")
                    }
                }
                if (songsWithoutKey.isNotEmpty()) {
                    Log.d(TAG, "Key backfill complete: ${songsWithoutKey.size} songs scanned")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key backfill failed", e)
            }
        }
    }

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun updateSetFilter(setNumber: Int?) { _setFilter.value = setNumber }
    fun toggleSort() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.TITLE) SortOrder.ARTIST else SortOrder.TITLE
    }

    fun addToPerformSet(songId: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            setlistRepository.addSongToSet(setId, songId)
            val count = setlistRepository.getSongsInSet(setId).first().size
            _statusMessage.value = "Staged ($count in set)"
        }
        uploadAllSetsInBackground()
    }

    fun removeFromPerformSet(entryId: String) {
        viewModelScope.launch { setlistRepository.removeSongFromSet(entryId) }
        uploadAllSetsInBackground()
    }

    fun reorderPerformSet(entryId: String, toIndex: Int) {
        viewModelScope.launch { setlistRepository.reorderSongInSet(entryId, toIndex) }
        uploadAllSetsInBackground()
    }

    /**
     * Saves the current perform set as a new named setlist.
     * Creates a copy — the working Set 1 is unaffected.
     */
    fun saveCurrentSetAs(name: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            val result = setlistRepository.createSetlist(name)
            val newSetlistId = result.getOrNull() ?: run {
                _statusMessage.value = "Could not save set"
                return@launch
            }
            val newSets = setlistRepository.getSetsForSetlist(newSetlistId).first()
            val newSetId = newSets.firstOrNull()?.id ?: return@launch
            val currentEntries = setlistRepository.getSongsInSet(setId).first()
            currentEntries.forEach { entry ->
                setlistRepository.addSongToSet(newSetId, entry.song.id)
            }
            _statusMessage.value = "Saved as \"$name\""
        }
    }

    /**
     * Replaces the current perform set's contents with songs from the chosen setlist.
     * Destructively clears Set 1 then refills from the chosen setlist's first set.
     */
    fun loadSetlistAsCurrent(setlistId: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            // Clear existing entries
            val existingEntries = setlistRepository.getSongsInSet(setId).first()
            existingEntries.forEach { entry ->
                setlistRepository.removeSongFromSet(entry.entry.id)
            }
            // Copy songs from the chosen setlist's first set
            val sets = setlistRepository.getSetsForSetlist(setlistId).first()
            val sourceSetId = sets.minByOrNull { it.number }?.id ?: return@launch
            val songs = setlistRepository.getSongsInSet(sourceSetId).first()
            songs.forEach { entry ->
                setlistRepository.addSongToSet(setId, entry.song.id)
            }
            _statusMessage.value = "Setlist loaded"
        }
    }
    fun clearImportResult() { _importResult.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _isImporting.value = false
    }

    /**
     * Import songs from markdown files using SAF URIs.
     */
    fun importSongs(context: Context, uris: List<Uri>) {
        importJob = viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0
            var updatedCount = 0
            try {
                uris.forEach { uri ->
                    try {
                        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: run {
                            Log.e(TAG, "Failed to read file: $uri")
                            return@forEach
                        }
                        val filename = getFilename(context, uri)
                        val (rawTitle, rawArtist) = parseFilename(filename)
                        val (title, artist) = normalizeSongData(rawTitle, rawArtist)
                        val key = parseKey(content)
                        val now = System.currentTimeMillis()
                        val existing = songRepository.findDuplicate(title, artist, "local-user")
                        if (existing != null) {
                            // Update existing record with fresh content (normalise stored names too)
                            songRepository.upsertSong(
                                existing.copy(
                                    title = title,
                                    artist = artist,
                                    markdownBody = content,
                                    displayKey = key,
                                    updatedAt = now,
                                    localUpdatedAt = now,
                                    version = existing.version + 1,
                                    syncStatus = SyncStatus.PENDING_UPLOAD
                                )
                            )
                            updatedCount++
                            Log.d(TAG, "Updated (import): $title - $artist")
                            return@forEach
                        }
                        val song = SongEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local-user",
                            title = title,
                            artist = artist,
                            displayKey = key,
                            markdownBody = content,
                            originalImportBody = content,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_UPLOAD,
                            localUpdatedAt = now,
                            lastSyncedAt = null
                        )
                        val result = songRepository.upsertSong(song)
                        if (result.isSuccess) {
                            addedCount++
                            Log.d(TAG, "Imported: $title - $artist (key: ${key ?: "none"})")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error importing file: ${e.message}", e)
                    }
                }
            } finally {
                _isImporting.value = false
                _importResult.value = ImportResult(addedCount, skippedCount, updatedCount)
            }
        }
    }

    /**
     * Sync all .md files from a SAF folder tree.
     *
     * Takes a persistable URI permission so the folder can be re-scanned in future
     * sessions without prompting the user again. Reuses the same import/dedup logic
     * as [importSongs].
     *
     * @param context Used for ContentResolver and DocumentFile access.
     * @param folderUri URI returned by [ActivityResultContracts.OpenDocumentTree].
     */
    fun syncFolder(context: Context, folderUri: Uri) {
        importJob = viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0
            try {
                // Persist permission so we can re-scan without re-prompting
                try {
                    context.contentResolver.takePersistableUriPermission(
                        folderUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    userPrefs.saveConnectedFolderUri(folderUri)
                } catch (e: SecurityException) {
                    Log.w(TAG, "Could not persist URI permission: ${e.message}")
                }

                val root = DocumentFile.fromTreeUri(context, folderUri) ?: run {
                    Log.e(TAG, "Cannot open folder: $folderUri")
                    return@launch
                }
                val mdFiles = scanForMarkdownFiles(root)
                val total = mdFiles.size
                Log.d(TAG, "Folder sync: $total .md files found")

                mdFiles.forEachIndexed { index, file ->
                    _syncProgress.value = SyncProgress(index + 1, total)
                    try {
                        val content = context.contentResolver.openInputStream(file.uri)?.use {
                            it.bufferedReader().use { r -> r.readText() }
                        } ?: run {
                            Log.e(TAG, "Failed to read: ${file.name}")
                            return@forEachIndexed
                        }
                        val filename = file.name ?: return@forEachIndexed
                        val (rawTitle, rawArtist) = parseFilename(filename)
                        val (title, artist) = normalizeSongData(rawTitle, rawArtist)
                        val key = parseKey(content)
                        val existingDuplicate = songRepository.findDuplicate(title, artist, "local-user")
                        if (existingDuplicate != null) {
                            skippedCount++
                            return@forEachIndexed
                        }
                        val now = System.currentTimeMillis()
                        val song = SongEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local-user",
                            title = title,
                            artist = artist,
                            displayKey = key,
                            markdownBody = content,
                            originalImportBody = content,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_UPLOAD,
                            localUpdatedAt = now,
                            lastSyncedAt = null
                        )
                        val result = songRepository.upsertSong(song)
                        if (result.isSuccess) {
                            addedCount++
                            Log.d(TAG, "Synced: $title - $artist (key: ${key ?: "none"})")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error syncing ${file.name}", e)
                    }
                }
            } finally {
                _syncProgress.value = null
                _isImporting.value = false
                _importResult.value = ImportResult(addedCount, skippedCount)
            }
        }
    }

    /**
     * Re-scan the connected folder without re-prompting the user.
     *
     * Smart-skip rules (keeps refresh fast):
     * - File not modified since last import (`file.lastModified() <= song.localUpdatedAt`): skip.
     * - File newer than last import: update content + key, increment version.
     * - Song not yet in DB: add as new.
     *
     * Requires that [syncFolder] was called at least once (persists the URI and permission).
     */
    fun refreshConnectedFolder(context: Context) {
        val savedUri = connectedFolderUri.value ?: run {
            Log.w(TAG, "No connected folder — call syncFolder first")
            return
        }
        val folderUri = Uri.parse(savedUri)

        // Verify the persistable permission is still held
        val permissionHeld = context.contentResolver.persistedUriPermissions
            .any { it.uri == folderUri && it.isReadPermission }
        if (!permissionHeld) {
            Log.w(TAG, "Persistable permission lost — user must re-select folder")
            _statusMessage.value = "Folder permission expired. Please re-link your folder."
            return
        }

        importJob = viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0
            var updatedCount = 0
            try {
                val root = DocumentFile.fromTreeUri(context, folderUri) ?: run {
                    Log.e(TAG, "Cannot open folder: $folderUri")
                    return@launch
                }
                val mdFiles = scanForMarkdownFiles(root)
                val total = mdFiles.size
                Log.d(TAG, "Refresh: $total .md files found")

                mdFiles.forEachIndexed { index, file ->
                    _syncProgress.value = SyncProgress(index + 1, total)
                    try {
                        val filename = file.name ?: return@forEachIndexed
                        val (rawTitle, rawArtist) = parseFilename(filename)
                        val (title, artist) = normalizeSongData(rawTitle, rawArtist)
                        val existing = songRepository.findDuplicate(title, artist, "local-user")

                        // Smart skip: file unchanged since last import
                        val fileModified = file.lastModified()
                        if (existing != null && fileModified > 0 && fileModified <= existing.localUpdatedAt) {
                            skippedCount++
                            return@forEachIndexed
                        }

                        val content = context.contentResolver.openInputStream(file.uri)?.use {
                            it.bufferedReader().use { r -> r.readText() }
                        } ?: run {
                            Log.e(TAG, "Failed to read: $filename")
                            return@forEachIndexed
                        }
                        val key = parseKey(content)
                        val now = System.currentTimeMillis()

                        if (existing != null) {
                            // File is newer — update content and key
                            songRepository.upsertSong(
                                existing.copy(
                                    markdownBody = content,
                                    displayKey = key,
                                    updatedAt = now,
                                    localUpdatedAt = now,
                                    version = existing.version + 1,
                                    syncStatus = SyncStatus.PENDING_UPLOAD
                                )
                            )
                            updatedCount++
                            Log.d(TAG, "Updated: $title - $artist")
                        } else {
                            val song = SongEntity(
                                id = UUID.randomUUID().toString(),
                                userId = "local-user",
                                title = title,
                                artist = artist,
                                displayKey = key,
                                markdownBody = content,
                                originalImportBody = content,
                                version = 1,
                                createdAt = now,
                                updatedAt = now,
                                syncStatus = SyncStatus.PENDING_UPLOAD,
                                localUpdatedAt = now,
                                lastSyncedAt = null
                            )
                            val result = songRepository.upsertSong(song)
                            if (result.isSuccess) {
                                addedCount++
                                Log.d(TAG, "Added (refresh): $title - $artist")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error refreshing ${file.name}", e)
                    }
                }
            } finally {
                _syncProgress.value = null
                _isImporting.value = false
                _importResult.value = ImportResult(addedCount, skippedCount, updatedCount)
            }
        }
    }

    private fun scanForMarkdownFiles(dir: DocumentFile): List<DocumentFile> {
        val results = mutableListOf<DocumentFile>()
        dir.listFiles().forEach { file ->
            when {
                file.isDirectory -> results.addAll(scanForMarkdownFiles(file))
                file.name?.endsWith(".md", ignoreCase = true) == true -> results.add(file)
            }
        }
        return results
    }

    /**
     * Delete a song permanently from the library.
     */
    fun deleteSong(song: SongEntity) {
        viewModelScope.launch { songRepository.deleteSong(song) }
    }

    fun updateSongMetadata(
        songId: String,
        title: String,
        artist: String,
        isLeadGuitar: Boolean = false,
        isHarmonyMode: Boolean = false,
        resetZoom: Boolean = false,
        clearHarmonies: Boolean = false,
        capoEnabled: Boolean = false,
        capoFret: Int = 2
    ) {
        viewModelScope.launch {
            val existing = songRepository.getSongById(songId) ?: return@launch
            val updatedBody = if (clearHarmonies)
                existing.markdownBody.replace(Regex("""\[/?h\]"""), "")
            else existing.markdownBody
            val bodyChanged = updatedBody != existing.markdownBody
            songRepository.upsertSong(
                existing.copy(
                    title = title,
                    artist = artist,
                    isLeadGuitar = isLeadGuitar,
                    isHarmonyMode = isHarmonyMode,
                    lastZoomLevel = if (resetZoom) 1.0f else existing.lastZoomLevel,
                    markdownBody = updatedBody,
                    isDirty = existing.isDirty || bodyChanged,
                    capoEnabled = capoEnabled,
                    capoFret = capoFret.coerceIn(1, 12)
                )
            )
            // DB committed — check for conflict before pushing to cloud
            uploadSongInBackground(songId)
        }
    }

    /** Single-shot flow for observing a song in the chart editor. */
    fun getSongFlow(songId: String): Flow<SongEntity?> = flow {
        emit(songRepository.getSongById(songId))
    }

    // ── Set cloud sync ────────────────────────────────────────────────────────

    /** Tracks the `updatedAt` we last processed per set — prevents re-applying stale web data. */
    private val lastSeenSetUpdatedAt = mutableMapOf<Int, Long>()

    private suspend fun uploadSetToCloud(setNumber: Int) {
        val provider = syncProvider ?: return
        val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return
        try {
            val set = setlistRepository.getOrCreateSetByNumber(setNumber)
            val entries = setlistRepository.getSongsInSet(set.id).first()
            val songIds = entries.map { it.song.id }
            val now = System.currentTimeMillis()
            val songIdsJson = songIds.joinToString(",") { "\"$it\"" }
            val json = """{"version":1,"updatedAt":$now,"source":"tablet","songIds":[$songIdsJson]}"""
            provider.uploadSetData(userId, setNumber, json)
            lastSeenSetUpdatedAt[setNumber] = now
            Log.d(TAG, "uploadSetToCloud(set=$setNumber, ${songIds.size} songs)")
        } catch (e: Exception) {
            Log.w(TAG, "uploadSetToCloud($setNumber) failed: ${e.message}")
        }
    }

    /** Uploads all currently known sets to cloud in the background. */
    private fun uploadAllSetsInBackground() {
        viewModelScope.launch {
            availableSets.value.forEach { set -> uploadSetToCloud(set.number) }
        }
    }

    private suspend fun checkAndApplyWebSetChanges(userId: String) {
        for (set in availableSets.value) {
            try {
                val json = syncProvider?.downloadSetData(userId, set.number) ?: continue
                val obj = JSONObject(json)
                if (obj.optString("source") != "web") continue
                val updatedAt = obj.optLong("updatedAt", 0L)
                if (updatedAt <= (lastSeenSetUpdatedAt[set.number] ?: 0L)) continue
                val arr = obj.getJSONArray("songIds")
                val songIds = (0 until arr.length()).map { arr.getString(it) }
                setlistRepository.replaceSetContents(set.id, songIds)
                lastSeenSetUpdatedAt[set.number] = updatedAt
                Log.d(TAG, "Applied web set changes for Set ${set.number}: ${songIds.size} songs")
            } catch (e: Exception) {
                Log.w(TAG, "checkAndApplyWebSetChanges(set=${set.number}) failed: ${e.message}")
            }
        }
    }

    /**
     * Fire-and-forget cloud upload for a single song.
     * Called immediately after any local save so the tablet never needs a manual sync.
     * Silently skipped if the user isn't signed in or has no network.
     *
     * Checks remote status before uploading — if the remote has been independently edited
     * since the last sync, marks the song as CONFLICT instead of silently overwriting.
     */
    private fun uploadSongInBackground(songId: String) {
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            val status = songRepository.checkSyncStatus(songId)
            if (status is ContentSyncStatus.Conflict) {
                songRepository.markConflict(songId)
            } else {
                songRepository.uploadSongToCloud(userId, songId)
            }
        }
    }

    /** Persist edited markdown body to DB and immediately push to cloud. */
    fun updateMarkdownBody(songId: String, body: String) {
        viewModelScope.launch {
            val existing = songRepository.getSongById(songId) ?: return@launch
            val now = System.currentTimeMillis()
            songRepository.upsertSong(
                existing.copy(markdownBody = body, updatedAt = now, localUpdatedAt = now, isDirty = true)
            )
            // DB committed — check for conflict before pushing to cloud
            uploadSongInBackground(songId)
        }
    }

    /**
     * Run a full library sync pass against the configured [SyncProvider].
     *
     * Drives the Sync Progress HUD in [PerformanceContextBar]:
     *  1. Sets [SyncHudState.InProgress] immediately and ticks the counter per song.
     *  2. Adds a 100ms delay between songs to simulate network latency.
     *  3. Switches to [SyncHudState.Complete] ("✓ Synced") for 3 seconds, then clears.
     *
     * Guard: if a sync is already running the call is ignored.
     *
     * Strategy (bidirectional):
     *  - isDirty or never synced → upload to GCS (tablet changes win)
     *  - clean local + remote is ahead → pull from GCS (web changes applied to Room)
     *  - conflict (both dirty) → upload tablet version (tablet wins for now)
     */
    fun triggerGlobalSync() {
        if (_syncHudState.value is SyncHudState.InProgress) return
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            val songs = songRepository.getAllSongsOnce()
            val total = songs.size
            if (total == 0) return@launch

            var firstUploadFailed = false
            for ((index, song) in songs.withIndex()) {
                _syncHudState.value = SyncHudState.InProgress(current = index + 1, total = total)

                if (song.lastSyncedHash == null || (song.isDirty && song.syncStatus != SyncStatus.CONFLICT)) {
                    // Tablet has local changes or song has never been synced → upload
                    val uploaded = songRepository.uploadSongToCloud(userId, song.id)
                    if (!uploaded && index == 0) {
                        // First upload failed — likely an auth/network issue. Abort to avoid
                        // spamming the same error for every song.
                        firstUploadFailed = true
                        break
                    }
                } else if (song.syncStatus != SyncStatus.CONFLICT) {
                    // Local is clean — check if the web app pushed a newer version
                    val status = songRepository.checkSyncStatus(song.id)
                    when (status) {
                        is ContentSyncStatus.RemoteAhead -> songRepository.pullSongFromCloud(userId, song.id)
                        is ContentSyncStatus.Conflict    -> songRepository.markConflict(song.id)
                        else -> Unit
                    }
                }
            }

            if (firstUploadFailed) {
                _syncHudState.value = null
                return@launch
            }

            val now = System.currentTimeMillis()
            userPrefs.saveLastSyncTimestamp(now)
            _lastSyncTimestamp.value = now

            _syncHudState.value = SyncHudState.Complete
            delay(3000)
            _syncHudState.value = null
        }
    }

    /**
     * Request an exclusive edit lock for [songId].
     * Updates [lockState] so the editor can react (show Read-Only banner or open normally).
     */
    fun requestEditLock(songId: String) {
        viewModelScope.launch {
            val result = songRepository.requestEditLock(songId)
            _lockState.value = result
        }
    }

    /** Release the lock when the editor is closed. */
    fun releaseEditLock(songId: String) {
        viewModelScope.launch {
            songRepository.releaseEditLock(songId)
            _lockState.value = null
        }
    }

    /**
     * Add a song to a set by number, auto-creating a default setlist + set if needed.
     * Never shows "not found" — the repository ensures the set exists.
     */
    fun addSongToSetNumber(songId: String, setNumber: Int) {
        viewModelScope.launch {
            try {
                val targetSet = setlistRepository.getOrCreateSetByNumber(setNumber)
                setlistRepository.addSongToSet(targetSet.id, songId)
                _statusMessage.value = "Added to Set $setNumber"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add song to set $setNumber", e)
                _statusMessage.value = "Could not add to Set $setNumber"
            }
        }
        uploadAllSetsInBackground()
    }

    /**
     * Remove a song from a specific set by set number.
     * Looks up the entry ID from the song/set relationship, then removes it.
     */
    fun removeSongFromSetNumber(songId: String, setNumber: Int) {
        viewModelScope.launch {
            try {
                val sets = setlistRepository.getSetsContainingSong(songId)
                val targetSet = sets.find { it.number == setNumber } ?: run {
                    Log.w(TAG, "Song $songId not in Set $setNumber")
                    return@launch
                }
                val entry = setlistRepository.getEntryForSongInSet(targetSet.id, songId) ?: run {
                    Log.w(TAG, "Entry not found for song $songId in set ${targetSet.id}")
                    return@launch
                }
                setlistRepository.removeSongFromSet(entry.id)
                _statusMessage.value = "Removed from Set $setNumber"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove song from set $setNumber", e)
            }
        }
        uploadAllSetsInBackground()
    }

    /**
     * Reorder a song within the currently active set.
     * Resolves the entry ID from the song+set relationship, then delegates to repository.
     *
     * @param songId Song to move
     * @param toIndex Target 0-indexed position in the set
     */
    fun reorderSong(songId: String, toIndex: Int) {
        val setNumber = _setFilter.value ?: return
        viewModelScope.launch {
            try {
                val sets = setlistRepository.getSetsContainingSong(songId)
                val targetSet = sets.find { it.number == setNumber } ?: return@launch
                val entry = setlistRepository.getEntryForSongInSet(targetSet.id, songId) ?: return@launch
                setlistRepository.reorderSongInSet(entry.id, toIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Reorder failed", e)
            }
        }
        uploadAllSetsInBackground()
    }

    /**
     * Observe all sets containing a song as a reactive Flow.
     * Room emits updates whenever set_entries change — add, remove, or set deletion.
     */
    fun observeSetsContainingSong(songId: String): Flow<List<SetEntity>> {
        return setlistRepository.observeSetsContainingSong(songId)
    }

    /**
     * Get all sets that contain a specific song (one-shot).
     */
    suspend fun getSetsContainingSong(songId: String): List<SetEntity> {
        return setlistRepository.getSetsContainingSong(songId)
    }

    private fun getFilename(context: Context, uri: Uri): String {
        var filename = "unknown.md"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) filename = cursor.getString(nameIndex)
            }
        }
        return filename
    }

    /**
     * Parse a ChordSidekick filename into (title, artist).
     *
     * ChordSidekick naming convention: `Song Title - Artist Name.md`
     *   → title = index 0 (everything before the first " - ")
     *   → artist = index 1 (everything after, stripped of extension)
     *
     * Sanitization:
     *   - Splits on the FIRST " - " only, so dashes in artist names are preserved.
     *   - Strips ".md" / ".MD" extension.
     *   - Replaces underscores with spaces.
     */
    private fun parseFilename(filename: String): Pair<String, String> {
        val base = filename.removeSuffix(".md").removeSuffix(".MD").replace("_", " ")
        val separatorIdx = base.indexOf(" - ")
        return if (separatorIdx != -1) {
            // "All The Small Things - Blink 182" → title="All The Small Things", artist="Blink 182"
            val title = base.substring(0, separatorIdx).trim()
            val artist = base.substring(separatorIdx + 3).trim()
            Pair(title, artist)
        } else {
            Pair(base.trim(), "Unknown Artist")
        }
    }

    /**
     * Normalize title and artist strings to a canonical form before storage
     * and deduplication lookups.
     *
     * Rules:
     *  - Trim leading / trailing whitespace
     *  - Collapse runs of internal whitespace to a single space
     *
     * Case is intentionally preserved (e.g. "blink-182" stays as-is).
     * The DAO `findDuplicate` query uses LOWER() on both sides so case
     * differences are handled at the DB layer.
     */
    private fun normalizeSongData(rawTitle: String, rawArtist: String): Pair<String, String> {
        val clean = { s: String -> s.trim().replace(Regex("""\s+"""), " ") }
        return Pair(clean(rawTitle), clean(rawArtist))
    }

    private fun parseKey(content: String): String? {
        val patterns = listOf(
            """(?i)\*?\*?Key:\*?\*?\s*([A-G][#b]?m?)""".toRegex(),
            """(?i)^\s*key\s*:\s*([A-G][#b]?m?)""".toRegex(RegexOption.MULTILINE),
            """(?i)^\s*k\s*:\s*([A-G][#b]?m?)""".toRegex(RegexOption.MULTILINE),
            """\[\s*(?i)key\s*:\s*([A-G][#b]?m?)\s*\]""".toRegex(RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) return match.groupValues[1].trim()
        }
        return null
    }

    /**
     * Serialize a setlist to JSON and write it to [outputUri] via SAF.
     *
     * Format: { version: 1, name: "...", songs: [{ title, artist, displayKey?, markdownBody }] }
     * File extension convention: .encore.json
     */
    fun exportSetlistToUri(context: Context, setlistId: String, outputUri: Uri) {
        viewModelScope.launch {
            try {
                val setlistWithSets = setlistRepository.getSetlistWithSets(setlistId) ?: run {
                    _statusMessage.value = "Setlist not found"
                    return@launch
                }
                val sourceSetId = setlistWithSets.sets.minByOrNull { it.set.number }?.set?.id ?: return@launch
                val entries = setlistRepository.getSongsInSet(sourceSetId).first()

                val songsArray = JSONArray()
                entries.forEach { e ->
                    val obj = JSONObject()
                    obj.put("title", e.song.title)
                    obj.put("artist", e.song.artist)
                    if (e.song.displayKey != null) obj.put("displayKey", e.song.displayKey)
                    obj.put("markdownBody", e.song.markdownBody)
                    songsArray.put(obj)
                }

                val root = JSONObject()
                root.put("version", 1)
                root.put("name", setlistWithSets.setlist.name)
                root.put("songs", songsArray)

                context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                    stream.bufferedWriter().use { writer -> writer.write(root.toString(2)) }
                }
                _statusMessage.value = "Exported \"${setlistWithSets.setlist.name}\""
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                _statusMessage.value = "Export failed"
            }
        }
    }

    /**
     * Read an Encore set export JSON file and import it as a new named setlist.
     *
     * Songs that already exist (matched by title + artist) are reused without
     * duplication. New songs are created and added to the library.
     */
    fun importSetFromJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: run {
                    _statusMessage.value = "Could not read file"
                    return@launch
                }

                val root = JSONObject(content)
                val name = root.optString("name", "Imported Set").ifBlank { "Imported Set" }
                val songsArray = root.optJSONArray("songs") ?: run {
                    _statusMessage.value = "Invalid set file"
                    return@launch
                }

                val newSetlistId = setlistRepository.createSetlist(name).getOrNull() ?: run {
                    _statusMessage.value = "Could not create setlist"
                    return@launch
                }
                val newSets = setlistRepository.getSetsForSetlist(newSetlistId).first()
                val newSetId = newSets.firstOrNull()?.id ?: return@launch

                val now = System.currentTimeMillis()
                var addedCount = 0
                var reusedCount = 0

                for (i in 0 until songsArray.length()) {
                    val obj = songsArray.getJSONObject(i)
                    val title = obj.optString("title").trim()
                    val artist = obj.optString("artist").trim()
                    if (title.isEmpty()) continue
                    val displayKey = obj.optString("displayKey").takeIf { it.isNotEmpty() }
                    val markdownBody = obj.optString("markdownBody")

                    val existing = songRepository.findDuplicate(title, artist, "local-user")
                    val songId = if (existing != null) {
                        reusedCount++
                        existing.id
                    } else {
                        val song = SongEntity(
                            id = UUID.randomUUID().toString(),
                            userId = "local-user",
                            title = title,
                            artist = artist,
                            displayKey = displayKey,
                            markdownBody = markdownBody,
                            originalImportBody = markdownBody,
                            version = 1,
                            createdAt = now,
                            updatedAt = now,
                            syncStatus = SyncStatus.PENDING_UPLOAD,
                            localUpdatedAt = now,
                            lastSyncedAt = null
                        )
                        songRepository.upsertSong(song)
                        addedCount++
                        song.id
                    }
                    setlistRepository.addSongToSet(newSetId, songId)
                }

                _statusMessage.value = "Imported \"$name\" ($addedCount new, $reusedCount matched)"
            } catch (e: Exception) {
                Log.e(TAG, "Set JSON import failed", e)
                _statusMessage.value = "Import failed: ${e.message}"
            }
        }
    }

    // ── Conflict Resolution ───────────────────────────────────────────────────

    /**
     * Fetch the remote markdown body for [songId] and open [ConflictResolutionDialog].
     * No-ops if the song is not found or the remote download fails (bad network, etc.).
     */
    fun prepareConflictResolution(songId: String) {
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            val song = songRepository.getAllSongsOnce().find { it.id == songId } ?: return@launch
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
        private const val TAG = "LibraryViewModel"
    }
}

enum class SortOrder { TITLE, ARTIST }

data class ImportResult(val addedCount: Int, val skippedCount: Int, val updatedCount: Int = 0)

data class SyncProgress(val current: Int, val total: Int) {
    val message: String get() = "Syncing $current of $total…"
    val fraction: Float get() = if (total > 0) current.toFloat() / total else 0f
}

data class ConflictResolutionState(
    val songId: String,
    val songTitle: String,
    val localBody: String,
    val remoteBody: String
)
