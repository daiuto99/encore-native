package com.encore.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Encore two-profile color system.
 *
 * Dark (Stage): True black + graphite cards — maximum contrast on stage.
 * Light (Classic): Apple system gray + white cards — high-contrast daylight use.
 *
 * All UI components that render visible text or backgrounds must reference
 * [LocalEncoreColors].current instead of hardcoded hex values so that the
 * Sun/Moon toggle switches both profiles simultaneously.
 */
data class EncoreColors(
    /** Full-screen background. */
    val screenBackground: Color,
    /** Song row / card background. */
    val cardBackground: Color,
    /** Card drop shadow depth. Use 0 on true black (invisible); 2dp on light. */
    val cardElevation: Dp,
    /** Primary text: song title, section labels. */
    val titleText: Color,
    /** Secondary text: artist name, subtitles. */
    val artistText: Color,
    /** Inline separator between title and artist (" — "). */
    val separatorText: Color,
    /** Utility icon tint (upload, settings, refresh, toggle). */
    val iconTint: Color,
    /** Faint supporting text: version badge, chip labels. */
    val subtleText: Color,
    /** Search bar container background. */
    val searchBarBackground: Color,
    /** Base color for performance viewer lyric/body text. */
    val lyricText: Color,
    /** Thin divider lines between sections. */
    val divider: Color,
    val isDark: Boolean,
)

val DarkEncoreColors = EncoreColors(
    screenBackground = Color(0xFF000000),
    cardBackground = Color(0xFF1C1C1E),
    cardElevation = 0.dp,
    titleText = Color.White,
    artistText = Color.White.copy(alpha = 0.6f),
    separatorText = Color.White.copy(alpha = 0.25f),
    iconTint = Color.White.copy(alpha = 0.7f),
    subtleText = Color.White.copy(alpha = 0.45f),
    searchBarBackground = Color(0xFF2C2C2E),
    lyricText = Color.White,
    divider = Color.White.copy(alpha = 0.12f),
    isDark = true,
)

val LightEncoreColors = EncoreColors(
    screenBackground = Color(0xFFF2F2F7),
    cardBackground = Color.White,
    cardElevation = 2.dp,
    titleText = Color.Black,
    artistText = Color.Black.copy(alpha = 0.6f),
    separatorText = Color.Black.copy(alpha = 0.25f),
    iconTint = Color.Black.copy(alpha = 0.7f),
    subtleText = Color.Black.copy(alpha = 0.45f),
    searchBarBackground = Color.White,
    lyricText = Color.Black,
    divider = Color.Black.copy(alpha = 0.12f),
    isDark = false,
)

/**
 * Provides the active [EncoreColors] to all descendants.
 * Set at the root [MainScreen] level via [CompositionLocalProvider].
 *
 * Defaults to [DarkEncoreColors] so previews and tests that omit the provider
 * always render in the safe stage-dark profile.
 */
val LocalEncoreColors = staticCompositionLocalOf { DarkEncoreColors }
