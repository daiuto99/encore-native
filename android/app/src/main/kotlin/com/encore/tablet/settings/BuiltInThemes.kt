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
        bgColor       = "#1A0F08",
        lyricColor    = "#F5E6D3",
        chordColor    = "#D4A574",
        harmonyColor  = "#3D2817",
        leadIconColor = "#E8B976",
        capoColor     = "#A0522D",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#8B7355"),
            "verse"        to SectionStyle("#D4A574"),
            "pre-chorus"   to SectionStyle("#C19660"),
            "chorus"       to SectionStyle("#E8B976"),
            "bridge"       to SectionStyle("#A0826D"),
            "solo"         to SectionStyle("#FF9F45"),
            "outro"        to SectionStyle("#9C8062"),
            "interlude"    to SectionStyle("#B8956A"),
            "instrumental" to SectionStyle("#D9A876"),
        )
    )

    val MIDNIGHT_MAINSTAGE = ThemePreset(
        id            = "midnight_mainstage",
        name          = "Midnight Mainstage",
        isBuiltIn     = true,
        bgColor       = "#000000",
        lyricColor    = "#FAFAFA",
        chordColor    = "#FFD60A",
        harmonyColor  = "#3A2F00",
        leadIconColor = "#FFD60A",
        capoColor     = "#FF6B35",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#A1A1AA"),
            "verse"        to SectionStyle("#60A5FA"),
            "pre-chorus"   to SectionStyle("#A78BFA"),
            "chorus"       to SectionStyle("#FF6B35"),
            "bridge"       to SectionStyle("#34D399"),
            "solo"         to SectionStyle("#FFD60A"),
            "outro"        to SectionStyle("#94A3B8"),
            "interlude"    to SectionStyle("#F472B6"),
            "instrumental" to SectionStyle("#22D3EE"),
        )
    )

    val NEON_NIGHT_SHIFT = ThemePreset(
        id            = "neon_night_shift",
        name          = "Neon Night-Shift",
        isBuiltIn     = true,
        bgColor       = "#0A0A1A",
        lyricColor    = "#E0E0FF",
        chordColor    = "#FF10F0",
        harmonyColor  = "#2A0E2A",
        leadIconColor = "#00FFFF",
        capoColor     = "#39FF14",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#A0A0C0"),
            "verse"        to SectionStyle("#00FFFF"),
            "pre-chorus"   to SectionStyle("#FF10F0"),
            "chorus"       to SectionStyle("#39FF14"),
            "bridge"       to SectionStyle("#FF6EC7"),
            "solo"         to SectionStyle("#FFEA00"),
            "outro"        to SectionStyle("#9D00FF"),
            "interlude"    to SectionStyle("#FF6B35"),
            "instrumental" to SectionStyle("#00FF9F"),
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
        bgColor       = "#FAF5EE",
        lyricColor    = "#3D2817",
        chordColor    = "#8B4513",
        harmonyColor  = "#F5DEB3",
        leadIconColor = "#A0522D",
        capoColor     = "#654321",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#6B5841"),
            "verse"        to SectionStyle("#8B4513"),
            "pre-chorus"   to SectionStyle("#A0522D"),
            "chorus"       to SectionStyle("#654321"),
            "bridge"       to SectionStyle("#8B6F47"),
            "solo"         to SectionStyle("#B8651F"),
            "outro"        to SectionStyle("#7A5C3F"),
            "interlude"    to SectionStyle("#9B7653"),
            "instrumental" to SectionStyle("#6E5239"),
        )
    )

    val STUDIO_DAYLIGHT = ThemePreset(
        id            = "studio_daylight",
        name          = "Studio Daylight",
        isBuiltIn     = true,
        bgColor       = "#FFFFFF",
        lyricColor    = "#0F172A",
        chordColor    = "#0284C7",
        harmonyColor  = "#FEF3C7",
        leadIconColor = "#0369A1",
        capoColor     = "#C2410C",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#475569"),
            "verse"        to SectionStyle("#0284C7"),
            "pre-chorus"   to SectionStyle("#6366F1"),
            "chorus"       to SectionStyle("#0F766E"),
            "bridge"       to SectionStyle("#7C3AED"),
            "solo"         to SectionStyle("#C2410C"),
            "outro"        to SectionStyle("#65A30D"),
            "interlude"    to SectionStyle("#DB2777"),
            "instrumental" to SectionStyle("#9333EA"),
        )
    )

    val BOURBON_VINYL = ThemePreset(
        id            = "bourbon_vinyl",
        name          = "Bourbon & Vinyl",
        isBuiltIn     = true,
        bgColor       = "#F4EDDF",
        lyricColor    = "#2C1810",
        chordColor    = "#7C2D12",
        harmonyColor  = "#FBE4C2",
        leadIconColor = "#9A3412",
        capoColor     = "#451A03",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#78716C"),
            "verse"        to SectionStyle("#7C2D12"),
            "pre-chorus"   to SectionStyle("#92400E"),
            "chorus"       to SectionStyle("#451A03"),
            "bridge"       to SectionStyle("#3F3F46"),
            "solo"         to SectionStyle("#9A3412"),
            "outro"        to SectionStyle("#52525B"),
            "interlude"    to SectionStyle("#86198F"),
            "instrumental" to SectionStyle("#365314"),
        )
    )

    val SOLAR_FLARE = ThemePreset(
        id            = "solar_flare",
        name          = "Solar Flare",
        isBuiltIn     = true,
        bgColor       = "#FFF8E7",
        lyricColor    = "#1F1300",
        chordColor    = "#EA580C",
        harmonyColor  = "#FED7AA",
        leadIconColor = "#C2410C",
        capoColor     = "#7C2D12",
        sectionStyles = mapOf(
            "intro"        to SectionStyle("#A16207"),
            "verse"        to SectionStyle("#EA580C"),
            "pre-chorus"   to SectionStyle("#DC2626"),
            "chorus"       to SectionStyle("#B91C1C"),
            "bridge"       to SectionStyle("#854D0E"),
            "solo"         to SectionStyle("#F97316"),
            "outro"        to SectionStyle("#65A30D"),
            "interlude"    to SectionStyle("#9333EA"),
            "instrumental" to SectionStyle("#0369A1"),
        )
    )

    // ── Index lists — Zen Studio first as the default ─────────────────────────
    val DARK:  List<ThemePreset> = listOf(ZEN_STUDIO_DARK, ANALOG_LUXE_DARK, MIDNIGHT_MAINSTAGE, NEON_NIGHT_SHIFT)
    val LIGHT: List<ThemePreset> = listOf(ZEN_STUDIO_LIGHT, ANALOG_LUXE_LIGHT, STUDIO_DAYLIGHT, BOURBON_VINYL, SOLAR_FLARE)
}
