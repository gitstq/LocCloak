package com.locctl.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    // 默认展示目标位置页面（如果有外部坐标传入），否则根据习惯展示 ADB 或 Location
    var selectedTab by remember {
        mutableStateOf(if (initialLat != null && initialLng != null) AppTab.LOCATION else AppTab.ADB)
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
                    InjectionTopStatusBadge()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                AppTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                tab.title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                AppTab.ADB -> AdbScreen()
                AppTab.LOCATION -> LocationScreen(
                    initialLat = initialLat,
                    initialLng = initialLng
                )
                AppTab.LOGS -> LogsScreen()
                AppTab.ABOUT -> AboutScreen()
            }
        }
    }
}

@Composable
private fun InjectionTopStatusBadge() {
    val isRunning by InjectionForegroundService.isRunning.collectAsState()
    Surface(
        shape = CircleShape,
        color = if (isRunning) Color(0xFF2EA44F).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(end = 12.dp)
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
