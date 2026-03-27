package com.encore.feature.performance

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Song Detail / Performance Screen.
 *
 * Manages:
 * - Song data loading from repository
 * - Auto-scroll state and calculations
 * - Text zoom state
 * - Duration parsing from markdown metadata
 *
 * Milestone 3: Performance Engine - Teleprompter Mode
 */
class SongDetailViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    // Song data
    private val _song = MutableStateFlow<SongEntity?>(null)
    val song: StateFlow<SongEntity?> = _song.asStateFlow()

    // Auto-scroll state
    private val _isAutoScrolling = MutableStateFlow(false)
    val isAutoScrolling: StateFlow<Boolean> = _isAutoScrolling.asStateFlow()

    // Text size multiplier (1.0 = default, 0.5 = 50%, 2.0 = 200%)
    private val _textSizeMultiplier = MutableStateFlow(1.0f)
    val textSizeMultiplier: StateFlow<Float> = _textSizeMultiplier.asStateFlow()

    // Scroll speed in pixels per second
    private val _scrollSpeedPxPerSecond = MutableStateFlow(0f)
    val scrollSpeedPxPerSecond: StateFlow<Float> = _scrollSpeedPxPerSecond.asStateFlow()

    /**
     * Load song by ID from repository.
     */
    fun loadSong(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId)
            _song.value = song

            // Calculate scroll speed based on duration metadata
            song?.let { calculateScrollSpeed(it) }
        }
    }

    /**
     * Toggle auto-scroll on/off.
     */
    fun toggleAutoScroll() {
        _isAutoScrolling.value = !_isAutoScrolling.value
        Log.d(TAG, "Auto-scroll ${if (_isAutoScrolling.value) "enabled" else "disabled"}")
    }

    /**
     * Update text size multiplier (for pinch-to-zoom).
     *
     * @param multiplier New size multiplier (clamped between 0.5 and 3.0)
     */
    fun updateTextSize(multiplier: Float) {
        _textSizeMultiplier.value = multiplier.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
    }

    /**
     * Reset text size to default.
     */
    fun resetTextSize() {
        _textSizeMultiplier.value = 1.0f
    }

    /**
     * Calculate scroll speed based on song duration.
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
        private const val DEFAULT_DURATION_SECONDS = 180 // 3 minutes
        private const val ESTIMATED_LINES = 50
        private const val PIXELS_PER_LINE = 100f

        // Text size constraints
        private const val MIN_TEXT_SIZE = 0.5f  // 50%
        private const val MAX_TEXT_SIZE = 3.0f  // 300%
    }
}
