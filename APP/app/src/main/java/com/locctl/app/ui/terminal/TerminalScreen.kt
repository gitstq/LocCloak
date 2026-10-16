package com.locctl.app.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.locctl.app.ui.theme.Green
import com.locctl.app.ui.theme.MonoStyle
import com.locctl.app.ui.theme.Red

/**
 * 内置终端。
 *
 * 只做最小实现：能跑 node 脚本、能调 bridge 里注册的方法。
 * 不做 bash、不做包管理 —— 这是计划里定下的范围控制。
 */
@Composable
fun TerminalScreen(
    nodeVersion: String?,
    alive: Boolean,
    lines: List<TerminalLine>,
    onRun: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("终端", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            val dot = if (alive) Green else Red
            Text(
                if (alive) "node ${nodeVersion ?: "…"}" else "node 未运行",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Spacer(
                Modifier
                    .size(8.dp)
                    .background(dot, shape = androidx.compose.foundation.shape.CircleShape),
            )
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF05070A)),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lines) { line ->
                    Text(
                        line.text,
                        style = MonoStyle,
                        color = when (line.kind) {
                            LineKind.COMMAND -> Color(0xFF8FD3F4)
                            LineKind.ERROR -> Red
                            LineKind.OK -> Green
                            LineKind.OUTPUT -> Color(0xFFD5DCE3)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("coords.convert 30.27,120.21 --from gcj02") },
            textStyle = MonoStyle,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (input.isNotBlank()) {
                    onRun(input.trim())
                    input = ""
                }
            }),
            trailingIcon = {
                androidx.compose.material3.IconButton(onClick = {
                    if (input.isNotBlank()) {
                        onRun(input.trim())
                        input = ""
                    }
                }) {
                    Icon(Icons.Default.Send, contentDescription = "执行")
                }
            },
        )
    }
}

enum class LineKind { COMMAND, OUTPUT, ERROR, OK }

data class TerminalLine(val text: String, val kind: LineKind)
