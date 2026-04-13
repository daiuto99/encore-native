package com.encore.tablet.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.encore.feature.library.ImportViewModel
import com.encore.feature.library.LibraryViewModel
import com.encore.feature.library.SetViewModel
import com.encore.feature.library.SyncViewModel
import com.encore.feature.performance.SongDetailViewModel
import com.encore.feature.setlists.SetlistViewModel
import com.encore.tablet.audit.LibraryAuditViewModel
import com.encore.tablet.auth.AuthViewModel
import com.encore.tablet.preferences.AppPreferencesViewModel

class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(
                    appContainer.songRepository,
                    appContainer.userPreferencesRepository,
                    appContainer.appPreferencesRepository,
                    appContainer.syncProvider,
                    appContainer.anthropicApiKey
                ) as T
            }
            modelClass.isAssignableFrom(SetViewModel::class.java) -> {
                SetViewModel(
                    appContainer.setlistRepository,
                    appContainer.userPreferencesRepository
                ) as T
            }
            modelClass.isAssignableFrom(SyncViewModel::class.java) -> {
                SyncViewModel(
                    appContainer.songRepository,
                    appContainer.setlistRepository,
                    appContainer.userPreferencesRepository,
                    appContainer.syncProvider
                ) as T
            }
            modelClass.isAssignableFrom(ImportViewModel::class.java) -> {
                ImportViewModel(
                    appContainer.songRepository,
                    appContainer.setlistRepository,
                    appContainer.userPreferencesRepository
                ) as T
            }
            modelClass.isAssignableFrom(SetlistViewModel::class.java) -> {
                SetlistViewModel(
                    appContainer.setlistRepository,
                    appContainer.songRepository
                ) as T
            }
            modelClass.isAssignableFrom(SongDetailViewModel::class.java) -> {
                SongDetailViewModel(
                    appContainer.songRepository,
                    appContainer.setlistRepository,
                    appContainer.userPreferencesRepository,
                    appContainer.syncProvider
                ) as T
            }
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(
                    appContainer.authRepository
                ) as T
            }
            modelClass.isAssignableFrom(AppPreferencesViewModel::class.java) -> {
                AppPreferencesViewModel(
                    appContainer.appPreferencesRepository
                ) as T
            }
            modelClass.isAssignableFrom(LibraryAuditViewModel::class.java) -> {
                LibraryAuditViewModel(
                    appContainer.songRepository
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
