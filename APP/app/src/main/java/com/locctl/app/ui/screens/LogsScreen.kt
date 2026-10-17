package com.locctl.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.locctl.app.core.AppLogger
import com.locctl.app.core.LogLevel
import com.locctl.app.ui.theme.MonoStyle
import compose.icons.FeatherIcons
import compose.icons.feathericons.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val logs by AppLogger.logs.collectAsState()
    val listState = rememberLazyListState()

    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }

    val filteredLogs = remember(logs, selectedLevel, searchQuery) {
        logs.filter { entry ->
            val matchLevel = selectedLevel == null || entry.level == selectedLevel
            val matchQuery = searchQuery.isEmpty() ||
                    entry.tag.contains(searchQuery, ignoreCase = true) ||
                    entry.message.contains(searchQuery, ignoreCase = true)
            matchLevel && matchQuery
        }
    }

    LaunchedEffect(filteredLogs.size, autoScroll) {
        if (autoScroll && !listState.isScrollInProgress && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(filteredLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==================== 顶部控制工具栏 ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    FeatherIcons.Terminal,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "实时系统日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "${filteredLogs.size}/${logs.size}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val text = AppLogger.getFormattedLogs()
                                    clipboardManager.setText(AnnotatedString(text))
                                    Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(FeatherIcons.Copy, contentDescription = "复制日志", modifier = Modifier.size(16.dp))
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    AppLogger.clear()
                                    Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    FeatherIcons.Trash2,
                                    contentDescription = "清空日志",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 搜索输入框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索 Tag 或关键字...", fontSize = 13.sp) },
                    leadingIcon = { Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(10.dp))

                // 筛选芯片与自动滚动开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedLevel == null,
                            onClick = { selectedLevel = null },
                            shape = CircleShape,
                            label = { Text("全部", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.INFO,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                            shape = CircleShape,
                            label = { Text("INFO", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.DEBUG,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.DEBUG) null else LogLevel.DEBUG },
                            shape = CircleShape,
                            label = { Text("DEBUG", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.ERROR,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                            shape = CircleShape,
                            label = { Text("ERROR", fontSize = 11.sp) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动滚屏", style = MaterialTheme.typography.labelSmall)
                        Checkbox(
                            checked = autoScroll,
                            onCheckedChange = { autoScroll = it }
                        )
                    }
                }
            }
        }

        // ==================== 黑色终端风格日志输出区 ====================
        Surface(
            color = Color(0xFF0D1117),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF30363D)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 模拟 macOS 终端控制条
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFEF4444)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF59E0B)))
                    Spacer(Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF10B981)))
                    Spacer(Modifier.width(12.dp))
                    Text("terminal: locctl ~ live logs", style = MonoStyle, fontSize = 11.sp, color = Color(0xFF8B949E))
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                FeatherIcons.FileText,
                                contentDescription = null,
                                tint = Color(0xFF484F58),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (logs.isEmpty()) "暂无日志，启动服务或执行操作后在此显示" else "无符合筛选条件的日志",
                                style = MonoStyle,
                                color = Color(0xFF6E7681),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredLogs, key = { it.id }) { item ->
                            val levelColor = when (item.level) {
                                LogLevel.DEBUG -> Color(0xFF58A6FF)
                                LogLevel.INFO -> Color(0xFF3FB950)
                                LogLevel.WARN -> Color(0xFFD29922)
                                LogLevel.ERROR -> Color(0xFFF85149)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "[${item.time}]",
                                    color = Color(0xFF8B949E),
                                    style = MonoStyle.copy(fontSize = 11.sp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "[${item.level.name}]",
                                    color = levelColor,
                                    style = MonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "[${item.tag}]",
                                    color = Color(0xFFD2A8FF),
                                    style = MonoStyle.copy(fontSize = 11.sp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.message,
                                    color = Color(0xFFC9D1D9),
                                    style = MonoStyle.copy(fontSize = 11.sp),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

