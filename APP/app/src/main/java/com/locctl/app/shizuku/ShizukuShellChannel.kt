package com.locctl.app.shizuku

import android.content.Context
import android.util.Log
import java.lang.reflect.Method
import com.locctl.app.core.GeoPoint
import com.locctl.app.core.InjectionChannel
import com.locctl.app.core.MockLocationException
import com.locctl.app.core.MockStatus
import com.locctl.app.core.ProviderFlags
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter

/**
 * Shizuku / shell UID 注入通道（可选增强）。
 *
 * Spike 2 结论：无 root 下这条通道基本不可用 —— 无线调试无法从 shell 开启，
 * 配对机制设计上就需要两个设备。所以它**不是主路径**，
 * 只是给 root 用户、或愿意用电脑配对一次的用户多一个选择。
 *
 * 实现上完全复刻 Node 版 `src/mock.js` 的行为：
 *   - 三源同时接管（gps / network / fused）
 *   - `cmd location` 成功时**不输出任何内容**，必须追加 `; echo rc=$?` 拿返回码
 *   - 常驻 shell 会话复用，避免每 tick 起进程
 */
class ShizukuShellChannel(private val context: Context) : InjectionChannel {

    override val id: String = "shizuku"
    override val label: String = "Shizuku / shell"

    private var session: ShellSession? = null
    private val installed = mutableSetOf<String>()

    override suspend fun isAvailable(): Boolean = runCatching {
        Shizuku.pingBinder() &&
            Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            newProcessReflector != null
    }.getOrDefault(false)

    override suspend fun unavailableReason(): String? = when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) ->
            "Shizuku 服务未运行。需要先在 Shizuku App 里启动服务（无 root 时需无线调试配对）。"
        Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED ->
            "未获得 Shizuku 授权，请在 Shizuku App 里允许本应用。"
        newProcessReflector == null ->
            "当前 Shizuku 版本不开放进程创建接口（api 13.1.5 起 newProcess 为 private）。"
        else -> null
    }

    /**
     * Shizuku 13.1.5 起 `newProcess` 是 private，公开 API 里没有创建进程的入口。
     * 社区通行做法是反射调用。反射不到就认为通道不可用，静默回退 SDK ——
     * 这正是 Spike 2 结论所说的：Shizuku 只是可选增强，不是主路径。
     */
    private val newProcessReflector: Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun newProcess(cmd: Array<String>): Process? =
        runCatching { newProcessReflector?.invoke(null, cmd, null, null) as? Process }.getOrNull()

    private fun shell(): ShellSession {
        if (session == null || session?.isClosed() == true) {
            val p = newProcess(Array(0) { "sh" })
                ?: throw MockLocationException("无法通过 Shizuku 创建 shell 进程")
            session = ShellSession(p)
        }
        return session!!
    }

    override suspend fun install(providers: List<String>) {
        if (!isAvailable()) throw MockLocationException(unavailableReason() ?: "Shizuku 不可用")
        val sh = shell()

        for (name in providers) {
            require(ProviderFlags.isValid(name)) { "不支持的 provider: $name" }
            // 先移除再重建，避免残留旧的 ProviderProperties（与 Node 版一致）
            runCatching { sh.sendChecked("cmd location providers remove-test-provider $name") }

            val flags = when (name) {
                ProviderFlags.GPS ->
                    "--requiresSatellite --supportsAltitude --supportsSpeed --supportsBearing --powerRequirement 3"
                ProviderFlags.NETWORK ->
                    "--requiresNetwork --requiresCell --supportsAltitude --supportsSpeed --supportsBearing"
                else -> ""
            }
            val addCmd = buildString {
                append("cmd location providers add-test-provider $name")
                if (flags.isNotEmpty()) append(" $flags")
            }
            sh.sendChecked(addCmd)
            sh.sendChecked("cmd location providers set-test-provider-enabled $name true")
            installed.add(name)
            Log.i(TAG, "installed mock provider via shell: $name")
        }
    }

    override suspend fun uninstall(providers: List<String>) {
        val sh = session ?: return
        for (name in providers) {
            runCatching { sh.sendChecked("cmd location providers remove-test-provider $name") }
            installed.remove(name)
        }
    }

    override suspend fun pushOnce(point: GeoPoint, accuracyMeters: Float, timeMillis: Long) {
        val sh = session ?: throw MockLocationException("shell 会话未建立")
        if (installed.isEmpty()) throw MockLocationException("尚未接管任何 provider")

        val loc = "--location ${point.lat},${point.lng} --accuracy $accuracyMeters --time $timeMillis"
        for (name in installed) {
            sh.sendChecked("cmd location providers set-test-provider-location $name $loc")
        }
    }

    override suspend fun status(): MockStatus {
        val sh = session ?: return MockStatus.EMPTY
        // dumpsys location 在 Windows 上返回 CRLF，这里统一归一化换行后再解析
        val raw = sh.send("dumpsys location")
        val text = raw.replace("\r\n", "\n")

        val mocked = mutableMapOf<String, Boolean>()
        val last = mutableMapOf<String, GeoPoint>()

        for (name in ProviderFlags.ALL) {
            val i = text.indexOf("$name provider")
            if (i < 0) continue
            mocked[name] = text.substring(i, i + 40).contains("[mock]")
            if (mocked[name] == true) {
                val chunk = text.substring(i, minOf(i + 4000, text.length))
                val m = Regex("last(?: mock)? location=Location\\[$name ([-\\d.]+),([-\\d.]+)")
                    .find(chunk)
                if (m != null) {
                    last[name] = GeoPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
                }
            }
        }
        return MockStatus(mocked, last, isLocationEnabled())
    }

    private suspend fun isLocationEnabled(): Boolean = runCatching {
        session?.send("cmd location is-location-enabled")?.trim() == "true"
    }.getOrDefault(false)

    override suspend fun enableLocation() {
        runCatching { shell().sendChecked("cmd location set-location-enabled true") }
    }

    fun release() {
        session?.close()
        session = null
    }

    companion object {
        private const val TAG = "ShizukuChannel"
    }
}

/**
 * 常驻 shell 会话。
 *
 * `sendChecked` 是 Node 版 `openShell().sendChecked()` 的 Kotlin 等价物：
 * `cmd location` 成功时不打印任何输出，所以必须追加 `; echo rc=$?`，
 * 把返回码当作唯一确定的响应行。
 */
class ShellSession(private val process: Process) {
    private val writer: BufferedWriter = process.outputStream.bufferedWriter()
    private val reader: BufferedReader = process.inputStream.bufferedReader()

    fun isClosed(): Boolean = runCatching { process.exitValue(); true }.getOrDefault(false)

    /** 发送命令并等待单行输出。注意：命令本身必须保证有输出。 */
    fun send(cmd: String, timeoutMs: Long = 15_000): String {
        writer.write(cmd)
        writer.newLine()
        writer.flush()
        return readLineWithTimeout(timeoutMs) ?: throw MockLocationException("命令超时：$cmd")
    }

    /** 发送命令并要求返回码为 0 */
    fun sendChecked(cmd: String, timeoutMs: Long = 15_000) {
        val line = send("$cmd; echo rc=$?", timeoutMs)
        val m = Regex("rc=(\\d+)").find(line)
        if (m == null || m.groupValues[1] != "0") {
            throw MockLocationException("命令失败（${m?.groupValues?.get(1) ?: "无返回码"}）：$cmd")
        }
    }

    /**
     * 用读线程 + 超时实现 readLine 超时。
     * BufferedReader.readLine() 本身不可中断，必须绕。
     */
    private fun readLineWithTimeout(timeoutMs: Long): String? {
        val box = arrayOfNulls<String>(1)
        val err = arrayOfNulls<Throwable>(1)
        val t = Thread {
            try {
                box[0] = reader.readLine()
            } catch (e: Throwable) {
                err[0] = e
            }
        }
        t.isDaemon = true
        t.start()
        t.join(timeoutMs)
        if (t.isAlive) {
            t.interrupt()
            return null
        }
        err[0]?.let { throw it }
        return box[0]
    }

    fun close() {
        runCatching { writer.close() }
        runCatching { reader.close() }
        runCatching { process.destroy() }
    }
}
