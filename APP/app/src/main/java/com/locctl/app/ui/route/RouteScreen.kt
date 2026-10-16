package com.locctl.app.ui.route

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locctl.app.core.PlaybackEngine
import com.locctl.app.service.PlaybackStateBus
import com.locctl.app.ui.theme.MonoStyle

/**
 * 轨迹页。
 *
 * GPX 由 Node 侧解析并一次性重采样成完整数组，之后由 Kotlin 原生回放 ——
 * 不做逐点 IPC。所以这里只展示统计信息，不显示加载中的大量点。
 */
@Composable
fun RouteScreen(
    track: TrackInfo?,
    speedKmh: Float,
    onSpeedChange: (Float) -> Unit,
    onPickFile: (android.net.Uri) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val state by PlaybackStateBus.states.collectAsState()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onPickFile) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("轨迹", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = { launcher.launch(arrayOf("application/gpx+xml", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (track == null) "导入 GPX 文件" else "更换 GPX 文件")
        }

        Spacer(Modifier.height(12.dp))

        if (track == null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("还没有导入轨迹", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "GPX 按规范就是 WGS-84，直接导入即可。若你的 GPX 是别家导出的 GCJ-02，" +
                            "在设置里把坐标系改成 GCJ-02。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            TrackCard(track)
            Spacer(Modifier.height(12.dp))

            Text("移动速度  ${"%.1f".format(speedKmh)} km/h", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = speedKmh,
                onValueChange = onSpeedChange,
                valueRange = 1f..120f,
                steps = 118,
            )

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = if (state.running) onPause else onPlay,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (state.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.running) "暂停" else "播放")
                }
                OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("停止")
                }
            }
        }

        if (state.running || state.current != null) {
            Spacer(Modifier.height(16.dp))
            PlaybackCard(state)
        }
    }
}

@Composable
private fun TrackCard(track: TrackInfo) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(track.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            InfoRow("轨迹点", "${track.pointCount} 个")
            InfoRow("总里程", formatDistance(track.totalMeters))
            InfoRow("预计时长", "${track.estimatedSeconds} s")
        }
    }
}

@Composable
private fun PlaybackCard(state: PlaybackEngine.PlaybackState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(state.modeLabel, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "${(state.routeProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.routeProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            state.current?.let {
                Text("当前  ${it.formatWgs84()}", style = MonoStyle)
            }
            InfoRow("已推送", "${state.pushCount} 次")
            InfoRow("已行进", formatDistance(state.travelledMeters))
            state.lastError?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

internal fun formatDistance(m: Double): String = when {
    m < 1000 -> "${m.toInt()} m"
    else -> "%.2f km".format(m / 1000)
}

data class TrackInfo(
    val name: String,
    val pointCount: Int,
    val totalMeters: Double,
    val estimatedSeconds: Int,
)
