package com.encore.core.data.preferences

/**
 * Font family choice for chart and lyric rendering.
 */
enum class SongFontFamily(val displayName: String) {
    SANS_SERIF("Sans-Serif"),
    MONOSPACE("Monospace")
}

/**
 * Per-section visual style stored in the global preference engine.
 */
data class SectionStyle(
    val hexColor: String,
    val fontSize: Int = 16,
    val isBold: Boolean = true
)

/**
 * Global app-wide display preferences persisted to DataStore.
 *
 * Replaces the in-memory DisplayPreferences + DisplayPreferencesHolder singleton.
 * Consumed as a StateFlow in LibraryViewModel and SongDetailScreen.
 */
data class AppPreferences(
    // ── Per-theme section styles matrix ──────────────────────────────────────
    val darkSectionStyles: Map<String, SectionStyle> = DEFAULT_SECTION_STYLES,
    val lightSectionStyles: Map<String, SectionStyle> = DEFAULT_SECTION_STYLES,

    // ── Typography & rhythm ──────────────────────────────────────────────────
    val lyricSize: Int = 14,
    val chordSpacing: Float = 0f,       // extra dp padding between chord line and lyric line

    // ── Performance mode flags ───────────────────────────────────────────────
    val showLeadIndicator: Boolean = true,
    val showTranspositionWarning: Boolean = true,
    val showChords: Boolean = true,
    val showKeyInfo: Boolean = true,

    // ── Dual-theme background colors ─────────────────────────────────────────
    val darkBgColor: String = "#000000",
    val lightBgColor: String = "#F2F2F7",

    // ── Dual-theme body text colors ───────────────────────────────────────────
    val darkLyricColor: String = "#D1D1D6",   // Midnight Mainstage default
    val lightLyricColor: String = "#1C1C1E",  // Studio Daylight default
    val darkChordColor: String = "#FFD60A",
    val lightChordColor: String = "#007AFF",
    val darkHarmonyColor: String = "#FF9F0A",
    val lightHarmonyColor: String = "#A35200",

    // ── Font family ───────────────────────────────────────────────────────────
    val fontFamily: SongFontFamily = SongFontFamily.SANS_SERIF
) {
    companion object {
        val DEFAULT_SECTION_STYLES = mapOf(
            "intro"          to SectionStyle("#3882F6"),
            "verse"          to SectionStyle("#F97316"),
            "chorus"         to SectionStyle("#EF4444"),
            "bridge"         to SectionStyle("#885CF6"),
            "outro"          to SectionStyle("#F59E0B"),
            "solo"           to SectionStyle("#10B981"),
            "interlude"      to SectionStyle("#06B6D4"),
            "instrumental"   to SectionStyle("#EC4899"),
            "pre-chorus"     to SectionStyle("#F97316"),
        )

        /** Returns the hex color for a section name from the active theme's style map. */
        fun getSectionColor(sectionName: String, prefs: AppPreferences, isDark: Boolean = true): String? {
            val styles = if (isDark) prefs.darkSectionStyles else prefs.lightSectionStyles
            val normalized = sectionName.lowercase().trim()
            return styles.entries
                .firstOrNull { (key, _) -> normalized.contains(key.lowercase()) }
                ?.value?.hexColor
        }
    }
}
