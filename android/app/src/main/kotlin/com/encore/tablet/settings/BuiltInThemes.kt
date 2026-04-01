package com.encore.tablet.settings

import com.encore.core.data.preferences.SectionStyle
import com.encore.core.data.preferences.ThemePreset

/**
 * Factory presets shipped with the app.
 *
 * These are hardcoded constants — never stored in DataStore, never deletable.
 * [DARK] presets apply to the Dark Mode tab; [LIGHT] presets apply to Light Mode.
 *
 * Loading a preset calls [AppPreferencesViewModel.loadPreset] which writes all
 * values atomically into the appropriate theme's DataStore keys.
 */
object BuiltInThemes {

    // ── Dark Mode Presets ─────────────────────────────────────────────────────

    val MIDNIGHT_MAINSTAGE = ThemePreset(
        id = "midnight_mainstage",
        name = "Midnight Mainstage",
        isBuiltIn = true,
        bgColor = "#000000",
        lyricColor = "#D1D1D6",
        chordColor = "#FFD60A",
        harmonyColor = "#FF9F0A",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#8E8E93"),
            "verse"        to SectionStyle("#32ADE6"),
            "pre-chorus"   to SectionStyle("#FF7570"),
            "chorus"       to SectionStyle("#FF453A"),
            "bridge"       to SectionStyle("#AF52DE"),
            "solo"         to SectionStyle("#AF52DE"),
            "outro"        to SectionStyle("#8E8E93"),
            "interlude"    to SectionStyle("#8E8E93"),
            "instrumental" to SectionStyle("#AF52DE"),
        )
    )

    val NEON_NIGHT_SHIFT = ThemePreset(
        id = "neon_night_shift",
        name = "Neon Night-Shift",
        isBuiltIn = true,
        bgColor = "#050505",
        lyricColor = "#30D158",
        chordColor = "#FFFFFF",
        harmonyColor = "#BF5AF2",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#008000"),
            "verse"        to SectionStyle("#30D158"),
            "pre-chorus"   to SectionStyle("#FFE04D"),
            "chorus"       to SectionStyle("#FFD700"),
            "bridge"       to SectionStyle("#00CED1"),
            "solo"         to SectionStyle("#00CED1"),
            "outro"        to SectionStyle("#008000"),
            "interlude"    to SectionStyle("#008000"),
            "instrumental" to SectionStyle("#00CED1"),
        )
    )

    // ── Light Mode Presets ────────────────────────────────────────────────────

    val STUDIO_DAYLIGHT = ThemePreset(
        id = "studio_daylight",
        name = "Studio Daylight",
        isBuiltIn = true,
        bgColor = "#F2F2F7",
        lyricColor = "#1C1C1E",
        chordColor = "#007AFF",
        harmonyColor = "#A35200",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#636366"),
            "verse"        to SectionStyle("#0040DD"),
            "pre-chorus"   to SectionStyle("#E8334A"),
            "chorus"       to SectionStyle("#D70015"),
            "bridge"       to SectionStyle("#8944AB"),
            "solo"         to SectionStyle("#8944AB"),
            "outro"        to SectionStyle("#636366"),
            "interlude"    to SectionStyle("#636366"),
            "instrumental" to SectionStyle("#8944AB"),
        )
    )

    val BOURBON_VINYL = ThemePreset(
        id = "bourbon_vinyl",
        name = "Bourbon & Vinyl",
        isBuiltIn = true,
        bgColor = "#FDF5E6",
        lyricColor = "#2C2C2E",
        chordColor = "#A0522D",
        harmonyColor = "#8B4513",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#708090"),
            "verse"        to SectionStyle("#2F4F4F"),
            "pre-chorus"   to SectionStyle("#C94444"),
            "chorus"       to SectionStyle("#B22222"),
            "bridge"       to SectionStyle("#483D8B"),
            "solo"         to SectionStyle("#483D8B"),
            "outro"        to SectionStyle("#708090"),
            "interlude"    to SectionStyle("#708090"),
            "instrumental" to SectionStyle("#483D8B"),
        )
    )

    val SOLAR_FLARE = ThemePreset(
        id = "solar_flare",
        name = "Solar Flare",
        isBuiltIn = true,
        bgColor = "#FFFFFF",
        lyricColor = "#000000",
        chordColor = "#0000FF",
        harmonyColor = "#FF8C00",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#7F7F7F"),
            "verse"        to SectionStyle("#000000"),
            "pre-chorus"   to SectionStyle("#FF4040"),
            "chorus"       to SectionStyle("#FF0000"),
            "bridge"       to SectionStyle("#800080"),
            "solo"         to SectionStyle("#800080"),
            "outro"        to SectionStyle("#7F7F7F"),
            "interlude"    to SectionStyle("#7F7F7F"),
            "instrumental" to SectionStyle("#800080"),
        )
    )

    // ── Index lists — declared after presets so forward references compile ────
    val DARK: List<ThemePreset> = listOf(MIDNIGHT_MAINSTAGE, NEON_NIGHT_SHIFT)
    val LIGHT: List<ThemePreset> = listOf(STUDIO_DAYLIGHT, BOURBON_VINYL, SOLAR_FLARE)
}
