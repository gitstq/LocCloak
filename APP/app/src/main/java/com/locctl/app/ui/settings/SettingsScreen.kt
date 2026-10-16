package com.locctl.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locctl.app.core.ProviderFlags
import com.locctl.app.ui.theme.MonoStyle

/**
 * 设置页。
 *
 * 接管的定位源默认三源全开 —— 这是 Node 版实测出来的硬要求：
 * 国内应用多走 network/fused，且 HyperOS 的 metoknlp 会回填 network，
 * 只 mock gps 对微信完全无效。所以这里允许关，但默认全开。
 */
@Composable
fun SettingsScreen(
    providers: Set<String>,
    onProviderToggle: (String, Boolean) -> Unit,
    intervalMs: Long,
    onIntervalChange: (Long) -> Unit,
    accuracyMeters: Float,
    onAccuracyChange: (Float) -> Unit,
    coordSystem: String,
    onCoordSystemChange: (String) -> Unit,
    autoStart: Boolean,
    onAutoStartChange: (Boolean) -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRestoreReal: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        SettingsGroup("接管的定位源") {
            Text(
                "三源必须同时接管。国内应用多走 network / fused，" +
                    "只 mock gps 对微信完全无效。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProviderFlags.ALL.forEach { p ->
                    FilterChip(
                        selected = p in providers,
                        onClick = { onProviderToggle(p, p !in providers) },
                        label = { Text(p) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("推送参数") {
            Text("推送间隔  ${intervalMs} ms", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = intervalMs.toFloat(),
                onValueChange = { onIntervalChange(it.toLong()) },
                valueRange = 200f..3000f,
                steps = 27,
            )
            Text("上报精度  ${accuracyMeters.toInt()} m", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = accuracyMeters,
                onValueChange = onAccuracyChange,
                valueRange = 1f..50f,
                steps = 48,
            )
            Text(
                "间隔越短，时间戳越新，应用判定过期的概率越低。默认 500 ms。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("GPX 坐标系") {
            Text(
                "GPX 按规范是 WGS-84。若导入后位置整体偏移，改成 GCJ-02 再试。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("wgs84", "gcj02", "bd09").forEach { cs ->
                    FilterChip(
                        selected = coordSystem == cs,
                        onClick = { onCoordSystemChange(cs) },
                        label = { Text(cs) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("服务行为") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("开机自启", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "重启后恢复上次注入的坐标。关闭时不会在后台悄悄启动。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = autoStart, onCheckedChange = onAutoStartChange)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("忽略电池优化（否则息屏后推送会中断）")
            }
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("危险操作") {
            OutlinedButton(onClick = onRestoreReal, modifier = Modifier.fillMaxWidth()) {
                Text("立即恢复真实定位")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "会移除所有 mock provider。若应用仍显示旧位置，重启应用即可。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(12.dp))

        SettingsGroup("关于") {
            Text("locctl for Android", style = MaterialTheme.typography.titleMedium)
            Text(
                "桌面版 locctl 的 Android 移植。坐标换算、GPX 解析、地点库" +
                    "与桌面版共用同一份 JS 源码。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text("内置 Node 运行时约 93 MB", style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("MIT License", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
