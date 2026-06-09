package com.encore.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Set cover color system — 10-color cycling Apple Music–style palette.
 *
 * Each set gets a (bg, fg) pair from the palette, cycling for sets beyond 10.
 * Used for SetTile album art, SetBuilderScreen hero, and SongTile initials.
 */
data class SetCoverColors(val bg: Color, val fg: Color)

val SET_COVER_PALETTE = listOf(
    SetCoverColors(Color(0xFFF87171), Color(0xFF7F1D1D)), // coral
    SetCoverColors(Color(0xFFFB923C), Color(0xFF7C2D12)), // tangerine
    SetCoverColors(Color(0xFFFBBF24), Color(0xFF78350F)), // amber
    SetCoverColors(Color(0xFFA3E635), Color(0xFF365314)), // lime
    SetCoverColors(Color(0xFF34D399), Color(0xFF064E3B)), // mint
    SetCoverColors(Color(0xFF22D3EE), Color(0xFF164E63)), // cyan
    SetCoverColors(Color(0xFF60A5FA), Color(0xFF1E3A8A)), // sky
    SetCoverColors(Color(0xFFA78BFA), Color(0xFF4C1D95)), // lavender
    SetCoverColors(Color(0xFFF472B6), Color(0xFF831843)), // rose
    SetCoverColors(Color(0xFF94A3B8), Color(0xFF0F172A)), // graphite
)

/**
 * Returns the cover color pair for a set number (cycles every 10).
 *
 * Uses floorMod so non-positive set numbers (e.g. -1 for a song opened with no
 * set context, as from performance-mode quick search) wrap into range instead of
 * producing a negative index and crashing.
 */
fun setCoverColors(setNumber: Int): SetCoverColors =
    SET_COVER_PALETTE[Math.floorMod(setNumber - 1, SET_COVER_PALETTE.size)]

/**
 * Stable hash-derived cover for songs not in a set.
 * Maps any string id to one of the 10 palette entries.
 */
fun songCoverColors(songId: String): SetCoverColors {
    var h = 0
    for (c in songId) h = (h * 31 + c.code) or 0
    return SET_COVER_PALETTE[Math.abs(h) % SET_COVER_PALETTE.size]
}

object SetColor {

    /**
     * Legacy single-color accessor kept for backward compatibility with SongDetailScreen
     * and other existing components. Returns the bg color for the set.
     */
    fun getSetColor(setNumber: Int): Color = setCoverColors(setNumber).bg

    /**
     * Get background color for a set based on its number.
     *
     * Uses Material 3 container colors in rotation for visual distinction.
     * Colors are mild and readable for long-form content.
     *
     * @param setNumber Set number (1, 2, 3, etc.)
     * @param colorScheme Material 3 color scheme (light or dark)
     * @return Container color for the set
     */
    fun getSetContainerColor(setNumber: Int, colorScheme: ColorScheme): Color {
        return when ((setNumber - 1) % 6) {
            0 -> colorScheme.primaryContainer
            1 -> colorScheme.secondaryContainer
            2 -> colorScheme.tertiaryContainer
            3 -> colorScheme.errorContainer.copy(alpha = 0.3f) // Soften error color
            4 -> colorScheme.surfaceVariant
            5 -> colorScheme.surfaceContainer
            else -> colorScheme.primaryContainer
        }
    }

    /**
     * Get text color for a set based on its number.
     *
     * Returns the appropriate "onContainer" color for readability.
     *
     * @param setNumber Set number (1, 2, 3, etc.)
     * @param colorScheme Material 3 color scheme (light or dark)
     * @return Text color for the set
     */
    fun getSetContentColor(setNumber: Int, colorScheme: ColorScheme): Color {
        return when ((setNumber - 1) % 6) {
            0 -> colorScheme.onPrimaryContainer
            1 -> colorScheme.onSecondaryContainer
            2 -> colorScheme.onTertiaryContainer
            3 -> colorScheme.onErrorContainer
            4 -> colorScheme.onSurfaceVariant
            5 -> colorScheme.onSurface
            else -> colorScheme.onPrimaryContainer
        }
    }

    /**
     * Get a compact badge color for showing set membership in Library.
     *
     * Uses slightly more saturated colors for small chips.
     *
     * @param setNumber Set number (1, 2, 3, etc.)
     * @param colorScheme Material 3 color scheme (light or dark)
     * @return Badge background color
     */
    fun getSetBadgeColor(setNumber: Int, colorScheme: ColorScheme): Color {
        return when ((setNumber - 1) % 6) {
            0 -> colorScheme.primary.copy(alpha = 0.2f)
            1 -> colorScheme.secondary.copy(alpha = 0.2f)
            2 -> colorScheme.tertiary.copy(alpha = 0.2f)
            3 -> colorScheme.error.copy(alpha = 0.15f)
            4 -> colorScheme.outline.copy(alpha = 0.1f)
            5 -> colorScheme.surfaceTint.copy(alpha = 0.15f)
            else -> colorScheme.primary.copy(alpha = 0.2f)
        }
    }

    /**
     * Get badge text color.
     *
     * @param setNumber Set number (1, 2, 3, etc.)
     * @param colorScheme Material 3 color scheme (light or dark)
     * @return Badge text color
     */
    fun getSetBadgeTextColor(setNumber: Int, colorScheme: ColorScheme): Color {
        return when ((setNumber - 1) % 6) {
            0 -> colorScheme.primary
            1 -> colorScheme.secondary
            2 -> colorScheme.tertiary
            3 -> colorScheme.error
            4 -> colorScheme.outline
            5 -> colorScheme.onSurface
            else -> colorScheme.primary
        }
    }
}
