package com.encore.feature.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.encore.core.data.entities.SetEntity
import com.encore.core.data.entities.SetlistEntity
import com.encore.core.data.preferences.UserPreferencesRepository
import com.encore.core.data.relations.SetEntryWithSong
import com.encore.core.data.repository.SetlistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for set management.
 *
 * Owns: performSetEntries, availableSets, setlists, all set CRUD
 * (add/remove/reorder songs, create/delete sets, save/load named setlists),
 * and set upload to cloud.
 *
 * Extracted from LibraryViewModel as part of Track 1.2.
 *
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SetViewModel(
    private val setlistRepository: SetlistRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _performSetId = MutableStateFlow<String?>(null)
    private val _defaultSetlistId = MutableStateFlow<String?>(null)

    val performSetEntries: StateFlow<List<SetEntryWithSong>> = _performSetId
        .flatMapLatest { setId ->
            if (setId == null) flowOf(emptyList())
            else setlistRepository.getSongsInSet(setId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableSets: StateFlow<List<SetEntity>> = _defaultSetlistId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else setlistRepository.getSetsForSetlist(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val setlists: StateFlow<List<SetlistEntity>> = setlistRepository.getSetlists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun clearStatusMessage() { _statusMessage.value = null }

    init {
        initPerformSet()
    }

    private fun initPerformSet() {
        viewModelScope.launch {
            try {
                val set1 = setlistRepository.getOrCreateSetByNumber(1)
                _performSetId.value = set1.id
                _defaultSetlistId.value = set1.setlistId
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize perform set", e)
            }
        }
    }

    // ── Set operations ────────────────────────────────────────────────────────

    fun addToPerformSet(songId: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            setlistRepository.addSongToSet(setId, songId)
            val count = setlistRepository.getSongsInSet(setId).first().size
            _statusMessage.value = "Staged ($count in set)"
        }
        uploadAllSetsInBackground()
    }

    fun removeFromPerformSet(entryId: String) {
        viewModelScope.launch { setlistRepository.removeSongFromSet(entryId) }
        uploadAllSetsInBackground()
    }

    fun reorderPerformSet(entryId: String, toIndex: Int) {
        viewModelScope.launch { setlistRepository.reorderSongInSet(entryId, toIndex) }
        uploadAllSetsInBackground()
    }

    fun addSongToSetNumber(songId: String, setNumber: Int) {
        viewModelScope.launch {
            try {
                val targetSet = setlistRepository.getOrCreateSetByNumber(setNumber)
                setlistRepository.addSongToSet(targetSet.id, songId)
                _statusMessage.value = "Added to Set $setNumber"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add song to set $setNumber", e)
                _statusMessage.value = "Could not add to Set $setNumber"
            }
        }
        uploadAllSetsInBackground()
    }

    fun removeSongFromSetNumber(songId: String, setNumber: Int) {
        viewModelScope.launch {
            try {
                val sets = setlistRepository.getSetsContainingSong(songId)
                val targetSet = sets.find { it.number == setNumber } ?: return@launch
                val entry = setlistRepository.getEntryForSongInSet(targetSet.id, songId) ?: return@launch
                setlistRepository.removeSongFromSet(entry.id)
                _statusMessage.value = "Removed from Set $setNumber"
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove song from set $setNumber", e)
            }
        }
        uploadAllSetsInBackground()
    }

    /** Reorder a song within a set. [setNumber] is the currently active set filter. */
    fun reorderSong(songId: String, toIndex: Int, setNumber: Int) {
        viewModelScope.launch {
            try {
                val sets = setlistRepository.getSetsContainingSong(songId)
                val targetSet = sets.find { it.number == setNumber } ?: return@launch
                val entry = setlistRepository.getEntryForSongInSet(targetSet.id, songId) ?: return@launch
                setlistRepository.reorderSongInSet(entry.id, toIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Reorder failed", e)
            }
        }
        uploadAllSetsInBackground()
    }

    fun saveCurrentSetAs(name: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            val newSetlistId = setlistRepository.createSetlist(name).getOrNull() ?: run {
                _statusMessage.value = "Could not save set"
                return@launch
            }
            val newSets = setlistRepository.getSetsForSetlist(newSetlistId).first()
            val newSetId = newSets.firstOrNull()?.id ?: return@launch
            val currentEntries = setlistRepository.getSongsInSet(setId).first()
            currentEntries.forEach { entry ->
                setlistRepository.addSongToSet(newSetId, entry.song.id)
            }
            _statusMessage.value = "Saved as \"$name\""
        }
    }

    fun loadSetlistAsCurrent(setlistId: String) {
        val setId = _performSetId.value ?: return
        viewModelScope.launch {
            val existingEntries = setlistRepository.getSongsInSet(setId).first()
            existingEntries.forEach { entry -> setlistRepository.removeSongFromSet(entry.entry.id) }
            val sets = setlistRepository.getSetsForSetlist(setlistId).first()
            val sourceSetId = sets.minByOrNull { it.number }?.id ?: return@launch
            val songs = setlistRepository.getSongsInSet(sourceSetId).first()
            songs.forEach { entry -> setlistRepository.addSongToSet(setId, entry.song.id) }
            _statusMessage.value = "Setlist loaded"
            uploadAllSetsInBackground()
        }
    }

    fun createNewSet() {
        val setlistId = _defaultSetlistId.value ?: return
        viewModelScope.launch {
            try {
                setlistRepository.addSetToSetlist(setlistId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create new set", e)
                _statusMessage.value = "Could not create set"
            }
        }
    }

    fun deleteSet(set: SetEntity) {
        if (set.id == _performSetId.value) return
        viewModelScope.launch {
            try {
                setlistRepository.deleteSetAndRenumber(set)
                uploadAllSetsInBackground()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete set ${set.number}", e)
                _statusMessage.value = "Could not delete set"
            }
        }
    }

    fun clearAllSets() {
        val setlistId = _defaultSetlistId.value ?: return
        viewModelScope.launch {
            setlistRepository.clearAllSets(setlistId)
            uploadSet(1)
        }
    }

    fun observeSetsContainingSong(songId: String): Flow<List<SetEntity>> =
        setlistRepository.observeSetsContainingSong(songId)

    suspend fun getSetsContainingSong(songId: String): List<SetEntity> =
        setlistRepository.getSetsContainingSong(songId)

    // ── Upload helpers ────────────────────────────────────────────────────────

    private suspend fun uploadSet(setNumber: Int) {
        val userId = userPrefs.persistedUser.first()?.googleAccountId ?: return
        setlistRepository.uploadSet(userId, setNumber)
    }

    private fun uploadAllSetsInBackground() {
        val setlistId = _defaultSetlistId.value ?: return
        viewModelScope.launch {
            setlistRepository.getSetsForSetlist(setlistId).first()
                .forEach { set -> uploadSet(set.number) }
        }
    }

    companion object {
        private const val TAG = "SetViewModel"
    }
}
