package com.locctl.app.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.adb.AdbManager
import com.locctl.app.adb.AdbPairingService
import com.locctl.app.adb.AdbState
import com.locctl.app.core.AppLogger
import com.locctl.app.core.CoordsConverter
import com.locctl.app.core.LogLevel
import com.locctl.app.service.BootReceiver
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSingleScreen(
    initialLat: Double? = null,
    initialLng: Double? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ADB 状态订阅
    val adbState by AdbManager.state.collectAsState()
    val adbStatusText by AdbManager.statusText.collectAsState()
    val activePort by AdbManager.activePort.collectAsState()

    // 注入服务状态订阅
    val injectionState by InjectionForegroundService.state.collectAsState()

    // 坐标与参数状态
    var latText by remember { mutableStateOf(initialLat?.toString() ?: "30.274602") }
    var lngText by remember { mutableStateOf(initialLng?.toString() ?: "120.211963") }
    var selectedCoordSystem by remember { mutableStateOf("gcj02") }
    var accuracyText by remember { mutableStateOf("3") }
    var intervalText by remember { mutableStateOf("500") }

    // 手动端口输入
    var manualPortText by remember { mutableStateOf("") }
    LaunchedEffect(activePort) {
        if (activePort > 0) {
            manualPortText = activePort.toString()
        }
    }

    // 实时经纬度解析与 WGS-84 换算
    val parsedLat = latText.toDoubleOrNull() ?: 0.0
    val parsedLng = lngText.toDoubleOrNull() ?: 0.0
    val (wgsLat, wgsLng) = remember(parsedLat, parsedLng, selectedCoordSystem) {
        CoordsConverter.toWgs84(parsedLat, parsedLng, selectedCoordSystem)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "WX定位 · 极简注入器",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Shizuku同款无线配对 · 系统免选模拟应用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ==================== 卡片 1：无线 ADB 状态与配对 ====================
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
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "无线 ADB 状态",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            when (adbState) {
                                AdbState.CONNECTED -> "已连接"
                                AdbState.CONNECTING -> "连接中"
                                AdbState.DISCONNECTED -> "未连接"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (adbState) {
                                AdbState.CONNECTED -> Color(0xFF2EA44F)
                                AdbState.CONNECTING -> Color(0xFFD29922)
                                AdbState.DISCONNECTED -> Color(0xFFCF222E)
                            }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        adbStatusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    context.startService(AdbPairingService.startIntent(context))
                                    Toast.makeText(context, "配对通知已发送，请在下拉通知栏中输入验证码", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "无线配对需要 Android 11+", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
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
                                    Toast.makeText(context, "无法直接跳转开发者选项，请在系统设置中打开", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("无线调试设置")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualPortText,
                            onValueChange = { manualPortText = it.filter { c -> c.isDigit() } },
                            label = { Text("连接端口 (Connect Port)") },
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
                            Text("直连")
                        }
                    }
                }
            }

            // ==================== 卡片 2：目标位置输入 ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "目标位置设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it.trim() },
                        label = { Text("纬度 (Latitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) }
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it.trim() },
                        label = { Text("经度 (Longitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) }
                    )

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "输入坐标所属坐标系：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = selectedCoordSystem == "gcj02",
                                onClick = { selectedCoordSystem = "gcj02" }
                            )
                            Text("GCJ-02", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = selectedCoordSystem == "wgs84",
                                onClick = { selectedCoordSystem = "wgs84" }
                            )
                            Text("WGS-84", style = MaterialTheme.typography.bodySmall)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            RadioButton(
                                selected = selectedCoordSystem == "bd09",
                                onClick = { selectedCoordSystem = "bd09" }
                            )
                            Text("BD-09", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // 换算结果实时预览
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "底层注入参数（转换后 WGS-84）：",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "%.6f, %.6f".format(wgsLat, wgsLng),
                                style = MonoStyle,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = accuracyText,
                            onValueChange = { accuracyText = it.filter { c -> c.isDigit() } },
                            label = { Text("精度 (米)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                            label = { Text("刷新间隔 (ms)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            // ==================== 卡片 3：控制开关 ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "常驻注入控制",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(12.dp))

                    if (injectionState.isRunning) {
                        Surface(
                            color = Color(0xFF2EA44F).copy(alpha = 0.1f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2EA44F))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "正在常驻注入",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2EA44F)
                                    )
                                    Text(
                                        "WGS: %.6f, %.6f · 已注入 %d 次".format(
                                            injectionState.lat,
                                            injectionState.lng,
                                            injectionState.count
                                        ),
                                        style = MonoStyle,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (injectionState.error != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "注入异常: ${injectionState.error}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            if (!AdbManager.isConnected()) {
                                Toast.makeText(context, "ADB 尚未连接，请先配对或连接无线调试", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val interval = intervalText.toLongOrNull() ?: 500L
                            val acc = accuracyText.toFloatOrNull() ?: 3f
                            BootReceiver.rememberPoint(context, wgsLat, wgsLng)
                            InjectionForegroundService.start(context, wgsLat, wgsLng, interval, acc)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !injectionState.isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("▶ 开始常驻注入 (保持有效)")
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            InjectionForegroundService.stop(context)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = injectionState.isRunning,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCF222E))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("■ 停止注入并恢复真实定位")
                    }
                }
            }

            // ==================== 卡片 4：实时调试日志 ====================
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val logs by AppLogger.logs.collectAsState()
                    val clipboardManager = LocalClipboardManager.current
                    val listState = rememberLazyListState()

                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "实时调试日志",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "(${logs.size})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    val text = AppLogger.getFormattedLogs()
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("复制", style = MaterialTheme.typography.labelMedium)
                            }

                            TextButton(
                                onClick = { AppLogger.clear() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("清空", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Surface(
                        color = Color(0xFF181A1F),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        if (logs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "暂无日志",
                                    style = MonoStyle,
                                    color = Color(0xFF6E7681)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(logs, key = { it.id }) { item ->
                                    val levelColor = when (item.level) {
                                        LogLevel.DEBUG -> Color(0xFF8B949E)
                                        LogLevel.INFO -> Color(0xFF58A6FF)
                                        LogLevel.WARN -> Color(0xFFD29922)
                                        LogLevel.ERROR -> Color(0xFFF85149)
                                    }
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "${item.time} ",
                                            style = MonoStyle.copy(fontSize = 11.sp),
                                            color = Color(0xFF6E7681)
                                        )
                                        Text(
                                            "[${item.tag}] ",
                                            style = MonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                                            color = levelColor
                                        )
                                        Text(
                                            item.message,
                                            style = MonoStyle.copy(fontSize = 11.sp),
                                            color = Color(0xFFC9D1D9)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 说明提示卡片
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2EA44F),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "系统免选模拟位置原理",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "本工具直接通过本地 ADB（UID 2000 Shell）向底层注入测试位置信息。\n" +
                        "开发者选项中的「选择模拟位置信息应用」始终保持为【无】，目标应用无法通过 Android 官方 API 探查到模拟应用，真正实现免选隐蔽模拟。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
