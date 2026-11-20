package com.example.data

import android.content.Context

data class UserSettings(
    val displayName: String = "",
    val avatarUri: String? = null,
    val glassOpacity: Float = 0.75f,
    val rogerBeepEnabled: Boolean = true,
    val rogerBeepStyle: String = "CLASSIC",
    val radioFxEnabled: Boolean = false,
    val hapticEnabled: Boolean = true,
    val keepScreenOn: Boolean = true,
    val volumeKeyPttEnabled: Boolean = true,
    val backgroundListeningEnabled: Boolean = false,
    val micGain: Float = 1.2f,
    val savedRadioMode: String = "PUSH_TO_TALK"
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
            glassOpacity = prefs.getFloat(KEY_GLASS_OPACITY, 0.75f).coerceIn(0.25f, 1.0f),
            rogerBeepEnabled = prefs.getBoolean(KEY_ROGER_BEEP, true),
            rogerBeepStyle = prefs.getString(KEY_ROGER_STYLE, "CLASSIC") ?: "CLASSIC",
            radioFxEnabled = prefs.getBoolean(KEY_RADIO_FX, false),
            hapticEnabled = prefs.getBoolean(KEY_HAPTIC, true),
            keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true),
            volumeKeyPttEnabled = prefs.getBoolean(KEY_VOLUME_PTT, true),
            backgroundListeningEnabled = prefs.getBoolean(KEY_BG_LISTEN, false),
            micGain = prefs.getFloat(KEY_MIC_GAIN, 1.2f).coerceIn(1.0f, 2.0f),
            savedRadioMode = prefs.getString(KEY_RADIO_MODE, "PUSH_TO_TALK") ?: "PUSH_TO_TALK"
        )
    }

    fun save(settings: UserSettings) {
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, settings.displayName.trim().take(40))
            .putString(KEY_AVATAR_URI, settings.avatarUri)
            .putFloat(KEY_GLASS_OPACITY, settings.glassOpacity.coerceIn(0.25f, 1.0f))
            .putBoolean(KEY_ROGER_BEEP, settings.rogerBeepEnabled)
            .putString(KEY_ROGER_STYLE, settings.rogerBeepStyle)
            .putBoolean(KEY_RADIO_FX, settings.radioFxEnabled)
            .putBoolean(KEY_HAPTIC, settings.hapticEnabled)
            .putBoolean(KEY_KEEP_SCREEN_ON, settings.keepScreenOn)
            .putBoolean(KEY_VOLUME_PTT, settings.volumeKeyPttEnabled)
            .putBoolean(KEY_BG_LISTEN, settings.backgroundListeningEnabled)
            .putFloat(KEY_MIC_GAIN, settings.micGain.coerceIn(1.0f, 2.0f))
            .putString(KEY_RADIO_MODE, settings.savedRadioMode)
            .commit()
    }

    companion object {
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_GLASS_OPACITY = "glass_opacity"
        private const val KEY_ROGER_BEEP = "roger_beep"
        private const val KEY_ROGER_STYLE = "roger_beep_style"
        private const val KEY_RADIO_FX = "radio_fx"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_VOLUME_PTT = "volume_key_ptt"
        private const val KEY_BG_LISTEN = "background_listening"
        private const val KEY_MIC_GAIN = "mic_gain"
        private const val KEY_RADIO_MODE = "radio_mode"
    }
}
