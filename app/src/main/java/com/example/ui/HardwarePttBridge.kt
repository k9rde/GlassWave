package com.example.ui

/**
 * Bridges hardware Volume keys and Floating PTT Overlay Bubble events
 * directly into the active WalkieTalkieViewModel PTT controls.
 */
object HardwarePttBridge {
    @Volatile
    var isEnabled: Boolean = false

    @Volatile
    var onVolumeDownPress: (() -> Unit)? = null

    @Volatile
    var onVolumeDownRelease: (() -> Unit)? = null

    @Volatile
    var onFloatingStart: (() -> Unit)? = null

    @Volatile
    var onFloatingStop: (() -> Unit)? = null
}
