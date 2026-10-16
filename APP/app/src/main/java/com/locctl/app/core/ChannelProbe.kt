package com.locctl.app.core

import android.content.Context
import com.locctl.app.shizuku.ShizukuShellChannel

/**
 * 通道探测与优先级决策。
 *
 * Spike 2 的结论：无 root 下 shell UID 拿不到（无线调试无法从 shell 开启，
 * 配对机制设计上就需要两个设备），所以 **SDK 通道是主路径**，
 * Shizuku 通道降级为可选增强。
 *
 * 探测顺序固定为：
 *   1. 用户偏好里显式指定的通道（若可用）
 *   2. SDK 通道（完全单机的保证）
 *   3. Shizuku 通道（root / 电脑配对过的用户）
 */
class ChannelProbe(
    private val sdkChannel: SdkMockLocationChannel,
    private val shizukuChannel: ShizukuShellChannel?,
) {
    data class Resolution(
        val channel: InjectionChannel,
        val fallbackOf: InjectionChannel?,
        val reason: String?,
    )

    /**
     * @param preferredId 用户偏好；null 表示自动
     */
    suspend fun resolve(preferredId: String? = null): Resolution {
        preferredId?.let { want ->
            val ch: InjectionChannel? = channelById(want)
            if (ch != null && ch.isAvailable()) {
                return Resolution(ch, null, null)
            }
            // 用户指定的通道不可用，明确告知并降级
        }

        if (sdkChannel.isAvailable()) {
            return Resolution(sdkChannel, null, null)
        }

        val sh: ShizukuShellChannel? = shizukuChannel
        if (sh != null && sh.isAvailable()) {
            return Resolution(sh, null, null)
        }

        // 都不可用，返回 SDK 通道并在 reason 里说清缺什么
        return Resolution(
            sdkChannel,
            sh,
            sdkChannel.unavailableReason() ?: "当前设备没有可用的注入通道",
        )
    }

    suspend fun channelById(id: String): InjectionChannel? = when (id) {
        sdkChannel.id -> sdkChannel
        shizukuChannel?.id -> shizukuChannel
        else -> null
    }

    companion object {
        fun create(context: Context, shizuku: ShizukuShellChannel? = null): ChannelProbe =
            ChannelProbe(SdkMockLocationChannel(context), shizuku)
    }
}
