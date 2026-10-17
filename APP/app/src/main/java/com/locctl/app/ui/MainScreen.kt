package com.locctl.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
                AppTab.ABOUT -> AboutScreen()
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
