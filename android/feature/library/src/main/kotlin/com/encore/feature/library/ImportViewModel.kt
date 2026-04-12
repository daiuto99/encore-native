package com.encore.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.entities.SyncStatus
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * ViewModel for all file-based import operations: SAF file pick, folder sync,
 * folder refresh, JSON set import, and setlist export.
 *
 * Extracted from LibraryViewModel as part of Track 1 (Foundation Hardening).
 */
class ImportViewModel(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult.asStateFlow()

    private val _syncProgress = MutableStateFlow<SyncProgress?>(null)
    val syncProgress: StateFlow<SyncProgress?> = _syncProgress.asStateFlow()

    private var importJob: Job? = null

    /** URI string of the last synced folder — null if no folder has been linked yet. */
    val connectedFolderUri: StateFlow<String?> = userPrefs.connectedFolderUri
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun clearImportResult() { _importResult.value = null }

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
                        if (result.isSuccess) addedCount++
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
     * Sync all .md files from a SAF folder tree. Persists URI permission for future
     * re-scans without re-prompting.
     */
    fun syncFolder(context: Context, folderUri: Uri) {
        importJob = viewModelScope.launch {
            _isImporting.value = true
            var addedCount = 0
            var skippedCount = 0
            try {
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
                        if (result.isSuccess) addedCount++
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
     * Re-scan the connected folder without re-prompting. Smart-skips files unchanged
     * since last import (compares file.lastModified vs song.localUpdatedAt).
     */
    fun refreshConnectedFolder(context: Context) {
        val savedUri = connectedFolderUri.value ?: run {
            Log.w(TAG, "No connected folder — call syncFolder first")
            return
        }
        val folderUri = Uri.parse(savedUri)
        val permissionHeld = context.contentResolver.persistedUriPermissions
            .any { it.uri == folderUri && it.isReadPermission }
        if (!permissionHeld) {
            Log.w(TAG, "Persistable permission lost — user must re-select folder")
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

                mdFiles.forEachIndexed { index, file ->
                    _syncProgress.value = SyncProgress(index + 1, total)
                    try {
                        val filename = file.name ?: return@forEachIndexed
                        val (rawTitle, rawArtist) = parseFilename(filename)
                        val (title, artist) = normalizeSongData(rawTitle, rawArtist)
                        val existing = songRepository.findDuplicate(title, artist, "local-user")
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
                            if (result.isSuccess) addedCount++
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

    /**
     * Serialize a setlist to JSON and write it to [outputUri] via SAF.
     * Format: { version: 1, name: "...", songs: [{ title, artist, displayKey?, markdownBody }] }
     */
    fun exportSetlistToUri(context: Context, setlistId: String, outputUri: Uri) {
        viewModelScope.launch {
            try {
                val setlistWithSets = setlistRepository.getSetlistWithSets(setlistId) ?: return@launch
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
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
            }
        }
    }

    /**
     * Read an Encore set export JSON file and import it as a new named setlist.
     * Songs that already exist (matched by title + artist) are reused without duplication.
     */
    fun importSetFromJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                } ?: return@launch
                val root = JSONObject(content)
                val name = root.optString("name", "Imported Set").ifBlank { "Imported Set" }
                val songsArray = root.optJSONArray("songs") ?: return@launch
                val newSetlistId = setlistRepository.createSetlist(name).getOrNull() ?: return@launch
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
                _importResult.value = ImportResult(addedCount, 0, reusedCount)
                Log.d(TAG, "Imported \"$name\": $addedCount new, $reusedCount matched")
            } catch (e: Exception) {
                Log.e(TAG, "Set JSON import failed", e)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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
        val base = filename.removeSuffix(".md").removeSuffix(".MD").replace("_", " ")
        val separatorIdx = base.indexOf(" - ")
        return if (separatorIdx != -1) {
            Pair(base.substring(0, separatorIdx).trim(), base.substring(separatorIdx + 3).trim())
        } else {
            Pair(base.trim(), "Unknown Artist")
        }
    }

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
        private const val TAG = "ImportViewModel"
    }
}
