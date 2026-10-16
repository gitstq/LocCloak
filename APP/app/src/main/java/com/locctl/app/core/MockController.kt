package com.locctl.app.core

import android.content.Context
import android.util.Log

/**
 * 对上层暴露的统一注入门面。
 *
 * 持有当前生效通道，负责 install / pushOnce / uninstall / status / doctor，
 * 并在通道不可用时抛出带可执行提示的异常。
 *
 * 不做高频循环 —— 那是 [PlaybackEngine] 的职责，它跑在前台服务里。
 */
class MockController(
    private val context: Context,
    private val probe: ChannelProbe,
    private val providersProvider: () -> List<String>,
    private val accuracyProvider: () -> Float,
) {
    @Volatile
    private var current: InjectionChannel? = null

    /** 当前生效通道，未解析时为 null */
    fun activeChannel(): InjectionChannel? = current

    /** 重新探测并绑定通道。UI 每次进入前台时应调用。 */
    suspend fun bind(preferredId: String? = null): ChannelProbe.Resolution {
        val r = probe.resolve(preferredId)
        current = r.channel
        return r
    }

    private fun channelOrThrow(): InjectionChannel =
        current ?: throw MockLocationException("注入通道尚未初始化")

    suspend fun install() {
        val ch = channelOrThrow()
        if (!ch.isAvailable()) {
            throw MockLocationException(ch.unavailableReason() ?: "注入通道不可用")
        }
        ch.install(providersProvider())
    }

    suspend fun uninstall() {
        current?.uninstall(providersProvider())
    }

    /** 注入一次位置。返回实际使用的通道 id，便于 UI 展示与排错。 */
    suspend fun pushOnce(point: GeoPoint): InjectResult {
        val ch = channelOrThrow()
        val providers = providersProvider()
        val accuracy = accuracyProvider()
        val t0 = System.nanoTime()
        ch.pushOnce(point, accuracy, System.currentTimeMillis())
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000
        Log.d(TAG, "pushOnce via ${ch.id} took ${elapsedMs}ms")
        return InjectResult(ch.id, providers, point, accuracy, elapsedMs)
    }

    suspend fun status(): MockStatus = current?.status() ?: MockStatus.EMPTY

    /** 自检。每一项失败都带 action，UI 直接展示给用户。 */
    suspend fun doctor(): DoctorReport {
        val r = probe.resolve(null)
        current = r.channel
        val checks = mutableListOf<DoctorCheck>()

        val st = runCatching { r.channel.status() }.getOrElse { MockStatus.EMPTY }
        checks += DoctorCheck(
            id = "location",
            title = "系统定位总开关",
            passed = st.locationEnabled,
            detail = if (st.locationEnabled) "已开启" else "已关闭",
            action = if (st.locationEnabled) null else "点击打开定位总开关",
        )

        val chAvailable = r.channel.isAvailable()
        checks += DoctorCheck(
            id = "channel",
            title = "注入通道",
            passed = chAvailable,
            detail = if (chAvailable) r.channel.label else (r.reason ?: "不可用"),
            action = if (chAvailable) null else "去授权",
        )

        // 厂商网络定位干扰项：HyperOS 的 metoknlp 会持续回填 network 源。
        // 这不阻塞使用，但用户遇到「位置偶发跳回」时应能在这里看到提示。
        val vendorNlp = runCatching {
            context.packageManager.getLaunchIntentForPackage("com.xiaomi.metoknlp") != null ||
                context.packageManager.getPackageInfo("com.xiaomi.metoknlp", 0) != null
        }.getOrDefault(false)
        checks += DoctorCheck(
            id = "vendor_nlp",
            title = "厂商网络定位",
            passed = !vendorNlp,
            detail = if (vendorNlp) {
                "检测到 com.xiaomi.metoknlp，可能回填 network 定位"
            } else {
                "未检测到"
            },
            action = if (vendorNlp) "三源已同时接管，通常无需处理" else null,
        )

        return DoctorReport(checks)
    }

    companion object {
        private const val TAG = "MockController"
    }
}
