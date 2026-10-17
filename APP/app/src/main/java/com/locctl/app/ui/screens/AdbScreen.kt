package com.locctl.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.adb.AdbManager
import com.locctl.app.adb.AdbPairingService
import com.locctl.app.adb.AdbState

@Composable
fun AdbScreen() {
    val context = LocalContext.current

    val adbState by AdbManager.state.collectAsState()
    val adbStatusText by AdbManager.statusText.collectAsState()
    val activePort by AdbManager.activePort.collectAsState()

    var manualPortText by remember { mutableStateOf("") }
    LaunchedEffect(activePort) {
        if (activePort > 0) {
            manualPortText = activePort.toString()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 卡片 1：当前无线 ADB 状态看板 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val dotColor = when (adbState) {
                            AdbState.CONNECTED -> Color(0xFF2EA44F)
                            AdbState.CONNECTING -> Color(0xFFD29922)
                            AdbState.DISCONNECTED -> Color(0xFFCF222E)
                        }
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "无线 ADB 服务状态",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Surface(
                        color = when (adbState) {
                            AdbState.CONNECTED -> Color(0xFF2EA44F).copy(alpha = 0.12f)
                            AdbState.CONNECTING -> Color(0xFFD29922).copy(alpha = 0.12f)
                            AdbState.DISCONNECTED -> Color(0xFFCF222E).copy(alpha = 0.12f)
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            when (adbState) {
                                AdbState.CONNECTED -> "已连接 (就绪)"
                                AdbState.CONNECTING -> "正在握手连接..."
                                AdbState.DISCONNECTED -> "未连接 (待配对)"
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (adbState) {
                                AdbState.CONNECTED -> Color(0xFF2EA44F)
                                AdbState.CONNECTING -> Color(0xFFD29922)
                                AdbState.DISCONNECTED -> Color(0xFFCF222E)
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    adbStatusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (activePort > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "活跃通信端口: 127.0.0.1:$activePort",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(14.dp))

                Text(
                    "快捷连接控制",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                context.startService(AdbPairingService.startIntent(context))
                                Toast.makeText(context, "配对通知已下发！请下拉手机通知栏输入 6 位配对码", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "无线配对需要 Android 11 (API 30) 及以上系统", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("发送配对通知")
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                Toast.makeText(context, "无法直接跳转开发者选项，请在系统设置中找到开发者选项", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("系统无线调试")
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualPortText,
                        onValueChange = { manualPortText = it.filter { c -> c.isDigit() } },
                        label = { Text("手动连接端口 (Port)") },
                        placeholder = { Text("例如 38791") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Button(
                        onClick = {
                            val port = manualPortText.toIntOrNull()
                            if (port != null && port in 1..65535) {
                                AdbManager.connect(context, port)
                            } else {
                                Toast.makeText(context, "请输入合法端口 (1~65535)", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = adbState != AdbState.CONNECTING
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("直连")
                    }
                }
            }
        }
        }

        // ==================== 卡片 2：新手全流程操作指南 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "无线 ADB 极简配对指南 (Shizuku 同款)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    GuideStepItem(
                        step = "1",
                        title = "开启开发者选项",
                        desc = "打开手机系统「设置」->「关于手机」-> 连续点击「版本号」或「OS版本」7次，直到提示已处于开发者模式。"
                    )

                    GuideStepItem(
                        step = "2",
                        title = "开启系统「无线调试」",
                        desc = "进入系统「设置」->「更多设置 / 系统」->「开发者选项」-> 找到并开启「无线调试」开关（若提示允许网络，点击始终允许）。"
                    )

                    GuideStepItem(
                        step = "3",
                        title = "进入配对窗口",
                        desc = "在「无线调试」页面中，点击「使用配对码配对设备」，此时系统会弹出 6 位配对码和配对端口（请保持留在该弹窗界面）。"
                    )

                    GuideStepItem(
                        step = "4",
                        title = "通知栏快捷输入完成配对",
                        desc = "点击上方【发送配对通知】按钮，下拉通知栏，在出现的输入框中直接输入 6 位配对码并点击发送，即可秒级完成配对！"
                    )

                    GuideStepItem(
                        step = "5",
                        title = "一次配对，持久生效",
                        desc = "配对完成后本地已自动持久化 RSA 密钥。下次打开只要开启无线调试，APP 即可自动直连，无需再次配对。"
                    )
                }
            }
        }

        // ==================== 卡片 3：免选模拟应用与微信防漏要点 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "底层核心原理与微信防漏要点",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "★ 彻底免选模拟位置应用（防被检测）：",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "传统定位软件需要用户在开发者选项中「选择模拟位置信息应用」，成熟 APP 极易扫描到该配置。本软件直接由无线 ADB（Shell UID 2000 特权）向系统内核注入，因此开发者选项中的模拟位置应用【必须保持为无（不选择）】，彻底规避应用层扫描！",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(10.dp))

                    Text(
                        "★ 微信/高德测试关键步骤：",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "1. 测试前请在下拉状态栏关闭 Wi-Fi 开关（使用移动数据），避免真实路由器 MAC 泄露给腾讯服务器。\n" +
                        "2. 划掉杀死微信后台，重新打开微信进入【发送位置】页面。\n" +
                        "3. 在地图界面停留 5~8 秒，让腾讯地图完成底图与定位点加载刷新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStepItem(step: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = step,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}
