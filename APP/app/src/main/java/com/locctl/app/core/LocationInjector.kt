package com.locctl.app.core

import com.locctl.app.adb.AdbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LocationInjector"

class LocationInjector(private val adbManager: AdbManager = AdbManager) {

    private val providers = listOf("gps", "network", "fused")

    /**
     * 免选模拟位置应用的核心：
     * 直接给 com.android.shell 赋予 android:mock_location
     */
    suspend fun setupPermissions() = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "设置 Shell 权限: appops set com.android.shell android:mock_location allow")
        adbManager.execute("appops set com.android.shell android:mock_location allow")
        AppLogger.i(TAG, "com.android.shell 模拟位置权限已生效（免选核心）")
    }

    /**
     * 接管系统的 gps、network、fused 三源
     */
    suspend fun installProviders() = withContext(Dispatchers.IO) {
        setupPermissions()
        for (p in providers) {
            runCatching {
                adbManager.execute("cmd location providers remove-test-provider $p")
            }
            val flags = when (p) {
                "gps" -> "--requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing --powerRequirement 3"
                "network" -> "--requiresNetwork --requiresCell --supportsAltitude --supportsSpeed --supportsBearing"
                else -> ""
            }
            adbManager.execute("cmd location providers add-test-provider $p $flags")
            adbManager.execute("cmd location providers set-test-provider-enabled $p true")
        }
        AppLogger.i(TAG, "已成功接管系统的 gps, network, fused 三大定位源")
    }

    /**
     * 实时推送坐标（刷新时间戳，规避微信 elapsedRealtime 过期丢弃）
     */
    suspend fun pushLocation(wgsLat: Double, wgsLng: Double, accuracy: Float = 3f) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val loc = "--location $wgsLat,$wgsLng --accuracy $accuracy --time $now"
        for (p in providers) {
            adbManager.execute("cmd location providers set-test-provider-location $p $loc")
        }
    }

    /**
     * 恢复真实定位
     */
    suspend fun restore() = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "正在移除系统测试提供商，还原真实定位...")
        for (p in providers) {
            runCatching {
                adbManager.execute("cmd location providers remove-test-provider $p")
            }
        }
        AppLogger.i(TAG, "真实定位已完全恢复")
    }
}
