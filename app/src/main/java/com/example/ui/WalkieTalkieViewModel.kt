package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioOutputRoute
import com.example.data.ApkInstallResult
import com.example.data.AppUpdateChecker
import com.example.data.AppUpdateInfo
import com.example.data.CloudRoom
import com.example.data.RoomChatMessage
import com.example.data.UserSettings
import com.example.service.RadioSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class RadioMode {
    PUSH_TO_TALK,
    ALWAYS_ON,
    ALWAYS_OFF
}

private data class UiLocalOverlayState(
    val refreshRateHz: Int = 120,
    val availableUpdate: AppUpdateInfo? = null,
    val showUpdateDialog: Boolean = false,
    val isCheckingUpdate: Boolean = false,
    val updateCheckMessage: String? = null,
    val isDownloadingUpdate: Boolean = false,
    val updateDownloadProgress: Int = 0,
    val updateDownloadDetail: String = "",
    val downloadedUpdateFilePath: String? = null,
    val pendingInstallAfterPermission: Boolean = false,
    val updateDownloadError: String? = null
)

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
    val updateDownloadDetail: String = "",
    val downloadedUpdateFilePath: String? = null,
    val pendingInstallAfterPermission: Boolean = false,
    val updateDownloadError: String? = null,
    val lastVoiceSenderName: String = "",
    val canReplayVoice: Boolean = false,
    val pingBannerMessage: String? = null,
    val chatMessages: List<RoomChatMessage> = emptyList(),
    val audioRoute: AudioOutputRoute = AudioOutputRoute.SPEAKER,
    val autoMuteRemainingSec: Int = 0
)

class WalkieTalkieViewModel(
    application: Application,
    currentUserId: String
) : AndroidViewModel(application) {

    private val overlayState = MutableStateFlow(UiLocalOverlayState())

    init {
        RadioSessionManager.ensureInitialized(application, currentUserId)
        checkForUpdates(manual = false)
    }

    val uiState: StateFlow<WalkieTalkieUiState> = combine(
        RadioSessionManager.state,
        overlayState
    ) { session, overlay ->
        WalkieTalkieUiState(
            mode = session.mode,
            isPttPressed = session.isPttPressed,
            isTransmitting = session.isTransmitting,
            isReceiving = session.isReceiving,
            micLevel = session.micLevel,
            myRooms = session.myRooms,
            activeRoom = session.activeRoom,
            rogerBeepEnabled = session.rogerBeepEnabled,
            refreshRateHz = overlay.refreshRateHz,
            hasMicPermission = session.hasMicPermission,
            isBusy = session.isBusy,
            errorMessage = session.errorMessage,
            settings = session.settings,
            currentUserId = session.currentUserId,
            currentUserEmail = session.currentUserEmail,
            availableUpdate = overlay.availableUpdate,
            showUpdateDialog = overlay.showUpdateDialog,
            isCheckingUpdate = overlay.isCheckingUpdate,
            updateCheckMessage = overlay.updateCheckMessage,
            isDownloadingUpdate = overlay.isDownloadingUpdate,
            updateDownloadProgress = overlay.updateDownloadProgress,
            updateDownloadDetail = overlay.updateDownloadDetail,
            downloadedUpdateFilePath = overlay.downloadedUpdateFilePath,
            pendingInstallAfterPermission = overlay.pendingInstallAfterPermission,
            updateDownloadError = overlay.updateDownloadError,
            lastVoiceSenderName = session.lastVoiceSenderName,
            canReplayVoice = session.canReplayVoice,
            pingBannerMessage = session.pingBannerMessage,
            chatMessages = session.chatMessages,
            audioRoute = session.audioRoute,
            autoMuteRemainingSec = session.autoMuteRemainingSec
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = WalkieTalkieUiState(
            mode = RadioSessionManager.state.value.mode,
            settings = RadioSessionManager.state.value.settings,
            currentUserId = RadioSessionManager.state.value.currentUserId,
            currentUserEmail = RadioSessionManager.state.value.currentUserEmail
        )
    )

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            overlayState.update {
                it.copy(
                    isCheckingUpdate = true,
                    updateCheckMessage = if (manual) "جاري التحقق من التحديثات…" else it.updateCheckMessage
                )
            }
            AppUpdateChecker.checkForUpdate()
                .onSuccess { info ->
                    val previousVersion = overlayState.value.availableUpdate?.latestVersion
                    overlayState.update {
                        val versionChanged = previousVersion != null && previousVersion != info.latestVersion
                        it.copy(
                            isCheckingUpdate = false,
                            availableUpdate = if (info.isUpdateAvailable) info else null,
                            showUpdateDialog = info.isUpdateAvailable,
                            downloadedUpdateFilePath = if (versionChanged) null else it.downloadedUpdateFilePath,
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
                    overlayState.update {
                        it.copy(
                            isCheckingUpdate = false,
                            updateCheckMessage = if (manual) "تعذر التحقق من التحديثات حالياً" else null
                        )
                    }
                }
        }
    }

    fun dismissUpdateDialog() {
        overlayState.update { it.copy(showUpdateDialog = false) }
    }

    fun startInAppUpdateDownload() {
        val update = overlayState.value.availableUpdate ?: return
        if (overlayState.value.isDownloadingUpdate) return

        val existingPath = overlayState.value.downloadedUpdateFilePath
        if (existingPath != null && File(existingPath).exists()) {
            installDownloadedUpdate()
            return
        }

        viewModelScope.launch {
            overlayState.update {
                it.copy(
                    isDownloadingUpdate = true,
                    updateDownloadProgress = 0,
                    updateDownloadDetail = "جاري بدء التنزيل…",
                    updateDownloadError = null,
                    pendingInstallAfterPermission = false
                )
            }
            AppUpdateChecker.downloadUpdateApk(
                context = getApplication(),
                url = update.downloadUrl,
                onProgress = { progress ->
                    val downloadedMb = progress.downloadedBytes / (1024f * 1024f)
                    val detail = if (progress.totalBytes > 0) {
                        val totalMb = progress.totalBytes / (1024f * 1024f)
                        String.format("%.1f / %.1f MB", downloadedMb, totalMb)
                    } else {
                        String.format("%.1f MB", downloadedMb)
                    }
                    overlayState.update {
                        it.copy(
                            updateDownloadProgress = progress.percent,
                            updateDownloadDetail = detail
                        )
                    }
                }
            )
                .onSuccess { file ->
                    overlayState.update {
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
                    overlayState.update {
                        it.copy(
                            isDownloadingUpdate = false,
                            updateDownloadError = err.localizedMessage ?: "فشل تنزيل التحديث"
                        )
                    }
                }
        }
    }

    fun onAppResumed() {
        val state = overlayState.value
        if (state.pendingInstallAfterPermission && state.downloadedUpdateFilePath != null) {
            val ctx = getApplication<Application>()
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
                ctx.packageManager.canRequestPackageInstalls()
            ) {
                overlayState.update { it.copy(pendingInstallAfterPermission = false) }
                installDownloadedUpdate()
            }
        }
    }

    fun installDownloadedUpdate() {
        val path = overlayState.value.downloadedUpdateFilePath ?: return
        val file = File(path)
        when (AppUpdateChecker.installApk(getApplication(), file)) {
            ApkInstallResult.LAUNCHED_INSTALLER -> {
                overlayState.update {
                    it.copy(
                        pendingInstallAfterPermission = false,
                        updateDownloadError = null
                    )
                }
            }
            ApkInstallResult.NEEDS_UNKNOWN_SOURCES_PERMISSION -> {
                overlayState.update {
                    it.copy(
                        pendingInstallAfterPermission = true,
                        updateDownloadError = "قم بتفعيل «السماح من هذا المصدر» وسيطلب التثبيت تلقائياً عند الرجوع"
                    )
                }
            }
            ApkInstallResult.FAILED -> {
                overlayState.update {
                    it.copy(
                        downloadedUpdateFilePath = null,
                        pendingInstallAfterPermission = false,
                        updateDownloadError = "تعذر فتح المثبّت، يرجى إعادة التنزيل"
                    )
                }
            }
        }
    }

    fun updateRefreshRate(hz: Int) {
        overlayState.update { it.copy(refreshRateHz = hz.coerceAtLeast(60)) }
    }

    fun selectRoom(room: CloudRoom) = RadioSessionManager.selectRoom(room)
    fun replayLastVoiceMessage() = RadioSessionManager.replayLastVoiceMessage()
    fun sendPing() = RadioSessionManager.sendPing()
    fun toggleMuteMember(targetUid: String) = RadioSessionManager.toggleMuteMember(targetUid)
    fun kickMember(targetUid: String) = RadioSessionManager.kickMember(targetUid)
    fun updateRoomPasscode(newPasscode: String) = RadioSessionManager.updateRoomPasscode(newPasscode)
    fun sendChatMessage(text: String) = RadioSessionManager.sendChatMessage(text)
    fun cycleAudioRoute() = RadioSessionManager.cycleAudioRoute()
    fun setMemberStatus(status: String) = RadioSessionManager.setMemberStatus(status)
    fun toggleChannelLock() = RadioSessionManager.toggleChannelLock()
    fun sendQuickLocation(onResult: (Boolean, String) -> Unit) = RadioSessionManager.sendQuickLocation(onResult)
    fun clearErrorMessage() = RadioSessionManager.clearErrorMessage()
    fun saveProfileAvatar(avatarFilePath: String, thumbBase64: String) =
        RadioSessionManager.saveProfileAvatar(avatarFilePath, thumbBase64)
    fun createRoom(roomName: String, passcode: String) = RadioSessionManager.createRoom(roomName, passcode)
    fun joinRoomByCode(code: String, passcode: String) = RadioSessionManager.joinRoomByCode(code, passcode)
    fun leaveActiveRoom() = RadioSessionManager.leaveActiveRoom()
    fun deleteActiveRoom() = RadioSessionManager.deleteActiveRoom()
    fun updateSettings(transform: (UserSettings) -> UserSettings) = RadioSessionManager.updateSettings(transform)
    fun onMicPermissionChanged(granted: Boolean) = RadioSessionManager.onMicPermissionChanged(granted)
    fun onPttPressStart() = RadioSessionManager.onPttPressStart()
    fun onPttPressEnd() = RadioSessionManager.onPttPressEnd()
    fun toggleAlwaysOn() = RadioSessionManager.toggleAlwaysOn()
    fun toggleAlwaysOff() = RadioSessionManager.toggleAlwaysOff()
    fun toggleRogerBeep() = RadioSessionManager.toggleRogerBeep()

    // Note: Do NOT stop RadioSessionManager or RadioForegroundService here!
    // Background session persists across Activity close / Recent Apps swipe-away until ALWAYS_OFF.

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
