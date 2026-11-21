package com.example.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

enum class AudioOutputRoute(val labelAr: String) {
    SPEAKER("سماعة خارجية"),
    EARPIECE("سماعة الأذن"),
    BLUETOOTH("بلوتوث")
}

object AudioRouteHelper {

    fun applyRoute(context: Context, route: AudioOutputRoute): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val target = when (route) {
                    AudioOutputRoute.BLUETOOTH -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }
                    AudioOutputRoute.SPEAKER -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    AudioOutputRoute.EARPIECE -> devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
                    }
                }
                if (target != null) {
                    am.setCommunicationDevice(target)
                    true
                } else if (route == AudioOutputRoute.BLUETOOTH) {
                    false
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = (route == AudioOutputRoute.SPEAKER)
                    true
                }
            } else {
                @Suppress("DEPRECATION")
                when (route) {
                    AudioOutputRoute.SPEAKER -> {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        am.isSpeakerphoneOn = true
                        true
                    }
                    AudioOutputRoute.EARPIECE -> {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                        am.isSpeakerphoneOn = false
                        true
                    }
                    AudioOutputRoute.BLUETOOTH -> {
                        am.isSpeakerphoneOn = false
                        am.startBluetoothSco()
                        am.isBluetoothScoOn = true
                        true
                    }
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}
