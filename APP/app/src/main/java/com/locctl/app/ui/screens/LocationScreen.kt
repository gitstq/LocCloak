package com.locctl.app.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.adb.AdbManager
import com.locctl.app.core.CoordsConverter
import com.locctl.app.core.SavedLocation
import com.locctl.app.core.SavedLocationManager
import com.locctl.app.service.BootReceiver
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.ui.theme.MonoStyle
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==================== 卡片 1：目标位置设置 ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        FeatherIcons.MapPin,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "目标位置设置",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                saveNameText = ""
                                showSaveDialog = true
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Icon(FeatherIcons.Bookmark, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("另存为常用", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    OutlinedTextField(
                        value = latText,
                        onValueChange = { latText = it.trim() },
                        label = { Text("纬度 (Latitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = {
                            Icon(FeatherIcons.Navigation, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        trailingIcon = {
                            if (latText.isNotEmpty()) {
                                IconButton(onClick = { latText = "" }) {
                                    Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = lngText,
                        onValueChange = { lngText = it.trim() },
                        label = { Text("经度 (Longitude)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        leadingIcon = {
                            Icon(FeatherIcons.Navigation, contentDescription = null, modifier = Modifier.size(17.dp))
                        },
                        trailingIcon = {
                            if (lngText.isNotEmpty()) {
                                IconButton(onClick = { lngText = "" }) {
                                    Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    )

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "坐标系转换标准（系统自动无损反解）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    // 独立的圆形按钮组，中间由空白完全隔开
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "gcj02" to "GCJ-02 (微信/高德)",
                            "wgs84" to "WGS-84 (GPS)",
                            "bd09" to "BD-09 (百度)"
                        ).forEach { (code, label) ->
                            val selected = selectedCoordSystem == code
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .clickable { selectedCoordSystem = code },
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 换算结果实时预览
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                FeatherIcons.Compass,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    "底层注入参数（标准 WGS-84）：",
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
                            leadingIcon = {
                                Icon(FeatherIcons.Target, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter { c -> c.isDigit() } },
                            label = { Text("刷新间隔 (ms)") },
                            leadingIcon = {
                                Icon(FeatherIcons.Clock, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            shape = RoundedCornerShape(12.dp),
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
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "常驻注入控制",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 独立局部计数组件
                    RunningInjectionBanner()

                    if (lastError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    FeatherIcons.AlertCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "注入异常: $lastError",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // 两个独立的圆形控制按钮，中间由空白完全隔开
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
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
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape,
                            enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF10B981),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(FeatherIcons.Play, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始注入", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                InjectionForegroundService.stop(context)
                                Toast.makeText(context, "已停止注入，正在还原真实定位", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = CircleShape,
                            enabled = isRunning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEF4444),
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Icon(FeatherIcons.Square, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("停止注入", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ==================== 卡片 3：常用地址库（快速选择） ====================
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
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
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        FeatherIcons.Layers,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "常用地址库",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "${savedLocations.size} 个地址",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    if (savedLocations.isEmpty()) {
                        Text(
                            "暂无保存的地址，可点击上方「另存为常用」添加常用地址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        savedLocations.forEachIndexed { index, loc ->
                            SavedLocationItem(
                                item = loc,
                                onSelect = {
                                    latText = it.lat.toString()
                                    lngText = it.lng.toString()
                                    selectedCoordSystem = it.coordSystem
                                    Toast.makeText(context, "已填入地址: ${it.name}", Toast.LENGTH_SHORT).show()
                                },
                                onDelete = {
                                    SavedLocationManager.deleteLocation(it.id)
                                    Toast.makeText(context, "已删除: ${it.name}", Toast.LENGTH_SHORT).show()
                                }
                            )
                            if (index < savedLocations.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                )
                            }
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
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(FeatherIcons.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("另存为常用地址")
                }
            },
            text = {
                Column {
                    Text(
                        "当前坐标: $parsedLat, $parsedLng (${selectedCoordSystem.uppercase()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = saveNameText,
                        onValueChange = { saveNameText = it },
                        label = { Text("地址备注名称") },
                        placeholder = { Text("例如：公司、西湖、家庭地址") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = CircleShape,
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
                TextButton(
                    shape = CircleShape,
                    onClick = { showSaveDialog = false }
                ) {
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
            Surface(
                shape = CircleShape,
                color = if (item.isPreset) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (item.isPreset) FeatherIcons.Compass else FeatherIcons.Star,
                        contentDescription = null,
                        tint = if (item.isPreset) MaterialTheme.colorScheme.primary else Color(0xFFF59E0B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            item.coordSystem.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp
                        )
                    }
                    if (item.isPreset) {
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                "预设",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { onSelect(item) },
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Text("选用", style = MaterialTheme.typography.labelMedium)
            }

            if (!item.isPreset) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier.size(32.dp)
                ) {
                    IconButton(
                        onClick = { onDelete(item) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            FeatherIcons.Trash2,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                    }
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
        color = Color(0xFF10B981).copy(alpha = 0.1f),
        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    "正在常驻注入中（微信/高德生效中）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF10B981)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "目标 WGS: %.6f, %.6f · 连续注入 %d 次".format(lat, lng, count),
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}
