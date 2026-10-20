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
        adbManager.execute("appops set com.android.shell android:mock_location allow")
        AppLogger.i(TAG, "com.android.shell 模拟位置权限已生效（免选核心）")
    }

    /**
     * 接管系统的 gps、network、fused 三源
     * 并动态整合用户在关于页面开启的各项防泄漏封堵指令
     */
    suspend fun installProviders() = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "正在注册并接管系统的 gps, network, fused 定位源...")
        val context = com.locctl.app.LocctlApp.get()
        val extraCmds = AntiLeakManager.getActiveInstallCommands(context)

        val cmd = buildString {
            append("appops set com.android.shell android:mock_location allow; ")
            append("cmd location providers remove-test-provider gps 2>/dev/null; ")
            append("cmd location providers add-test-provider gps --requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing --powerRequirement 3; ")
            append("cmd location providers set-test-provider-enabled gps true; ")
            append("cmd location providers remove-test-provider network 2>/dev/null; ")
            append("cmd location providers add-test-provider network --requiresNetwork --requiresCell --supportsAltitude --supportsSpeed --supportsBearing; ")
            append("cmd location providers set-test-provider-enabled network true; ")
            append("cmd location providers remove-test-provider fused 2>/dev/null; ")
            append("cmd location providers add-test-provider fused; ")
            append("cmd location providers set-test-provider-enabled fused true")
            if (extraCmds.isNotEmpty()) {
                append("; ")
                append(extraCmds.joinToString("; "))
            }
        }
        adbManager.execute(cmd)
        AppLogger.i(TAG, "已成功接管系统的定位源，并动态生效了 ${extraCmds.size} 项防泄漏底层规则")
    }

    /**
     * 实时推送坐标（单条合并指令执行，刷新时间戳，规避微信 elapsedRealtime 过期丢弃）
     */
    suspend fun pushLocation(wgsLat: Double, wgsLng: Double, accuracy: Float = 3f) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val loc = "--location $wgsLat,$wgsLng --accuracy $accuracy --time $now"
        val context = com.locctl.app.LocctlApp.get()
        val isPassiveEnabled = AntiLeakManager.isEnabled(context, "passive_provider")

        val cmd = buildString {
            append("cmd location providers set-test-provider-location gps $loc; ")
            append("cmd location providers set-test-provider-location network $loc; ")
            append("cmd location providers set-test-provider-location fused $loc")
            if (isPassiveEnabled) {
                append("; cmd location providers set-test-provider-location passive $loc")
            }
        }
        adbManager.execute(cmd)
    }

    /**
     * 恢复真实定位（单条合并指令执行）
     */
    suspend fun restore() = withContext(Dispatchers.IO) {
        AppLogger.i(TAG, "正在移除系统测试提供商，还原真实定位...")
        val cmd = "cmd location providers remove-test-provider gps 2>/dev/null; " +
                  "cmd location providers remove-test-provider network 2>/dev/null; " +
                  "cmd location providers remove-test-provider fused 2>/dev/null; " +
                  "cmd location providers remove-test-provider passive 2>/dev/null"
        runCatching { adbManager.execute(cmd) }
        AppLogger.i(TAG, "真实定位已完全恢复")
    }
}
