package com.locctl.app.node

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * stdio JSON-RPC 桥。
 *
 * 为什么走 stdio 而不是 HTTP：
 *   - 不需要 INTERNET 权限，也不需要开端口
 *   - 无网络栈攻击面，对工具类应用更合适
 *   - Node 进程随 Service 生死，不会残留监听
 *
 * 协议（每行一个 JSON 对象）：
 *   请求  {"id":1,"method":"coords.convert","params":{...}}
 *   响应  {"id":1,"ok":true,"result":{...}}  或  {"id":1,"ok":false,"error":"..."}
 *
 * 高频路径（推送循环）**不经过这里** —— 那由 Kotlin 侧的 PlaybackEngine 直接调 Binder。
 * 本桥只承载低频计算：坐标换算、GPX 解析、地点检索。
 */
class NodeBridge(
    private val runtime: NodeRuntime,
    private val scope: CoroutineScope,
) {
    private val seq = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, kotlinx.coroutines.CompletableDeferred<JSONObject>>()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var readerJob: kotlinx.coroutines.Job? = null

    @Volatile
    var isAlive: Boolean = false
        private set

    /** node 版本，未启动时为 null */
    @Volatile
    var nodeVersion: String? = null
        private set

    /**
     * 启动 node 进程并等待它就绪。
     * @param scriptAssetPath assets 中的入口脚本路径，如 "js/bridge.js"
     */
    suspend fun start(scriptAssetPath: String = BRIDGE_SCRIPT): Boolean = withContext(Dispatchers.IO) {
        if (isAlive) return@withContext true
        try {
            // bridge.js 与 coords/gpx/presets 一起从 assets 解到 filesDir。
            // assets 里的文件不能直接 require（不可写、路径也不是真实文件），
            // 所以整套 js 目录都要落盘。
            val scriptFile = extractScript(scriptAssetPath)
            val p = runtime.spawn(scriptFile.absolutePath)
            process = p
            writer = p.outputStream.bufferedWriter()
            isAlive = true

            readerJob = scope.launch(Dispatchers.IO) { readLoop(p) }

            // 握手：拿 node 版本，同时确认进程真的活着
            val r = call("system.version", JSONObject(), timeoutMs = 8000)
            nodeVersion = r.optString("version", "未知")
            Log.i(TAG, "node bridge ready, version=$nodeVersion")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "node bridge start failed", e)
            stop()
            false
        }
    }

    /** 把 assets 里的 js 目录整体解到 filesDir/js */
    private fun extractScript(assetPath: String): File {
        val ctx = runtime.appContext
        val outDir = File(ctx.filesDir, "js").apply { mkdirs() }
        val rel = assetPath.removePrefix("js/")
        val out = File(outDir, rel)
        if (out.exists()) return out
        ctx.assets.open(assetPath).use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    private suspend fun readLoop(p: Process) {
        try {
            val br: BufferedReader = p.inputStream.bufferedReader()
            while (isAlive) {
                val line = br.readLine() ?: break
                if (line.isBlank()) continue
                runCatching {
                    val obj = JSONObject(line)
                    val id = obj.optLong("id", -1L)
                    pending.remove(id)?.complete(obj)
                }.onFailure { Log.w(TAG, "bad json line: $line", it) }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "read loop ended", e)
        } finally {
            isAlive = false
            // 把还在等的请求全部失败掉，避免 UI 永久转圈
            pending.values.forEach { it.completeExceptionally(IllegalStateException("node 进程已退出")) }
            pending.clear()
        }
    }

    /** 调用一个方法。超时或进程死亡都会抛异常。 */
    suspend fun call(
        method: String,
        params: JSONObject = JSONObject(),
        timeoutMs: Long = 15_000,
    ): JSONObject = withContext(Dispatchers.IO) {
        check(isAlive) { "node 桥未启动" }
        val id = seq.incrementAndGet()
        val deferred = kotlinx.coroutines.CompletableDeferred<JSONObject>()
        pending[id] = deferred

        val req = JSONObject()
            .put("id", id)
            .put("method", method)
            .put("params", params)

        try {
            writer?.apply {
                write(req.toString())
                newLine()
                flush()
            } ?: throw IllegalStateException("writer 已关闭")

            val resp = kotlinx.coroutines.withTimeout(timeoutMs) { deferred.await() }
            if (!resp.optBoolean("ok", false)) {
                throw NodeBridgeException(resp.optString("error", "未知错误"))
            }
            resp.optJSONObject("result") ?: JSONObject()
        } catch (e: Throwable) {
            pending.remove(id)
            throw e
        }
    }

    fun stop() {
        isAlive = false
        readerJob?.cancel()
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        process = null
        writer = null
    }

    companion object {
        private const val TAG = "NodeBridge"
        const val BRIDGE_SCRIPT = "js/bridge.js"
    }
}

class NodeBridgeException(message: String) : Exception(message)

/** JSONArray 的便捷构造 */
fun jsonArrayOf(vararg items: Any?): JSONArray = JSONArray().apply {
    items.forEach { put(it) }
}
