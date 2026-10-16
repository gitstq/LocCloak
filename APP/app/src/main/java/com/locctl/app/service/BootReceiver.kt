package com.locctl.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启。
 *
 * 只在用户显式开启过该选项时才恢复推送 —— 否则会在用户不知情时
 * 启动一个持有 WakeLock 的前台服务，属于侵犯性行为。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_AUTO_START, false)) {
            Log.i(TAG, "auto-start disabled, ignoring $action")
            return
        }

        val last = prefs.getString(KEY_LAST_POINT, null) ?: return
        val parts = last.split(",")
        if (parts.size != 2) return

        Log.i(TAG, "restoring last mock point after $action")
        InjectionForegroundService.hold(
            context,
            com.locctl.app.core.GeoPoint(
                parts[0].toDoubleOrNull() ?: return,
                parts[1].toDoubleOrNull() ?: return,
            ),
        )
    }

    companion object {
        private const val TAG = "BootReceiver"
        const val PREFS = "locctl_boot"
        const val KEY_AUTO_START = "auto_start"
        const val KEY_LAST_POINT = "last_point"

        fun rememberPoint(context: Context, lat: Double, lng: Double) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_POINT, "$lat,$lng")
                .apply()
        }

        fun setAutoStart(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_AUTO_START, enabled)
                .apply()
        }
    }
}
