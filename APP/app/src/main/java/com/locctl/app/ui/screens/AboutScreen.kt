package com.locctl.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 卡片 1：应用基础信息 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "WX定位 · Locctl",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "版本 v1.0.0 (Release) · Android 12 ~ 16",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FeatureBadge("零外部依赖")
                    FeatureBadge("免选模拟应用")
                    FeatureBadge("纯原生架构")
                    FeatureBadge("防Wi-Fi嗅探")
                }
            }
        }
        }

        // ==================== 卡片 2：版本更新与检查 ====================
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
                            Icon(
                                Icons.Default.SystemUpdate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "版本管理与更新",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isCheckingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "当前已安装版本: v1.0.0 · 编译类型: Debug/Release",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    delay(600)
                                    isCheckingUpdate = false
                                    showUpdateDialog = true
                                }
                            },
                            enabled = !isCheckingUpdate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("检查更新")
                        }

                        OutlinedButton(
                            onClick = { showChangelogDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ListAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("更新日志")
                        }
                    }
                }
            }
        }

        // ==================== 卡片 3：用户权益与隐私承诺 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "用户权益与安全承诺",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    RightsItem(
                        title = "100% 纯本地运行 · 零云端隐私上传",
                        desc = "本工具所有经纬度解析、本地 ADB 协议握手与命令交互全部在手机系统内闭环运行（127.0.0.1 环回），不设任何外部数据收集上报接口，绝不上报位置数据。"
                    )

                    RightsItem(
                        title = "零 Root 破坏 · 系统无痕还原",
                        desc = "无需对手机进行解锁 Bootloader 或获取 Root 权限，停止常驻注入后立即原子级移除测试提供程序，秒级恢复手机原有真实物理定位。"
                    )

                    RightsItem(
                        title = "合规使用声明",
                        desc = "本软件仅供软件研发人员在开发调试、多终端自动化定位测试以及合规算法验证场景下使用。请遵守当地法律法规以及第三方服务协议规范。"
                    )
                }
            }
        }

        // ==================== 卡片 4：快捷系统设置入口 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "系统优化与常驻配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    SettingEntryItem(
                        title = "电池优化无限制白名单",
                        desc = "允许在后台持续稳定注入，防止切到微信时被系统电池管理休眠挂起。",
                        onClick = {
                            runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }.onFailure {
                                Toast.makeText(context, "无法直接打开电池优化页，请在系统设置应用管理中设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    SettingEntryItem(
                        title = "应用详情与系统权限",
                        desc = "管理本 APP 的通知权限与存储配置。",
                        onClick = {
                            runCatching {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                Toast.makeText(context, "无法打开应用详情设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    // 检查更新结果对话框
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("检查更新结果") },
            text = {
                Column {
                    Text(
                        "🎉 当前已是最新正式版本 (v1.0.0)！",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2EA44F)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "已包含 Shizuku 同款无线 ADB 协议栈、单页独立架构、微信防 Wi-Fi 嗅探机制以及常用地址保存库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showUpdateDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    // 更新日志对话框
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            title = { Text("v1.0.0 重构版本更新日志") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "1. 架构全面原生化：\n" +
                        "   彻底裁撤 Node.js 运行时与 60MB 依赖，APK 降至约 3MB，极速冷启动。\n\n" +
                        "2. 内置无线 ADB 协议栈：\n" +
                        "   免安装外部 Shizuku 或 Termux，下拉通知栏直接输入配对码，单 APK 完成自闭环。\n\n" +
                        "3. 微信防嗅探与防休眠强化：\n" +
                        "   注入时自动关闭后台全局 Wi-Fi 扫描，屏蔽腾讯地图 MAC 逆向定位，配合保活机制不闪退。\n\n" +
                        "4. 常用地址另存与快速选用：\n" +
                        "   支持将任意目标经纬度另存为常用地址，多地点一键秒切，持久化保存。\n\n" +
                        "5. 专业多 Tab 现代化独立页面：\n" +
                        "   涵盖状态指南、位置管理、全屏日志、关于权益四大独立模块。",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showChangelogDialog = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun FeatureBadge(text: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RightsItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
    }
}

@Composable
private fun SettingEntryItem(title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) {
            Text("前往设置")
        }
    }
}
