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
import com.locctl.app.core.PlaybackEngine

/**
 * 通知构建。
 *
 * 常驻推送时通知**不可滑动清除**，明确显示当前坐标与停止按钮 ——
 * 这是用户判断「现在是不是在模拟」的最直接依据。
 */
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

    fun build(state: PlaybackEngine.PlaybackState): Notification {
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
            !state.running -> context.getString(R.string.notif_idle)
            state.current != null ->
                context.getString(
                    R.string.notif_pushing,
                    state.current.formatWgs84(),
                ) + "  ·  " + context.getString(R.string.notif_count, state.pushCount)
            else -> context.getString(R.string.notif_idle)
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("locctl · ${state.modeLabel}")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_stop), stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun update(state: PlaybackEngine.PlaybackState) {
        nm.notify(NOTIF_ID, build(state))
    }

    fun cancel() {
        nm.cancel(NOTIF_ID)
    }

    companion object {
        const val CHANNEL_ID = "locctl.mock"
        const val NOTIF_ID = 1001
    }
}
