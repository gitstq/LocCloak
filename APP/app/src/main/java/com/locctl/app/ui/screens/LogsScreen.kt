package com.locctl.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ==================== 顶部控制工具栏 ====================
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "实时系统与注入日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "${filteredLogs.size} / ${logs.size}",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                val text = AppLogger.getFormattedLogs()
                                clipboardManager.setText(AnnotatedString(text))
                                Toast.makeText(context, "日志已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制日志", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = {
                                AppLogger.clear()
                                Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "清空日志", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 搜索输入框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索 Tag 或关键字...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(8.dp))

                // 筛选芯片与自动滚动开关
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedLevel == null,
                            onClick = { selectedLevel = null },
                            label = { Text("全部", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.INFO,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.INFO) null else LogLevel.INFO },
                            label = { Text("INFO", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.DEBUG,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.DEBUG) null else LogLevel.DEBUG },
                            label = { Text("DEBUG", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = selectedLevel == LogLevel.ERROR,
                            onClick = { selectedLevel = if (selectedLevel == LogLevel.ERROR) null else LogLevel.ERROR },
                            label = { Text("ERROR", fontSize = 11.sp) }
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("自动滚动", style = MaterialTheme.typography.labelSmall)
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
            color = Color(0xFF16181D),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Notes,
                            contentDescription = null,
                            tint = Color(0xFF6E7681),
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
