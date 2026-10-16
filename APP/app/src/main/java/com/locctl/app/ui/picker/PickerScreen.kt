package com.locctl.app.ui.picker

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.locctl.app.core.CoordSystem
import com.locctl.app.ui.theme.MonoStyle

/**
 * 地点选择页。
 *
 * 坐标输入框接受任意坐标系，选 [CoordSystem] 后由 Node 侧统一换算成 WGS-84。
 * 同时展示两套坐标值，方便和高德、微信里的数字对照 —— 这是 ChangeLocation 的
 * 核心价值，必须让用户在注入前就能核对。
 */
@Composable
fun PickerScreen(
    state: PickerUiState,
    onSearch: (String) -> Unit,
    onPick: (PlaceItem) -> Unit,
    onManualInject: (lat: String, lng: String, cs: CoordSystem) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var manualLat by remember { mutableStateOf("") }
    var manualLng by remember { mutableStateOf("") }
    var manualCs by remember { mutableStateOf(CoordSystem.WGS84) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("地点", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("搜索名称 / 别名 / 分类") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
        }

        if (state.loading) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("计算中…", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        items(state.places, key = { it.name }) { place ->
            PlaceCard(place = place, onClick = { onPick(place) })
        }

        if (state.places.isEmpty() && !state.loading) {
            item {
                Text(
                    "没有匹配的地点",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("手动输入坐标", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualLat,
                            onValueChange = { manualLat = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("纬度") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = manualLng,
                            onValueChange = { manualLng = it },
                            modifier = Modifier.weight(1f),
                            label = { Text("经度") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "坐标系",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CoordSystem.entries.forEach { cs ->
                            FilterChip(
                                selected = manualCs == cs,
                                onClick = { manualCs = cs },
                                label = { Text(cs.label, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onManualInject(manualLat, manualLng, manualCs) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = manualLat.isNotBlank() && manualLng.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("换算并注入")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(place: PlaceItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = {},
                    label = { Text(place.cat) },
                    colors = AssistChipDefaults.assistChipColors(),
                    modifier = Modifier.height(24.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "WGS-84  ${"%.6f".format(place.lat)}, ${"%.6f".format(place.lng)}",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (place.gcj02 != null) {
                Text(
                    "GCJ-02  ${"%.6f".format(place.gcj02.first)}, ${"%.6f".format(place.gcj02.second)}",
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

data class PlaceItem(
    val name: String,
    val cat: String,
    val lat: Double,
    val lng: Double,
    val gcj02: Pair<Double, Double>?,
)

data class PickerUiState(
    val places: List<PlaceItem> = emptyList(),
    val loading: Boolean = false,
)
