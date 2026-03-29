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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

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
    private val userPrefs: UserPreferencesRepository
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

    /** URI string of the last synced folder — null if no folder has been linked yet. */
    val connectedFolderUri: StateFlow<String?> = userPrefs.connectedFolderUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

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
    }

    /**
     * Scans all songs with null currentKey and re-parses their markdown body to extract the key.
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
                            song.copy(currentKey = key, updatedAt = now, localUpdatedAt = now)
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
                                    currentKey = key,
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
                            currentKey = key,
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
                            currentKey = key,
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
                                    currentKey = key,
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
                                currentKey = key,
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
    }

    /**
     * Get all sets that contain a specific song.
     * Used for displaying set membership badges in library.
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
