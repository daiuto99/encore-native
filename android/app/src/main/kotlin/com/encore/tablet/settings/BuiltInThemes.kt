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
 *
 * Default theme: Zen Studio (dark + light). Applied on first launch via
 * [AppPreferencesViewModel.init] → [AppPreferencesRepository.applyDefaultsIfNeeded].
 */
object BuiltInThemes {

    // ── Dark Mode Presets ─────────────────────────────────────────────────────

    /** DEFAULT dark theme. Cool, precise, modern. */
    val ZEN_STUDIO_DARK = ThemePreset(
        id            = "zen_studio_dark",
        name          = "Zen Studio",
        isBuiltIn     = true,
        bgColor       = "#0F1115",
        lyricColor    = "#E5E7EB",
        chordColor    = "#7DD3FC",
        harmonyColor  = "#5B4A1A",
        leadIconColor = "#7DD3FC",
        capoColor     = "#F59E0B",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#94A3B8"),
            "verse"        to SectionStyle("#60A5FA"),
            "pre-chorus"   to SectionStyle("#818CF8"),
            "chorus"       to SectionStyle("#22D3EE"),
            "bridge"       to SectionStyle("#34D399"),
            "solo"         to SectionStyle("#F59E0B"),
            "outro"        to SectionStyle("#A3E635"),
            "interlude"    to SectionStyle("#C084FC"),
            "instrumental" to SectionStyle("#F472B6"),
        )
    )

    /** Warmer, slightly more musical. Soul and character without losing clarity. */
    val ANALOG_LUXE_DARK = ThemePreset(
        id            = "analog_luxe_dark",
        name          = "Analog Luxe",
        isBuiltIn     = true,
        bgColor       = "#111315",
        lyricColor    = "#F3F4F6",
        chordColor    = "#5EEAD4",
        harmonyColor  = "#6A4B1F",
        leadIconColor = "#5EEAD4",
        capoColor     = "#FB923C",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#A8A29E"),
            "verse"        to SectionStyle("#93C5FD"),
            "pre-chorus"   to SectionStyle("#C4B5FD"),
            "chorus"       to SectionStyle("#7DD3FC"),
            "bridge"       to SectionStyle("#86EFAC"),
            "solo"         to SectionStyle("#FDBA74"),
            "outro"        to SectionStyle("#A3E635"),
            "interlude"    to SectionStyle("#FB923C"),
            "instrumental" to SectionStyle("#FCA5A5"),
        )
    )

    val MIDNIGHT_MAINSTAGE = ThemePreset(
        id            = "midnight_mainstage",
        name          = "Midnight Mainstage",
        isBuiltIn     = true,
        bgColor       = "#000000",
        lyricColor    = "#D1D1D6",
        chordColor    = "#FFD60A",
        harmonyColor  = "#FF9F0A",
        leadIconColor = "#FF9F0A",
        capoColor     = "#FF9F0A",
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
        id            = "neon_night_shift",
        name          = "Neon Night-Shift",
        isBuiltIn     = true,
        bgColor       = "#050505",
        lyricColor    = "#30D158",
        chordColor    = "#FFFFFF",
        harmonyColor  = "#BF5AF2",
        leadIconColor = "#BF5AF2",
        capoColor     = "#BF5AF2",
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

    /** DEFAULT light theme. Cool, precise, modern. */
    val ZEN_STUDIO_LIGHT = ThemePreset(
        id            = "zen_studio_light",
        name          = "Zen Studio",
        isBuiltIn     = true,
        bgColor       = "#F6F7F4",
        lyricColor    = "#1E293B",
        chordColor    = "#1D4ED8",
        harmonyColor  = "#FFF3BF",
        leadIconColor = "#2563EB",
        capoColor     = "#B45309",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#475569"),
            "verse"        to SectionStyle("#2563EB"),
            "pre-chorus"   to SectionStyle("#4F46E5"),
            "chorus"       to SectionStyle("#0369A1"),
            "bridge"       to SectionStyle("#0F766E"),
            "solo"         to SectionStyle("#B45309"),
            "outro"        to SectionStyle("#4D7C0F"),
            "interlude"    to SectionStyle("#7C3AED"),
            "instrumental" to SectionStyle("#BE123C"),
        )
    )

    /** Warmer, slightly more musical. Soul and character without losing clarity. */
    val ANALOG_LUXE_LIGHT = ThemePreset(
        id            = "analog_luxe_light",
        name          = "Analog Luxe",
        isBuiltIn     = true,
        bgColor       = "#FAF9F6",
        lyricColor    = "#292524",
        chordColor    = "#0F766E",
        harmonyColor  = "#FFE2A8",
        leadIconColor = "#0F766E",
        capoColor     = "#B45309",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#57534E"),
            "verse"        to SectionStyle("#1D4ED8"),
            "pre-chorus"   to SectionStyle("#6D28D9"),
            "chorus"       to SectionStyle("#0369A1"),
            "bridge"       to SectionStyle("#15803D"),
            "solo"         to SectionStyle("#C2410C"),
            "outro"        to SectionStyle("#3F6212"),
            "interlude"    to SectionStyle("#7C2D12"),
            "instrumental" to SectionStyle("#B91C1C"),
        )
    )

    val STUDIO_DAYLIGHT = ThemePreset(
        id            = "studio_daylight",
        name          = "Studio Daylight",
        isBuiltIn     = true,
        bgColor       = "#F2F2F7",
        lyricColor    = "#1C1C1E",
        chordColor    = "#007AFF",
        harmonyColor  = "#A35200",
        leadIconColor = "#A35200",
        capoColor     = "#A35200",
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
        id            = "bourbon_vinyl",
        name          = "Bourbon & Vinyl",
        isBuiltIn     = true,
        bgColor       = "#FDF5E6",
        lyricColor    = "#2C2C2E",
        chordColor    = "#A0522D",
        harmonyColor  = "#8B4513",
        leadIconColor = "#8B4513",
        capoColor     = "#8B4513",
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
        id            = "solar_flare",
        name          = "Solar Flare",
        isBuiltIn     = true,
        bgColor       = "#FFFFFF",
        lyricColor    = "#000000",
        chordColor    = "#0000FF",
        harmonyColor  = "#FF8C00",
        leadIconColor = "#FF8C00",
        capoColor     = "#FF8C00",
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

    // ── Index lists — Zen Studio first as the default ─────────────────────────
    val DARK:  List<ThemePreset> = listOf(ZEN_STUDIO_DARK, ANALOG_LUXE_DARK, MIDNIGHT_MAINSTAGE, NEON_NIGHT_SHIFT)
    val LIGHT: List<ThemePreset> = listOf(ZEN_STUDIO_LIGHT, ANALOG_LUXE_LIGHT, STUDIO_DAYLIGHT, BOURBON_VINYL, SOLAR_FLARE)
}
