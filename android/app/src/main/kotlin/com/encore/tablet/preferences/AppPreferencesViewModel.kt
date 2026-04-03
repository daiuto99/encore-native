package com.encore.tablet.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.preferences.AppPreferencesRepository
import com.encore.core.data.preferences.SectionStyle
import com.encore.core.data.preferences.SongFontFamily
import com.encore.core.data.preferences.ThemePreset
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AppPreferencesViewModel(
    private val repo: AppPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = repo.appPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val darkUserPresets: StateFlow<List<ThemePreset>> = repo.darkUserPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lightUserPresets: StateFlow<List<ThemePreset>> = repo.lightUserPresets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Typography ────────────────────────────────────────────────────────────

    fun updateLyricSize(size: Int) =
        viewModelScope.launch { repo.updateLyricSize(size) }

    fun updateChordSpacing(spacing: Float) =
        viewModelScope.launch { repo.updateChordSpacing(spacing) }

    fun updateFontFamily(family: SongFontFamily) =
        viewModelScope.launch { repo.updateFontFamily(family) }

    // ── Performance HUD ───────────────────────────────────────────────────────

    fun updateShowLeadIndicator(show: Boolean) =
        viewModelScope.launch { repo.updateShowLeadIndicator(show) }

    fun updateShowTranspositionWarning(show: Boolean) =
        viewModelScope.launch { repo.updateShowTranspositionWarning(show) }

    fun updateShowChords(show: Boolean) =
        viewModelScope.launch { repo.updateShowChords(show) }

    fun updateShowKeyInfo(show: Boolean) =
        viewModelScope.launch { repo.updateShowKeyInfo(show) }

    // ── Theme — backgrounds ───────────────────────────────────────────────────

    fun updateDarkBgColor(hex: String) =
        viewModelScope.launch { repo.updateDarkBgColor(hex) }

    fun updateLightBgColor(hex: String) =
        viewModelScope.launch { repo.updateLightBgColor(hex) }

    // ── Theme — body text colors ──────────────────────────────────────────────

    fun updateDarkLyricColor(hex: String) =
        viewModelScope.launch { repo.updateDarkLyricColor(hex) }

    fun updateLightLyricColor(hex: String) =
        viewModelScope.launch { repo.updateLightLyricColor(hex) }

    fun updateDarkChordColor(hex: String) =
        viewModelScope.launch { repo.updateDarkChordColor(hex) }

    fun updateLightChordColor(hex: String) =
        viewModelScope.launch { repo.updateLightChordColor(hex) }

    fun updateDarkHarmonyColor(hex: String) =
        viewModelScope.launch { repo.updateDarkHarmonyColor(hex) }

    fun updateLightHarmonyColor(hex: String) =
        viewModelScope.launch { repo.updateLightHarmonyColor(hex) }

    // ── Theme — section styles ────────────────────────────────────────────────

    fun updateDarkSectionStyle(section: String, style: SectionStyle) =
        viewModelScope.launch { repo.updateDarkSectionStyle(section, style) }

    fun updateLightSectionStyle(section: String, style: SectionStyle) =
        viewModelScope.launch { repo.updateLightSectionStyle(section, style) }

    // ── Presets ───────────────────────────────────────────────────────────────

    /** Applies all preset values atomically to the active theme's DataStore keys. */
    fun loadPreset(preset: ThemePreset, isDark: Boolean) =
        viewModelScope.launch { repo.loadPreset(preset, isDark) }

    /**
     * Captures the current state of the active theme into a named user preset.
     * Uses a random UUID as the stable id.
     */
    fun saveCurrentAsPreset(name: String, isDark: Boolean) {
        val prefs = preferences.value
        val preset = ThemePreset(
            id           = UUID.randomUUID().toString(),
            name         = name.trim(),
            isBuiltIn    = false,
            bgColor      = if (isDark) prefs.darkBgColor      else prefs.lightBgColor,
            lyricColor   = if (isDark) prefs.darkLyricColor   else prefs.lightLyricColor,
            chordColor   = if (isDark) prefs.darkChordColor   else prefs.lightChordColor,
            harmonyColor = if (isDark) prefs.darkHarmonyColor else prefs.lightHarmonyColor,
            sectionStyles = if (isDark) prefs.darkSectionStyles else prefs.lightSectionStyles
        )
        viewModelScope.launch { repo.savePreset(preset, isDark) }
    }

    /** Deletes a user-created preset by id. No-op for built-in presets. */
    fun deletePreset(id: String, isDark: Boolean) =
        viewModelScope.launch { repo.deletePreset(id, isDark) }

    fun promoteToGlobal(song: SongEntity, isDarkTheme: Boolean) =
        viewModelScope.launch { repo.promoteToGlobal(song, isDarkTheme) }
}
