package com.example.service

import android.content.Context
import android.graphics.BitmapFactory
import com.example.audio.AudioOutputRoute
import com.example.audio.AudioRouteHelper
import com.example.audio.WalkieTalkieAudioEngine
import com.example.data.CloudRadioRepository
import com.example.data.CloudRoom
import com.example.data.LocationHelper
import com.example.data.RoomChatMessage
import com.example.data.UserSettings
import com.example.data.UserSettingsStore
import com.example.data.toArabicErrorMessage
import com.example.ui.HardwarePttBridge
import com.example.ui.RadioMode
import com.example.ui.components.encodeAvatarThumbnailBase64
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class RadioSessionState(
    val currentUserId: String = "",
    val currentUserEmail: String = "",
    val mode: RadioMode = RadioMode.PUSH_TO_TALK,
    val isPttPressed: Boolean = false,
    val isTransmitting: Boolean = false,
    val isReceiving: Boolean = false,
    val micLevel: Float = 0f,
    val myRooms: List<CloudRoom> = emptyList(),
    val activeRoom: CloudRoom? = null,
    val rogerBeepEnabled: Boolean = true,
    val hasMicPermission: Boolean = false,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    val settings: UserSettings = UserSettings(),
    val lastVoiceSenderName: String = "",
    val canReplayVoice: Boolean = false,
    val pingBannerMessage: String? = null,
    val chatMessages: List<RoomChatMessage> = emptyList(),
    val audioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val autoMuteRemainingSec: Int = 0
)

/**
 * Process-wide singleton managing active room Firestore listeners, live audio engine,
 * and background persistence across Activity destruction / Recent-Apps swipe-away.
 * Everything stays connected in background until the user explicitly activates ALWAYS_OFF ("إغلاق دائم")
 * or signs out.
 */
object RadioSessionManager {

    private val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioEngine = WalkieTalkieAudioEngine()
    private val localState = MutableStateFlow(RadioSessionState())

    val state: StateFlow<RadioSessionState> = combine(
        localState,
        audioEngine.micLevel,
        audioEngine.isReceiving
    ) { s, micLevel, isReceiving ->
        val hasRoom = s.activeRoom != null
        val activelyTransmitting = s.hasMicPermission && hasRoom && when (s.mode) {
            RadioMode.ALWAYS_ON -> true
            RadioMode.PUSH_TO_TALK -> s.isPttPressed
            RadioMode.ALWAYS_OFF -> false
        }
        s.copy(
            isTransmitting = activelyTransmitting,
            isReceiving = if (s.mode == RadioMode.ALWAYS_OFF) false else isReceiving,
            micLevel = if (activelyTransmitting) (micLevel * s.settings.micGain).coerceIn(0f, 1f) else 0f
        )
    }.stateIn(
        scope = sessionScope,
        started = SharingStarted.Eagerly,
        initialValue = localState.value
    )

    private var appContext: Context? = null
    private var repository: CloudRadioRepository? = null
    private var settingsStore: UserSettingsStore? = null

    private var roomsJob: Job? = null
    private var roomDetailJob: Job? = null
    private var packetsJob: Job? = null
    private var chatJob: Job? = null
    private var autoMuteJob: Job? = null
    private var txStartedAtMs: Long = 0L
    private val playedPacketIds = LinkedHashSet<String>()
    private var initialPacketsLoaded = false
    private val lastReceivedChunks = ArrayDeque<String>()
    private var lastHandledPingAt: Long = 0L

    @Synchronized
    fun ensureInitialized(context: Context, userIdOverride: String? = null) {
        val ctx = context.applicationContext
        appContext = ctx
        if (repository == null) {
            repository = CloudRadioRepository(ctx)
        }

        val user = Firebase.auth.currentUser
        val uid = userIdOverride?.takeIf { it.isNotBlank() } ?: user?.uid.orEmpty()
        if (uid.isBlank()) return

        if (localState.value.currentUserId == uid && settingsStore != null) {
            syncHardwarePttBridge()
            return
        }

        // Switching accounts cleanly
        cancelAllJobs()
        audioEngine.setMuted(false)

        val store = UserSettingsStore(ctx, uid)
        settingsStore = store
        val initialSettings = store.load(defaultName = user?.displayName ?: "صديق")
        val initialMode = RadioMode.entries.firstOrNull {
            it.name == initialSettings.savedRadioMode
        } ?: RadioMode.PUSH_TO_TALK

        audioEngine.rogerBeepStyle = initialSettings.rogerBeepStyle
        audioEngine.radioFxEnabled = initialSettings.radioFxEnabled
        audioEngine.noiseSuppressionEnabled = initialSettings.noiseSuppressionEnabled
        audioEngine.squelchEnabled = initialSettings.squelchEnabled
        audioEngine.setMuted(initialMode == RadioMode.ALWAYS_OFF)

        localState.value = RadioSessionState(
            currentUserId = uid,
            currentUserEmail = user?.email ?: "",
            mode = initialMode,
            rogerBeepEnabled = initialSettings.rogerBeepEnabled,
            settings = initialSettings,
            hasMicPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.RECORD_AUDIO
                )
        )

        AudioRouteHelper.applyRoute(ctx, AudioOutputRoute.SPEAKER)
        syncHardwarePttBridge()

        if (initialMode == RadioMode.ALWAYS_OFF) {
            RadioForegroundService.stop(ctx)
            FloatingPttService.stop(ctx)
            return
        }

        if (initialSettings.floatingBubbleEnabled) {
            FloatingPttService.start(ctx)
        }
        backfillAvatarThumbnailIfNeeded(initialSettings)
        observeRooms()
    }

    fun shouldStayAliveInBackground(): Boolean {
        val s = localState.value
        return s.currentUserId.isNotBlank() &&
            s.mode != RadioMode.ALWAYS_OFF &&
            s.activeRoom != null
    }

    fun activeRoomNameOrDefault(): String {
        return localState.value.activeRoom?.roomName?.takeIf { it.isNotBlank() } ?: "غرفة لاسلكي"
    }

    private fun backfillAvatarThumbnailIfNeeded(initialSettings: UserSettings) {
        if (initialSettings.avatarThumbBase64.isBlank() && !initialSettings.avatarUri.isNullOrBlank()) {
            sessionScope.launch(Dispatchers.IO) {
                val file = File(initialSettings.avatarUri)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val thumb = encodeAvatarThumbnailBase64(bmp)
                        bmp.recycle()
                        if (thumb.isNotBlank()) {
                            updateSettings { it.copy(avatarThumbBase64 = thumb) }
                            localState.value.activeRoom?.let { room ->
                                repository?.updateMemberAvatar(room, thumb)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun syncHardwarePttBridge() {
        val s = localState.value
        val allowPtt = s.mode == RadioMode.PUSH_TO_TALK && s.activeRoom != null
        HardwarePttBridge.isEnabled = s.settings.volumeKeyPttEnabled && allowPtt
        HardwarePttBridge.onVolumeDownPress = { onPttPressStart() }
        HardwarePttBridge.onVolumeDownRelease = { onPttPressEnd() }
        HardwarePttBridge.onFloatingStart = { onPttPressStart() }
        HardwarePttBridge.onFloatingStop = { onPttPressEnd() }
    }

    private fun syncBackgroundService(room: CloudRoom?, mode: RadioMode = localState.value.mode) {
        val ctx = appContext ?: return
        if (room != null && mode != RadioMode.ALWAYS_OFF) {
            RadioForegroundService.start(ctx, room.roomName)
        } else {
            RadioForegroundService.stop(ctx)
        }
    }

    private fun observeRooms() {
        val repo = repository ?: return
        if (localState.value.mode == RadioMode.ALWAYS_OFF) return
        roomsJob?.cancel()
        roomsJob = sessionScope.launch {
            repo.observeMyRooms()
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val root = firestoreError ?: error
                    localState.update {
                        it.copy(errorMessage = toArabicErrorMessage(root, "تعذر الاتصال بالغرف السحابية"))
                    }
                }
                .collect { rooms ->
                    if (localState.value.mode == RadioMode.ALWAYS_OFF) return@collect
                    val currentActiveId = localState.value.activeRoom?.id
                    val matched = rooms.firstOrNull { it.id == currentActiveId } ?: rooms.firstOrNull()
                    localState.update { it.copy(myRooms = rooms, errorMessage = null) }
                    if (matched != null && matched.id != currentActiveId) {
                        selectRoom(matched)
                    } else if (matched == null && currentActiveId != null) {
                        stopAllTransmission()
                        roomDetailJob?.cancel()
                        packetsJob?.cancel()
                        chatJob?.cancel()
                        localState.update { it.copy(activeRoom = null) }
                        syncBackgroundService(null)
                        syncHardwarePttBridge()
                    }
                }
        }
    }

    fun selectRoom(room: CloudRoom) {
        if (localState.value.mode == RadioMode.ALWAYS_OFF) return
        if (localState.value.activeRoom?.id == room.id && packetsJob?.isActive == true) {
            syncBackgroundService(room)
            return
        }
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
        syncHardwarePttBridge()
        syncBackgroundService(room)
        attachRoomListeners(room.id)
        val uid = localState.value.currentUserId
        val thumb = localState.value.settings.avatarThumbBase64
        if (thumb.isNotBlank() && room.memberAvatars[uid] != thumb) {
            sessionScope.launch {
                repository?.updateMemberAvatar(room, thumb)
            }
        }
        if (localState.value.mode == RadioMode.ALWAYS_ON && localState.value.hasMicPermission) {
            beginCloudTransmission()
        }
    }

    private fun attachRoomListeners(roomId: String) {
        val repo = repository ?: return
        val uid = localState.value.currentUserId
        roomDetailJob?.cancel()
        packetsJob?.cancel()
        chatJob?.cancel()
        playedPacketIds.clear()
        initialPacketsLoaded = false

        roomDetailJob = sessionScope.launch {
            repo.observeRoom(roomId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                }
                .collect { updatedRoom ->
                    if (updatedRoom != null && localState.value.mode != RadioMode.ALWAYS_OFF) {
                        if (updatedRoom.lastPingAt > lastHandledPingAt &&
                            updatedRoom.lastPingUid.isNotBlank() &&
                            updatedRoom.lastPingUid != uid
                        ) {
                            lastHandledPingAt = updatedRoom.lastPingAt
                            audioEngine.playPingSiren()
                            val sender = updatedRoom.lastPingName.ifBlank { "صديق" }
                            localState.update { it.copy(pingBannerMessage = "نداء عاجل من $sender!") }
                            sessionScope.launch {
                                delay(4500L)
                                localState.update { it.copy(pingBannerMessage = null) }
                            }
                        } else if (updatedRoom.lastPingAt > lastHandledPingAt) {
                            lastHandledPingAt = updatedRoom.lastPingAt
                        }

                        if (uid in updatedRoom.mutedUids && localState.value.isTransmitting) {
                            stopAllTransmission(playTone = false)
                        }
                        localState.update { it.copy(activeRoom = updatedRoom) }
                    }
                }
        }

        packetsJob = sessionScope.launch {
            repo.observeVoicePackets(roomId)
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
                            if (packet.senderUid != uid &&
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

        chatJob = sessionScope.launch {
            repo.observeChatMessages(roomId)
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
        if (chunks.isEmpty() || localState.value.mode == RadioMode.ALWAYS_OFF) return
        sessionScope.launch {
            for (chunk in chunks) {
                audioEngine.playIncomingAudio(chunk)
                delay(240L)
            }
        }
    }

    fun sendPing() {
        val room = localState.value.activeRoom ?: return
        audioEngine.playPingSiren()
        sessionScope.launch {
            repository?.sendPing(room.id, localState.value.settings.displayName)
        }
    }

    fun toggleMuteMember(targetUid: String) {
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            repository?.toggleMuteMember(room, targetUid)
        }
    }

    fun kickMember(targetUid: String) {
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            repository?.kickMember(room, targetUid)
        }
    }

    fun updateRoomPasscode(newPasscode: String) {
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            repository?.updateRoomPasscode(room, newPasscode)
                ?.onFailure { err ->
                    localState.update {
                        it.copy(errorMessage = toArabicErrorMessage(err, "تعذر تحديث رمز الدخول"))
                    }
                }
        }
    }

    fun sendChatMessage(text: String) {
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            repository?.sendChatMessage(
                roomId = room.id,
                memberUids = room.memberUids,
                text = text,
                senderName = localState.value.settings.displayName
            )
        }
    }

    fun cycleAudioRoute() {
        val ctx = appContext ?: return
        val next = when (localState.value.audioRoute) {
            AudioOutputRoute.SPEAKER -> AudioOutputRoute.EARPIECE
            AudioOutputRoute.EARPIECE -> AudioOutputRoute.BLUETOOTH
            AudioOutputRoute.BLUETOOTH -> AudioOutputRoute.SPEAKER
        }
        val applied = AudioRouteHelper.applyRoute(ctx, next)
        if (!applied && next == AudioOutputRoute.BLUETOOTH) {
            AudioRouteHelper.applyRoute(ctx, AudioOutputRoute.SPEAKER)
            localState.update {
                it.copy(
                    audioRoute = AudioOutputRoute.SPEAKER,
                    errorMessage = "لم يتم العثور على سماعة بلوتوث متصلة — تم التحويل للسماعة الخارجية"
                )
            }
        } else {
            localState.update { it.copy(audioRoute = next, errorMessage = null) }
        }
    }

    fun setMemberStatus(status: String) {
        val clean = status.trim().ifBlank { "متاح" }.take(24)
        updateSettings { it.copy(memberStatus = clean) }
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            repository?.updateMemberStatus(room, clean)
        }
    }

    fun toggleChannelLock() {
        val room = localState.value.activeRoom ?: return
        if (room.ownerId != localState.value.currentUserId) return
        sessionScope.launch {
            repository?.setChannelLock(room, !room.channelLockEnabled)
                ?.onFailure { err ->
                    localState.update {
                        it.copy(errorMessage = toArabicErrorMessage(err, "تعذر تغيير حالة قفل البث"))
                    }
                }
        }
    }

    fun sendQuickLocation(onResult: (Boolean, String) -> Unit) {
        val ctx = appContext ?: return
        val room = localState.value.activeRoom
        if (room == null) {
            onResult(false, "انضم إلى غرفة أولاً لإرسال موقعك")
            return
        }
        sessionScope.launch {
            val link = LocationHelper.getQuickMapsLink(ctx)
            if (link == null) {
                onResult(false, "تعذر تحديد الموقع الحالي، تأكد من تفعيل GPS")
            } else {
                repository?.sendChatMessage(
                    roomId = room.id,
                    memberUids = room.memberUids,
                    text = "موقعي الحالي:\n$link",
                    senderName = localState.value.settings.displayName
                )
                onResult(true, "تم إرسال موقعك في دردشة الغرفة")
            }
        }
    }

    fun clearErrorMessage() {
        localState.update { it.copy(errorMessage = null) }
    }

    fun saveProfileAvatar(avatarFilePath: String, thumbBase64: String) {
        val oldPath = localState.value.settings.avatarUri
        updateSettings {
            it.copy(
                avatarUri = avatarFilePath,
                avatarThumbBase64 = thumbBase64
            )
        }
        if (!oldPath.isNullOrBlank() && oldPath != avatarFilePath) {
            runCatching {
                val oldFile = File(oldPath)
                if (oldFile.exists() && oldFile.parentFile == appContext?.filesDir) {
                    oldFile.delete()
                }
            }
        }
        val active = localState.value.activeRoom
        if (active != null && thumbBase64.isNotBlank()) {
            sessionScope.launch {
                repository?.updateMemberAvatar(active, thumbBase64)
            }
        }
    }

    fun createRoom(roomName: String, passcode: String) {
        val repo = repository ?: return
        if (localState.value.mode == RadioMode.ALWAYS_OFF) {
            exitAlwaysOff(RadioMode.PUSH_TO_TALK)
        }
        sessionScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repo.createRoom(
                roomName = roomName,
                passcode = passcode,
                creatorName = localState.value.settings.displayName,
                creatorStatus = localState.value.settings.memberStatus,
                creatorAvatar = localState.value.settings.avatarThumbBase64
            )
                .onSuccess { room ->
                    localState.update { it.copy(isBusy = false) }
                    selectRoom(room)
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = toArabicErrorMessage(err, "تعذر إنشاء الغرفة السحابية")
                        )
                    }
                }
        }
    }

    fun joinRoomByCode(code: String, passcode: String) {
        val repo = repository ?: return
        if (localState.value.mode == RadioMode.ALWAYS_OFF) {
            exitAlwaysOff(RadioMode.PUSH_TO_TALK)
        }
        sessionScope.launch {
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repo.joinRoomByCode(
                rawCode = code,
                passcode = passcode,
                memberName = localState.value.settings.displayName,
                memberStatus = localState.value.settings.memberStatus,
                memberAvatar = localState.value.settings.avatarThumbBase64
            )
                .onSuccess { room ->
                    localState.update { it.copy(isBusy = false) }
                    selectRoom(room)
                }
                .onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = toArabicErrorMessage(err, "تعذر الانضمام للغرفة")
                        )
                    }
                }
        }
    }

    fun leaveActiveRoom() {
        val room = localState.value.activeRoom ?: return
        sessionScope.launch {
            stopAllTransmission()
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository?.leaveRoom(room)
                ?.onSuccess {
                    roomDetailJob?.cancel()
                    packetsJob?.cancel()
                    chatJob?.cancel()
                    localState.update { it.copy(isBusy = false, activeRoom = null) }
                    syncBackgroundService(null)
                    syncHardwarePttBridge()
                }
                ?.onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = toArabicErrorMessage(err, "تعذر الخروج من الغرفة")
                        )
                    }
                }
        }
    }

    fun deleteActiveRoom() {
        val room = localState.value.activeRoom ?: return
        if (room.ownerId != localState.value.currentUserId) return
        sessionScope.launch {
            stopAllTransmission()
            localState.update { it.copy(isBusy = true, errorMessage = null) }
            repository?.deleteRoom(room.id)
                ?.onSuccess {
                    roomDetailJob?.cancel()
                    packetsJob?.cancel()
                    chatJob?.cancel()
                    localState.update { it.copy(isBusy = false, activeRoom = null) }
                    syncBackgroundService(null)
                    syncHardwarePttBridge()
                }
                ?.onFailure { err ->
                    localState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = toArabicErrorMessage(err, "تعذر حذف الغرفة")
                        )
                    }
                }
        }
    }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        val previousName = localState.value.settings.displayName
        val previousBubble = localState.value.settings.floatingBubbleEnabled
        val updated = transform(localState.value.settings)
        settingsStore?.save(updated)
        audioEngine.rogerBeepStyle = updated.rogerBeepStyle
        audioEngine.radioFxEnabled = updated.radioFxEnabled
        audioEngine.noiseSuppressionEnabled = updated.noiseSuppressionEnabled
        audioEngine.squelchEnabled = updated.squelchEnabled
        localState.update {
            it.copy(
                settings = updated,
                rogerBeepEnabled = updated.rogerBeepEnabled
            )
        }
        syncHardwarePttBridge()
        syncBackgroundService(localState.value.activeRoom)
        val ctx = appContext
        if (ctx != null && updated.floatingBubbleEnabled != previousBubble) {
            if (updated.floatingBubbleEnabled && localState.value.mode != RadioMode.ALWAYS_OFF) {
                FloatingPttService.start(ctx)
            } else {
                FloatingPttService.stop(ctx)
            }
        }
        if (updated.displayName != previousName) {
            val active = localState.value.activeRoom
            if (active != null && localState.value.mode != RadioMode.ALWAYS_OFF) {
                sessionScope.launch {
                    repository?.updateMemberDisplayName(active, updated.displayName)
                }
            }
        }
    }

    fun onMicPermissionChanged(granted: Boolean) {
        localState.update { it.copy(hasMicPermission = granted) }
        if (granted && localState.value.mode == RadioMode.ALWAYS_ON && localState.value.activeRoom != null) {
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
        if (!current.hasMicPermission) return
        if (current.mode == RadioMode.ALWAYS_OFF) {
            exitAlwaysOff(RadioMode.ALWAYS_ON)
            return
        }
        if (current.activeRoom == null) return
        if (current.mode == RadioMode.ALWAYS_ON) {
            val newMode = RadioMode.PUSH_TO_TALK
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(false)
            stopAllTransmission(playTone = current.rogerBeepEnabled)
            syncHardwarePttBridge()
            syncBackgroundService(current.activeRoom, newMode)
        } else {
            val newMode = RadioMode.ALWAYS_ON
            updateSettings { it.copy(savedRadioMode = newMode.name) }
            localState.update { it.copy(mode = newMode, isPttPressed = false) }
            audioEngine.setMuted(false)
            syncHardwarePttBridge()
            syncBackgroundService(current.activeRoom, newMode)
            beginCloudTransmission()
        }
    }

    fun toggleAlwaysOff() {
        if (localState.value.mode == RadioMode.ALWAYS_OFF) {
            exitAlwaysOff(RadioMode.PUSH_TO_TALK)
        } else {
            enterAlwaysOff()
        }
    }

    fun enterAlwaysOff(context: Context? = null) {
        if (context != null && appContext == null) {
            ensureInitialized(context)
        }
        val current = localState.value
        val updatedSettings = current.settings.copy(savedRadioMode = RadioMode.ALWAYS_OFF.name)
        settingsStore?.save(updatedSettings)
        stopAllTransmission(playTone = false)
        cancelAllJobs()
        audioEngine.setMuted(true)
        localState.update {
            it.copy(
                mode = RadioMode.ALWAYS_OFF,
                isPttPressed = false,
                settings = updatedSettings,
                autoMuteRemainingSec = 0
            )
        }
        syncHardwarePttBridge()
        appContext?.let { ctx ->
            RadioForegroundService.stop(ctx)
            FloatingPttService.stop(ctx)
        }
    }

    private fun exitAlwaysOff(targetMode: RadioMode) {
        val current = localState.value
        val updatedSettings = current.settings.copy(savedRadioMode = targetMode.name)
        settingsStore?.save(updatedSettings)
        audioEngine.setMuted(false)
        localState.update {
            it.copy(
                mode = targetMode,
                isPttPressed = false,
                settings = updatedSettings
            )
        }
        syncHardwarePttBridge()
        appContext?.let { ctx ->
            if (updatedSettings.floatingBubbleEnabled) {
                FloatingPttService.start(ctx)
            }
        }
        observeRooms()
        val room = localState.value.activeRoom
        if (room != null) {
            syncBackgroundService(room, targetMode)
            attachRoomListeners(room.id)
            if (targetMode == RadioMode.ALWAYS_ON && localState.value.hasMicPermission) {
                beginCloudTransmission()
            }
        }
    }

    fun toggleRogerBeep() {
        updateSettings { it.copy(rogerBeepEnabled = !it.rogerBeepEnabled) }
    }

    private fun beginCloudTransmission() {
        val room = localState.value.activeRoom ?: return
        val uid = localState.value.currentUserId
        if (uid in room.mutedUids) {
            localState.update {
                it.copy(
                    isPttPressed = false,
                    errorMessage = "تم كتمك من قبل مالك الغرفة"
                )
            }
            return
        }
        if (room.channelLockEnabled &&
            room.activeSpeakerUid.isNotBlank() &&
            room.activeSpeakerUid != uid &&
            room.ownerId != uid
        ) {
            val who = room.activeSpeakerName.ifBlank { "عضو" }
            localState.update {
                it.copy(
                    isPttPressed = false,
                    mode = if (it.mode == RadioMode.ALWAYS_ON) RadioMode.PUSH_TO_TALK else it.mode,
                    errorMessage = "البث مقفول حالياً ($who يتحدث)"
                )
            }
            return
        }

        txStartedAtMs = System.currentTimeMillis()
        val speakerName = localState.value.settings.displayName
        sessionScope.launch {
            repository?.updateActiveSpeaker(room.id, speaking = true, speakerName = speakerName)
        }

        autoMuteJob?.cancel()
        val autoLimit = localState.value.settings.autoMuteSeconds
        if (localState.value.mode == RadioMode.ALWAYS_ON && autoLimit > 0) {
            autoMuteJob = sessionScope.launch {
                for (rem in autoLimit downTo 1) {
                    localState.update { it.copy(autoMuteRemainingSec = rem) }
                    delay(1000L)
                }
                localState.update { it.copy(autoMuteRemainingSec = 0) }
                if (localState.value.mode == RadioMode.ALWAYS_ON) {
                    toggleAlwaysOn()
                }
            }
        } else {
            localState.update { it.copy(autoMuteRemainingSec = 0) }
        }

        audioEngine.startTransmitting(
            playTone = localState.value.rogerBeepEnabled,
            onChunkEncoded = { audioBase64 ->
                val active = localState.value.activeRoom ?: return@startTransmitting
                sessionScope.launch {
                    repository?.sendVoicePacket(
                        roomId = active.id,
                        memberUids = active.memberUids,
                        audioBase64 = audioBase64,
                        speakerName = localState.value.settings.displayName,
                        channel = 1
                    )
                }
            }
        )
    }

    private fun stopAllTransmission(playTone: Boolean = false) {
        autoMuteJob?.cancel()
        autoMuteJob = null
        localState.update { it.copy(autoMuteRemainingSec = 0) }
        audioEngine.stopTransmitting(playTone = playTone)

        val room = localState.value.activeRoom
        if (txStartedAtMs > 0L) {
            val elapsedSec = ((System.currentTimeMillis() - txStartedAtMs) / 1000L).coerceAtLeast(1L)
            txStartedAtMs = 0L
            updateSettings {
                it.copy(
                    totalTxSeconds = it.totalTxSeconds + elapsedSec,
                    totalTxCount = it.totalTxCount + 1
                )
            }
            if (room != null) {
                sessionScope.launch {
                    repository?.incrementMemberTxCount(room)
                }
            }
        }
        if (room != null) {
            sessionScope.launch {
                repository?.updateActiveSpeaker(room.id, speaking = false)
            }
        }
    }

    private fun cancelAllJobs() {
        autoMuteJob?.cancel()
        roomsJob?.cancel()
        roomDetailJob?.cancel()
        packetsJob?.cancel()
        chatJob?.cancel()
        autoMuteJob = null
        roomsJob = null
        roomDetailJob = null
        packetsJob = null
        chatJob = null
    }

    fun shutdownCurrentSession(context: Context) {
        stopAllTransmission(playTone = false)
        cancelAllJobs()
        audioEngine.setMuted(true)
        HardwarePttBridge.isEnabled = false
        HardwarePttBridge.onVolumeDownPress = null
        HardwarePttBridge.onVolumeDownRelease = null
        HardwarePttBridge.onFloatingStart = null
        HardwarePttBridge.onFloatingStop = null
        RadioForegroundService.stop(context)
        FloatingPttService.stop(context)
        settingsStore = null
        localState.value = RadioSessionState()
    }
}
