package com.encore.feature.library

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.entities.SyncStatus
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val setlistRepository: SetlistRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _setFilter = MutableStateFlow<Int?>(null)
    val setFilter: StateFlow<Int?> = _setFilter.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    // One-shot feedback messages (add to set, remove from set, etc.)
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    /**
     * Songs list with reactive search and set filtering.
     *
     * - Non-empty search: global search across all songs (ignores set filter)
     * - Empty search + set filter: songs in set ordered by position
     * - Empty search + no filter: all songs alphabetically
     */
    val songs: StateFlow<List<SongEntity>> = combine(_searchQuery, _setFilter) { query, setFilter ->
        Pair(query, setFilter)
    }.flatMapLatest { (query, setFilter) ->
        when {
            query.isNotBlank() -> songRepository.searchSongs(query)
            setFilter != null -> songRepository.getSongsInSetOrdered(setFilter)
            else -> songRepository.searchSongs("")
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
    fun clearImportResult() { _importResult.value = null }
    fun clearStatusMessage() { _statusMessage.value = null }

    /**
     * Import songs from markdown files using SAF URIs.
     */
    fun importSongs(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0
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
                        val (title, artist) = parseFilename(filename)
                        val key = parseKey(content)
                        val now = System.currentTimeMillis()
                        val existingDuplicate = songRepository.findDuplicate(title, artist, "local-user")
                        if (existingDuplicate != null) {
                            skippedCount++
                            Log.d(TAG, "Duplicate skipped: $title - $artist")
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
                _importResult.value = ImportResult(addedCount, skippedCount)
            }
        }
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

    private fun parseFilename(filename: String): Pair<String, String> {
        val regex = """(.+?)\s*-\s*(.+?)\.md$""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(filename)
        return if (match != null) {
            Pair(match.groupValues[1].trim(), match.groupValues[2].trim())
        } else {
            Pair(filename.removeSuffix(".md").removeSuffix(".MD"), "Unknown Artist")
        }
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

data class ImportResult(val addedCount: Int, val skippedCount: Int)
