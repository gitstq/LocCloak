package com.locctl.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.locctl.app.core.CoordsConverter
import com.locctl.app.ui.theme.MonoStyle
import compose.icons.FeatherIcons
import compose.icons.feathericons.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AmapSearchResult(
    val title: String,
    val subtitle: String,
    val lat: Double, // GCJ-02
    val lng: Double  // GCJ-02
)

private val POPULAR_CITIES = listOf(
    "北京" to (39.9042 to 116.4074),
    "上海" to (31.2304 to 121.4737),
    "广州" to (23.1291 to 113.2644),
    "深圳" to (22.5431 to 114.0579),
    "杭州" to (30.2741 to 120.1551),
    "成都" to (30.5728 to 104.0668),
    "重庆" to (29.5630 to 106.5516),
    "武汉" to (30.5928 to 114.3055),
    "南京" to (32.0603 to 118.7969),
    "西安" to (34.3416 to 108.9398),
    "苏州" to (31.2990 to 120.5853),
    "郑州" to (34.7466 to 113.6253),
    "长沙" to (28.2282 to 112.9388)
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AmapPickerModal(
    initialLat: Double,
    initialLng: Double,
    initialCoordSystem: String,
    onDismissRequest: () -> Unit,
    onLocationSelected: (lat: Double, lng: Double, address: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 换算起始点为 GCJ-02
    val (startGcjLat, startGcjLng) = remember(initialLat, initialLng, initialCoordSystem) {
        when (initialCoordSystem.lowercase()) {
            "gcj02" -> initialLat to initialLng
            "bd09" -> CoordsConverter.bd09ToGcj02(initialLat, initialLng)
            else -> CoordsConverter.wgs84ToGcj02(initialLat, initialLng)
        }
    }

    var currentGcjLat by remember { mutableStateOf(startGcjLat) }
    var currentGcjLng by remember { mutableStateOf(startGcjLng) }
    var currentAddress by remember { mutableStateOf("正在获取地址...") }
    var isGeocoding by remember { mutableStateOf(false) }

    // 图层模式：vec(矢量) / img(卫星实景)
    var isSatelliteLayer by remember { mutableStateOf(false) }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<AmapSearchResult>>(emptyList()) }
    var showSearchResults by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // 坐标变化防抖逆地理编码
    LaunchedEffect(currentGcjLat, currentGcjLng) {
        delay(400) // 400ms 防抖，避免拖拽过程频繁网络请求
        isGeocoding = true
        val (wgsLat, wgsLng) = CoordsConverter.gcj02ToWgs84(currentGcjLat, currentGcjLng)
        val address = withContext(Dispatchers.IO) {
            fetchReverseGeocode(wgsLat, wgsLng)
        }
        currentAddress = address
        isGeocoding = false
    }

    // 执行地名或坐标搜索
    fun doSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return

        // 优先尝试解析经纬度数字格式 (例如: "30.27, 120.21" 或 "120.21, 30.27")
        val coordRegex = Regex("""^([-+]?\d*\.?\d+)[,\s]+([-+]?\d*\.?\d+)$""")
        val match = coordRegex.find(q)
        if (match != null) {
            val v1 = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val v2 = match.groupValues[2].toDoubleOrNull() ?: 0.0
            val (targetLat, targetLng) = if (v1 in -90.0..90.0 && v2 in -180.0..180.0) {
                v1 to v2
            } else if (v2 in -90.0..90.0 && v1 in -180.0..180.0) {
                v2 to v1
            } else {
                v1 to v2
            }
            webViewRef?.evaluateJavascript("moveToLocation($targetLat, $targetLng, 16);", null)
            showSearchResults = false
            return
        }

        // 网络搜索地点
        isSearching = true
        showSearchResults = true
        coroutineScope.launch {
            val results = withContext(Dispatchers.IO) {
                searchLocations(q)
            }
            searchResults = results
            isSearching = false
        }
    }

    // 定位到设备物理 GPS
    fun locateDeviceLocation() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            val loc = lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (loc != null) {
                val (gcjLat, gcjLng) = CoordsConverter.wgs84ToGcj02(loc.latitude, loc.longitude)
                webViewRef?.evaluateJavascript("moveToLocation($gcjLat, $gcjLng, 17);", null)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. 底层高德在线 Leaflet 地图
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun onMapReady() {
                                post {
                                    evaluateJavascript("initAmap($startGcjLat, $startGcjLng, 16);", null)
                                }
                            }

                            @JavascriptInterface
                            fun onLocationChanged(lat: Double, lng: Double) {
                                post {
                                    currentGcjLat = lat
                                    currentGcjLng = lng
                                }
                            }
                        }, "AmapBridge")

                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("file:///android_asset/amap_picker.html")
                        webViewRef = this
                    }
                }
            )

            // 2. 顶部悬浮控制卡片（返回、标题、图层切换、搜索栏、城市快捷跳转）
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // 标题行与控制按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onDismissRequest,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(FeatherIcons.X, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(
                                        "高德地图在线选点",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "拖动地图或点击目标精确扎针",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 矢量 / 卫星切换按钮
                            FilledTonalButton(
                                onClick = {
                                    isSatelliteLayer = !isSatelliteLayer
                                    val layerName = if (isSatelliteLayer) "img" else "vec"
                                    webViewRef?.evaluateJavascript("setMapLayer('$layerName');", null)
                                },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(
                                    FeatherIcons.Layers,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isSatelliteLayer) "卫星图" else "矢量图",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // 搜索输入框
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索地点、街道或输入经纬度...", fontSize = 13.sp) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = {
                                Icon(FeatherIcons.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            showSearchResults = false
                                        }) {
                                            Icon(FeatherIcons.X, contentDescription = null, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                    Button(
                                        onClick = { doSearch(searchQuery) },
                                        shape = CircleShape,
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                        modifier = Modifier
                                            .padding(end = 4.dp)
                                            .height(32.dp)
                                    ) {
                                        Text("搜索", fontSize = 12.sp)
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch(searchQuery) })
                        )

                        Spacer(Modifier.height(8.dp))

                        // 常用热门城市快捷跳转
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            POPULAR_CITIES.forEach { (cityName, coords) ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            webViewRef?.evaluateJavascript(
                                                "moveToLocation(${coords.first}, ${coords.second}, 14);",
                                                null
                                            )
                                        }
                                ) {
                                    Text(
                                        cityName,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 搜索结果列表下拉面板
                AnimatedVisibility(
                    visible = showSearchResults,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 8.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        if (isSearching) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("正在全球检索高精度地点...", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "未搜索到相关地点，可直接输入经纬度定位",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(searchResults) { item ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                webViewRef?.evaluateJavascript(
                                                    "moveToLocation(${item.lat}, ${item.lng}, 17);",
                                                    null
                                                )
                                                showSearchResults = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            item.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. 右侧浮动快捷动作按钮组（定位、缩放）
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // GPS 当前位置定位按钮
                FloatingActionButton(
                    onClick = { locateDeviceLocation() },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(FeatherIcons.Navigation, contentDescription = "当前定位", modifier = Modifier.size(20.dp))
                }

                // 放大按钮
                FloatingActionButton(
                    onClick = { webViewRef?.evaluateJavascript("zoomIn();", null) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(FeatherIcons.Plus, contentDescription = "放大", modifier = Modifier.size(20.dp))
                }

                // 缩小按钮
                FloatingActionButton(
                    onClick = { webViewRef?.evaluateJavascript("zoomOut();", null) },
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    elevation = FloatingActionButtonDefaults.elevation(4.dp)
                ) {
                    Icon(FeatherIcons.Minus, contentDescription = "缩小", modifier = Modifier.size(20.dp))
                }
            }

            // 4. 底部位置确认悬浮 Bento 卡片
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 地址信息展示
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "选定目标位置",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isGeocoding) {
                                    Spacer(Modifier.width(6.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp)
                                }
                            }
                            Text(
                                currentAddress,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 实时换算坐标预览
                    val (wgsLat, wgsLng) = remember(currentGcjLat, currentGcjLng) {
                        CoordsConverter.gcj02ToWgs84(currentGcjLat, currentGcjLng)
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "高德/微信 (GCJ-02):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "%.6f, %.6f".format(currentGcjLat, currentGcjLng),
                                    style = MonoStyle,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 12.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "底层注入 (WGS-84):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "%.6f, %.6f".format(wgsLat, wgsLng),
                                    style = MonoStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // 两个独立的控制按钮，中间由空白完全隔开
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismissRequest,
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                        ) {
                            Text("取消")
                        }

                        Button(
                            onClick = {
                                onLocationSelected(currentGcjLat, currentGcjLng, currentAddress)
                                onDismissRequest()
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .weight(1.8f)
                                .height(46.dp)
                        ) {
                            Icon(FeatherIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("确定使用此位置", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 逆地理编码服务：根据 WGS-84 经纬度逆解人类可读中文地址
 */
private fun fetchReverseGeocode(lat: Double, lng: Double): String {
    return runCatching {
        val url = URL("https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lng&zoom=18&addressdetails=1")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LocCloakApp/2.2.0")
            connectTimeout = 4000
            readTimeout = 4000
        }
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(response)
        val displayName = json.optString("display_name", "")
        val addressObj = json.optJSONObject("address")

        if (addressObj != null) {
            val road = addressObj.optString("road", "")
            val suburb = addressObj.optString("suburb", "")
            val city = addressObj.optString("city", addressObj.optString("county", ""))
            val state = addressObj.optString("state", "")
            val name = listOf(state, city, suburb, road).filter { it.isNotBlank() }.joinToString("")
            if (name.isNotBlank()) name else displayName
        } else {
            displayName.ifBlank { "已选择坐标点 (%.6f, %.6f)".format(lat, lng) }
        }
    }.getOrDefault("选定位置 (%.6f, %.6f)".format(lat, lng))
}

/**
 * 地点检索服务：输入关键词搜索并转换为 GCJ-02 坐标
 */
private fun searchLocations(query: String): List<AmapSearchResult> {
    return runCatching {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=6&countrycodes=cn")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", "LocCloakApp/2.2.0")
            connectTimeout = 5000
            readTimeout = 5000
        }
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        val array = JSONArray(response)
        val list = mutableListOf<AmapSearchResult>()
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val name = item.optString("name", item.optString("display_name", "未知地点"))
            val displayName = item.optString("display_name", "")
            val wgsLat = item.optDouble("lat", 0.0)
            val wgsLng = item.optDouble("lon", 0.0)
            val (gcjLat, gcjLng) = CoordsConverter.wgs84ToGcj02(wgsLat, wgsLng)
            list.add(
                AmapSearchResult(
                    title = name,
                    subtitle = displayName,
                    lat = gcjLat,
                    lng = gcjLng
                )
            )
        }
        list
    }.getOrDefault(emptyList())
}

