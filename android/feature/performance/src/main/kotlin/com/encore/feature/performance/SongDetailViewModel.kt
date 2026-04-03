package com.encore.feature.performance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Song Detail / Performance Screen.
 *
 * Manages:
 * - Song data loading from repository
 * - Per-song zoom level persistence with debouncing
 * - Text zoom state
 * - Auto-scroll state (retained for Post-v1, not used in UI)
 * - Duration parsing from markdown metadata
 *
 * Milestone 3: Performance Engine - Production Mode
 */
class SongDetailViewModel(
    private val songRepository: SongRepository,
    private val setlistRepository: SetlistRepository
) : ViewModel() {

    // Song data
    private val _song = MutableStateFlow<SongEntity?>(null)
    val song: StateFlow<SongEntity?> = _song.asStateFlow()

    // Auto-scroll state (retained for Post-v1, not used in current UI)
    private val _isAutoScrolling = MutableStateFlow(false)
    val isAutoScrolling: StateFlow<Boolean> = _isAutoScrolling.asStateFlow()

    // Text size multiplier (1.0 = default, 0.5 = 50%, 2.0 = 200%)
    private val _textSizeMultiplier = MutableStateFlow(1.0f)
    val textSizeMultiplier: StateFlow<Float> = _textSizeMultiplier.asStateFlow()

    // Scroll speed in pixels per second (retained for Post-v1)
    private val _scrollSpeedPxPerSecond = MutableStateFlow(0f)
    val scrollSpeedPxPerSecond: StateFlow<Float> = _scrollSpeedPxPerSecond.asStateFlow()

    // Set navigation context
    private val _currentSetNumber = MutableStateFlow(-1)
    val currentSetNumber: StateFlow<Int> = _currentSetNumber.asStateFlow()

    private val _prevSongId = MutableStateFlow<String?>(null)
    val prevSongId: StateFlow<String?> = _prevSongId.asStateFlow()

    private val _nextSongId = MutableStateFlow<String?>(null)
    val nextSongId: StateFlow<String?> = _nextSongId.asStateFlow()

    // Set context for the Performance Context Bar
    private val _setName = MutableStateFlow("")
    val setName: StateFlow<String> = _setName.asStateFlow()

    private val _prevSong = MutableStateFlow<SongEntity?>(null)
    val prevSong: StateFlow<SongEntity?> = _prevSong.asStateFlow()

    private val _nextSong = MutableStateFlow<SongEntity?>(null)
    val nextSong: StateFlow<SongEntity?> = _nextSong.asStateFlow()

    // Save / Load set operations
    private val _currentSetId = MutableStateFlow<String?>(null)

    /** All named setlists — drives the Load dialog picker. */
    val setlists: StateFlow<List<SetlistEntity>> = setlistRepository.getSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saveSuccess = MutableStateFlow<String?>(null)
    val saveSuccess: StateFlow<String?> = _saveSuccess.asStateFlow()

    /** Incremented when loadSetlist() completes so the composable can reset to page 0. */
    private val _pagerResetTrigger = MutableStateFlow(0)
    val pagerResetTrigger: StateFlow<Int> = _pagerResetTrigger.asStateFlow()

    // Song cache + pager state
    private val _songCache = MutableStateFlow<Map<String, SongEntity>>(emptyMap())
    private val _performSongIds = MutableStateFlow<List<String>>(emptyList())
    val performSongIds: StateFlow<List<String>> = _performSongIds.asStateFlow()

    /**
     * Returns a Flow for a specific song page — loads and caches on first call.
     * Adjacent pages collect independently, enabling smooth pager previews.
     */
    fun getSongForPage(songId: String): Flow<SongEntity?> {
        if (!_songCache.value.containsKey(songId)) {
            viewModelScope.launch {
                songRepository.getSongById(songId)?.let { s ->
                    _songCache.value = _songCache.value + (songId to s)
                }
            }
        }
        return _songCache.map { it[songId] }
    }

    /** Called by pager when the visible page changes — updates main song state + zoom. */
    fun onPageChanged(songId: String, setNumber: Int) {
        _songCache.value[songId]?.let { cached ->
            _song.value = cached
            _textSizeMultiplier.value = cached.lastZoomLevel
            _currentSetNumber.value = setNumber
        } ?: viewModelScope.launch {
            // Not yet cached — load synchronously and update
            songRepository.getSongById(songId)?.let { s ->
                _songCache.value = _songCache.value + (songId to s)
                _song.value = s
                _textSizeMultiplier.value = s.lastZoomLevel
                _currentSetNumber.value = setNumber
            }
        }
        // Recompute prev/next for arrow affordance and context bar
        val ids = _performSongIds.value
        val idx = ids.indexOf(songId)
        _prevSongId.value = ids.getOrNull(idx - 1)
        _nextSongId.value = ids.getOrNull(idx + 1)
        _prevSong.value = ids.getOrNull(idx - 1)?.let { _songCache.value[it] }
        _nextSong.value = ids.getOrNull(idx + 1)?.let { _songCache.value[it] }
    }

    // Debounce job for saving zoom level
    private var saveZoomJob: Job? = null

    /**
     * Load song by ID from repository.
     * Restores saved zoom level and computes prev/next navigation within the set.
     *
     * @param songId Song UUID to load
     * @param setNumber Set context for prev/next navigation (-1 = no set context)
     */
    fun loadSong(songId: String, setNumber: Int = -1) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId)
            _song.value = song
            _currentSetNumber.value = setNumber

            // Restore saved zoom level
            song?.let {
                _textSizeMultiplier.value = it.lastZoomLevel
                Log.d(TAG, "Loaded song: ${it.title}, zoom level: ${it.lastZoomLevel}")
                calculateScrollSpeed(it)
            }

            // Populate pager song list and cache first few entries
            try {
                if (setNumber > 0) {
                    val setEntity = setlistRepository.getOrCreateSetByNumber(setNumber)
                    _currentSetId.value = setEntity.id
                    val songsInSet = setlistRepository.getSongsInSet(setEntity.id).first()
                        .map { it.song }
                    _performSongIds.value = songsInSet.map { it.id }
                    // Seed cache with first 3 songs for instant pager previews
                    val seedCache = _songCache.value.toMutableMap()
                    songsInSet.take(3).forEach { s -> seedCache[s.id] = s }
                    _songCache.value = seedCache
                    val idx = songsInSet.indexOfFirst { it.id == songId }
                    _prevSongId.value = if (idx > 0) songsInSet[idx - 1].id else null
                    _nextSongId.value = if (idx < songsInSet.size - 1) songsInSet[idx + 1].id else null
                    _prevSong.value = if (idx > 0) songsInSet[idx - 1] else null
                    _nextSong.value = if (idx < songsInSet.size - 1) songsInSet[idx + 1] else null
                    // Resolve setlist name for context bar
                    val setlistName = try {
                        setlistRepository.getSetlistWithSets(setEntity.setlistId)?.setlist?.name
                    } catch (e: Exception) { null }
                    _setName.value = setlistName ?: "Set $setNumber"
                    Log.d(TAG, "Set $setNumber (id=${setEntity.id}): ${songsInSet.size} songs, idx=$idx")
                } else {
                    _performSongIds.value = if (song != null) listOf(songId) else emptyList()
                    _prevSongId.value = null
                    _nextSongId.value = null
                    _prevSong.value = null
                    _nextSong.value = null
                    _setName.value = ""
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load set $setNumber context, falling back to single-song", e)
                _performSongIds.value = listOf(songId)
                _prevSongId.value = null
                _nextSongId.value = null
            }
        }
    }

    /**
     * Toggle auto-scroll on/off.
     * NOTE: Retained for Post-v1, not used in current Performance Mode UI.
     */
    fun toggleAutoScroll() {
        _isAutoScrolling.value = !_isAutoScrolling.value
        Log.d(TAG, "Auto-scroll ${if (_isAutoScrolling.value) "enabled" else "disabled"}")
    }

    /**
     * Update text size multiplier (for pinch-to-zoom and button controls).
     * Saves zoom level to database with 500ms debounce to avoid excessive writes.
     *
     * @param multiplier New size multiplier (clamped between 0.5 and 3.0)
     */
    fun updateTextSize(multiplier: Float) {
        val clampedMultiplier = multiplier.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
        _textSizeMultiplier.value = clampedMultiplier

        // Debounced save to database
        val currentSongId = _song.value?.id
        if (currentSongId != null) {
            saveZoomJob?.cancel()
            saveZoomJob = viewModelScope.launch {
                delay(SAVE_DEBOUNCE_MS)
                val result = songRepository.updateZoomLevel(currentSongId, clampedMultiplier)
                if (result.isSuccess) {
                    Log.d(TAG, "Saved zoom level: $clampedMultiplier for song: $currentSongId")
                } else {
                    Log.e(TAG, "Failed to save zoom level: ${result.exceptionOrNull()}")
                }
            }
        }
    }

    /**
     * Reset text size to default (100%).
     * Saves to database with debounce.
     */
    fun resetTextSize() {
        updateTextSize(1.0f)
    }

    /**
     * Save current Set 1 contents as a new named setlist.
     * Creates a snapshot copy — the working Set 1 is unaffected.
     */
    fun saveCurrentSet(name: String) {
        val setId = _currentSetId.value ?: return
        viewModelScope.launch {
            try {
                val result = setlistRepository.createSetlist(name)
                val newSetlistId = result.getOrNull() ?: return@launch
                val newSets = setlistRepository.getSetsForSetlist(newSetlistId).first()
                val newSetId = newSets.firstOrNull()?.id ?: return@launch
                val currentEntries = setlistRepository.getSongsInSet(setId).first()
                currentEntries.forEach { setlistRepository.addSongToSet(newSetId, it.song.id) }
                _saveSuccess.value = "Saved as \"$name\""
                Log.d(TAG, "Saved set as: $name")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save set", e)
            }
        }
    }

    /**
     * Replace Set 1 contents with songs from [setlistId] and reload the pager.
     * Destructively clears Set 1 then refills from the chosen setlist's first set.
     * Signals the composable to scroll to page 0 via [pagerResetTrigger].
     */
    fun loadSetlist(setlistId: String) {
        val setId = _currentSetId.value ?: return
        viewModelScope.launch {
            try {
                // Clear existing Set 1
                val existingEntries = setlistRepository.getSongsInSet(setId).first()
                existingEntries.forEach { setlistRepository.removeSongFromSet(it.entry.id) }

                // Copy songs from the chosen setlist's first set into Set 1
                val sets = setlistRepository.getSetsForSetlist(setlistId).first()
                val sourceSetId = sets.minByOrNull { it.number }?.id ?: return@launch
                val sourceSongs = setlistRepository.getSongsInSet(sourceSetId).first()
                sourceSongs.forEach { setlistRepository.addSongToSet(setId, it.song.id) }

                // Reload pager from the now-updated Set 1
                val newEntries = setlistRepository.getSongsInSet(setId).first()
                val newSongs = newEntries.map { it.song }
                val newIds = newSongs.map { it.id }
                _performSongIds.value = newIds

                // Reseed cache with first 3 songs
                val seedCache = mutableMapOf<String, SongEntity>()
                newSongs.take(3).forEach { s -> seedCache[s.id] = s }
                _songCache.value = seedCache

                // Update dashboard to the first song in the new set
                val firstSong = newSongs.firstOrNull()
                _song.value = firstSong
                firstSong?.let { _textSizeMultiplier.value = it.lastZoomLevel }

                // Reset prev/next to position 0
                _prevSongId.value = null
                _prevSong.value = null
                _nextSongId.value = newIds.getOrNull(1)
                _nextSong.value = newSongs.getOrNull(1)

                // Update set name in context bar
                val loadedName = try {
                    setlistRepository.getSetlistWithSets(setlistId)?.setlist?.name
                } catch (e: Exception) { null }
                _setName.value = loadedName ?: _setName.value

                // Signal composable to scroll to page 0
                _pagerResetTrigger.value += 1
                Log.d(TAG, "Loaded setlist $setlistId: ${newSongs.size} songs")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load setlist $setlistId", e)
            }
        }
    }

    /** Clear save success message after the composable has displayed it. */
    fun clearSaveSuccess() {
        _saveSuccess.value = null
    }

    /**
     * Calculate scroll speed based on song duration.
     * NOTE: Retained for Post-v1 auto-scroll feature.
     *
     * Parses duration from markdown metadata:
     * - "**Duration:** 3:30" → 210 seconds
     * - "Duration: 4:15" → 255 seconds
     * - No duration found → Default 180 seconds (3 minutes)
     *
     * Assumes average content height and calculates px/second to finish scrolling
     * within the song duration.
     */
    private fun calculateScrollSpeed(song: SongEntity) {
        val durationSeconds = parseDuration(song.markdownBody) ?: DEFAULT_DURATION_SECONDS

        // Estimate: 100px per line, ~50 lines average song
        // This will be adjusted dynamically in the UI based on actual content height
        val estimatedContentHeight = ESTIMATED_LINES * PIXELS_PER_LINE

        val speedPxPerSecond = estimatedContentHeight / durationSeconds.toFloat()
        _scrollSpeedPxPerSecond.value = speedPxPerSecond

        Log.d(TAG, "Calculated scroll speed: $speedPxPerSecond px/sec (duration: $durationSeconds sec)")
    }

    /**
     * Parse duration from markdown content.
     * NOTE: Retained for Post-v1 auto-scroll feature.
     *
     * Handles formats:
     * - "**Duration:** 3:30"
     * - "Duration: 4:15"
     * - "[Duration: 2:45]"
     *
     * @param content Markdown file content
     * @return Duration in seconds, or null if not found
     */
    private fun parseDuration(content: String): Int? {
        // Regex patterns for duration detection
        val patterns = listOf(
            // Match "**Duration:**" with optional asterisks (Obsidian bold)
            """(?i)\*?\*?Duration:\*?\*?\s*(\d+):(\d+)""".toRegex(),

            // Match "Duration: 3:30" without bold at start of line
            """(?i)^\s*duration\s*:\s*(\d+):(\d+)""".toRegex(RegexOption.MULTILINE),

            // Match "[Duration: 3:30]" bracketed format
            """\[\s*(?i)duration\s*:\s*(\d+):(\d+)\s*\]""".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(content)
            if (match != null) {
                val minutes = match.groupValues[1].toIntOrNull() ?: 0
                val seconds = match.groupValues[2].toIntOrNull() ?: 0
                val totalSeconds = minutes * 60 + seconds
                Log.d(TAG, "Found duration: $minutes:$seconds ($totalSeconds seconds)")
                return totalSeconds
            }
        }

        Log.d(TAG, "No duration found, using default: $DEFAULT_DURATION_SECONDS seconds")
        return null
    }

    companion object {
        private const val TAG = "SongDetailViewModel"

        // Auto-scroll constants (retained for Post-v1)
        private const val DEFAULT_DURATION_SECONDS = 180 // 3 minutes
        private const val ESTIMATED_LINES = 50
        private const val PIXELS_PER_LINE = 100f

        // Text size constraints
        private const val MIN_TEXT_SIZE = 0.5f  // 50%
        private const val MAX_TEXT_SIZE = 3.0f  // 300%

        // Zoom level persistence
        private const val SAVE_DEBOUNCE_MS = 500L  // 500ms debounce for database writes
    }
}
