package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.WalkieTalkieAudioEngine
import com.example.data.CloudRadioRepository
import com.example.data.CloudRoom
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RadioMode {
    PUSH_TO_TALK,
    ALWAYS_ON,
    ALWAYS_OFF
}

data class WalkieTalkieUiState(
    val mode: RadioMode = RadioMode.PUSH_TO_TALK,
    val isPttPressed: Boolean = false,
    val isTransmitting: Boolean = false,
    val isReceiving: Boolean = false,
    val micLevel: Float = 0f,
    val myRooms: List<CloudRoom> = emptyList(),
    val activeRoom: CloudRoom? = null,
    val rogerBeepEnabled: Boolean = true,
    val refreshRateHz: Int = 120,
    val hasMicPermission: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null
)

class WalkieTalkieViewModel(
    application: Application,
    private val currentUserId: String
) : AndroidViewModel(application) {

    private val repository = CloudRadioRepository(application)
    private val audioEngine = WalkieTalkieAudioEngine()
    private val localState = MutableStateFlow(WalkieTalkieUiState())

    private var roomsJob: Job? = null
    private var roomDetailJob: Job? = null
    private var packetsJob: Job? = null
    private val playedPacketIds = LinkedHashSet<String>()
    private var initialPacketsLoaded = false

    val uiState: StateFlow<WalkieTalkieUiState> = combine(
        localState,
        audioEngine.micLevel,
        audioEngine.isReceiving
    ) { state, micLevel, isReceiving ->
        val hasRoom = state.activeRoom != null
        val activelyTransmitting = state.hasMicPermission && hasRoom && when (state.mode) {
            RadioMode.ALWAYS_ON -> true
            RadioMode.PUSH_TO_TALK -> state.isPttPressed
            RadioMode.ALWAYS_OFF -> false
        }
        state.copy(
            isTransmitting = activelyTransmitting,
            isReceiving = if (state.mode == RadioMode.ALWAYS_OFF) false else isReceiving,
            micLevel = if (activelyTransmitting) micLevel else 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WalkieTalkieUiState()
    )

    init {
        if (currentUserId.isNotBlank()) {
            observeRooms()
        }
    }

    private fun observeRooms() {
        roomsJob?.cancel()
        roomsJob = viewModelScope.launch {
            repository.observeMyRooms()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    localState.update {
                        it.copy(errorMessage = root.localizedMessage ?: "تعذر الاتصال بالغرف السحابية")
                    }
                }
                .collect { rooms ->
                    val currentActiveId = localState.value.activeRoom?.id
                    val matched = rooms.firstOrNull { it.id == currentActiveId } ?: rooms.firstOrNull()
                    localState.update { it.copy(myRooms = rooms, errorMessage = null) }
                    if (matched != null && matched.id != currentActiveId) {
                        selectRoom(matched)
                    }
                }
        }
    }

    fun selectRoom(room: CloudRoom) {
        if (localState.value.activeRoom?.id == room.id && packetsJob?.isActive == true) return
        stopAllTransmission()
        localState.update { it.copy(activeRoom = room, errorMessage = null) }
        attachRoomListeners(room.id)
    }

    private fun attachRoomListeners(roomId: String) {
        roomDetailJob?.cancel()
        packetsJob?.cancel()
        playedPacketIds.clear()
        initialPacketsLoaded = false

        roomDetailJob = viewModelScope.launch {
            repository.observeRoom(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                }
                .collect { updatedRoom ->
                    if (updatedRoom != null) {
                        localState.update { it.copy(activeRoom = updatedRoom) }
                    }
                }
        }

        packetsJob = viewModelScope.launch {
            repository.observeVoicePackets(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                }
                .collect { packets ->
                    if (!initialPacketsLoaded) {
                        // Skip historical packets on initial attach; only play live incoming voice
                        packets.forEach { playedPacketIds.add(it.id) }
                        initialPacketsLoaded = true
                        return@collect
                    }

                    val ordered = packets.reversed()
                    for (packet in ordered) {
                        if (packet.id.isNotBlank() && playedPacketIds.add(packet.id)) {
                            if (playedPacketIds.size > 120) {
                                playedPacketIds.iterator().let { iter ->
                                    if (iter.hasNext()) {
                                        iter.next()
                                        iter.remove()
                                    }
                                }
                            }
                            if (packet.senderUid != currentUserId &&
                                localState.value.mode != RadioMode.ALWAYS_OFF
                            ) {
                                audioEngine.playIncomingAudio(packet.audioBase64)
                            }
                        }
                    }
                }
        }
    }

    fun createRoom(roomName: String) {
        viewModelScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.createRoom(roomName)
                .onSuccess { room ->
                    localState.update { it.copy(isBusy = false) }
                    selectRoom(room)
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "تعذر إنشاء الغرفة"
                        )
                    }
                }
        }
    }

    fun joinRoomByCode(code: String) {
        viewModelScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.joinRoomByCode(code)
                .onSuccess { room ->
                    localState.update { it.copy(isBusy = false) }
                    selectRoom(room)
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "تعذر الانضمام للغرفة"
                        )
                    }
                }
        }
    }

    fun clearError() {
        localState.update { it.copy(errorMessage = null) }
    }

    fun updateRefreshRate(hz: Int) {
        localState.update { it.copy(refreshRateHz = hz.coerceAtLeast(60)) }
    }

    fun onMicPermissionChanged(granted: Boolean) {
        localState.update { it.copy(hasMicPermission = granted) }
        if (granted && localState.value.mode == RadioMode.ALWAYS_ON) {
            beginCloudTransmission()
        }
    }

    fun onPttPressStart() {
        val current = localState.value
        if (!current.hasMicPermission || current.mode != RadioMode.PUSH_TO_TALK) return
        if (current.activeRoom == null || current.isPttPressed) return
        localState.update { it.copy(isPttPressed = true) }
        beginCloudTransmission()
    }

    fun onPttPressEnd() {
        val current = localState.value
        if (!current.isPttPressed) return
        localState.update { it.copy(isPttPressed = false) }
        if (current.mode == RadioMode.PUSH_TO_TALK) {
            stopAllTransmission(playTone = current.rogerBeepEnabled)
        }
    }

    fun toggleAlwaysOn() {
        val current = localState.value
        if (!current.hasMicPermission || current.activeRoom == null) return
        if (current.mode == RadioMode.ALWAYS_ON) {
            localState.update { it.copy(mode = RadioMode.PUSH_TO_TALK, isPttPressed = false) }
            audioEngine.setMuted(false)
            stopAllTransmission(playTone = current.rogerBeepEnabled)
        } else {
            localState.update { it.copy(mode = RadioMode.ALWAYS_ON, isPttPressed = false) }
            audioEngine.setMuted(false)
            beginCloudTransmission()
        }
    }

    fun toggleAlwaysOff() {
        val current = localState.value
        if (current.mode == RadioMode.ALWAYS_OFF) {
            localState.update { it.copy(mode = RadioMode.PUSH_TO_TALK, isPttPressed = false) }
            audioEngine.setMuted(false)
        } else {
            localState.update { it.copy(mode = RadioMode.ALWAYS_OFF, isPttPressed = false) }
            audioEngine.setMuted(true)
            stopAllTransmission(playTone = false)
        }
    }

    fun toggleRogerBeep() {
        localState.update { it.copy(rogerBeepEnabled = !it.rogerBeepEnabled) }
    }

    private fun beginCloudTransmission() {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.updateActiveSpeaker(room.id, speaking = true)
        }
        audioEngine.startTransmitting(
            playTone = localState.value.rogerBeepEnabled,
            onChunkEncoded = { audioBase64 ->
                val active = localState.value.activeRoom ?: return@startTransmitting
                viewModelScope.launch {
                    repository.sendVoicePacket(
                        roomId = active.id,
                        memberUids = active.memberUids,
                        audioBase64 = audioBase64
                    )
                }
            }
        )
    }

    private fun stopAllTransmission(playTone: Boolean = false) {
        audioEngine.stopTransmitting(playTone = playTone)
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.updateActiveSpeaker(room.id, speaking = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        roomsJob?.cancel()
        roomDetailJob?.cancel()
        packetsJob?.cancel()
        audioEngine.release()
    }

    class Factory(
        private val application: Application,
        private val currentUserId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WalkieTalkieViewModel(application, currentUserId) as T
        }
    }
}
