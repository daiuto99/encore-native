package com.encore.tablet.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.preferences.AppPreferences
import com.encore.core.data.preferences.AppPreferencesRepository
import com.encore.core.data.preferences.SectionStyle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Thin ViewModel wrapper around [AppPreferencesRepository].
 *
 * Exposes preferences as a [StateFlow] so any screen can observe and react
 * instantly when a global setting changes. Provides suspend-safe write
 * helpers that delegate to the repository.
 *
 * The Settings UI screen will use this ViewModel directly.
 * [SongDetailScreen] can also inject it for read access.
 */
class AppPreferencesViewModel(
    private val repo: AppPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<AppPreferences> = repo.appPreferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AppPreferences()
        )

    fun updateLyricSize(size: Int) =
        viewModelScope.launch { repo.updateLyricSize(size) }

    fun updateChordSpacing(spacing: Float) =
        viewModelScope.launch { repo.updateChordSpacing(spacing) }

    fun updateShowLeadIndicator(show: Boolean) =
        viewModelScope.launch { repo.updateShowLeadIndicator(show) }

    fun updateShowTranspositionWarning(show: Boolean) =
        viewModelScope.launch { repo.updateShowTranspositionWarning(show) }

    fun updateShowChords(show: Boolean) =
        viewModelScope.launch { repo.updateShowChords(show) }

    fun updateShowKeyInfo(show: Boolean) =
        viewModelScope.launch { repo.updateShowKeyInfo(show) }

    fun updateSectionStyle(section: String, style: SectionStyle) =
        viewModelScope.launch { repo.updateSectionStyle(section, style) }

    /**
     * Promotes the song's current visual state to global defaults.
     *
     * @param song        Song being promoted.
     * @param isDarkTheme Pass [isSystemInDarkTheme()] from the calling composable.
     *                    Only the matching theme's background key is updated.
     */
    fun promoteToGlobal(song: SongEntity, isDarkTheme: Boolean) =
        viewModelScope.launch { repo.promoteToGlobal(song, isDarkTheme) }
}
