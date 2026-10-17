package com.locctl.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.locctl.app.core.GeoPoint
import com.locctl.app.service.InjectionForegroundService
import com.locctl.app.ui.MainScreen
import com.locctl.app.ui.theme.LocctlTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingInject = MutableStateFlow<GeoPoint?>(null)

    private val runtimePermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        Log.i(TAG, "permission result: $result")
    }

    private fun requestRuntimePermissionsIfNeeded() {
        val missing = runtimePermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            Log.i(TAG, "requesting permissions: $missing")
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingInject.value = parseInjectExtra(intent)
        requestRuntimePermissionsIfNeeded()

        setContent {
            LocctlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val pending by pendingInject.collectAsState()
                    MainScreen(
                        initialLat = pending?.lat,
                        initialLng = pending?.lng
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseInjectExtra(intent)?.let {
            pendingInject.value = it
            // 外部命令行直接注入支持
            InjectionForegroundService.start(this, it.lat, it.lng)
        }
    }

    private fun parseInjectExtra(intent: Intent?): GeoPoint? {
        val raw = intent?.getStringExtra(EXTRA_INJECT)?.trim() ?: return null
        val parts = raw.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lng = parts[1].trim().toDoubleOrNull() ?: return null
        return GeoPoint(lat, lng)
    }

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_INJECT = "inject"
    }
}
