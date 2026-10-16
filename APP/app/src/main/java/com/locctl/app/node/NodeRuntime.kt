package com.locctl.app.node

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Node 运行时生命周期管理。
 *
 * 关键点：
 *  1. APK 的 jniLibs 不支持符号链接，而 Termux 的 node 依赖
 *     libz.so.1 -> libz.so.1.3.2 这类 SONAME 链接。所以首启时要把
 *     jniLibs 里的实体文件复制到 filesDir，再在设备上建好符号链接。
 *  2. `node` 可执行文件本身也要有执行位，复制过去后 chmod 755。
 *  3. 冷启动约 300ms（实测），所以进程必须长驻，UI 层绝不能触发重启。
 */
class NodeRuntime(internal val appContext: Context) {

    private val root: File get() = File(appContext.filesDir, "node")
    private val binFile: File get() = File(root, "bin/node")

    @Volatile
    private var installedOk: Boolean? = null

    /** node 可执行文件是否已就绪 */
    val isInstalled: Boolean
        get() = installedOk ?: synchronized(this) {
            installedOk ?: (binFile.exists() && binFile.canExecute()).also { installedOk = it }
        }

    /**
     * 从 jniLibs / assets 铺设运行时。幂等，重复调用代价极低。
     * @return 是否成功
     */
    fun ensureInstalled(): Boolean {
        if (isInstalled) return true
        return synchronized(this) {
            if (isInstalled) return true
            try {
                install()
                installedOk = true
                Log.i(TAG, "node runtime installed at ${binFile.absolutePath}")
                true
            } catch (e: Throwable) {
                Log.e(TAG, "node runtime install failed", e)
                installedOk = false
                false
            }
        }
    }

    private fun install() {
        root.mkdirs()

        // 1. 复制 node 二进制与所有 .so
        val nativeDir = File(appContext.applicationInfo.nativeLibraryDir)
        val sources = nativeDir.listFiles()?.toList()
            ?: throw IOException("nativeLibraryDir 为空: ${nativeDir.absolutePath}")

        val nodeSrc = sources.firstOrNull { it.name == "libnode.so" }
            ?: throw IOException("未找到 libnode.so，请检查 jniLibs/arm64-v8a/")
        nodeSrc.copyTo(binFile, overwrite = true)

        // 其余 .so 原样复制到 lib 子目录
        val libDir = File(root, "lib").apply { mkdirs() }
        sources.filter { it.name.endsWith(".so") && it.name != "libnode.so" }
            .forEach { so -> so.copyTo(File(libDir, so.name), overwrite = true) }

        binFile.setExecutable(true, true)

        // 2. 建符号链接：SONAME -> 实体文件
        //    Android 的 Runtime.exec 支持符号链接，这一步必须在设备上做。
        createSymlinks(libDir)
    }

    /**
     * 建立 SONAME 链接。映射关系来自 Termux 各包的 .so 版本号：
     *   libz.so.1        -> libz.so.1.3.2
     *   libsqlite3.so.0  -> libsqlite3.so.3.54.0
     *   libicui18n.so.78 -> libicui18n.so.78.3
     *   ...
     * 若链接已存在则跳过。失败不致命 —— 有的 ABI 允许直接按 SONAME 找实体文件。
     */
    private fun createSymlinks(libDir: File) {
        val links = mapOf(
            "libz.so.1" to "libz.so.1.3.2",
            "libz.so" to "libz.so.1.3.2",
            "libsqlite3.so.0" to "libsqlite3.so.3.54.0",
            "libsqlite3.so" to "libsqlite3.so.3.54.0",
            "libcares.so.2" to "libcares.so",
            "libffi.so.8" to "libffi.so",
            "libicudata.so.78" to "libicudata.so.78.3",
            "libicui18n.so.78" to "libicui18n.so.78.3",
            "libicuuc.so.78" to "libicuuc.so.78.3",
            "libicutu.so.78" to "libicutu.so.78.3",
            "libicuio.so.78" to "libicuio.so.78.3",
        )
        links.forEach { (soname, target) ->
            val link = File(libDir, soname)
            if (link.exists()) return@forEach
            runCatching {
                val rc = ProcessBuilder("ln", "-sf", target, link.absolutePath)
                    .directory(libDir)
                    .redirectErrorStream(true)
                    .start()
                    .waitFor()
                if (rc != 0) Log.w(TAG, "symlink $soname -> $target failed rc=$rc")
            }.onFailure { Log.w(TAG, "symlink $soname failed", it) }
        }
    }

    /**
     * 启动 node 进程并返回它。
     * @param scriptPath assets 里的入口脚本绝对路径
     */
    fun spawn(scriptPath: String): Process {
        check(ensureInstalled()) { "node 运行时未就绪" }
        val libDir = File(root, "lib")
        val env = arrayOf(
            "LD_LIBRARY_PATH=${libDir.absolutePath}",
            "HOME=${appContext.filesDir.absolutePath}",
            "TMPDIR=${appContext.cacheDir.absolutePath}",
            // 关掉 ICU 的本地化查找，避免 assets 不可写导致的告警
            "NODE_ICU_DATA=${File(libDir, "libicudata.so.78").absolutePath}",
        )
        val cmd = listOf(binFile.absolutePath, scriptPath)
        Log.i(TAG, "spawning: ${cmd.joinToString(" ")}")
        return ProcessBuilder(cmd)
            .directory(root)
            .redirectErrorStream(false)
            .apply { environment().putAll(env.associate { s ->
                val i = s.indexOf('=')
                s.substring(0, i) to s.substring(i + 1)
            }) }
            .start()
    }

    /** node 版本号，用于 UI 展示 */
    fun version(): String {
        if (!ensureInstalled()) return "未安装"
        return runCatching {
            val p = ProcessBuilder(binFile.absolutePath, "--version")
                .directory(root)
                .apply {
                    environment()["LD_LIBRARY_PATH"] = File(root, "lib").absolutePath
                }
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out
        }.getOrDefault("未知")
    }

    companion object {
        private const val TAG = "NodeRuntime"
    }
}
