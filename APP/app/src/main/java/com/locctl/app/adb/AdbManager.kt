package com.locctl.app.adb

import android.content.Context
import android.os.Build
import com.locctl.app.core.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "AdbManager"

enum class AdbState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

object AdbManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mdns: AdbMdns? = null

    private var client: AdbClient? = null
    private var adbKey: AdbKey? = null

    private val _state = MutableStateFlow(AdbState.DISCONNECTED)
    val state: StateFlow<AdbState> = _state.asStateFlow()

    private val _statusText = MutableStateFlow("未连接")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _activePort = MutableStateFlow(-1)
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    fun getOrCreateKey(context: Context): AdbKey {
        adbKey?.let { return it }
        val sp = context.getSharedPreferences("adb_key_pref", Context.MODE_PRIVATE)
        val key = AdbKey(PreferenceAdbKeyStore(sp), "locctl")
        adbKey = key
        AppLogger.d(TAG, "已加载/生成本地 ADB 证书密钥")
        return key
    }

    fun init(context: Context) {
        AppLogger.i(TAG, "初始化 ADB 管理器，开始嗅探无线调试服务...")
        getOrCreateKey(context)
        startMdnsDiscovery(context)
    }

    private fun startMdnsDiscovery(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mdns == null) {
            mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { port ->
                if (port > 0) {
                    AppLogger.i(TAG, "mDNS 侦测到无线调试直连端口: $port")
                    _activePort.value = port
                    if (_state.value != AdbState.CONNECTED) {
                        connect(context, port)
                    }
                } else {
                    AppLogger.w(TAG, "无线调试服务已断开或端口失效")
                    _activePort.value = -1
                    if (_state.value == AdbState.CONNECTED) {
                        disconnect()
                    }
                }
            }.apply {
                runCatching { start() }
            }
        }
    }

    fun connect(context: Context, port: Int) {
        if (_state.value == AdbState.CONNECTING) return
        _state.value = AdbState.CONNECTING
        _statusText.value = "正在连接端口 $port..."
        AppLogger.i(TAG, "正在发起本地 ADB TCP 连接 -> 127.0.0.1:$port")

        scope.launch {
            try {
                val key = getOrCreateKey(context)
                client?.close()
                val newClient = AdbClient("127.0.0.1", port, key)
                newClient.connect()
                client = newClient
                _activePort.value = port
                _state.value = AdbState.CONNECTED
                _statusText.value = "已连接 (端口 $port)"
                AppLogger.i(TAG, "ADB 握手成功！会话已就绪 (端口 $port)")

                // 自动给自身加入电池优化白名单和后台运行权限，防止切至微信时被系统智能省电休眠冻结
                val pkg = context.packageName
                runCatching {
                    execute("dumpsys deviceidle whitelist +$pkg")
                    execute("appops set $pkg RUN_IN_BACKGROUND allow")
                    execute("appops set $pkg RUN_ANY_IN_BACKGROUND allow")
                    execute("appops set $pkg START_FOREGROUND allow")
                    execute("cmd appops set $pkg 10008 allow 2>/dev/null")
                    execute("cmd appops set $pkg 10021 allow 2>/dev/null")
                    AppLogger.i(TAG, "已自动解除自身后台运行限制（防休眠与自启动白名单）")
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "连接端口 $port 失败: ${e.message}", e)
                client?.close()
                client = null
                _state.value = AdbState.DISCONNECTED
                _statusText.value = "连接失败: ${e.message}"
            }
        }
    }

    private fun reconnectSync(port: Int): Boolean {
        return try {
            val context = com.locctl.app.LocctlApp.get()
            val key = getOrCreateKey(context)
            client?.close()
            val newClient = AdbClient("127.0.0.1", port, key)
            newClient.connect()
            client = newClient
            _activePort.value = port
            _state.value = AdbState.CONNECTED
            _statusText.value = "已连接 (端口 $port)"
            AppLogger.i(TAG, "ADB 同步重连成功 (端口 $port)")
            true
        } catch (e: Exception) {
            AppLogger.w(TAG, "ADB 同步重连失败: ${e.message}")
            client?.close()
            client = null
            _state.value = AdbState.DISCONNECTED
            _statusText.value = "重连失败: ${e.message}"
            false
        }
    }

    fun disconnect() {
        AppLogger.i(TAG, "断开 ADB 连接")
        client?.close()
        client = null
        _state.value = AdbState.DISCONNECTED
        _statusText.value = "未连接"
    }

    fun onPairingSucceeded() {
        AppLogger.i(TAG, "无线调试配对握手成功，等待自动嗅探直连端口...")
        _statusText.value = "配对成功，等待连接无线调试..."
    }

    private val execMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        execMutex.withLock {
            var currentClient = client
            if (currentClient == null && _activePort.value > 0) {
                AppLogger.i(TAG, "ADB 未连接，尝试自动重连端口 ${_activePort.value}...")
                reconnectSync(_activePort.value)
                currentClient = client
            }
            if (currentClient == null) {
                AppLogger.e(TAG, "执行命令失败: ADB 未连接 ($command)")
                error("ADB 未连接")
            }
            AppLogger.d(TAG, "执行 Shell: $command")
            try {
                val out = currentClient.executeCommand(command)
                if (out.isNotBlank()) {
                    AppLogger.d(TAG, "返回 -> ${out.trim()}")
                }
                return@withLock out
            } catch (e: Exception) {
                AppLogger.e(TAG, "执行 Shell 异常，重置 ADB 连接: ${e.message}")
                client?.close()
                client = null
                _state.value = AdbState.DISCONNECTED
                _statusText.value = "连接异常断开"
                throw e
            }
        }
    }

    fun isConnected(): Boolean = _state.value == AdbState.CONNECTED
}
