package com.example.data

import android.content.Context

data class UserSettings(
    val displayName: String = "",
    val avatarUri: String? = null,
    val avatarThumbBase64: String = "",
    val glassOpacity: Float = 0.75f,
    val glassTheme: String = "CYAN",
    val memberStatus: String = "متاح",
    val selectedChannel: Int = 1,
    val rogerBeepEnabled: Boolean = true,
    val rogerBeepStyle: String = "CLASSIC",
    val radioFxEnabled: Boolean = false,
    val noiseSuppressionEnabled: Boolean = true,
    val squelchEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val volumeKeyPttEnabled: Boolean = true,
    val backgroundListeningEnabled: Boolean = true,
    val floatingBubbleEnabled: Boolean = false,
    val autoMuteSeconds: Int = 0,
    val micGain: Float = 1.2f,
    val savedRadioMode: String = "PUSH_TO_TALK",
    val totalTxSeconds: Long = 0L,
    val totalTxCount: Int = 0
)

class UserSettingsStore(context: Context, userId: String) {

    private val prefs = context.applicationContext.getSharedPreferences(
        "glasswave_settings_$userId",
        Context.MODE_PRIVATE
    )

    fun load(defaultName: String): UserSettings {
        val savedName = prefs.getString(KEY_DISPLAY_NAME, null)?.takeIf { it.isNotBlank() }
        return UserSettings(
            displayName = savedName ?: defaultName.ifBlank { "صديق" },
            avatarUri = prefs.getString(KEY_AVATAR_URI, null),
            avatarThumbBase64 = prefs.getString(KEY_AVATAR_THUMB, "") ?: "",
            glassOpacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.75f).coerceIn(0.25f, 1.0f),
            glassTheme = prefs.getString(KEY_GLASS_THEME, "CYAN") ?: "CYAN",
            memberStatus = prefs.getString(KEY_MEMBER_STATUS, "متاح") ?: "متاح",
            selectedChannel = prefs.getInt(KEY_CHANNEL, 1).coerceIn(1, 99),
            rogerBeepEnabled = prefs.getBoolean(KEY_ROGER_BEEP, true),
            rogerBeepStyle = prefs.getString(KEY_ROGER_STYLE, "CLASSIC") ?: "CLASSIC",
            radioFxEnabled = prefs.getBoolean(KEY_RADIO_FX, false),
            noiseSuppressionEnabled = prefs.getBoolean(KEY_NOISE_SUPP, true),
            squelchEnabled = prefs.getBoolean(KEY_SQUELCH, true),
            hapticEnabled = prefs.getBoolean(KEY_HAPTIC, true),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            volumeKeyPttEnabled = prefs.getBoolean(KEY_VOLUME_PTT, true),
            backgroundListeningEnabled = prefs.getBoolean(KEY_BG_LISTEN, true),
            floatingBubbleEnabled = prefs.getBoolean(KEY_FLOATING_BUBBLE, false),
            autoMuteSeconds = prefs.getInt(KEY_AUTO_MUTE_SEC, 0),
            micGain = prefs.getFloat(KEY_MIC_GAIN, 1.2f).coerceIn(1.0f, 2.0f),
            savedRadioMode = prefs.getString(KEY_RADIO_MODE, "PUSH_TO_TALK") ?: "PUSH_TO_TALK",
            totalTxSeconds = prefs.getLong(KEY_TOTAL_TX_SECONDS, 0L).coerceAtLeast(0L),
            totalTxCount = prefs.getInt(KEY_TOTAL_TX_COUNT, 0).coerceAtLeast(0)
        )
    }

    fun save(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, settings.displayName.trim().take(40))
            .putString(KEY_AVATAR_URI, settings.avatarUri)
            .putString(KEY_AVATAR_THUMB, settings.avatarThumbBase64)
            .putFloat(KEY_GLASS_OPACITY, settings.glassOpacity.coerceIn(0.25f, 1.0f))
            .putString(KEY_GLASS_THEME, settings.glassTheme)
            .putString(KEY_MEMBER_STATUS, settings.memberStatus)
            .putInt(KEY_CHANNEL, settings.selectedChannel.coerceIn(1, 99))
            .putBoolean(KEY_ROGER_BEEP, settings.rogerBeepEnabled)
            .putString(KEY_ROGER_STYLE, settings.rogerBeepStyle)
            .putBoolean(KEY_RADIO_FX, settings.radioFxEnabled)
            .putBoolean(KEY_NOISE_SUPP, settings.noiseSuppressionEnabled)
            .putBoolean(KEY_SQUELCH, settings.squelchEnabled)
            .putBoolean(KEY_HAPTIC, settings.hapticEnabled)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_VOLUME_PTT, settings.volumeKeyPttEnabled)
            .putBoolean(KEY_BG_LISTEN, settings.backgroundListeningEnabled)
            .putBoolean(KEY_FLOATING_BUBBLE, settings.floatingBubbleEnabled)
            .putInt(KEY_AUTO_MUTE_SEC, settings.autoMuteSeconds)
            .putFloat(KEY_MIC_GAIN, settings.micGain.coerceIn(1.0f, 2.0f))
            .putString(KEY_RADIO_MODE, settings.savedRadioMode)
            .putLong(KEY_TOTAL_TX_SECONDS, settings.totalTxSeconds)
            .putInt(KEY_TOTAL_TX_COUNT, settings.totalTxCount)
            .commit()
    }

    companion object {
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_AVATAR_THUMB = "avatar_thumb_base64"
        private const val KEY_GLASS_OPACITY = "glass_opacity"
        private const val KEY_GLASS_THEME = "glass_theme"
        private const val KEY_MEMBER_STATUS = "member_status"
        private const val KEY_CHANNEL = "selected_channel"
        private const val KEY_ROGER_BEEP = "roger_beep"
        private const val KEY_ROGER_STYLE = "roger_beep_style"
        private const val KEY_RADIO_FX = "radio_fx"
        private const val KEY_NOISE_SUPP = "noise_suppression"
        private const val KEY_SQUELCH = "squelch_enabled"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VOLUME_PTT = "volume_key_ptt"
        private const val KEY_BG_LISTEN = "background_listening"
        private const val KEY_FLOATING_BUBBLE = "floating_bubble"
        private const val KEY_AUTO_MUTE_SEC = "auto_mute_sec"
        private const val KEY_MIC_GAIN = "mic_gain"
        private const val KEY_RADIO_MODE = "radio_mode"
        private const val KEY_TOTAL_TX_SECONDS = "total_tx_seconds"
        private const val KEY_TOTAL_TX_COUNT = "total_tx_count"
    }
}
