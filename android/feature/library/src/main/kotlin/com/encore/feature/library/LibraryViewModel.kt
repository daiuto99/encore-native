package com.encore.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.preferences.AppPreferencesRepository
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.core.data.repository.SongRepository
import com.encore.core.data.sync.ContentSyncStatus
import com.encore.core.data.sync.LockResult
import com.encore.core.data.sync.SyncProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 *
 * Responsibilities:
 * - Reactive song list with search, set filter, and sort
 * - Song metadata + body edits
 * - Key backfill for legacy imports
 *
 * NOTE — Residuals (documented, will be cleaned up in subsequent Track 1 tasks):
 *   • [lockState], [requestEditLock], [releaseEditLock] — used by SongChartEditorScreen;
 *     move to SyncViewModel in Track 4 when that screen is updated.
 *   • [uploadSongInBackground] — direct SyncProvider call; route through repository
 *     layer in Track 1.4.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val userPrefs: UserPreferencesRepository,
    private val appPrefsRepository: AppPreferencesRepository? = null,
    private val syncProvider: SyncProvider? = null,
    private val anthropicApiKey: String = ""
) : ViewModel() {

    // ── Search / filter / sort ────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _setFilter = MutableStateFlow<String?>(null)
    val setFilter: StateFlow<String?> = _setFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.TITLE)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun updateSetFilter(setId: String?) { _setFilter.value = setId }
    fun toggleSort() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.TITLE) SortOrder.ARTIST else SortOrder.TITLE
    }

    // ── Songs reactive flow ───────────────────────────────────────────────────

    val songs: StateFlow<List<SongEntity>> = combine(_searchQuery, _setFilter, _sortOrder) { q, f, s ->
        Triple(q, f, s)
    }.flatMapLatest { (query, setFilter, sortOrder) ->
        val base = when {
            query.isNotBlank() -> songRepository.searchSongs(query)
            setFilter != null  -> songRepository.getSongsInSetOrdered(setFilter)
            else               -> songRepository.searchSongs("")
        }
        if (setFilter != null && query.isBlank()) {
            base
        } else {
            base.map { list ->
                val sorted = when (sortOrder) {
                    SortOrder.TITLE  -> list.sortedBy { it.title.lowercase() }
                    SortOrder.ARTIST -> list.sortedBy { it.artist.lowercase() }
                }
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── App preferences ───────────────────────────────────────────────────────

    val appPreferences: StateFlow<AppPreferences> = (appPrefsRepository?.appPreferences
        ?: kotlinx.coroutines.flow.flowOf(AppPreferences()))
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    // ── Lock state (residual — see class-level note) ──────────────────────────

    private val _lockState = MutableStateFlow<LockResult?>(null)
    val lockState: StateFlow<LockResult?> = _lockState.asStateFlow()

    fun requestEditLock(songId: String) {
        viewModelScope.launch {
            val result = songRepository.requestEditLock(songId)
            _lockState.value = result
        }
    }

    fun releaseEditLock(songId: String) {
        viewModelScope.launch {
            songRepository.releaseEditLock(songId)
            _lockState.value = null
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        backfillMissingKeys()
    }

    private fun backfillMissingKeys() {
        viewModelScope.launch {
            try {
                val songsWithoutKey = songRepository.getSongsWithoutKey()
                songsWithoutKey.forEach { song ->
                    val key = parseKey(song.markdownBody)
                    if (key != null) {
                        val now = System.currentTimeMillis()
                        songRepository.upsertSong(
                            song.copy(
                                displayKey = key,
                                // originalKey is the immutable import-time reference; set it once
                                // from the parsed key if it was never populated from YAML front-matter.
                                originalKey = song.originalKey ?: key,
                                updatedAt = now,
                                localUpdatedAt = now
                            )
                        )
                    }
                }
                // Back-fill originalKey for songs that already have displayKey but not originalKey
                // (e.g. songs imported before this field was introduced)
                val allSongs = songRepository.getAllSongsOnce()
                allSongs.filter { it.displayKey != null && it.originalKey == null }.forEach { song ->
                    val now = System.currentTimeMillis()
                    songRepository.upsertSong(
                        song.copy(originalKey = song.displayKey, updatedAt = now, localUpdatedAt = now)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Key backfill failed", e)
            }
        }
    }

    // ── Song mutations ────────────────────────────────────────────────────────

    fun deleteSong(song: SongEntity) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            songRepository.deleteSongFromCloud(userId, song.id)
        }
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
        capoFret: Int = 2,
        displayKey: String? = null,
        bpm: Int? = null
    ) {
        viewModelScope.launch {
            val existing = songRepository.getSongById(songId) ?: return@launch
            var updatedBody = if (clearHarmonies)
                existing.markdownBody.replace(Regex("""\[/?h\]"""), "")
            else existing.markdownBody
            if (bpm != null) {
                updatedBody = writeBpmToMarkdown(updatedBody, bpm)
            }
            songRepository.upsertSong(
                existing.copy(
                    title = title,
                    artist = artist,
                    isLeadGuitar = isLeadGuitar,
                    isHarmonyMode = isHarmonyMode,
                    lastZoomLevel = if (resetZoom) 1.0f else existing.lastZoomLevel,
                    markdownBody = updatedBody,
                    isDirty = true,
                    capoEnabled = capoEnabled,
                    capoFret = capoFret.coerceIn(1, 12),
                    displayKey = displayKey ?: existing.displayKey
                )
            )
            uploadSongInBackground(songId)
        }
    }

    private fun writeBpmToMarkdown(body: String, bpm: Int): String {
        val bpmPattern = Regex(
            """^(?:\*\*)?(?:bpm|tempo)(?:\*\*)?\s*:\s*\d{2,3}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
        )
        return if (bpmPattern.containsMatchIn(body)) {
            bpmPattern.replaceFirst(body, "bpm: $bpm")
        } else {
            val lines = body.lines().toMutableList()
            val insertAt = (lines.indexOfFirst { it.isNotBlank() } + 1).coerceAtMost(lines.size)
            lines.add(insertAt, "bpm: $bpm")
            lines.joinToString("\n")
        }
    }

    fun updateMarkdownBody(songId: String, body: String) {
        viewModelScope.launch {
            val existing = songRepository.getSongById(songId) ?: return@launch
            val now = System.currentTimeMillis()
            songRepository.upsertSong(
                existing.copy(markdownBody = body, updatedAt = now, localUpdatedAt = now, isDirty = true)
            )
            uploadSongInBackground(songId)
        }
    }

    /** Single-shot flow for observing a song in the chart editor. */
    fun getSongFlow(songId: String): Flow<SongEntity?> = kotlinx.coroutines.flow.flow {
        emit(songRepository.getSongById(songId))
    }

    // ── AI normalize ──────────────────────────────────────────────────────────

    sealed class NormalizeState {
        object Idle : NormalizeState()
        object Loading : NormalizeState()
        data class Success(val normalized: String) : NormalizeState()
        data class Error(val message: String) : NormalizeState()
    }

    private val _normalizeState = MutableStateFlow<NormalizeState>(NormalizeState.Idle)
    val normalizeState: StateFlow<NormalizeState> = _normalizeState.asStateFlow()

    val hasAnthropicKey: Boolean get() = anthropicApiKey.isNotBlank()

    fun normalizeSong(songId: String) {
        viewModelScope.launch {
            _normalizeState.value = NormalizeState.Loading
            val song = songRepository.getSongById(songId) ?: run {
                _normalizeState.value = NormalizeState.Error("Song not found")
                return@launch
            }
            com.encore.core.data.ai.AnthropicClient
                .normalizeChordSheet(anthropicApiKey, song.markdownBody)
                .onSuccess { _normalizeState.value = NormalizeState.Success(it) }
                .onFailure { _normalizeState.value = NormalizeState.Error(it.message ?: "Normalize failed") }
        }
    }

    fun clearNormalizeState() { _normalizeState.value = NormalizeState.Idle }

    // ── Upload helper (residual — see class-level note, Track 1.4) ──────────

    private fun uploadSongInBackground(songId: String) {
        viewModelScope.launch {
            val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return@launch
            songRepository.invalidateRemoteCache()
            val status = songRepository.checkSyncStatus(songId)
            if (status is ContentSyncStatus.Conflict) {
                songRepository.markConflict(songId)
            } else {
                songRepository.uploadSongToCloud(userId, songId)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

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

data class ConflictResolutionState(
    val songId: String,
    val songTitle: String,
    val localBody: String,
    val remoteBody: String
)
