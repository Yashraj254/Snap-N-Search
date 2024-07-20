package com.yashraj.snapnsearch.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.yashraj.snapnsearch.services.BasicForegroundService.Companion.NOTIFICATION_ACTION_ID
import com.yashraj.snapnsearch.services.BasicForegroundService.Companion.NOTIFICATION_ACTION_STOP
import com.yashraj.snapnsearch.services.BasicForegroundService.Companion.NOTIFICATION_CHANNEL_FOREGROUND

/**
 * Foreground service for MediaProjection
 */
class BasicForegroundService : Service() {
    companion object {
        const val NOTIFICATION_CHANNEL_FOREGROUND = "notification_channel_foreground"
        const val NOTIFICATION_ACTION_STOP = "notification_action_stop"
        const val NOTIFICATION_ACTION_ID = "notification_action_id"
        private const val FOREGROUND_SERVICE_ID = 7594
        const val FOREGROUND_NOTIFICATION_ID = 8140
        private const val FOREGROUND_ON_START =
            "BasicForegroundService.FOREGROUND_ON_START"
        private const val RESUME_SCREENSHOT =
            "BasicForegroundService.RESUME_SCREENSHOT"
        var instance: BasicForegroundService? = null

        /**
         * Start this service in the foreground
         */
        fun startForegroundService(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                return
            }
            val serviceIntent = Intent(context, BasicForegroundService::class.java)
            serviceIntent.action = FOREGROUND_ON_START
            context.startForegroundService(serviceIntent)
        }

    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (intent?.action == FOREGROUND_ON_START) {
            foreground()
        } else if (intent?.action == RESUME_SCREENSHOT) {
            foreground()
        }

        return START_STICKY
    }

    /**
     * Start foreground with sticky notification, necessary for MediaProjection
     */
    fun foreground() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }

        startForeground(
            FOREGROUND_SERVICE_ID,
            foregroundNotification(this, FOREGROUND_NOTIFICATION_ID).build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    /**
     * Stop foreground and remove sticky notification
     */
    fun background() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}

fun foregroundNotification(context: Context, notificationId: Int): Notification.Builder {
    return Notification.Builder(context, createNotificationForegroundServiceChannel(context))
        .apply {
            setShowWhen(false)
            setContentTitle("Taking Screenshot...")
            setContentText("Please wait")
            setAutoCancel(true)
            setSmallIcon(android.R.drawable.divider_horizontal_dark)
            val notificationIntent = Intent().apply {
                action = NOTIFICATION_ACTION_STOP
                putExtra(NOTIFICATION_ACTION_ID, notificationId)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                8456,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setContentIntent(pendingIntent)
        }
}

/**
 * Create notification channel (if it does not exists) and return its name.
 */
fun createNotificationForegroundServiceChannel(context: Context): String {

    val channelName = "Show notification when taking screenshot"
    val notificationTitle = "Taking Screenshot..."
    val channelDescription =
        "Show notification " + "\n'$notificationTitle'"

    context.applicationContext.getSystemService(NotificationManager::class.java)?.run {
        if (getNotificationChannel(NOTIFICATION_CHANNEL_FOREGROUND) == null) {
            createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_FOREGROUND,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = channelDescription
                    enableVibration(false)
                    enableLights(false)
                    setSound(null, null)
                })
        }
    }
    return NOTIFICATION_CHANNEL_FOREGROUND
}
