package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.WalkieTalkieAudioEngine
import com.example.data.ApkInstallResult
import com.example.data.AppUpdateChecker
import com.example.data.AppUpdateInfo
import java.io.File
import com.example.data.CloudRadioRepository
import com.example.data.CloudRoom
import com.example.data.RoomChatMessage
import com.example.data.UserSettings
import com.example.data.UserSettingsStore
import com.example.service.RadioForegroundService
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val errorMessage: String? = null,
    val settings: UserSettings = UserSettings(),
    val currentUserId: String = "",
    val currentUserEmail: String = "",
    val availableUpdate: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateCheckMessage: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val downloadedUpdateFilePath: String? = null,
    val updateDownloadError: String? = null,
    val lastVoiceSenderName: String = "",
    val canReplayVoice: Boolean = false,
    val pingBannerMessage: String? = null,
    val chatMessages: List<RoomChatMessage> = emptyList()
)

class WalkieTalkieViewModel(
    application: Application,
    private val currentUserId: String
) : AndroidViewModel(application) {

    private val repository = CloudRadioRepository(application)
    private val settingsStore = UserSettingsStore(application, currentUserId)
    private val audioEngine = WalkieTalkieAudioEngine()

    private val initialSettings = settingsStore.load(
        defaultName = Firebase.auth.currentUser?.displayName ?: "صديق"
    )

    private val initialMode = RadioMode.entries.firstOrNull {
        it.name == initialSettings.savedRadioMode
    } ?: RadioMode.PUSH_TO_TALK

    private val localState = MutableStateFlow(
        WalkieTalkieUiState(
            mode = initialMode,
            rogerBeepEnabled = initialSettings.rogerBeepEnabled,
            settings = initialSettings,
            currentUserId = currentUserId,
            currentUserEmail = Firebase.auth.currentUser?.email ?: ""
        )
    )

    private var roomsJob: Job? = null
    private var roomDetailJob: Job? = null
    private var packetsJob: Job? = null
    private var chatJob: Job? = null
    private val playedPacketIds = LinkedHashSet<String>()
    private var initialPacketsLoaded = false
    private val lastReceivedChunks = ArrayDeque<String>()
    private var lastHandledPingAt: Long = 0L

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
            micLevel = if (activelyTransmitting) (micLevel * state.settings.micGain).coerceIn(0f, 1f) else 0f
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = localState.value
    )

    init {
        audioEngine.rogerBeepStyle = initialSettings.rogerBeepStyle
        audioEngine.radioFxEnabled = initialSettings.radioFxEnabled
        if (initialMode == RadioMode.ALWAYS_OFF) {
            audioEngine.setMuted(true)
        }
        if (currentUserId.isNotBlank()) {
            observeRooms()
        }
        checkForUpdates(manual = false)
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            localState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateCheckMessage = if (manual) "جاري التحقق من التحديثات…" else it.updateCheckMessage
                )
            }
            AppUpdateChecker.checkForUpdate()
                .onSuccess { info ->
                    localState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = if (info.isUpdateAvailable) info else null,
                            showUpdateDialog = info.isUpdateAvailable,
                            updateDownloadError = null,
                            updateCheckMessage = when {
                                info.isUpdateAvailable -> "يتوفر تحديث جديد (${info.latestVersion})!"
                                manual -> "أنت تستخدم أحدث إصدار (${info.latestVersion})"
                                else -> null
                            }
                        )
                    }
                }
                .onFailure {
                    localState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateCheckMessage = if (manual) "تعذر التحقق من التحديثات حالياً" else null
                        )
                    }
                }
        }
    }

    fun dismissUpdateDialog() {
        localState.update { it.copy(showUpdateDialog = false) }
    }

    fun startInAppUpdateDownload() {
        val update = localState.value.availableUpdate ?: return
        if (localState.value.isDownloadingUpdate) return

        val existingPath = localState.value.downloadedUpdateFilePath
        if (existingPath != null && File(existingPath).exists()) {
            installDownloadedUpdate()
            return
        }

        viewModelScope.launch {
            localState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0,
                    updateDownloadError = null
                )
            }
            AppUpdateChecker.downloadUpdateApk(
                context = getApplication(),
                url = update.downloadUrl,
                onProgress = { pct ->
                    localState.update { it.copy(updateDownloadProgress = pct) }
                }
            )
                .onSuccess { file ->
                    localState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadProgress = 100,
                            downloadedUpdateFilePath = file.absolutePath,
                            updateDownloadError = null
                        )
                    }
                    installDownloadedUpdate()
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadError = err.localizedMessage ?: "فشل تنزيل التحديث"
                        )
                    }
                }
        }
    }

    fun installDownloadedUpdate() {
        val path = localState.value.downloadedUpdateFilePath ?: return
        val file = File(path)
        when (AppUpdateChecker.installApk(getApplication(), file)) {
            ApkInstallResult.LAUNCHED_INSTALLER -> {
                localState.update { it.copy(updateDownloadError = null) }
            }
            ApkInstallResult.NEEDS_UNKNOWN_SOURCES_PERMISSION -> {
                localState.update {
                    it.copy(
                        updateDownloadError = "اسمح بتثبيت التطبيقات من هذا المصدر ثم اضغط «تثبيت التحديث الآن»"
                    )
                }
            }
            ApkInstallResult.FAILED -> {
                localState.update {
                    it.copy(
                        downloadedUpdateFilePath = null,
                        updateDownloadError = "تعذر فتح المثبّت، يرجى إعادة التنزيل"
                    )
                }
            }
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
                    } else if (matched == null && currentActiveId != null) {
                        stopAllTransmission()
                        roomDetailJob?.cancel()
                        packetsJob?.cancel()
                        localState.update { it.copy(activeRoom = null) }
                    }
                }
        }
    }

    fun selectRoom(room: CloudRoom) {
        if (localState.value.activeRoom?.id == room.id && packetsJob?.isActive == true) return
        stopAllTransmission()
        lastReceivedChunks.clear()
        lastHandledPingAt = room.lastPingAt
        localState.update {
            it.copy(
                activeRoom = room,
                errorMessage = null,
                canReplayVoice = false,
                lastVoiceSenderName = "",
                chatMessages = emptyList()
            )
        }
        syncBackgroundService(room)
        attachRoomListeners(room.id)
        if (localState.value.mode == RadioMode.ALWAYS_ON && localState.value.hasMicPermission) {
            beginCloudTransmission()
        }
    }

    private fun syncBackgroundService(room: CloudRoom?) {
        val ctx = getApplication<Application>()
        if (localState.value.settings.backgroundListeningEnabled && room != null) {
            RadioForegroundService.start(ctx, room.roomName)
        } else {
            RadioForegroundService.stop(ctx)
        }
    }

    private fun attachRoomListeners(roomId: String) {
        roomDetailJob?.cancel()
        packetsJob?.cancel()
        chatJob?.cancel()
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
                        // Detect incoming Emergency Ping from another member
                        if (updatedRoom.lastPingAt > lastHandledPingAt &&
                            updatedRoom.lastPingUid.isNotBlank() &&
                            updatedRoom.lastPingUid != currentUserId
                        ) {
                            lastHandledPingAt = updatedRoom.lastPingAt
                            if (localState.value.mode != RadioMode.ALWAYS_OFF) {
                                audioEngine.playPingSiren()
                            }
                            val sender = updatedRoom.lastPingName.ifBlank { "صديق" }
                            localState.update { it.copy(pingBannerMessage = "نداء عاجل من $sender!") }
                            viewModelScope.launch {
                                delay(4500L)
                                localState.update { it.copy(pingBannerMessage = null) }
                            }
                        } else if (updatedRoom.lastPingAt > lastHandledPingAt) {
                            lastHandledPingAt = updatedRoom.lastPingAt
                        }

                        // Stop transmitting if owner just muted us
                        if (currentUserId in updatedRoom.mutedUids && localState.value.isTransmitting) {
                            stopAllTransmission(playTone = false)
                        }
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
                                if (localState.value.lastVoiceSenderName != packet.senderName) {
                                    lastReceivedChunks.clear()
                                }
                                if (lastReceivedChunks.size >= 24) {
                                    lastReceivedChunks.removeFirst()
                                }
                                lastReceivedChunks.addLast(packet.audioBase64)
                                localState.update {
                                    it.copy(
                                        lastVoiceSenderName = packet.senderName,
                                        canReplayVoice = lastReceivedChunks.isNotEmpty()
                                    )
                                }
                                audioEngine.playIncomingAudio(packet.audioBase64)
                            }
                        }
                    }
                }
        }

        chatJob = viewModelScope.launch {
            repository.observeChatMessages(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                }
                .collect { messages ->
                    localState.update { it.copy(chatMessages = messages) }
                }
        }
    }

    fun replayLastVoiceMessage() {
        val chunks = lastReceivedChunks.toList()
        if (chunks.isEmpty()) return
        viewModelScope.launch {
            for (chunk in chunks) {
                audioEngine.playIncomingAudio(chunk)
                delay(240L)
            }
        }
    }

    fun sendPing() {
        val room = localState.value.activeRoom ?: return
        audioEngine.playPingSiren()
        viewModelScope.launch {
            repository.sendPing(room.id, localState.value.settings.displayName)
        }
    }

    fun toggleMuteMember(targetUid: String) {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.toggleMuteMember(room, targetUid)
        }
    }

    fun kickMember(targetUid: String) {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.kickMember(room, targetUid)
        }
    }

    fun updateRoomPasscode(newPasscode: String) {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.updateRoomPasscode(room, newPasscode)
                .onFailure { err ->
                    localState.update { it.copy(errorMessage = err.localizedMessage) }
                }
        }
    }

    fun sendChatMessage(text: String) {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            repository.sendChatMessage(
                roomId = room.id,
                memberUids = room.memberUids,
                text = text,
                senderName = localState.value.settings.displayName
            )
        }
    }

    fun createRoom(roomName: String, passcode: String) {
        viewModelScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.createRoom(
                roomName = roomName,
                passcode = passcode,
                creatorName = localState.value.settings.displayName
            )
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

    fun joinRoomByCode(code: String, passcode: String) {
        viewModelScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.joinRoomByCode(
                rawCode = code,
                passcode = passcode,
                memberName = localState.value.settings.displayName
            )
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

    fun leaveActiveRoom() {
        val room = localState.value.activeRoom ?: return
        viewModelScope.launch {
            stopAllTransmission()
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.leaveRoom(room)
                .onSuccess {
                    roomDetailJob?.cancel()
                    packetsJob?.cancel()
                    localState.update { it.copy(isBusy = false, activeRoom = null) }
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "تعذر الخروج من الغرفة"
                        )
                    }
                }
        }
    }

    fun deleteActiveRoom() {
        val room = localState.value.activeRoom ?: return
        if (room.ownerId != currentUserId) return
        viewModelScope.launch {
            stopAllTransmission()
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository.deleteRoom(room.id)
                .onSuccess {
                    roomDetailJob?.cancel()
                    packetsJob?.cancel()
                    localState.update { it.copy(isBusy = false, activeRoom = null) }
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = err.localizedMessage ?: "تعذر حذف الغرفة"
                        )
                    }
                }
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val previousName = localState.value.settings.displayName
        val updated = transform(localState.value.settings)
        settingsStore.save(updated)
        audioEngine.rogerBeepStyle = updated.rogerBeepStyle
        audioEngine.radioFxEnabled = updated.radioFxEnabled
        localState.update {
            it.copy(
                settings = updated,
                rogerBeepEnabled = updated.rogerBeepEnabled
            )
        }
        syncBackgroundService(localState.value.activeRoom)
        if (updated.displayName != previousName) {
            val active = localState.value.activeRoom
            if (active != null) {
                viewModelScope.launch {
                    repository.updateMemberDisplayName(active, updated.displayName)
                }
            }
        }
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
            val newMode = RadioMode.PUSH_TO_TALK
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(false)
            stopAllTransmission(playTone = current.rogerBeepEnabled)
        } else {
            val newMode = RadioMode.ALWAYS_ON
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(false)
            beginCloudTransmission()
        }
    }

    fun toggleAlwaysOff() {
        val current = localState.value
        if (current.mode == RadioMode.ALWAYS_OFF) {
            val newMode = RadioMode.PUSH_TO_TALK
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(false)
        } else {
            val newMode = RadioMode.ALWAYS_OFF
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(true)
            stopAllTransmission(playTone = false)
        }
    }

    fun toggleRogerBeep() {
        updateSettings { it.copy(rogerBeepEnabled = !it.rogerBeepEnabled) }
    }

    private fun beginCloudTransmission() {
        val room = localState.value.activeRoom ?: return
        if (currentUserId in room.mutedUids) {
            localState.update {
                it.copy(
                    isPttPressed = false,
                    errorMessage = "تم كتمك من قبل مالك الغرفة"
                )
            }
            return
        }
        val speakerName = localState.value.settings.displayName
        viewModelScope.launch {
            repository.updateActiveSpeaker(room.id, speaking = true, speakerName = speakerName)
        }
        audioEngine.startTransmitting(
            playTone = localState.value.rogerBeepEnabled,
            onChunkEncoded = { audioBase64 ->
                val active = localState.value.activeRoom ?: return@startTransmitting
                viewModelScope.launch {
                    repository.sendVoicePacket(
                        roomId = active.id,
                        memberUids = active.memberUids,
                        audioBase64 = audioBase64,
                        speakerName = localState.value.settings.displayName
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
        chatJob?.cancel()
        RadioForegroundService.stop(getApplication())
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
