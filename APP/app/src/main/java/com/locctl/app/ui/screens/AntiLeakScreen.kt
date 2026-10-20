package com.locctl.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.adb.AdbManager
import com.locctl.app.adb.AdbState
import com.locctl.app.core.AntiLeakManager
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AntiLeakScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val antiLeakVersion by AntiLeakManager.stateVersion.collectAsState()
    val adbState by AdbManager.state.collectAsState()
    val isAdbConnected = adbState == AdbState.CONNECTED

    val enabledCount = remember(antiLeakVersion) { AntiLeakManager.getEnabledCount(context) }
    val totalCount = AntiLeakManager.rules.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "真实定位防泄漏封堵",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "基站/Wi-Fi/蓝牙/系统NLP 9大底层硬核阻断",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            FeatherIcons.ArrowLeft,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isAdbConnected) Color(0xFF2EA44F).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isAdbConnected) Color(0xFF2EA44F) else Color(0xFF888888),
                                modifier = Modifier.size(6.dp)
                            ) {}
                            Spacer(Modifier.width(5.dp))
                            Text(
                                if (isAdbConnected) "ADB 已就绪" else "ADB 未连接",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAdbConnected) Color(0xFF2EA44F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ==================== 1. 顶部控制中心 Bento ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2EA44F).copy(alpha = 0.15f),
                                    modifier = Modifier.size(46.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            FeatherIcons.Shield,
                                            contentDescription = null,
                                            tint = Color(0xFF2EA44F),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "防护状态概览",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "已启用 $enabledCount / $totalCount 项底层封堵",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (enabledCount > 0) Color(0xFF2EA44F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    "${(enabledCount * 100) / totalCount}%",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        LinearProgressIndicator(
                            progress = { enabledCount.toFloat() / totalCount.toFloat() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = Color(0xFF2EA44F),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            "原理说明：腾讯定位 SDK 会在后台通过收集附近 Wi-Fi MAC、4G/5G 移动基站代码、商场蓝牙信标以及系统 NLP 计算真实坐标。开启以下封堵即可通过底层特权逐项掐断数据泄露通道。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        // 全局一键控制按钮组
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val res = AntiLeakManager.applyAll(context, true)
                                        if (res.isFailure) {
                                            Toast.makeText(context, "执行失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "已一键开启全部 9 项防泄漏策略", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("一键全开 (推荐)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        val res = AntiLeakManager.applyAll(context, false)
                                        if (res.isFailure) {
                                            Toast.makeText(context, "执行失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "已一键关闭并还原默认规则", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = CircleShape,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("一键全关", fontSize = 13.sp)
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 清空微信缓存辅助按钮
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    val res = AntiLeakManager.forceStopWeChat()
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "⚡ 已彻底强制停止微信，请重新进入定位页面", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "清理失败: ${res.exceptionOrNull()?.message ?: "ADB未连接"}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = CircleShape,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(FeatherIcons.Zap, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("清空微信内存定位缓存 (强杀微信进程)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            // ==================== 2. 九大独立封堵策略列表 Bento ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "封堵策略清单 (逐项单独管控)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "点击每个开关即时通过 ADB 发送底层指令生效，本地自动保存配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(thickness = 0.6.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        Spacer(Modifier.height(6.dp))

                        AntiLeakManager.rules.forEachIndexed { index, rule ->
                            val isChecked = remember(antiLeakVersion) { AntiLeakManager.isEnabled(context, rule.id) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                        ) {
                                            Text(
                                                rule.category,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            rule.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        rule.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 18.sp,
                                        fontSize = 12.sp
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                Switch(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        coroutineScope.launch {
                                            val res = AntiLeakManager.applyRule(context, rule.id, checked)
                                            if (res.isFailure) {
                                                Toast.makeText(context, "执行失败: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                            } else if (!isAdbConnected) {
                                                Toast.makeText(context, "已保存设置（ADB未连接，连接后自动生效）", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "${rule.title} 已${if (checked) "生效" else "关闭"}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.scale(0.85f)
                                )
                            }

                            if (index < AntiLeakManager.rules.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }
                }
            }

            // ==================== 3. 最佳实践操作指引 Bento ====================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(FeatherIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("防泄漏验证三步黄金法则", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "1. 确认上方规则已【一键全开】；\n2. 注入目标位置后，点击【清空微信内存定位缓存】彻底强杀微信；\n3. 重新打开微信进入【发送位置】页面，在地图上停留 5~8 秒等待底图图钉吸附。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

