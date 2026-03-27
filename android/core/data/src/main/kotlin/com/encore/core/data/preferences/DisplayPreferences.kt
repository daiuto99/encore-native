package com.encore.core.data.preferences

/**
 * Display preferences for Performance Mode.
 *
 * Global settings that control what is displayed in the song viewer.
 * Stored in-memory for now, will be persisted to DataStore in future milestone.
 */
data class DisplayPreferences(
    val showChords: Boolean = true,
    val showKeyInfo: Boolean = true,
    val sectionColors: Map<String, String> = DEFAULT_SECTION_COLORS
) {
    companion object {
        /**
         * Global persistent colors for Sets 1-4.
         * Used consistently across Library rows, Sets footer chips, and performance mode.
         */
        val SET_COLOR_HEXES = mapOf(
            1 to "#3B82F6",  // Blue
            2 to "#F97316",  // Orange
            3 to "#10B981",  // Green
            4 to "#8B5CF6"   // Purple
        )

        /**
         * Default section header colors based on design specs.
         * Maps section names to HEX color codes.
         */
        val DEFAULT_SECTION_COLORS = mapOf(
            "intro" to "#3882F6",        // Blue (design spec)
            "verse" to "#F97316",        // Orange (design spec)
            "chorus" to "#EF4444",       // Red (design spec)
            "bridge" to "#885CF6",       // Purple (design spec)
            "outro" to "#F59E0B",        // Amber (design spec)
            "solo" to "#10B981",         // Green (design spec)
            "interlude" to "#06B6D4",    // Cyan (design spec)
            "instrumental" to "#EC4899"  // Pink (design spec)
        )

        /**
         * Get color for a section header.
         * Case-insensitive matching.
         */
        fun getSectionColor(sectionName: String, preferences: DisplayPreferences): String? {
            val normalizedName = sectionName.lowercase().trim()
            return preferences.sectionColors.entries.firstOrNull { (key, _) ->
                normalizedName.contains(key.lowercase())
            }?.value
        }
    }
}

/**
 * Singleton holder for display preferences.
 * TODO: Replace with proper DataStore persistence in future milestone.
 */
object DisplayPreferencesHolder {
    private var _preferences = DisplayPreferences()

    fun getPreferences(): DisplayPreferences = _preferences

    fun updatePreferences(preferences: DisplayPreferences) {
        _preferences = preferences
    }

    fun toggleShowChords() {
        _preferences = _preferences.copy(showChords = !_preferences.showChords)
    }

    fun toggleShowKeyInfo() {
        _preferences = _preferences.copy(showKeyInfo = !_preferences.showKeyInfo)
    }
}
