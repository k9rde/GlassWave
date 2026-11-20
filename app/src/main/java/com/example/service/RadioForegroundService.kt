package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/**
 * Foreground service that keeps GlassWave alive in the background so users can
 * continue receiving voice broadcasts with a persistent control notification.
 */
class RadioForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val roomName = intent?.getStringExtra(EXTRA_ROOM_NAME).orEmpty().ifBlank { "غرفة لاسلكي" }
        ensureChannel()
        val notification = buildNotification(roomName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "الاستماع بالخلفية",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "إشعار دائم لإبقاء الاستماع اللاسلكي نشطاً في الخلفية"
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
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("GlassWave • متصل بالخلفية")
            .setContentText("الاستماع نشط في غرفة: $roomName")
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(0, "إيقاف", stopPending)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "glasswave_bg_radio"
        private const val NOTIFICATION_ID = 2601
        private const val ACTION_STOP = "com.example.glasswave.STOP_BG_RADIO"
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
