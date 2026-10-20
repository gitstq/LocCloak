package com.locctl.app.core

import android.content.Context
import com.locctl.app.adb.AdbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AntiLeakRule(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val onCommands: String,
    val offCommands: String,
    val defaultEnabled: Boolean = true
)

object AntiLeakManager {
    private const val TAG = "AntiLeakManager"
    private const val PREFS_NAME = "loccloak_anti_leak_prefs"

    // 状态版本号，用于通知 Compose 页面刷新状态
    private val _stateVersion = MutableStateFlow(0)
    val stateVersion = _stateVersion.asStateFlow()

    val rules = listOf(
        AntiLeakRule(
            id = "wechat_wifi_scan",
            category = "微信权限管控",
            title = "拦截微信 Wi-Fi 扫描",
            description = "禁止微信主动扫描周边的所有 Wi-Fi 路由器。防止腾讯根据附近的几十个 Wi-Fi 名字和信号强弱反查你的真实物理大楼。",
            onCommands = "cmd appops set com.tencent.mm android:wifi_scan ignore 2>/dev/null",
            offCommands = "cmd appops set com.tencent.mm android:wifi_scan allow 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "wechat_nearby_devices",
            category = "微信权限管控",
            title = "拦截微信近距离设备嗅探",
            description = "禁止微信探测身边的近距离无线设备广播，阻止局域网硬件拓扑与周围设备关联分析。",
            onCommands = "cmd appops set com.tencent.mm android:nearby_wifi_devices ignore 2>/dev/null",
            offCommands = "cmd appops set com.tencent.mm android:nearby_wifi_devices allow 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "system_wifi_scan_always",
            category = "系统全局扫描",
            title = "关闭系统后台 Wi-Fi 扫描",
            description = "安卓即使在手动关闭 Wi-Fi 开关后，仍会在后台全天候搜集 Wi-Fi 供系统定位。开启此项后彻底休眠系统暗中搜集周围 Wi-Fi 的行为。",
            onCommands = "settings put global wifi_scan_always_enabled 0 2>/dev/null",
            offCommands = "settings put global wifi_scan_always_enabled 1 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "wechat_cell_info",
            category = "蜂窝基站防查",
            title = "拦截微信读取蜂窝基站",
            description = "禁止微信读取 4G/5G 通信基站塔编号（MCC/MNC/LAC/CID）。没有基站代码，腾讯就无法拿基站代码逆算你在哪个小区大楼。",
            onCommands = "cmd appops set com.tencent.mm android:neighboring_cells ignore 2>/dev/null; cmd appops set com.tencent.mm android:read_phone_state ignore 2>/dev/null",
            offCommands = "cmd appops set com.tencent.mm android:neighboring_cells allow 2>/dev/null; cmd appops set com.tencent.mm android:read_phone_state allow 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "wechat_ble_scan",
            category = "室内蓝牙信标",
            title = "拦截微信低功耗蓝牙扫描",
            description = "商场、地铁、办公楼普遍部署了微型蓝牙信标（iBeacon）。禁止微信扫描蓝牙信标，彻底斩断室内超高精度定位通道。",
            onCommands = "cmd appops set com.tencent.mm android:bluetooth_scan ignore 2>/dev/null; cmd appops set com.tencent.mm android:bluetooth_connect ignore 2>/dev/null",
            offCommands = "cmd appops set com.tencent.mm android:bluetooth_scan allow 2>/dev/null; cmd appops set com.tencent.mm android:bluetooth_connect allow 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "system_ble_scan_always",
            category = "系统全局扫描",
            title = "关闭系统后台蓝牙扫描",
            description = "即使关闭了手机蓝牙，安卓系统的「提高定位精确度」默认仍在后台偷扫周围蓝牙设备。开启此项彻底关闭后台蓝牙探测。",
            onCommands = "settings put global ble_scan_always_enabled 0 2>/dev/null",
            offCommands = "settings put global ble_scan_always_enabled 1 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "system_assisted_gps",
            category = "定位辅助网络",
            title = "关闭 A-GPS 辅助网络查询",
            description = "禁止系统向运营商 SUPL 服务器发送真实基站明文换取星历，避免向外部网络辅助服务暴露物理基站归属。",
            onCommands = "settings put global assisted_gps_enabled 0 2>/dev/null",
            offCommands = "settings put global assisted_gps_enabled 1 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "passive_provider",
            category = "系统定位源",
            title = "接管系统 Passive 被动定位源",
            description = "接管安卓系统的『被动定位偷听通道』。防止微信通过暗中接收天气预报、打车软件等其他 APP 获取到的真实定位而穿帮。",
            onCommands = "cmd location providers remove-test-provider passive 2>/dev/null; cmd location providers add-test-provider passive 2>/dev/null; cmd location providers set-test-provider-enabled passive true 2>/dev/null",
            offCommands = "cmd location providers remove-test-provider passive 2>/dev/null",
            defaultEnabled = true
        ),
        AntiLeakRule(
            id = "freeze_oem_nlp",
            category = "厂商底层服务",
            title = "冻结厂商闭源网络定位服务",
            description = "【终极硬核封堵】利用 ADB 权限冻结小米(metoknlp)、华为(geolocation)、OPPO(location.service)、vivo(nlp) 后台偷偷计算真实网络坐标的闭源服务。",
            onCommands = "pm disable-user --user 0 com.xiaomi.metoknlp 2>/dev/null; pm disable-user --user 0 com.huawei.geolocation 2>/dev/null; pm disable-user --user 0 com.coloros.location.service 2>/dev/null; pm disable-user --user 0 com.vivo.nlp 2>/dev/null",
            offCommands = "pm enable com.xiaomi.metoknlp 2>/dev/null; pm enable com.huawei.geolocation 2>/dev/null; pm enable com.coloros.location.service 2>/dev/null; pm enable com.vivo.nlp 2>/dev/null",
            defaultEnabled = false
        )
    )

    fun isEnabled(context: Context, id: String): Boolean {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rule = rules.find { it.id == id } ?: return false
        return sp.getBoolean(id, rule.defaultEnabled)
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(id, enabled).apply()
        _stateVersion.value += 1
    }

    fun getEnabledCount(context: Context): Int {
        return rules.count { isEnabled(context, it.id) }
    }

    suspend fun applyRule(context: Context, id: String, enabled: Boolean): Result<Unit> {
        setEnabled(context, id, enabled)
        val rule = rules.find { it.id == id } ?: return Result.failure(IllegalArgumentException("未知规则 $id"))
        val cmd = if (enabled) rule.onCommands else rule.offCommands
        return runCatching {
            if (AdbManager.isConnected()) {
                AdbManager.execute(cmd)
                AppLogger.i(TAG, "已按需执行 [${rule.title}] -> ${if (enabled) "开启" else "关闭"}")
            } else {
                AppLogger.w(TAG, "已保存 [${rule.title}] 设置，但当前 ADB 未连接，将在连接后生效")
            }
            Unit
        }
    }

    suspend fun applyAll(context: Context, enableAll: Boolean): Result<Unit> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        val cmdBuilder = StringBuilder()
        rules.forEach { rule ->
            sp.putBoolean(rule.id, enableAll)
            val cmd = if (enableAll) rule.onCommands else rule.offCommands
            if (cmd.isNotBlank()) {
                if (cmdBuilder.isNotEmpty()) cmdBuilder.append("; ")
                cmdBuilder.append(cmd)
            }
        }
        sp.apply()
        _stateVersion.value += 1

        return runCatching {
            if (AdbManager.isConnected() && cmdBuilder.isNotEmpty()) {
                AdbManager.execute(cmdBuilder.toString())
                AppLogger.i(TAG, "已一键${if (enableAll) "开启" else "关闭"}全部防泄漏封堵规则")
            } else {
                AppLogger.w(TAG, "已一键更新配置，当前 ADB 未连接，将在连接后生效")
            }
            Unit
        }
    }

    suspend fun forceStopWeChat(): Result<Unit> {
        return runCatching {
            if (AdbManager.isConnected()) {
                AdbManager.execute("am force-stop com.tencent.mm")
                AppLogger.i(TAG, "已通过 ADB 强制停止微信 (am force-stop com.tencent.mm)")
            } else {
                error("ADB 未连接")
            }
            Unit
        }
    }

    fun getActiveInstallCommands(context: Context): List<String> {
        return rules.filter { isEnabled(context, it.id) }.map { it.onCommands }
    }
}

