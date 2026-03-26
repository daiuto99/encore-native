package com.encore.feature.setlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.relations.SetlistWithSets
import com.encore.core.data.repository.SetlistRepository
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for Setlist management.
 *
 * Manages:
 * - List of all setlists
 * - Creating new setlists
 * - Deleting setlists
 * - Adding songs to setlists
 * - Setlist detail view
 */
class SetlistViewModel(
    private val setlistRepository: SetlistRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    /**
     * All setlists ordered by name.
     */
    val setlists: StateFlow<List<SetlistEntity>> = setlistRepository.getSetlists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Create a new setlist.
     *
     * Creates setlist with initial Set 1 automatically.
     *
     * @param name Setlist name
     */
    fun createSetlist(name: String) {
        viewModelScope.launch {
            setlistRepository.createSetlist(name)
        }
    }

    /**
     * Delete a setlist (cascade deletes sets and entries).
     *
     * @param setlist Setlist to delete
     */
    fun deleteSetlist(setlist: SetlistEntity) {
        viewModelScope.launch {
            setlistRepository.deleteSetlist(setlist)
        }
    }

    /**
     * Add a song to the first set of a setlist.
     *
     * Adds to end of Set 1 (most common use case).
     *
     * @param setlistId Setlist UUID
     * @param songId Song UUID
     */
    fun addSongToSetlist(setlistId: String, songId: String) {
        viewModelScope.launch {
            // Get the setlist with sets
            val setlistGroup = setlistRepository.getSetlistWithSets(setlistId)
            if (setlistGroup != null && setlistGroup.sets.isNotEmpty()) {
                // Add to first set (Set 1)
                val firstSetData = setlistGroup.sets.first()
                val firstSetId = firstSetData.set.id
                setlistRepository.addSongToSet(firstSetId, songId)
            }
        }
    }

    /**
     * Get setlist with all songs in order.
     *
     * @param setlistId Setlist UUID
     * @return Flow of setlist with sets and songs
     */
    fun getSetlistWithSongs(setlistId: String): StateFlow<SetlistWithSets?> {
        return setlistRepository.observeSetlistWithSets(setlistId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null
            )
    }
}
