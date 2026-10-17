package com.locctl.app.adb

import android.content.Context
import android.os.Build
import com.locctl.app.core.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            } catch (e: Exception) {
                AppLogger.e(TAG, "连接端口 $port 失败: ${e.message}", e)
                client?.close()
                client = null
                _state.value = AdbState.DISCONNECTED
                _statusText.value = "连接失败: ${e.message}"
            }
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

    suspend fun execute(command: String): String = withContext(Dispatchers.IO) {
        val currentClient = client ?: run {
            AppLogger.e(TAG, "执行命令失败: ADB 未连接 ($command)")
            error("ADB 未连接")
        }
        AppLogger.d(TAG, "执行 Shell: $command")
        val out = currentClient.executeCommand(command)
        if (out.isNotBlank()) {
            AppLogger.d(TAG, "返回 -> ${out.trim()}")
        }
        return@withContext out
    }

    fun isConnected(): Boolean = _state.value == AdbState.CONNECTED
}
