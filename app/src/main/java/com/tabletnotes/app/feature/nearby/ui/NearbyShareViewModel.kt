package com.tabletnotes.app.feature.nearby.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletnotes.app.core.common.IoDispatcher
import com.tabletnotes.app.domain.repository.NoteRepository
import com.tabletnotes.app.domain.repository.NoteSummary
import com.tabletnotes.app.feature.nearby.service.NearbyPeerDevice
import com.tabletnotes.app.feature.nearby.service.NearbyTransferManager
import com.tabletnotes.app.feature.nearby.service.NearbyTransferSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NearbyShareViewModel @Inject constructor(
    private val nearbyTransferManager: NearbyTransferManager,
    private val noteRepository: NoteRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    val isAdvertising: StateFlow<Boolean> = nearbyTransferManager.isAdvertising
    val isDiscovering: StateFlow<Boolean> = nearbyTransferManager.isDiscovering
    val discoveredPeers: StateFlow<List<NearbyPeerDevice>> = nearbyTransferManager.discoveredPeers
    val activeTransfer: StateFlow<NearbyTransferSession?> = nearbyTransferManager.activeTransfer

    val notes: StateFlow<List<NoteSummary>> = noteRepository.observeNoteSummaries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedNoteId = MutableStateFlow<String?>(null)
    val selectedNoteId: StateFlow<String?> = _selectedNoteId.asStateFlow()

    fun selectNote(noteId: String) {
        _selectedNoteId.value = noteId
    }

    fun startAdvertising() {
        nearbyTransferManager.startAdvertising("Pixel Tablet (Not Defteri)")
    }

    fun startDiscovery() {
        nearbyTransferManager.startDiscovery()
    }

    fun stopAll() {
        nearbyTransferManager.stopAll()
    }

    fun sendSelectedNoteToPeer(peer: NearbyPeerDevice) {
        viewModelScope.launch(ioDispatcher) {
            val targetId = _selectedNoteId.value ?: notes.value.firstOrNull()?.id ?: return@launch
            val doc = noteRepository.loadNoteDocument(targetId) ?: return@launch
            nearbyTransferManager.sendNoteToPeer(peer, doc)
        }
    }

    fun simulateDiscoverAndSendSelectedNote() {
        viewModelScope.launch(ioDispatcher) {
            val targetId = _selectedNoteId.value ?: notes.value.firstOrNull()?.id ?: return@launch
            val doc = noteRepository.loadNoteDocument(targetId) ?: return@launch
            nearbyTransferManager.simulateDiscoverAndSend(doc)
        }
    }

    fun simulateReceiveNearbyNote() {
        nearbyTransferManager.simulateIncomingNearbyNote()
    }

    fun cancelActiveTransfer() {
        nearbyTransferManager.cancelActiveTransfer()
    }

    fun retryLastTransfer() {
        nearbyTransferManager.retryLastTransfer()
    }
}
