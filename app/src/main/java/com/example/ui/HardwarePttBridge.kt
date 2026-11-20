package com.example.ui

/**
 * Lightweight bridge allowing MainActivity hardware Volume key events
 * to trigger Push-To-Talk when enabled in Settings.
 */
object HardwarePttBridge {
    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var onVolumeDownPress: (() -> Unit)? = null

    @Volatile
    var onVolumeDownRelease: (() -> Unit)? = null
}
