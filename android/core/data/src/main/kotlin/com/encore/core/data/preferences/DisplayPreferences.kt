package com.encore.core.data.preferences

/**
 * Legacy display preferences shim — constants only.
 *
 * The mutable singleton (DisplayPreferencesHolder) has been replaced by
 * [AppPreferencesRepository] backed by DataStore. This file is kept for
 * any call sites that still reference SET_COLOR_HEXES or DEFAULT_SECTION_COLORS
 * during the migration.
 */
object DisplayPreferences {
    /** Global persistent colors for Sets 1-4. */
    val SET_COLOR_HEXES = mapOf(
        1 to "#3B82F6",  // Blue
        2 to "#F97316",  // Orange
        3 to "#10B981",  // Green
        4 to "#8B5CF6"   // Purple
    )
}
