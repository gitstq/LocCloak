package com.locctl.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.locctl.app.MainActivity
import com.locctl.app.R

class ServiceNotifier(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun build(lat: Double, lng: Double, count: Long, isRunning: Boolean, error: String? = null): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, InjectionForegroundService::class.java)
                .setAction(InjectionForegroundService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val text = when {
            error != null -> "错误: $error"
            !isRunning -> context.getString(R.string.notif_idle)
            else -> "正在注入 %.6f, %.6f · %d 次".format(lat, lng, count)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("locctl 模拟定位")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(isRunning)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRunning) {
            builder.addAction(0, context.getString(R.string.notif_stop), stopIntent)
        }

        return builder.build()
    }

    fun update(lat: Double, lng: Double, count: Long, isRunning: Boolean, error: String? = null) {
        nm.notify(NOTIF_ID, build(lat, lng, count, isRunning, error))
    }

    fun cancel() {
        nm.cancel(NOTIF_ID)
    }

    companion object {
        const val CHANNEL_ID = "locctl.mock"
        const val NOTIF_ID = 1001
    }
}
