package com.locctl.app

import android.app.Application
import com.locctl.app.core.ChannelProbe
import com.locctl.app.core.MockController
import com.locctl.app.core.ProviderFlags
import com.locctl.app.core.SdkMockLocationChannel
import com.locctl.app.node.NodeBridge
import com.locctl.app.node.NodeRuntime
import com.locctl.app.shizuku.ShizukuShellChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用级单例容器。
 *
 * 手写依赖注入，不引 Hilt —— 这个 App 只有 4 个核心对象，
 * 引框架的成本大于收益。
 */
class LocctlApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var nodeRuntime: NodeRuntime
        private set

    lateinit var nodeBridge: NodeBridge
        private set

    lateinit var controller: MockController
        private set

    lateinit var probe: ChannelProbe
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        nodeRuntime = NodeRuntime(this)
        nodeBridge = NodeBridge(nodeRuntime, appScope)

        val sdk = SdkMockLocationChannel(this)
        // Shizuku 是可选增强：探测不到就静默走 SDK
        val shizuku = runCatching { ShizukuShellChannel(this) }.getOrNull()
        probe = ChannelProbe(sdk, shizuku)

        controller = MockController(
            context = this,
            probe = probe,
            providersProvider = { ProviderFlags.DEFAULT },
            accuracyProvider = { 5f },
        )

        // node 运行时体积约 93MB，首启时铺设。放后台线程，不阻塞冷启动。
        appScope.launch {
            val ok = withContext(Dispatchers.IO) { nodeRuntime.ensureInstalled() }
            if (ok) nodeBridge.start()
        }
    }

    companion object {
        @Volatile
        var instance: LocctlApp? = null
            private set

        fun get(): LocctlApp =
            instance ?: error("LocctlApp not initialized")
    }
}
