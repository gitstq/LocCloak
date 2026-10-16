package com.locctl.app.core

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 播放引擎：在独立工作线程上驱动三种模式。
 *
 * 为什么必须常驻刷新（而不是一次性注入）：
 *   微信这类应用请求定位时只注册几百毫秒就退出，并且会校验 elapsedRealtime。
 *   一次性注入的坐标放几分钟后会被判定过期丢弃 —— 这是 Node 版实测到的
 *   `dumpsys location` 里微信 `locations = 0` 的根因。
 *
 * 为什么用独立线程 + 单线程串行：
 *   推送是 Binder 调用（SDK 约 1ms，shell 约 5ms），不能放在主线程；
 *   同时每 tick 只发一次、串行执行，避免息屏后线程池被挂起导致积压或重复。
 *   状态回调会再抛回主线程给 UI。
 */
class PlaybackEngine(
    private val controller: MockController,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onTick: (PlaybackState) -> Unit,
) {
    private val thread = HandlerThread("locctl-playback").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var mode: TrackMode? = null

    @Volatile
    private var running = false

    private var intervalMs = 500L
    private var accuracyMeters = 5f

    /** 轨迹播放进度 */
    private var routeIndex = 0
    private var routeSamples: List<GeoPoint> = emptyList()

    /** 统计 */
    private var pushCount = 0L
    private var errorCount = 0
    private var lastError: String? = null
    private var startPoint: GeoPoint? = null
    private var lastWanderPoint: GeoPoint? = null
    private var currentLabel = "未注入"

    val isRunning: Boolean get() = running

    sealed interface TrackMode {
        data class Hold(val point: GeoPoint) : TrackMode
        data class Route(val samples: List<GeoPoint>) : TrackMode
        data class Wander(val center: GeoPoint, val radiusMeters: Double) : TrackMode
    }

    data class PlaybackState(
        val running: Boolean,
        val modeLabel: String,
        val current: GeoPoint?,
        val pushCount: Long,
        val routeProgress: Float,
        val travelledMeters: Double,
        val lastError: String?,
    ) {
        companion object {
            val IDLE = PlaybackState(false, "未注入", null, 0, 0f, 0.0, null)
        }
    }

    /**
     * 启动 hold 模式：固定坐标，只刷新时间戳。
     */
    fun startHold(point: GeoPoint, intervalMs: Long, accuracyMeters: Float) {
        stopInternal()
        this.mode = TrackMode.Hold(point)
        this.intervalMs = intervalMs
        this.accuracyMeters = accuracyMeters
        this.startPoint = point
        startInternal("常驻保持")
    }

    /**
     * 启动轨迹回放。
     * [samples] 是 Node 侧一次性算好的重采样结果，之后由本机原生回放，
     * 不做逐点 IPC —— 这是计划里定下的职责划分。
     */
    fun startRoute(samples: List<GeoPoint>, intervalMs: Long, accuracyMeters: Float) {
        if (samples.isEmpty()) {
            main.post { onTick(PlaybackState.IDLE.copy(lastError = "轨迹没有采样点")) }
            return
        }
        stopInternal()
        this.routeSamples = samples
        this.routeIndex = 0
        this.mode = TrackMode.Route(samples)
        this.intervalMs = intervalMs
        this.accuracyMeters = accuracyMeters
        this.startPoint = samples.first()
        startInternal("轨迹回放")
    }

    /**
     * 启动随机漫游：在 [center] 周围 [radiusMeters] 内随机跳点。
     * 半径/角度运算就在 Kotlin 里做，不值得为十几行跨进程。
     */
    fun startWander(center: GeoPoint, radiusMeters: Double, intervalMs: Long, accuracyMeters: Float) {
        stopInternal()
        this.mode = TrackMode.Wander(center, radiusMeters)
        this.intervalMs = intervalMs
        this.accuracyMeters = accuracyMeters
        this.startPoint = center
        startInternal("随机漫游")
    }

    /** 暂停（保留模式与进度，恢复时从当前位置继续） */
    fun pause() {
        running = false
        worker.removeCallbacksAndMessages(null)
        emitState()
    }

    /** 从暂停恢复 */
    fun resume() {
        if (mode == null || running) return
        running = true
        worker.post(tick)
    }

    /** 停止并回到空闲。不触碰已注入的 provider —— 那是 uninstall 的职责。 */
    fun stop() {
        stopInternal()
        emitState()
    }

    /** 释放线程。Service onDestroy 时调用。 */
    fun release() {
        stopInternal()
        thread.quitSafely()
    }

    private fun startInternal(label: String) {
        running = true
        pushCount = 0
        errorCount = 0
        lastError = null
        currentLabel = label
        worker.post(tick)
    }

    private fun stopInternal() {
        running = false
        worker.removeCallbacksAndMessages(null)
        mode = null
        routeIndex = 0
        routeSamples = emptyList()
        startPoint = null
        lastWanderPoint = null
    }

    private val tick: Runnable = Runnable {
        if (!running) return@Runnable
        val m: TrackMode? = mode ?: return@Runnable

        val target: GeoPoint = when (m) {
            is TrackMode.Hold -> m.point
            is TrackMode.Route ->
                if (routeIndex >= routeSamples.size) {
                    // 播完了，停在最后一点继续刷新时间戳
                    routeSamples.last()
                } else {
                    routeSamples[routeIndex]
                }
            is TrackMode.Wander -> randomPointAround(m.center, m.radiusMeters)
            null -> return@Runnable
        }

        push(target)

        if (m is TrackMode.Route && routeIndex < routeSamples.size) {
            routeIndex += 1
        }

        emitState()
        if (running) {
            worker.postDelayed(tick, intervalMs)
        }
    }

    /**
     * 在工作线程里同步推送。
     * SDK 通道单次约 1ms，直接 Binder 调用即可；
     * Shizuku 通道约 5ms 且要写 stdin，也还在同一线程串行，不会阻塞 UI。
     */
    private fun push(target: GeoPoint) {
        try {
            kotlinx.coroutines.runBlocking {
                controller.pushOnce(target)
            }
            pushCount += 1
            errorCount = 0
            lastError = null
        } catch (e: Throwable) {
            errorCount += 1
            lastError = e.message ?: e.javaClass.simpleName
            Log.w(TAG, "push failed (${errorCount})", e)
            // 连续失败后自动停，避免无意义耗电
            if (errorCount >= MAX_ERRORS) {
                Log.e(TAG, "too many consecutive errors, stopping")
                stopInternal()
            }
        }
    }

    private fun emitState() {
        val m = mode
        val current = when (m) {
            is TrackMode.Hold -> m.point
            is TrackMode.Route ->
                routeSamples.getOrNull(routeIndex) ?: routeSamples.lastOrNull()
            is TrackMode.Wander -> lastWanderPoint
            null -> null
        }
        val progress = if (m is TrackMode.Route && routeSamples.isNotEmpty()) {
            routeIndex.toFloat() / routeSamples.size.toFloat()
        } else {
            0f
        }
        val travelled = startPoint?.let { haversine(it, current ?: it) } ?: 0.0
        val state = PlaybackState(
            running = running,
            modeLabel = currentLabel,
            current = current,
            pushCount = pushCount,
            routeProgress = progress.coerceIn(0f, 1f),
            travelledMeters = travelled,
            lastError = lastError,
        )
        main.post { onTick(state) }
    }

    private fun randomPointAround(center: GeoPoint, radiusMeters: Double): GeoPoint {
        // WGS-84 下 1 度纬度约 111.32 km，经度随纬度收缩
        val mPerDegLat = 111_320.0
        val mPerDegLng = 111_320.0 * cos(Math.toRadians(center.lat))

        // sqrt 保证在圆盘内均匀分布，否则会偏向中心
        val r = radiusMeters * sqrt(Random.nextDouble())
        val theta = Random.nextDouble() * 2.0 * Math.PI

        val p = GeoPoint(
            lat = center.lat + (r * sin(theta)) / mPerDegLat,
            lng = center.lng + (r * cos(theta)) / mPerDegLng,
            gcj02 = null,
        )
        lastWanderPoint = p
        return p
    }

    companion object {
        private const val TAG = "PlaybackEngine"
        private const val MAX_ERRORS = 10

        fun haversine(a: GeoPoint, b: GeoPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLng = Math.toRadians(b.lng - a.lng)
            val lat1 = Math.toRadians(a.lat)
            val lat2 = Math.toRadians(b.lat)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
            return 2 * r * asin(sqrt(h))
        }
    }
}
