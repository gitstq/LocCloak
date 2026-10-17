package com.locctl.app.adb

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.locctl.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.net.ConnectException

@TargetApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    companion object {
        const val NOTIFICATION_CHANNEL = "adb_pairing"
        private const val TAG = "AdbPairingService"

        private const val NOTIFICATION_ID = 101
        private const val REPLY_REQUEST_ID = 102
        private const val STOP_REQUEST_ID = 103
        private const val RETRY_REQUEST_ID = 104

        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val ACTION_REPLY = "reply"

        private const val REMOTE_INPUT_RESULT_KEY = "pairing_code"
        private const val PORT_KEY = "pairing_port"

        fun startIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(ACTION_START)
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, AdbPairingService::class.java).setAction(ACTION_STOP)
        }

        private fun replyIntent(context: Context, port: Int): Intent {
            return Intent(context, AdbPairingService::class.java)
                .setAction(ACTION_REPLY)
                .putExtra(PORT_KEY, port)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var adbMdns: AdbMdns? = null
    private var started = false

    private val observer = PortObserver { port ->
        Log.i(TAG, "Pairing service port found: $port")
        if (port <= 0) return@PortObserver

        com.locctl.app.core.AppLogger.i(TAG, "mDNS 发现配对端口: $port，已弹出通知栏输入框")
        val notification = createInputNotification(port)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL,
            getString(R.string.notification_channel_adb_pairing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            setShowBadge(false)
            setAllowBubbles(false)
        }
        nm.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = when (intent?.action) {
            ACTION_START -> {
                onStart()
            }
            ACTION_REPLY -> {
                val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(REMOTE_INPUT_RESULT_KEY) ?: ""
                val port = intent.getIntExtra(PORT_KEY, -1)
                if (port != -1 && code.isNotEmpty()) {
                    onInput(code.toString().trim(), port)
                } else {
                    onStart()
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                null
            }
            else -> return START_NOT_STICKY
        }

        if (notification != null) {
            try {
                val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } catch (e: Throwable) {
                Log.e(TAG, "startForeground failed", e)
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun startSearch() {
        if (started) return
        started = true
        adbMdns = AdbMdns(this, AdbMdns.TLS_PAIRING, observer).apply { start() }
    }

    private fun stopSearch() {
        if (!started) return
        started = false
        adbMdns?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSearch()
    }

    private fun onStart(): Notification {
        com.locctl.app.core.AppLogger.i(TAG, "已启动配对前台服务，开始嗅探无线配对服务...")
        startSearch()
        return searchingNotification
    }

    private fun onInput(code: String, port: Int): Notification {
        com.locctl.app.core.AppLogger.i(TAG, "已截获 6 位配对码，正在与 127.0.0.1:$port 执行 TLS SPAKE2 握手...")
        serviceScope.launch {
            val host = "127.0.0.1"
            val key = try {
                AdbManager.getOrCreateKey(this@AdbPairingService)
            } catch (e: Throwable) {
                com.locctl.app.core.AppLogger.e(TAG, "获取 ADB 密钥失败", e)
                handleResult(false, e)
                return@launch
            }

            AdbPairingClient(host, port, code, key).use { client ->
                runCatching {
                    client.start()
                }.onFailure {
                    com.locctl.app.core.AppLogger.e(TAG, "配对通信发生异常: ${it.message}", it)
                    handleResult(false, it)
                }.onSuccess { success ->
                    if (success) {
                        com.locctl.app.core.AppLogger.i(TAG, "配对握手圆满完成！公钥已成功交换并持久化")
                    } else {
                        com.locctl.app.core.AppLogger.w(TAG, "配对握手未通过（验证码错误或服务已超时）")
                    }
                    handleResult(success, null)
                }
            }
        }
        return workingNotification
    }

    private fun handleResult(success: Boolean, exception: Throwable?) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        val title: String
        val text: String?

        if (success) {
            title = getString(R.string.notification_adb_pairing_succeed_title)
            text = getString(R.string.notification_adb_pairing_succeed_text)
            stopSearch()
            AdbManager.onPairingSucceeded()
        } else {
            title = getString(R.string.notification_adb_pairing_failed_title)
            text = when (exception) {
                is ConnectException -> getString(R.string.cannot_connect_port)
                is AdbInvalidPairingCodeException -> getString(R.string.paring_code_is_wrong)
                is AdbKeyException -> getString(R.string.adb_error_key_store)
                else -> exception?.message ?: getString(R.string.notification_adb_pairing_failed_title)
            }
        }

        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            Notification.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build()
        )
        stopSelf()
    }

    private val stopNotificationAction: Notification.Action
        get() {
            val pendingIntent = PendingIntent.getService(
                this,
                STOP_REQUEST_ID,
                stopIntent(this),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    PendingIntent.FLAG_IMMUTABLE
                else
                    0
            )
            return Notification.Action.Builder(
                null,
                getString(R.string.notification_adb_pairing_stop_searching),
                pendingIntent
            ).build()
        }

    private fun replyNotificationAction(port: Int): Notification.Action {
        val remoteInput = RemoteInput.Builder(REMOTE_INPUT_RESULT_KEY).run {
            setLabel(getString(R.string.dialog_adb_pairing_paring_code))
            build()
        }

        val pendingIntent = PendingIntent.getService(
            this,
            REPLY_REQUEST_ID,
            replyIntent(this, port),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        return Notification.Action.Builder(
            null,
            getString(R.string.notification_adb_pairing_input_paring_code),
            pendingIntent
        ).addRemoteInput(remoteInput).build()
    }

    private val searchingNotification: Notification
        get() = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_adb_pairing_searching_for_service_title))
            .addAction(stopNotificationAction)
            .build()

    private fun createInputNotification(port: Int): Notification {
        return Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_adb_pairing_service_found_title))
            .addAction(replyNotificationAction(port))
            .addAction(stopNotificationAction)
            .build()
    }

    private val workingNotification: Notification
        get() = Notification.Builder(this, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_adb_pairing_working_title))
            .build()

    override fun onBind(intent: Intent?): IBinder? = null
}

