package com.netfetch.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.HotspotState
import com.netfetch.app.service.HotspotService
import com.netfetch.app.ui.screens.DevicesScreen
import com.netfetch.app.ui.screens.HelpScreen
import com.netfetch.app.ui.screens.HomeScreen
import com.netfetch.app.ui.screens.SettingsScreen
import com.netfetch.app.ui.theme.CreamBackground
import com.netfetch.app.ui.theme.NetFetchTheme
import com.netfetch.app.ui.theme.PrimaryBlack
import com.netfetch.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    private var hotspotService: HotspotService? = null
    private var isBound = false

    private val hotspotStateFlow = mutableStateOf<HotspotState>(HotspotState.Idle)
    private val configStateFlow = mutableStateOf(HotspotConfig())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HotspotService.LocalBinder
            hotspotService = binder.getService()
            isBound = true

            lifecycleScopeLaunch {
                hotspotService?.hotspotState?.collect { state ->
                    hotspotStateFlow.value = state
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            hotspotService = null
            isBound = false
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            toggleHotspot()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Intent(this, HotspotService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }

        setContent {
            NetFetchTheme {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route ?: "home"

                val state by hotspotStateFlow
                var config by configStateFlow

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = CreamBackground,
                            contentColor = PrimaryBlack
                        ) {
                            NavigationBarItem(
                                selected = currentRoute == "home",
                                onClick = { navController.navigate("home") { popUpTo("home") { inclusive = true } } },
                                icon = { Icon(Icons.Default.WifiTethering, contentDescription = "Home") },
                                label = { Text("Home") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlack,
                                    selectedTextColor = PrimaryBlack,
                                    indicatorColor = PrimaryBlack.copy(alpha = 0.1f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                            NavigationBarItem(
                                selected = currentRoute == "devices",
                                onClick = { navController.navigate("devices") },
                                icon = {
                                    val count = if (state is HotspotState.Active) (state as HotspotState.Active).connectedClients.size else 0
                                    BadgedBox(badge = { if (count > 0) Badge { Text("$count") } }) {
                                        Icon(Icons.Default.Devices, contentDescription = "Devices")
                                    }
                                },
                                label = { Text("Devices") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlack,
                                    selectedTextColor = PrimaryBlack,
                                    indicatorColor = PrimaryBlack.copy(alpha = 0.1f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                            NavigationBarItem(
                                selected = currentRoute == "settings",
                                onClick = { navController.navigate("settings") },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlack,
                                    selectedTextColor = PrimaryBlack,
                                    indicatorColor = PrimaryBlack.copy(alpha = 0.1f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                            NavigationBarItem(
                                selected = currentRoute == "help",
                                onClick = { navController.navigate("help") },
                                icon = { Icon(Icons.Default.HelpOutline, contentDescription = "Help") },
                                label = { Text("Help") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = PrimaryBlack,
                                    selectedTextColor = PrimaryBlack,
                                    indicatorColor = PrimaryBlack.copy(alpha = 0.1f),
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted
                                )
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                state = state,
                                config = config,
                                onToggleHotspot = { checkPermissionsAndToggle() },
                                onModeChange = { mode ->
                                    config = config.copy(mode = mode)
                                    if (state is HotspotState.Active) {
                                        startHotspotService(config)
                                    }
                                },
                                onBandChange = { band ->
                                    config = config.copy(bandPreference = band)
                                    if (state is HotspotState.Active) {
                                        startHotspotService(config)
                                    }
                                },
                                onNavigateToDevices = { navController.navigate("devices") }
                            )
                        }
                        composable("devices") {
                            val clients = if (state is HotspotState.Active) (state as HotspotState.Active).connectedClients else emptyList()
                            DevicesScreen(connectedClients = clients)
                        }
                        composable("settings") {
                            SettingsScreen(
                                config = config,
                                onUpdateConfig = { newConfig ->
                                    config = newConfig
                                    if (state is HotspotState.Active) {
                                        startHotspotService(newConfig)
                                    }
                                }
                            )
                        }
                        composable("help") {
                            HelpScreen()
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndToggle() {
        val requiredPermissions = mutableListOf<String>()
        requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            toggleHotspot()
        }
    }

    private fun toggleHotspot() {
        if (hotspotStateFlow.value is HotspotState.Active || hotspotStateFlow.value is HotspotState.Starting) {
            val intent = Intent(this, HotspotService::class.java).apply {
                action = HotspotService.ACTION_STOP
            }
            startService(intent)
        } else {
            startHotspotService(configStateFlow.value)
        }
    }

    private fun startHotspotService(config: HotspotConfig) {
        val intent = Intent(this, HotspotService::class.java).apply {
            action = HotspotService.ACTION_START
            putExtra(HotspotService.EXTRA_BAND, config.bandPreference.ordinal)
            putExtra(HotspotService.EXTRA_PORT, config.proxyPort)
            putExtra(HotspotService.EXTRA_MODE, config.mode.ordinal)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun lifecycleScopeLaunch(block: suspend () -> Unit) {
        kotlinx.coroutines.MainScope().launch {
            block()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
