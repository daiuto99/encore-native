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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for Library Screen.
 *
 * Manages:
 * - Song list from Room database (reactive Flow)
 * - Search query state
 * - Search filtering logic
 * - Import flow with SAF
 *
 * Follows offline-first pattern with Flow-based reactive updates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository
) : ViewModel() {

    // Search query state (mutable for UI updates)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Import progress state
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    // Import result state
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    /**
     * Songs list with reactive search filtering.
     *
     * Uses flatMapLatest to automatically switch to new search query Flow
     * whenever searchQuery changes. This ensures UI always shows results
     * for the latest query without manual cancellation.
     */
    val songs: StateFlow<List<SongEntity>> = _searchQuery
        .flatMapLatest { query ->
            songRepository.searchSongs(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Update search query from UI.
     *
     * @param query New search term (can be empty to show all songs)
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search query and show all songs.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Import songs from markdown files using SAF URIs.
     *
     * Parses filename for title/artist using simple regex pattern.
     * Parses Key from markdown content (e.g., "Key: G" or "K:G").
     * Reads file content as markdown body.
     * Skips duplicates and reports results.
     *
     * @param context Android context for content resolver
     * @param uris List of file URIs from SAF picker
     */
    fun importSongs(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0

            try {
                uris.forEach { uri ->
                    try {
                        // Read file content
                        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader().use { it.readText() }
                        } ?: run {
                            Log.e(TAG, "Failed to read file: $uri")
                            return@forEach
                        }

                        // Get filename
                        val filename = getFilename(context, uri)
                        Log.d(TAG, "Importing file: $filename")

                        // Parse filename for title/artist
                        val (title, artist) = parseFilename(filename)

                        // Parse key from content
                        val key = parseKey(content)
                        val now = System.currentTimeMillis()

                        // Check if duplicate - skip to prevent overwrites
                        val existingDuplicate = songRepository.findDuplicate(title, artist, "local-user")
                        if (existingDuplicate != null) {
                            skippedCount++
                            Log.d(TAG, "Duplicate skipped: $title - $artist")
                            return@forEach
                        }

                        // Create new song entity
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

                        // Import via repository
                        val result = songRepository.upsertSong(song)
                        if (result.isSuccess) {
                            addedCount++
                            Log.d(TAG, "Imported new song: $title - $artist (Key: ${key ?: "none"})")
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
     * Clear import result after showing snackbar.
     */
    fun clearImportResult() {
        _importResult.value = null
    }

    /**
     * Get all sets that contain a specific song.
     * Used for displaying set membership badges in library.
     *
     * @param songId Song UUID
     * @return List of sets containing the song
     */
    suspend fun getSetsContainingSong(songId: String): List<SetEntity> {
        return setlistRepository.getSetsContainingSong(songId)
    }

    /**
     * Get filename from URI using ContentResolver.
     */
    private fun getFilename(context: Context, uri: Uri): String {
        var filename = "unknown.md"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    filename = cursor.getString(nameIndex)
                }
            }
        }
        return filename
    }

    /**
     * Parse filename to extract title and artist.
     *
     * Expected format: "Title - Artist.md"
     * Fallback: Use filename as title, "Unknown Artist" as artist
     *
     * @param filename File name from SAF
     * @return Pair of (title, artist)
     */
    private fun parseFilename(filename: String): Pair<String, String> {
        // Regex pattern: "Title - Artist.md"
        val regex = """(.+?)\s*-\s*(.+?)\.md$""".toRegex(RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(filename)

        return if (match != null) {
            val title = match.groupValues[1].trim()
            val artist = match.groupValues[2].trim()
            Pair(title, artist)
        } else {
            // Fallback: Use filename without extension as title
            val title = filename.removeSuffix(".md").removeSuffix(".MD")
            Pair(title, "Unknown Artist")
        }
    }

    /**
     * Parse key from markdown content.
     *
     * Handles Obsidian chord sheet format with bold markers:
     * - "**Key:** G", "**key:** g"
     * - "Key: G", "key: g" (without bold)
     * - "[Key: G]" (bracketed)
     * - Case insensitive with flexible spacing
     *
     * @param content Markdown file content
     * @return Key string (e.g., "G", "Dm", "C#m") or null if not found
     */
    private fun parseKey(content: String): String? {
        // Regex patterns for key detection (Obsidian format)
        val patterns = listOf(
            // Match "**Key:**" with optional asterisks (Obsidian bold)
            // Handles: **Key:** G, **key:** D, *Key:* C#m
            """(?i)\*?\*?Key:\*?\*?\s*([A-G][#b]?m?)""".toRegex(),

            // Match "Key: G" without bold at start of line
            """(?i)^\s*key\s*:\s*([A-G][#b]?m?)""".toRegex(RegexOption.MULTILINE),

            // Match "K: G" short form
            """(?i)^\s*k\s*:\s*([A-G][#b]?m?)""".toRegex(RegexOption.MULTILINE),

            // Match "[Key: G]" bracketed format
            """\[\s*(?i)key\s*:\s*([A-G][#b]?m?)\s*\]""".toRegex(RegexOption.IGNORE_CASE)
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                val key = match.groupValues[1].trim()
                Log.d(TAG, "Found key: $key (pattern matched)")
                return key
            }
        }

        Log.d(TAG, "No key found in content")
        return null
    }

    companion object {
        private const val TAG = "LibraryViewModel"
    }
}

/**
 * Result of import operation.
 */
data class ImportResult(
    val addedCount: Int,
    val skippedCount: Int
)
