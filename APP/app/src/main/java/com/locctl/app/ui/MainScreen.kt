package com.locctl.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.ui.screens.AboutScreen
import com.locctl.app.ui.screens.AdbScreen
import com.locctl.app.ui.screens.LocationScreen
import com.locctl.app.ui.screens.LogsScreen

import compose.icons.FeatherIcons
import compose.icons.feathericons.Wifi
import compose.icons.feathericons.MapPin
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Info

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import com.locctl.app.BuildConfig
import com.locctl.app.update.UpdateCheckResult
import com.locctl.app.update.UpdateInfo
import com.locctl.app.update.UpdateManager
import androidx.activity.compose.BackHandler
import com.locctl.app.ui.screens.AntiLeakScreen
import compose.icons.feathericons.Download
import compose.icons.feathericons.MoreVertical
import compose.icons.feathericons.Moon
import compose.icons.feathericons.Sun
import compose.icons.feathericons.Smartphone
import compose.icons.feathericons.Shield
import compose.icons.feathericons.ChevronRight
import compose.icons.feathericons.Check
import androidx.compose.ui.draw.scale
import com.locctl.app.ui.theme.ThemeManager
import com.locctl.app.ui.theme.ThemeMode
import kotlinx.coroutines.delay

enum class AppTab(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    ADB("无线ADB", "状态与操作指南", FeatherIcons.Wifi),
    LOCATION("目标位置", "参数与常用地址", FeatherIcons.MapPin),
    LOGS("运行日志", "实时调试控制台", FeatherIcons.Terminal),
    ABOUT("关于更多", "检查更新与权益", FeatherIcons.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialLat: Double? = null,
    initialLng: Double? = null
) {
    val context = LocalContext.current
    var selectedTab by remember {
        mutableStateOf(if (initialLat != null && initialLng != null) AppTab.LOCATION else AppTab.ADB)
    }

    // 后台静默检查更新状态（若无更新或网络故障完全静默不打扰；若有更新则弹出询问）
    var autoUpdateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var showAntiLeakScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = showAntiLeakScreen) {
        showAntiLeakScreen = false
    }

    LaunchedEffect(Unit) {
        delay(1500) // 冷启动后延时 1.5 秒发起轻量检测，避免资源竞争
        val result = UpdateManager.checkForUpdates(BuildConfig.VERSION_NAME)
        if (result is UpdateCheckResult.HasUpdate) {
            autoUpdateInfo = result.info
        }
    }

    if (showAntiLeakScreen) {
        AntiLeakScreen(onBack = { showAntiLeakScreen = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            selectedTab.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            selectedTab.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        InjectionTopStatusBadge()
                        // 独立防泄漏盾牌按钮 (直接一键直达)
                        IconButton(
                            onClick = { showAntiLeakScreen = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                FeatherIcons.Shield,
                                contentDescription = "真实定位防泄漏封堵",
                                tint = Color(0xFF2EA44F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        TopAppBarOptionsMenu(onOpenAntiLeak = { showAntiLeakScreen = true })
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // 1. 各功能主页面
            when (selectedTab) {
                AppTab.ADB -> AdbScreen()
                AppTab.LOCATION -> LocationScreen(
                    initialLat = initialLat,
                    initialLng = initialLng
                )
                AppTab.LOGS -> LogsScreen()
                AppTab.ABOUT -> AboutScreen(onNavigateToAntiLeak = { showAntiLeakScreen = true })
            }

            // 2. 独立的悬浮在页面之上的圆形按钮组，中间完全由空白隔开
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    FloatingCircularTabButton(
                        tab = tab,
                        isSelected = isSelected,
                        onClick = { selectedTab = tab }
                    )
                }
            }

            // 3. 后台静默检测到新版本时的询问弹窗
            autoUpdateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { autoUpdateInfo = null },
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
                        Text("🎉 发现新版本 ${info.latestVersion}", fontWeight = FontWeight.Bold)
                    },
                    text = {
                        Column {
                            Text(
                                "检测到「隐影斗篷 (LocCloak)」已发布最新版本，是否现在下载更新？",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    "当前: v${BuildConfig.VERSION_NAME}  ➔  最新: ${info.latestVersion}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 160.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        info.releaseNotes,
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
                                autoUpdateInfo = null
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                        ) {
                            Icon(FeatherIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("立即更新")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            shape = CircleShape,
                            onClick = { autoUpdateInfo = null }
                        ) {
                            Text("稍后再说")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FloatingCircularTabButton(
    tab: AppTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    ) {
        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
            shadowElevation = if (isSelected) 8.dp else 4.dp,
            border = BorderStroke(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            ),
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    tab.icon,
                    contentDescription = tab.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Surface(
            shape = CircleShape,
            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            shadowElevation = 1.dp
        ) {
            Text(
                tab.title,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InjectionTopStatusBadge() {
    val isRunning by InjectionForegroundService.isRunning.collectAsState()
    Surface(
        shape = CircleShape,
        color = if (isRunning) Color(0xFF2EA44F).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isRunning) Color(0xFF2EA44F) else Color(0xFF8B949E))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isRunning) "注入中" else "未注入",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isRunning) Color(0xFF2EA44F) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun TopAppBarOptionsMenu(onOpenAntiLeak: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val currentThemeMode by ThemeManager.themeMode.collectAsState()

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                FeatherIcons.MoreVertical,
                contentDescription = "更多选项",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(min = 230.dp)
        ) {
            // 1. 真实定位防泄漏封堵 菜单按钮项
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            "防泄漏与反嗅探封堵",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "基站/Wi-Fi/蓝牙/系统NLP拦截",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                leadingIcon = {
                    Surface(
                        shape = CircleShape,
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
                },
                trailingIcon = {
                    Icon(
                        FeatherIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                },
                onClick = {
                    expanded = false
                    onOpenAntiLeak()
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            Text(
                "外观主题",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            // 1. 跟随系统选项
            ThemeModeDropdownItem(
                title = "跟随系统",
                subtitle = "随系统外观自动切换深浅",
                icon = FeatherIcons.Smartphone,
                iconColor = Color(0xFF6366F1),
                isSelected = currentThemeMode == ThemeMode.SYSTEM,
                onClick = {
                    ThemeManager.setMode(context, ThemeMode.SYSTEM)
                    expanded = false
                }
            )

            // 2. 浅色模式选项
            ThemeModeDropdownItem(
                title = "浅色模式",
                subtitle = "明亮白昼清新视觉",
                icon = FeatherIcons.Sun,
                iconColor = Color(0xFFF59E0B),
                isSelected = currentThemeMode == ThemeMode.LIGHT,
                onClick = {
                    ThemeManager.setMode(context, ThemeMode.LIGHT)
                    expanded = false
                }
            )

            // 3. 深色模式选项
            ThemeModeDropdownItem(
                title = "深色模式",
                subtitle = "夜间舒适护眼高对比度",
                icon = FeatherIcons.Moon,
                iconColor = Color(0xFF8B5CF6),
                isSelected = currentThemeMode == ThemeMode.DARK,
                onClick = {
                    ThemeManager.setMode(context, ThemeMode.DARK)
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun ThemeModeDropdownItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        leadingIcon = {
            Surface(
                shape = CircleShape,
                color = if (isSelected) iconColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (isSelected) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        trailingIcon = {
            if (isSelected) {
                Icon(
                    FeatherIcons.Check,
                    contentDescription = "当前选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        onClick = onClick,
        modifier = if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) else Modifier
    )
}
