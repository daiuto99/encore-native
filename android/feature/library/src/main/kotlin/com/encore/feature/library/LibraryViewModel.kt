package com.encore.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for Library Screen.
 *
 * Manages:
 * - Song list from Room database (reactive Flow)
 * - Search query state
 * - Search filtering logic
 *
 * Follows offline-first pattern with Flow-based reactive updates.
 */
class LibraryViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    // Search query state (mutable for UI updates)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /**
     * Songs list with reactive search filtering.
     *
     * Uses flatMapLatest to automatically switch to new search query Flow
     * whenever searchQuery changes. This ensures UI always shows results
     * for the latest query without manual cancellation.
     */
    val songs: StateFlow<List<SongEntity>> = _searchQuery
        .flatMapLatest { query ->
            songRepository.searchSongs(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Update search query from UI.
     *
     * @param query New search term (can be empty to show all songs)
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear search query and show all songs.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
}
