package com.example.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Foreground service that keeps GlassWave alive even when swiped away from Recent Apps
 * or while using other apps. Only stops when ALWAYS_OFF ("إغلاق دائم") is activated.
 */
class RadioForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_ALWAYS_OFF) {
            RadioSessionManager.enterAlwaysOff(applicationContext)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Handle sticky system restarts cleanly: reconnect session unless ALWAYS_OFF
        val isStickyRestart = intent == null
        if (isStickyRestart) {
            RadioSessionManager.ensureInitialized(applicationContext)
            if (!RadioSessionManager.shouldStayAliveInBackground()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val roomName = intent?.getStringExtra(EXTRA_ROOM_NAME)
            ?.takeIf { it.isNotBlank() }
            ?: RadioSessionManager.activeRoomNameOrDefault()

        ensureChannel()
        val notification = buildNotification(roomName)

        if (!startForegroundCompat(notification, allowMicType = !isStickyRestart)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep running in background when user swipes app from Recent Apps unless ALWAYS_OFF
        if (!RadioSessionManager.shouldStayAliveInBackground()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun startForegroundCompat(notification: Notification, allowMicType: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val hasMic = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                val fgsType = if (allowMicType && hasMic) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                }
                try {
                    startForeground(NOTIFICATION_ID, notification, fgsType)
                } catch (_: SecurityException) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "البث والاستماع الدائم",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "يبقي الاتصال الصوتي نشطاً في الخلفية حتى الضغط على إغلاق دائم"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(roomName: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RadioForegroundService::class.java).apply {
            action = ACTION_ALWAYS_OFF
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GlassWave • الاتصال نشط بالخلفية")
            .setContentText("غرفة: $roomName • ينقطع فقط عند الضغط على إغلاق دائم")
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "إغلاق دائم", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "glasswave_bg_radio"
        private const val NOTIFICATION_ID = 2601
        private const val ACTION_ALWAYS_OFF = "com.example.glasswave.ALWAYS_OFF_RADIO"
        private const val EXTRA_ROOM_NAME = "room_name"

        fun start(context: Context, roomName: String) {
            val intent = Intent(context, RadioForegroundService::class.java).apply {
                putExtra(EXTRA_ROOM_NAME, roomName)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, RadioForegroundService::class.java))
            } catch (_: Exception) {}
        }
    }
}
