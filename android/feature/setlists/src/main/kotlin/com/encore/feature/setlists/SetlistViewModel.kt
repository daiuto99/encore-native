package com.encore.feature.setlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetlistEntity
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
 * - Navigation to setlist detail
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
     * @return Result with setlist ID or error
     */
    fun createSetlist(name: String): StateFlow<Result<String>?> {
        val result = MutableStateFlow<Result<String>?>(null)
        viewModelScope.launch {
            result.value = setlistRepository.createSetlist(name)
        }
        return result
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
}
