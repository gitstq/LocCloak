package com.locctl.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.BuildConfig
import com.locctl.app.update.UpdateCheckResult
import com.locctl.app.update.UpdateManager
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val currentVersion = BuildConfig.VERSION_NAME

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateCheckResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var showChangelogDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 卡片 1：应用基础信息 Bento ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.size(68.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                FeatherIcons.Navigation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        "WX定位 · Locctl",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            "版本 v$currentVersion (Release) · Android 11 ~ 16",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ModernFeatureBadge("零外部依赖", FeatherIcons.Zap)
                            ModernFeatureBadge("免选模拟应用", FeatherIcons.CheckCircle)
                            ModernFeatureBadge("纯原生架构", FeatherIcons.Shield)
                            ModernFeatureBadge("防嗅探伪装", FeatherIcons.Lock)
                        }
                    }
                }
            }
        }

        // ==================== 卡片 2：开发者与开源支持 Bento ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.User,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "开发者与开源信息",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 微信
                    ContactInfoItem(
                        icon = FeatherIcons.MessageCircle,
                        iconColor = Color(0xFF07C160),
                        label = "开发 · 微信 (WeChat)",
                        value = "pOVOq-521",
                        actionText = "复制微信",
                        onAction = {
                            clipboardManager.setText(AnnotatedString("pOVOq-521"))
                            Toast.makeText(context, "微信号 pOVOq-521 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // QQ
                    ContactInfoItem(
                        icon = FeatherIcons.Send,
                        iconColor = Color(0xFF12B7F5),
                        label = "交流 · QQ",
                        value = "2697905019",
                        actionText = "复制QQ",
                        onAction = {
                            clipboardManager.setText(AnnotatedString("2697905019"))
                            Toast.makeText(context, "QQ 号 2697905019 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // GitHub
                    ContactInfoItem(
                        icon = FeatherIcons.Github,
                        iconColor = MaterialTheme.colorScheme.onSurface,
                        label = "GitHub 开源仓库",
                        value = "https://github.com/gitstq/locctl",
                        actionText = "访问项目",
                        onAction = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/gitstq/locctl")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }.onFailure {
                                clipboardManager.setText(AnnotatedString("https://github.com/gitstq/locctl"))
                                Toast.makeText(context, "GitHub 链接已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // ==================== 卡片 3：版本更新与检查 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        FeatherIcons.RefreshCw,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
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
                        "当前已安装版本: v$currentVersion · 真实连接 GitHub Releases 校验",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCheckingUpdate = true
                                    val result = UpdateManager.checkForUpdates(currentVersion)
                                    isCheckingUpdate = false
                                    updateCheckResult = result
                                }
                            },
                            enabled = !isCheckingUpdate,
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(FeatherIcons.RefreshCw, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isCheckingUpdate) "检查中..." else "检查更新")
                        }

                        OutlinedButton(
                            onClick = { showChangelogDialog = true },
                            shape = CircleShape,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(FeatherIcons.FileText, contentDescription = null, modifier = Modifier.size(16.dp))
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
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2EA44F).copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.Shield,
                                    contentDescription = null,
                                    tint = Color(0xFF2EA44F),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "用户权益与安全承诺",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    ModernRightsItem(
                        icon = FeatherIcons.Lock,
                        title = "100% 纯本地运行 · 零云端隐私上传",
                        desc = "本工具所有经纬度解析、本地 ADB 协议握手与命令交互全部在手机系统内闭环运行（127.0.0.1 环回），不设任何外部数据上报接口，绝不上报位置数据。"
                    )

                    Spacer(Modifier.height(10.dp))

                    ModernRightsItem(
                        icon = FeatherIcons.Zap,
                        title = "零 Root 破坏 · 系统无痕还原",
                        desc = "无需解锁 Bootloader 或获取 Root 权限，停止常驻注入后立即原子级移除测试提供程序，秒级恢复手机原有真实物理定位与系统原装状态。"
                    )

                    Spacer(Modifier.height(10.dp))

                    ModernRightsItem(
                        icon = FeatherIcons.CheckCircle,
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
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.Sliders,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "系统优化与常驻配置",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    ModernSettingEntry(
                        icon = FeatherIcons.Battery,
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

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    ModernSettingEntry(
                        icon = FeatherIcons.Settings,
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

    // 真实的 GitHub Releases 检查更新结果对话框
    updateCheckResult?.let { result ->
        when (result) {
            is UpdateCheckResult.HasUpdate -> {
                AlertDialog(
                    onDismissRequest = { updateCheckResult = null },
                    shape = RoundedCornerShape(20.dp),
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2EA44F).copy(alpha = 0.15f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.Download,
                                    contentDescription = null,
                                    tint = Color(0xFF2EA44F),
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            "🎉 发现新版本 ${result.info.latestVersion}",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    "当前版本: v${result.info.currentVersion}  ➔  最新: ${result.info.latestVersion}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                "更新日志与发版说明：",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        result.info.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            shape = CircleShape,
                            onClick = {
                                updateCheckResult = null
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.info.downloadUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }.onFailure {
                                    Toast.makeText(context, "无法打开系统浏览器，请手动访问 GitHub", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("立即下载新版")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            shape = CircleShape,
                            onClick = { updateCheckResult = null }
                        ) {
                            Text("稍后再说")
                        }
                    }
                )
            }
            is UpdateCheckResult.UpToDate -> {
                AlertDialog(
                    onDismissRequest = { updateCheckResult = null },
                    shape = RoundedCornerShape(20.dp),
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2EA44F).copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF2EA44F),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    title = { Text("已是最新版本", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                "🎉 当前已是最新正式版本 (v${result.currentVersion})！",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2EA44F)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "已包含 Shizuku 同款无线 ADB 协议栈、单页独立架构、微信防 Wi-Fi 嗅探机制以及常用地址保存库，暂无更新需要。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { updateCheckResult = null },
                            shape = CircleShape
                        ) {
                            Text("确定")
                        }
                    }
                )
            }
            is UpdateCheckResult.Error -> {
                AlertDialog(
                    onDismissRequest = { updateCheckResult = null },
                    shape = RoundedCornerShape(20.dp),
                    icon = {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    },
                    title = { Text("检查更新失败", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text(
                                result.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "由于国内访问 GitHub 偶发网络波动或限流，您可以直接在手机浏览器中打开 GitHub 发行版页面下载最新安装包。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                updateCheckResult = null
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(result.fallbackUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            shape = CircleShape
                        ) {
                            Icon(FeatherIcons.ExternalLink, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("打开发行页面")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            shape = CircleShape,
                            onClick = { updateCheckResult = null }
                        ) {
                            Text("关闭")
                        }
                    }
                )
            }
        }
    }

    // 更新日志对话框
    if (showChangelogDialog) {
        AlertDialog(
            onDismissRequest = { showChangelogDialog = false },
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(FeatherIcons.FileText, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                }
            },
            title = { Text("v1.0.0 重构版本更新日志", fontWeight = FontWeight.Bold) },
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
                Button(
                    onClick = { showChangelogDialog = false },
                    shape = CircleShape
                ) {
                    Text("关闭")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModernFeatureBadge(text: String, icon: ImageVector) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ModernRightsItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ModernSettingEntry(icon: ImageVector, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.width(8.dp))

        Icon(
            FeatherIcons.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ContactInfoItem(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    actionText: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onAction)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = CircleShape,
                color = iconColor.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        OutlinedButton(
            onClick = onAction,
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Text(actionText, style = MaterialTheme.typography.labelSmall)
        }
    }
}


