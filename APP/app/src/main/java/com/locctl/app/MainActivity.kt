package com.locctl.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.locctl.app.core.CoordSystem
import com.locctl.app.core.DoctorReport
import com.locctl.app.core.GeoPoint
import com.locctl.app.core.ProviderFlags
import com.locctl.app.service.BootReceiver
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.service.PlaybackStateBus
import com.locctl.app.ui.picker.PickerScreen
import com.locctl.app.ui.picker.PickerUiState
import com.locctl.app.ui.picker.PlaceItem
import com.locctl.app.ui.route.RouteScreen
import com.locctl.app.ui.route.TrackInfo
import com.locctl.app.ui.setup.SetupScreen
import com.locctl.app.ui.settings.SettingsScreen
import com.locctl.app.ui.terminal.TerminalLine
import com.locctl.app.ui.terminal.TerminalScreen
import com.locctl.app.ui.theme.LocctlTheme
import com.locctl.app.ui.theme.MonoStyle
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LocctlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    LocctlRoot()
                }
            }
        }
    }

    @Composable
    private fun LocctlRoot() {
        val app = LocctlApp.get()
        val scope = rememberCoroutineScope()

        var report by remember { mutableStateOf<DoctorReport?>(null) }
        var doctorLoading by remember { mutableStateOf(false) }
        var channelLabel by remember { mutableStateOf("探测中…") }

        var pickerState by remember { mutableStateOf(PickerUiState()) }
        var track by remember { mutableStateOf<TrackInfo?>(null) }
        var routeSamples by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
        var speedKmh by remember { mutableStateOf(5f) }
        var terminalLines by remember { mutableStateOf(listOf(TerminalLine("locctl terminal ready", com.locctl.app.ui.terminal.LineKind.OK))) }

        var providers by remember { mutableStateOf(ProviderFlags.DEFAULT.toSet()) }
        var intervalMs by remember { mutableStateOf(500L) }
        var accuracy by remember { mutableStateOf(5f) }
        var coordSystem by remember { mutableStateOf("wgs84") }
        var autoStart by remember { mutableStateOf(false) }

        val playback by PlaybackStateBus.states.collectAsState()

        fun refreshDoctor() {
            doctorLoading = true
            scope.launch {
                runCatching { app.controller.doctor() }
                    .onSuccess {
                        report = it
                        channelLabel = app.controller.activeChannel()?.label ?: "未知"
                    }
                    .onFailure { e -> channelLabel = "自检失败：${e.message}" }
                doctorLoading = false
            }
        }

        fun searchPlaces(q: String) {
            pickerState = pickerState.copy(loading = true)
            scope.launch {
                runCatching {
                    val r = app.nodeBridge.call(
                        "presets.search",
                        JSONObject().put("keyword", q),
                    )
                    val arr = r.getJSONArray("places")
                    (0 until arr.length()).map { i ->
                        val o = arr.getJSONObject(i)
                        val g = if (o.isNull("gcj02")) null else {
                            val gj = o.getJSONObject("gcj02")
                            gj.getDouble("lat") to gj.getDouble("lng")
                        }
                        PlaceItem(
                            name = o.getString("name"),
                            cat = o.getString("cat"),
                            lat = o.getDouble("lat"),
                            lng = o.getDouble("lng"),
                            gcj02 = g,
                        )
                    }
                }.onSuccess {
                    pickerState = pickerState.copy(places = it, loading = false)
                }.onFailure {
                    pickerState = pickerState.copy(loading = false)
                    terminalLines = terminalLines + TerminalLine(
                        "检索失败：${it.message}",
                        com.locctl.app.ui.terminal.LineKind.ERROR,
                    )
                }
            }
        }

        fun inject(point: GeoPoint) {
            scope.launch {
                runCatching { app.controller.bind(); app.controller.install() }
                    .onFailure { e ->
                        terminalLines = terminalLines + TerminalLine(
                            "注入失败：${e.message}",
                            com.locctl.app.ui.terminal.LineKind.ERROR,
                        )
                        return@launch
                    }
                runCatching { app.controller.pushOnce(point) }
                    .onSuccess {
                        BootReceiver.rememberPoint(this@MainActivity, point.lat, point.lng)
                        InjectionForegroundService.hold(this@MainActivity, point, intervalMs)
                    }
                    .onFailure { e ->
                        terminalLines = terminalLines + TerminalLine(
                            "注入失败：${e.message}",
                            com.locctl.app.ui.terminal.LineKind.ERROR,
                        )
                    }
            }
        }

        fun runTerminal(raw: String) {
            terminalLines = terminalLines + TerminalLine("$ " + raw, com.locctl.app.ui.terminal.LineKind.COMMAND)
            scope.launch {
                val (method, params) = parseTerminalCommand(raw) ?: run {
                    terminalLines = terminalLines + TerminalLine(
                        "无法解析：$raw",
                        com.locctl.app.ui.terminal.LineKind.ERROR,
                    )
                    return@launch
                }
                runCatching { app.nodeBridge.call(method, params) }
                    .onSuccess { r ->
                        terminalLines = terminalLines + TerminalLine(
                            r.toString(2),
                            com.locctl.app.ui.terminal.LineKind.OUTPUT,
                        )
                    }
                    .onFailure { e ->
                        terminalLines = terminalLines + TerminalLine(
                            "${e.message}",
                            com.locctl.app.ui.terminal.LineKind.ERROR,
                        )
                    }
            }
        }

        fun loadGpx(uri: Uri) {
            scope.launch {
                runCatching {
                    // OpenDocument 给的 uri 只能读一次，先落到 cacheDir
                    val name = "imported.gpx"
                    val dest = java.io.File(cacheDir, name)
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("无法读取所选文件")

                    val r = app.nodeBridge.call(
                        "gpx.loadAndResample",
                        JSONObject()
                            .put("gpxPath", dest.absolutePath)
                            .put("cs", coordSystem)
                            .put("stepSeconds", 1),
                    )
                    val samples = r.getJSONArray("samples")
                    val pts = (0 until samples.length()).map {
                        val s = samples.getJSONObject(it)
                        GeoPoint(s.getDouble("lat"), s.getDouble("lng"))
                    }
                    val totalMeters = r.getDouble("totalMeters")
                    val totalSeconds = ((totalMeters / 1000.0) / speedKmh * 3600).toInt()
                    Triple(r, pts, totalSeconds)
                }.onSuccess { (r, pts, totalSeconds) ->
                    routeSamples = pts
                    track = TrackInfo(
                        name = r.getString("file").substringAfterLast('/'),
                        pointCount = r.getInt("pointCount"),
                        totalMeters = r.getDouble("totalMeters"),
                        estimatedSeconds = totalSeconds,
                    )
                    terminalLines = terminalLines + TerminalLine(
                        "已载入 ${pts.size} 个采样点",
                        com.locctl.app.ui.terminal.LineKind.OK,
                    )
                }.onFailure { e ->
                    terminalLines = terminalLines + TerminalLine(
                        "GPX 解析失败：${e.message}",
                        com.locctl.app.ui.terminal.LineKind.ERROR,
                    )
                }
            }
        }

        LocctlScaffold(
            playback = playback,
            channelLabel = channelLabel,
            setup = {
                SetupScreen(
                    report = report,
                    loading = doctorLoading,
                    channelLabel = channelLabel,
                    onRefresh = ::refreshDoctor,
                    onGrantMockLocation = { openMockLocationSettings() },
                    onEnableLocation = { openLocationSettings() },
                    onOpenBatterySettings = { openBatterySettings() },
                )
            },
            picker = {
                PickerScreen(
                    state = pickerState,
                    onSearch = ::searchPlaces,
                    onPick = { place ->
                        inject(
                            GeoPoint(
                                place.lat,
                                place.lng,
                                place.gcj02,
                            ),
                        )
                    },
                    onManualInject = { lat, lng, cs ->
                        scope.launch {
                            runCatching {
                                val r = app.nodeBridge.call(
                                    "coords.toWgs84",
                                    JSONObject()
                                        .put("lat", lat.toDouble())
                                        .put("lng", lng.toDouble())
                                        .put("cs", cs.id),
                                )
                                GeoPoint(
                                    r.getDouble("lat"),
                                    r.getDouble("lng"),
                                    r.getJSONObject("gcj02").let {
                                        it.getDouble("lat") to it.getDouble("lng")
                                    },
                                )
                            }.onSuccess { inject(it) }
                                .onFailure { e ->
                                    terminalLines = terminalLines + TerminalLine(
                                        "换算失败：${e.message}",
                                        com.locctl.app.ui.terminal.LineKind.ERROR,
                                    )
                                }
                        }
                    },
                )
            },
            route = {
                RouteScreen(
                    track = track,
                    speedKmh = speedKmh,
                    onSpeedChange = { speedKmh = it },
                    onPickFile = ::loadGpx,
                    onPlay = {
                        if (routeSamples.isNotEmpty()) {
                            InjectionForegroundService.route(
                                this@MainActivity,
                                routeSamples,
                                1000L,
                            )
                        }
                    },
                    onPause = { PlaybackStateBus.publish(playback.copy(running = false)) },
                    onStop = { InjectionForegroundService.stop(this@MainActivity) },
                )
            },
            terminal = {
                TerminalScreen(
                    nodeVersion = app.nodeBridge.nodeVersion,
                    alive = app.nodeBridge.isAlive,
                    lines = terminalLines,
                    onRun = ::runTerminal,
                )
            },
            settings = {
                SettingsScreen(
                    providers = providers,
                    onProviderToggle = { p, on ->
                        providers = if (on) providers + p else providers - p
                    },
                    intervalMs = intervalMs,
                    onIntervalChange = { intervalMs = it },
                    accuracyMeters = accuracy,
                    onAccuracyChange = { accuracy = it },
                    coordSystem = coordSystem,
                    onCoordSystemChange = { coordSystem = it },
                    autoStart = autoStart,
                    onAutoStartChange = {
                        autoStart = it
                        BootReceiver.setAutoStart(this@MainActivity, it)
                    },
                    onOpenBatterySettings = { openBatterySettings() },
                    onRestoreReal = {
                        scope.launch {
                            InjectionForegroundService.stop(this@MainActivity)
                            runCatching { app.controller.uninstall() }
                            channelLabel = app.controller.activeChannel()?.label ?: channelLabel
                        }
                    },
                )
            },
        )

        // 首次进入：自检 + 加载默认地点列表
        remember {
            refreshDoctor()
            searchPlaces("")
            true
        }
    }

    private fun openMockLocationSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    private fun openLocationSettings() {
        startActivity(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun openBatterySettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

/**
 * 把终端输入解析成 method + params。
 * 支持两种形式：
 *   coords.convert 30.27,120.21 --from gcj02
 *   coords.convert {"lat":30.27,"lng":120.21,"from":"gcj02"}
 */
internal fun parseTerminalCommand(raw: String): Pair<String, JSONObject>? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.startsWith("{")) {
        val o = JSONObject(trimmed)
        val m: String = o.optString("method").ifEmpty { return null }
        val p: JSONObject = o.optJSONObject("params") ?: JSONObject()
        return m to p
    }

    val parts = trimmed.split(Regex("\\s+"))
    val method = parts.firstOrNull() ?: return null
    val params = JSONObject()

    var i = 1
    while (i < parts.size) {
        val p = parts[i]
        if (p.startsWith("--")) {
            val key = p.removePrefix("--")
            val value = parts.getOrNull(i + 1) ?: ""
            params.put(key, value)
            i += 2
        } else {
            // 位置参数：lat,lng
            val seg = p.split(",")
            if (seg.size == 2) {
                params.put("lat", seg[0].toDoubleOrNull() ?: 0.0)
                params.put("lng", seg[1].toDoubleOrNull() ?: 0.0)
            } else {
                params.put("keyword", p)
            }
            i += 1
        }
    }
    return method to params
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Setup("setup", "授权", Icons.Default.VerifiedUser),
    Picker("picker", "地点", Icons.Default.Place),
    Route("route", "轨迹", Icons.Default.Route),
    Terminal("terminal", "终端", Icons.Default.Terminal),
    Settings("settings", "设置", Icons.Default.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocctlScaffold(
    playback: com.locctl.app.core.PlaybackEngine.PlaybackState,
    channelLabel: String,
    setup: @Composable () -> Unit,
    picker: @Composable () -> Unit,
    route: @Composable () -> Unit,
    terminal: @Composable () -> Unit,
    settings: @Composable () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("locctl") })
                StatusStrip(playback = playback, channelLabel = channelLabel)
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Setup.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Setup.route) { setup() }
            composable(Tab.Picker.route) { picker() }
            composable(Tab.Route.route) { route() }
            composable(Tab.Terminal.route) { terminal() }
            composable(Tab.Settings.route) { settings() }
        }
    }
}

/** 顶部常驻状态条：任何页面都能一眼看出「现在是不是在模拟」 */
@Composable
private fun StatusStrip(
    playback: com.locctl.app.core.PlaybackEngine.PlaybackState,
    channelLabel: String,
) {
    val color = when {
        playback.running -> MaterialTheme.colorScheme.tertiary
        playback.lastError != null -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(color, shape = MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        playback.running -> "正在模拟 · ${playback.modeLabel}"
                        playback.lastError != null -> "注入异常"
                        else -> "未注入"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = color,
                )
                if (playback.running && playback.current != null) {
                    Text(
                        playback.current.formatWgs84(),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                channelLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
