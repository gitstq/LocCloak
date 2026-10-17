package com.locctl.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.adb.AdbManager
import com.locctl.app.core.CoordsConverter
import com.locctl.app.core.SavedLocation
import com.locctl.app.core.SavedLocationManager
import com.locctl.app.service.BootReceiver
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.ui.theme.MonoStyle

@Composable
fun LocationScreen(
    initialLat: Double? = null,
    initialLng: Double? = null
) {
    val context = LocalContext.current

    // 仅监听低频的运行状态与错误，避免 500ms 高频重组
    val isRunning by InjectionForegroundService.isRunning.collectAsState()
    val lastError by InjectionForegroundService.lastError.collectAsState()

    // 常用地址列表
    val savedLocations by SavedLocationManager.locations.collectAsState()

    // 坐标与参数状态
    var latText by remember { mutableStateOf(initialLat?.toString() ?: "30.274602") }
    var lngText by remember { mutableStateOf(initialLng?.toString() ?: "120.211963") }
    var selectedCoordSystem by remember { mutableStateOf("gcj02") }
    var accuracyText by remember { mutableStateOf("3") }
    var intervalText by remember { mutableStateOf("500") }

    // 另存为对话框状态
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameText by remember { mutableStateOf("") }

    // 实时经纬度解析与 WGS-84 换算
    val parsedLat = latText.toDoubleOrNull() ?: 0.0
    val parsedLng = lngText.toDoubleOrNull() ?: 0.0
    val (wgsLat, wgsLng) = remember(parsedLat, parsedLng, selectedCoordSystem) {
        CoordsConverter.toWgs84(parsedLat, parsedLng, selectedCoordSystem)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 卡片 1：目标位置设置 ====================
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
                    Text(
                        "目标位置设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = {
                            saveNameText = ""
                            showSaveDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("另存为常用", style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = latText,
                    onValueChange = { latText = it.trim() },
                    label = { Text("纬度 (Latitude)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    trailingIcon = {
                        if (latText.isNotEmpty()) {
                            IconButton(onClick = { latText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = lngText,
                    onValueChange = { lngText = it.trim() },
                    label = { Text("经度 (Longitude)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                    trailingIcon = {
                        if (lngText.isNotEmpty()) {
                            IconButton(onClick = { lngText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "输入坐标所属标准（系统将自动数学无损反解）：",
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
                        Text("GCJ-02 (微信/高德)", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = selectedCoordSystem == "wgs84",
                            onClick = { selectedCoordSystem = "wgs84" }
                        )
                        Text("WGS-84 (原始GPS)", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        RadioButton(
                            selected = selectedCoordSystem == "bd09",
                            onClick = { selectedCoordSystem = "bd09" }
                        )
                        Text("BD-09 (百度)", style = MaterialTheme.typography.bodySmall)
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
                            "底层注入参数（转换后标准 WGS-84）：",
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
        }

        // ==================== 卡片 2：常驻注入控制 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "常驻注入控制",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))

                    // 独立局部计数组件，仅此子组件每 500ms 重组，外层零重组
                    RunningInjectionBanner()

                    if (lastError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "注入异常: $lastError",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = {
                            if (!AdbManager.isConnected()) {
                                Toast.makeText(context, "ADB 尚未连接，请先在「无线ADB」页连接或配对", Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val interval = intervalText.toLongOrNull() ?: 500L
                            val acc = accuracyText.toFloatOrNull() ?: 3f
                            BootReceiver.rememberPoint(context, wgsLat, wgsLng)
                            InjectionForegroundService.start(context, wgsLat, wgsLng, interval, acc)
                            Toast.makeText(context, "已启动前台常驻注入！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isRunning,
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
                            Toast.makeText(context, "已停止注入，正在还原真实定位", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = isRunning,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCF222E))
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("■ 停止注入并恢复真实定位")
                    }
                }
            }
        }

        // ==================== 卡片 3：常用地址库（快速选择） ====================
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
                                Icons.Default.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "常用地址库 (点击快速填入)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "${savedLocations.size} 个",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (savedLocations.isEmpty()) {
                        Text(
                            "暂无保存的地址，可点击上方「另存为常用」添加常用地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savedLocations.forEach { loc ->
                            SavedLocationItem(
                                item = loc,
                                onSelect = {
                                    latText = it.lat.toString()
                                    lngText = it.lng.toString()
                                    selectedCoordSystem = it.coordSystem
                                    Toast.makeText(context, "已选择地址: ${it.name}", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    SavedLocationManager.deleteLocation(it.id)
                                    Toast.makeText(context, "已删除: ${it.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }

    // 另存为常用地址对话框
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("另存为常用地址") },
            text = {
                Column {
                    Text(
                        "当前坐标: $parsedLat, $parsedLng (${selectedCoordSystem.uppercase()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = saveNameText,
                        onValueChange = { saveNameText = it },
                        label = { Text("地址备注名称") },
                        placeholder = { Text("例如：公司、西湖、家庭地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = saveNameText.trim().ifEmpty { "自定义地点" }
                        SavedLocationManager.saveLocation(name, parsedLat, parsedLng, selectedCoordSystem)
                        showSaveDialog = false
                        Toast.makeText(context, "已成功保存到常用地址库！", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun SavedLocationItem(
    item: SavedLocation,
    onSelect: (SavedLocation) -> Unit,
    onDelete: (SavedLocation) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(item) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (item.isPreset) Icons.Default.LocationCity else Icons.Default.Star,
                contentDescription = null,
                tint = if (item.isPreset) MaterialTheme.colorScheme.primary else Color(0xFFD29922),
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            item.coordSystem.uppercase(),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                    if (item.isPreset) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "预设",
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "%.6f, %.6f".format(item.lat, item.lng),
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onSelect(item) },
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Text("选用", style = MaterialTheme.typography.labelMedium)
            }

            if (!item.isPreset) {
                IconButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 独立的常驻注入状态条组件
 * 核心优化：仅该局部微组件收集高频的 injectCount，
 * 彻底消除 500ms 一次的全局页面重组与重新测量，保障滑动手势极致流畅。
 */
@Composable
private fun RunningInjectionBanner() {
    val isRunning by InjectionForegroundService.isRunning.collectAsState()
    if (!isRunning) return

    val count by InjectionForegroundService.injectCount.collectAsState()
    val lat by InjectionForegroundService.targetLat.collectAsState()
    val lng by InjectionForegroundService.targetLng.collectAsState()

    Surface(
        color = Color(0xFF2EA44F).copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2EA44F))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "正在常驻注入中（微信/高德生效中）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2EA44F)
                )
                Text(
                    "目标 WGS: %.6f, %.6f · 连续注入 %d 次".format(lat, lng, count),
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}
