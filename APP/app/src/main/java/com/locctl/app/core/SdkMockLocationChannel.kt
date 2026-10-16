package com.locctl.app.core

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * SDK 模拟定位通道。
 *
 * 原理：把系统的定位提供者替换为 test provider，再周期性向其写入位置。
 * 与 Node 版 `src/mock.js` 策略一致，但走 LocationManager API 而非 `cmd location`，
 * 因此不需要 shell UID —— 用户只需在开发者选项里把本 App 选为「模拟位置应用」。
 *
 * 相比 shell 通道的额外收益：
 *  1. 能设置 bearing / speed / altitude（shell 只开放 4 个参数）
 *  2. elapsedRealtimeNanos 由本进程时钟源给出，无跨设备时钟偏差
 *  3. 单次调用是直接 Binder，约 1ms，比 shell 命令快一个量级
 */
class SdkMockLocationChannel(private val context: Context) : InjectionChannel {

    override val id: String = "sdk"
    override val label: String = "SDK 模拟定位"
    override val supportsMotionFields: Boolean = true

    private val lm: LocationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /** 已由本通道接管的 provider，用于幂等与还原 */
    private val installed = mutableSetOf<String>()

    override suspend fun isAvailable(): Boolean {
        if (!isMockLocationApp()) return false
        return isLocationEnabledInternal()
    }

    override suspend fun unavailableReason(): String? = when {
        !isMockLocationApp() ->
            "未在开发者选项中把本 App 选为「模拟位置应用」。\n" +
                "路径：设置 → 系统设置 → 开发者选项 → 选择模拟位置信息应用"
        !isLocationEnabledInternal() ->
            "系统定位总开关已关闭。"
        else -> null
    }

    /**
     * 是否被选为模拟位置应用。
     *
     * 用 AppOpsManager 查询 android:mock_location 的 appop 状态，
     * 对应 Node 版 doctor 里的 `appops get com.android.shell android:mock_location`。
     */
    private fun isMockLocationApp(): Boolean {
        return try {
            val aom = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            aom.checkOpNoThrow(
                AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(),
                context.packageName,
            ) == AppOpsManager.MODE_ALLOWED
        } catch (e: Throwable) {
            Log.w(TAG, "checkOpNoThrow failed", e)
            false
        }
    }

    private fun isLocationEnabledInternal(): Boolean = try {
        lm.isLocationEnabled
    } catch (e: Throwable) {
        Log.w(TAG, "isLocationEnabled failed", e)
        false
    }

    override suspend fun install(providers: List<String>) {
        if (!isMockLocationApp()) {
            throw MockLocationException(
                "没有模拟定位权限。请在开发者选项里把本 App 选为「模拟位置应用」",
            )
        }
        if (!isLocationEnabledInternal()) {
            throw MockLocationException("系统定位总开关已关闭，请先打开")
        }

        for (name in providers) {
            require(ProviderFlags.isValid(name)) { "不支持的 provider: $name" }

            // 已接管则先移除再重建，避免残留旧的 ProviderProperties。
            // 与 Node 版 install() 的 remove-then-add 策略一致。
            runCatching { lm.removeTestProvider(name) }
            installed.remove(name)

            try {
                addTestProviderCompat(name)
                lm.setTestProviderEnabled(name, true)
                installed.add(name)
                Log.i(TAG, "installed mock provider: $name")
            } catch (e: Throwable) {
                throw MockLocationException("接管 provider $name 失败：${e.message}", e)
            }
        }
    }

    override suspend fun uninstall(providers: List<String>) {
        for (name in providers) {
            try {
                lm.removeTestProvider(name)
                installed.remove(name)
                Log.i(TAG, "removed mock provider: $name")
            } catch (e: Throwable) {
                // provider 本就不在 mock 状态时 remove 会抛异常，忽略即可
                Log.i(TAG, "provider $name not in mock state, skipped")
            }
        }
    }

    override suspend fun pushOnce(point: GeoPoint, accuracyMeters: Float, timeMillis: Long) {
        if (installed.isEmpty()) {
            throw MockLocationException("尚未接管任何 provider，请先 install")
        }
        for (name in installed) {
            // 每个 provider 需要独立的 Location 实例，因为 provider 字段不同
            lm.setTestProviderLocation(
                name,
                buildLocation(name, point, accuracyMeters, timeMillis),
            )
        }
        Log.v(TAG, "pushed $point acc=$accuracyMeters to $installed")
    }

    override suspend fun status(): MockStatus {
        val mocked = mutableMapOf<String, Boolean>()
        val last = mutableMapOf<String, GeoPoint>()

        for (name in ProviderFlags.ALL) {
            val exists = runCatching { lm.getProvider(name) != null }.getOrDefault(false)
            if (!exists) continue

            // 判断是否处于 mock 状态：取该 provider 最后一次定位，
            // 若 isFromMockProvider 为 true 则说明是 test provider 发出的。
            // 比反射读 LocationProvider.mocked 稳，后者不是公开 API。
            val loc = runCatching { lm.getLastKnownLocation(name) }.getOrNull()
            val isMock = loc?.isFromMockProvider == true
            mocked[name] = isMock
            if (isMock && loc != null) {
                last[name] = loc.toGeoPoint()
            }
        }
        return MockStatus(mocked, last, isLocationEnabledInternal())
    }

    override suspend fun enableLocation() {
        // LocationManager 没有公开的开关 API，跳系统设置让用户开
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * minSdk 31，直接走 ProviderProperties。
     * API 36 已移除 Criteria.POWER_REQUIREMENT_*，废弃重载无法编译。
     */
    private fun addTestProviderCompat(name: String) {
        val (net, sat, cell) = when (name) {
            ProviderFlags.GPS -> Triple(false, true, false)
            ProviderFlags.NETWORK -> Triple(true, false, true)
            else -> Triple(false, false, false)
        }
        lm.addTestProvider(name, propertiesFor(name, net, sat, cell))
    }

    /**
     * provider 能力声明，等价于 Node 版 PROVIDER_FLAGS。
     * 三源必须同时接管：国内应用多走 network/fused，且 HyperOS 的 metoknlp 会回填 network。
     */
    private fun propertiesFor(
        name: String,
        requiresNetwork: Boolean,
        requiresSatellite: Boolean,
        requiresCell: Boolean,
    ): ProviderProperties = ProviderProperties.Builder()
        .setHasNetworkRequirement(requiresNetwork)
        .setHasSatelliteRequirement(requiresSatellite)
        .setHasCellRequirement(requiresCell)
        .setHasAltitudeSupport(true)
        .setHasSpeedSupport(true)
        .setHasBearingSupport(true)
        .setPowerUsage(
            if (name == ProviderFlags.GPS) {
                ProviderProperties.POWER_USAGE_HIGH
            } else {
                ProviderProperties.POWER_USAGE_LOW
            },
        )
        .setAccuracy(
            if (name == ProviderFlags.NETWORK) {
                ProviderProperties.ACCURACY_COARSE
            } else {
                ProviderProperties.ACCURACY_FINE
            },
        )
        .build()

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude, null)

    companion object {
        private const val TAG = "SdkMockChannel"
    }
}

class MockLocationException(message: String, cause: Throwable? = null) : Exception(message, cause)
