package com.encore.tablet.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.encore.core.data.repository.SongRepository
import com.encore.feature.library.LibraryViewModel

/**
 * Factory for creating ViewModels with dependencies.
 *
 * Milestone 2: Manual factory (will migrate to Hilt in Milestone 4)
 */
class ViewModelFactory(
    private val appContainer: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) -> {
                LibraryViewModel(appContainer.songRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
