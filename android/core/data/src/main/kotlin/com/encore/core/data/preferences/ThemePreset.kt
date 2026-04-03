package com.encore.core.data.preferences

/**
 * A named snapshot of one theme mode's visual configuration.
 *
 * Built-in presets ([isBuiltIn] = true) are hardcoded constants — they are never
 * persisted to DataStore and cannot be deleted.
 *
 * User-saved presets ([isBuiltIn] = false) are stored as a JSON array under
 * [AppPreferencesRepository.Keys.DARK_USER_PRESETS] or LIGHT_USER_PRESETS.
 *
 * Loading a preset writes all its fields atomically into the active theme's
 * DataStore keys via [AppPreferencesRepository.loadPreset].
 */
data class ThemePreset(
    val id: String,
    val name: String,
    val isBuiltIn: Boolean = false,
    val bgColor: String,
    val lyricColor: String,
    val chordColor: String,
    val harmonyColor: String,
    val sectionStyles: Map<String, SectionStyle>
)
