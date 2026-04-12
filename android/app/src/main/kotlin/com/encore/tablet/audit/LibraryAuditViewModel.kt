package com.encore.tablet.audit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.encore.core.data.entities.SongEntity
import com.encore.core.data.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryAuditViewModel(
    private val songRepository: SongRepository
) : ViewModel() {

    /** Reactive list of songs with validation errors — updates automatically after each scan. */
    val invalidSongs: StateFlow<List<SongEntity>> = songRepository.getInvalidSongs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /**
     * Enqueues a one-time [LibraryAuditWorker] run and tracks its state.
     * [invalidSongs] updates automatically when the worker writes results to Room.
     */
    fun runScan(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true

        val request = OneTimeWorkRequestBuilder<LibraryAuditWorker>().build()
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.enqueue(request)

        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                if (info?.state?.isFinished == true) {
                    _isScanning.value = false
                }
            }
        }
    }
}
