package com.locctl.app.ui.setup

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.locctl.app.core.DoctorReport
import com.locctl.app.ui.theme.Green
import com.locctl.app.ui.theme.MonoStyle
import com.locctl.app.ui.theme.Orange
import com.locctl.app.ui.theme.Red

/**
 * 授权状态页 —— 进入 App 的第一屏。
 *
 * 每项检测都要带 action：用户看到「失败」时，必须知道下一步点什么，
 * 而不是只看到一个错误码。
 */
@Composable
fun SetupScreen(
    report: DoctorReport?,
    loading: Boolean,
    channelLabel: String,
    onRefresh: () -> Unit,
    onGrantMockLocation: () -> Unit,
    onEnableLocation: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("授权状态", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "装到手机上就能用，不需要电脑、不需要 root",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        ChannelCard(channelLabel)

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("自检中…", style = MaterialTheme.typography.bodyMedium)
            }
        } else if (report == null) {
            Text(
                "尚未自检",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            report.checks.forEach { check ->
                CheckCard(
                    title = check.title,
                    passed = check.passed,
                    detail = check.detail,
                    action = check.action,
                    onAction = {
                        when (check.id) {
                            "channel" -> onGrantMockLocation()
                            "location" -> onEnableLocation()
                            "vendor_nlp" -> onOpenBatterySettings()
                            else -> {}
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("重新自检")
        }
    }
}

@Composable
private fun ChannelCard(label: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("当前注入通道", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "通道会自动降级：能用 SDK 就走 SDK，检测到 shell 权限才走命令通道。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckCard(
    title: String,
    passed: Boolean,
    detail: String,
    action: String?,
    onAction: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            val (icon, tint) = when {
                passed -> Icons.Default.CheckCircle to Green
                action != null -> Icons.Default.Error to Red
                else -> Icons.Default.Warning to Orange
            }
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (action != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onAction) { Text(action) }
                }
            }
        }
    }
}
