package com.locctl.app.core

import android.location.Location

/**
 * 注入通道抽象。
 *
 * 两条实现：
 *  - [SdkMockLocationChannel]  走 LocationManager API，用户把本 App 选为模拟位置应用即可，
 *                              完全不需要电脑，是「完全单机」的唯一保证
 *  - [ShizukuShellChannel]     走 `cmd location` 命令通道，需要 shell UID
 *
 * 接口刻意保持与 Node 版 `src/mock.js` 的能力对齐，方便对照行为。
 */
interface InjectionChannel {
    val id: String
    val label: String

    /** 该通道当前是否可用。探测必须廉价，会被 UI 频繁调用。 */
    suspend fun isAvailable(): Boolean

    /** 不可用时的原因，用于 UI 展示 */
    suspend fun unavailableReason(): String?

    /** 接管指定 provider。重复调用必须幂等。 */
    suspend fun install(providers: List<String>)

    /** 交还 provider，恢复真实定位 */
    suspend fun uninstall(providers: List<String>)

    /** 注入一次位置 */
    suspend fun pushOnce(point: GeoPoint, accuracyMeters: Float, timeMillis: Long)

    /** 读取当前 mock 状态 */
    suspend fun status(): MockStatus

    /** 打开系统定位总开关 */
    suspend fun enableLocation()

    /**
     * SDK 通道额外能力：bearing/speed/altitude。
     * shell 的 `cmd location` 只开放 4 个参数，无法设置这三项。
     * 默认 false，只有 SDK 通道返回 true。
     */
    val supportsMotionFields: Boolean get() = false
}

/**
 * provider 能力声明，等价于 Node 版 `src/mock.js` 的 PROVIDER_FLAGS。
 * 三源必须同时接管：国内应用多走 network/fused，且 HyperOS 的 metoknlp 会回填 network。
 */
object ProviderFlags {
    const val GPS = "gps"
    const val NETWORK = "network"
    const val FUSED = "fused"
    const val PASSIVE = "passive"

    val DEFAULT = listOf(GPS, NETWORK, FUSED)
    val ALL = listOf(GPS, NETWORK, FUSED, PASSIVE)

    fun isValid(name: String): Boolean = name in ALL
}

/** 构造一个带完整运动字段的 Location，供 SDK 通道使用 */
fun buildLocation(
    provider: String,
    point: GeoPoint,
    accuracyMeters: Float,
    timeMillis: Long,
    bearingDegrees: Float? = null,
    speedMetersPerSecond: Float? = null,
    altitudeMeters: Double? = null,
): Location = Location(provider).apply {
    latitude = point.lat
    longitude = point.lng
    accuracy = accuracyMeters
    time = timeMillis
    // elapsedRealtimeNanos 必须设成当前值，否则应用会判定定位过期并丢弃。
    // 这是 Node 版「一次性注入对微信无效」的根因。
    elapsedRealtimeNanos = android.os.SystemClock.elapsedRealtimeNanos()
    bearingDegrees?.let { bearing = it }
    speedMetersPerSecond?.let { speed = it }
    altitudeMeters?.let { altitude = it }
}
