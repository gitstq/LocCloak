package com.locctl.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.locctl.app.core.LocationInjector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InjectionState(
    val isRunning: Boolean = false,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val count: Long = 0,
    val error: String? = null
)

class InjectionForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var injectionJob: Job? = null

    private lateinit var notifier: ServiceNotifier
    private var wakeLock: PowerManager.WakeLock? = null
    private val injector = LocationInjector()

    override fun onCreate() {
        super.onCreate()
        notifier = ServiceNotifier(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopInjection()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
                val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL, 500L)
                val accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 3f)

                if (!lat.isNaN() && !lng.isNaN()) {
                    startInjection(lat, lng, intervalMs, accuracy)
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundCompat(lat: Double, lng: Double) {
        val notification = notifier.build(lat, lng, 0, true)
        val locationType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(this, ServiceNotifier.NOTIF_ID, notification, locationType)
        } catch (e: Throwable) {
            Log.w(TAG, "startForeground with location type failed, retrying without it", e)
            runCatching {
                ServiceCompat.startForeground(this, ServiceNotifier.NOTIF_ID, notification, 0)
            }.onFailure {
                Log.e(TAG, "startForeground failed entirely", it)
                stopSelf()
            }
        }
    }

    private fun startInjection(lat: Double, lng: Double, intervalMs: Long, accuracy: Float) {
        acquireWakeLock()
        startForegroundCompat(lat, lng)

        injectionJob?.cancel()
        injectionJob = serviceScope.launch {
            _isRunning.value = true
            _injectCount.value = 0L
            _targetLat.value = lat
            _targetLng.value = lng
            _lastError.value = null
            _state.value = InjectionState(isRunning = true, lat = lat, lng = lng, count = 0)
            try {
                com.locctl.app.core.AppLogger.i(TAG, "启动前台注入服务，目标坐标 WGS: %.6f, %.6f (间隔: %d ms)".format(lat, lng, intervalMs))
                injector.installProviders()

                var count = 0L
                var consecutiveErrors = 0
                while (isActive) {
                    try {
                        injector.pushLocation(lat, lng, accuracy)
                        count++
                        consecutiveErrors = 0
                        _injectCount.value = count
                        _state.value = InjectionState(isRunning = true, lat = lat, lng = lng, count = count)
                        if (count % 2 == 0L) {
                            notifier.update(lat, lng, count, true)
                        }
                        if (count == 1L || count % 20 == 0L) {
                            com.locctl.app.core.AppLogger.d(TAG, "已连续高频注入 $count 次最新定位时间戳")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        consecutiveErrors++
                        com.locctl.app.core.AppLogger.w(TAG, "单次定位推送异常 (连续 $consecutiveErrors 次): ${e.message}")
                        if (consecutiveErrors >= 5) {
                            throw e
                        }
                    }
                    delay(intervalMs)
                }
            } catch (e: CancellationException) {
                com.locctl.app.core.AppLogger.d(TAG, "注入循环已正常终止")
            } catch (e: Throwable) {
                com.locctl.app.core.AppLogger.e(TAG, "注入循环连续异常退出: ${e.message}", e)
                _isRunning.value = false
                _lastError.value = e.message
                _state.value = InjectionState(isRunning = false, lat = lat, lng = lng, error = e.message)
                notifier.update(lat, lng, 0, false, error = e.message)
            } finally {
                runCatching { injector.restore() }
            }
        }
    }

    private fun stopInjection() {
        com.locctl.app.core.AppLogger.i(TAG, "收到停止注入指令，正在清理测试源...")
        val job = injectionJob
        injectionJob = null

        serviceScope.launch {
            if (job != null) {
                job.cancelAndJoin()
            } else {
                runCatching { injector.restore() }
            }
            withContext(Dispatchers.Main) {
                _isRunning.value = false
                _state.value = InjectionState(isRunning = false)
                releaseWakeLock()
                ServiceCompat.stopForeground(this@InjectionForegroundService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                notifier.cancel()
                com.locctl.app.core.AppLogger.i(TAG, "注入前台服务已退出，已释放 WakeLock")
                stopSelf()
            }
        }
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
        injectionJob?.cancel()
        serviceScope.cancel()
        releaseWakeLock()
        notifier.cancel()
        _isRunning.value = false
        _state.value = InjectionState(isRunning = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "InjectionService"

        const val ACTION_START = "com.locctl.app.action.START"
        const val ACTION_STOP = "com.locctl.app.action.STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_ACCURACY = "accuracy"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _injectCount = MutableStateFlow(0L)
        val injectCount: StateFlow<Long> = _injectCount.asStateFlow()

        private val _targetLat = MutableStateFlow(0.0)
        val targetLat: StateFlow<Double> = _targetLat.asStateFlow()

        private val _targetLng = MutableStateFlow(0.0)
        val targetLng: StateFlow<Double> = _targetLng.asStateFlow()

        private val _lastError = MutableStateFlow<String?>(null)
        val lastError: StateFlow<String?> = _lastError.asStateFlow()

        private val _state = MutableStateFlow(InjectionState())
        val state: StateFlow<InjectionState> = _state.asStateFlow()

        fun start(context: Context, lat: Double, lng: Double, intervalMs: Long = 500L, accuracy: Float = 3f) {
            val intent = Intent(context, InjectionForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LAT, lat)
                putExtra(EXTRA_LNG, lng)
                putExtra(EXTRA_INTERVAL, intervalMs)
                putExtra(EXTRA_ACCURACY, accuracy)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, InjectionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
