package com.encore.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Set Color Helper for Setlist UI.
 *
 * Provides distinct, mild background colors using Material 3 tonal palettes.
 * Each set number gets a different color to visually distinguish sets in the UI.
 */
object SetColor {

    /**
     * Global persistent color for a set number (1-4).
     * These are fixed, vivid colors shared across Library circles, Sets chips, and any future UI.
     *
     * @param setNumber Set number (1-4)
     * @return Vivid Color for this set
     */
    fun getSetColor(setNumber: Int): Color {
        return when (setNumber) {
            1 -> Color(0xFF3B82F6) // Blue
            2 -> Color(0xFFF97316) // Orange
            3 -> Color(0xFF10B981) // Green
            4 -> Color(0xFF8B5CF6) // Purple
            else -> Color(0xFF6B7280) // Gray fallback
        }
    }

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
