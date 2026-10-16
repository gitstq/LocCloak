package com.locctl.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.locctl.app.LocctlApp
import com.locctl.app.core.GeoPoint
import com.locctl.app.core.PlaybackEngine
import com.locctl.app.core.ProviderFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 常驻注入服务。
 *
 * 为什么必须放前台服务：
 *   HyperOS 对后台进程管控严格，普通 Service 息屏几分钟就被杀，
 *   常驻推送会中断。前台服务 + location 类型是 Android 14 起的硬性要求。
 *
 * 为什么持 WakeLock：
 *   推送循环用 Handler.postDelayed，设备休眠时定时器可能被挂起。
 *   一把 partial wake lock 足够，且只在推送期间持有。
 */
class InjectionForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var notifier: ServiceNotifier
    private var engine: PlaybackEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notifier = ServiceNotifier(this)
        ensureChannelsInstalled()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            ACTION_START_HOLD -> {
                val point = intent.getParcelableExtra<GeoPoint>(EXTRA_POINT) ?: return START_NOT_STICKY
                startHold(point, intent.getLongExtra(EXTRA_INTERVAL, 500L))
            }
            ACTION_START_ROUTE -> {
                val samples = intent.getParcelableArrayListExtra<GeoPoint>(EXTRA_SAMPLES)
                    ?: return START_NOT_STICKY
                startRoute(samples, intent.getLongExtra(EXTRA_INTERVAL, 1000L))
            }
            ACTION_START_WANDER -> {
                val center = intent.getParcelableExtra<GeoPoint>(EXTRA_POINT) ?: return START_NOT_STICKY
                val radius = intent.getDoubleExtra(EXTRA_RADIUS, 300.0)
                startWander(center, radius, intent.getLongExtra(EXTRA_INTERVAL, 3000L))
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(state: PlaybackEngine.PlaybackState) {
        ServiceCompat.startForeground(
            this,
            ServiceNotifier.NOTIF_ID,
            notifier.build(state),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )
    }

    private fun ensureChannelsInstalled() {
        scope.launch {
            val app = LocctlApp.get()
            runCatching { app.controller.install() }
                .onFailure { e ->
                    // 装不上就通知用户，但服务仍以前台形式起来，避免闪退
                    notifier.update(
                        PlaybackEngine.PlaybackState.IDLE.copy(
                            lastError = e.message ?: "注入通道不可用",
                        ),
                    )
                }
        }
    }

    private fun ensureEngine(): PlaybackEngine {
        if (engine == null) {
            engine = PlaybackEngine(
                controller = LocctlApp.get().controller,
                scope = scope,
                onTick = { state ->
                    notifier.update(state)
                    PlaybackStateBus.publish(state)
                },
            )
        }
        return engine!!
    }

    private fun startHold(point: GeoPoint, intervalMs: Long) {
        acquireWakeLock()
        startForegroundCompat(PlaybackEngine.PlaybackState.IDLE)
        ensureEngine().startHold(point, intervalMs, 5f)
    }

    private fun startRoute(samples: List<GeoPoint>, intervalMs: Long) {
        acquireWakeLock()
        startForegroundCompat(PlaybackEngine.PlaybackState.IDLE)
        ensureEngine().startRoute(samples, intervalMs, 5f)
    }

    private fun startWander(center: GeoPoint, radius: Double, intervalMs: Long) {
        acquireWakeLock()
        startForegroundCompat(PlaybackEngine.PlaybackState.IDLE)
        ensureEngine().startWander(center, radius, intervalMs, 20f)
    }

    private fun stopPlayback() {
        engine?.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locctl:push").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        releaseWakeLock()
        notifier.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_HOLD = "com.locctl.app.action.START_HOLD"
        const val ACTION_START_ROUTE = "com.locctl.app.action.START_ROUTE"
        const val ACTION_START_WANDER = "com.locctl.app.action.START_WANDER"
        const val ACTION_STOP = "com.locctl.app.action.STOP"

        const val EXTRA_POINT = "point"
        const val EXTRA_SAMPLES = "samples"
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_RADIUS = "radius"

        fun hold(context: android.content.Context, point: GeoPoint, intervalMs: Long = 500L) {
            context.startForegroundService(
                Intent(context, InjectionForegroundService::class.java)
                    .setAction(ACTION_START_HOLD)
                    .putExtra(EXTRA_POINT, point)
                    .putExtra(EXTRA_INTERVAL, intervalMs),
            )
        }

        fun route(context: android.content.Context, samples: List<GeoPoint>, intervalMs: Long = 1000L) {
            context.startForegroundService(
                Intent(context, InjectionForegroundService::class.java)
                    .setAction(ACTION_START_ROUTE)
                    .putParcelableArrayListExtra(EXTRA_SAMPLES, ArrayList(samples))
                    .putExtra(EXTRA_INTERVAL, intervalMs),
            )
        }

        fun wander(context: android.content.Context, center: GeoPoint, radiusMeters: Double, intervalMs: Long = 3000L) {
            context.startForegroundService(
                Intent(context, InjectionForegroundService::class.java)
                    .setAction(ACTION_START_WANDER)
                    .putExtra(EXTRA_POINT, center)
                    .putExtra(EXTRA_RADIUS, radiusMeters)
                    .putExtra(EXTRA_INTERVAL, intervalMs),
            )
        }

        fun stop(context: android.content.Context) {
            context.startService(
                Intent(context, InjectionForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}

/** 播放状态的进程内总线，供 Compose 订阅 */
object PlaybackStateBus {
    private val flow = kotlinx.coroutines.flow.MutableStateFlow(PlaybackEngine.PlaybackState.IDLE)
    val states: kotlinx.coroutines.flow.StateFlow<PlaybackEngine.PlaybackState> = flow

    fun publish(state: PlaybackEngine.PlaybackState) {
        flow.value = state
    }
}
