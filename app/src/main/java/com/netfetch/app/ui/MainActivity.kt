package com.netfetch.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.HotspotState
import com.netfetch.app.model.TetherMode
import com.netfetch.app.service.HotspotService
import com.netfetch.app.service.NetfetchReceiverVpnService
import com.netfetch.app.netfetchlink.NetfetchReceiverLink
import com.netfetch.app.netfetchlink.NetfetchReceiverState
import com.netfetch.app.ui.screens.DevicesScreen
import com.netfetch.app.ui.screens.HelpScreen
import com.netfetch.app.ui.screens.HomeScreen
import com.netfetch.app.ui.screens.SettingsScreen
import com.netfetch.app.ui.theme.CreamBackground
import com.netfetch.app.ui.theme.NetFetchTheme
import com.netfetch.app.ui.theme.PrimaryBlack
import com.netfetch.app.ui.theme.TextMuted

class MainActivity : ComponentActivity() {

    private lateinit var preferences: SharedPreferences

    private fun loadSavedConfig(): HotspotConfig {
        val prefs = preferences

        val bandOrdinal = prefs.getInt(
            "band_preference",
            BandPreference.AUTO.ordinal
        )

        val band = BandPreference.fromOrdinal(bandOrdinal)

        return HotspotConfig(
            ssid = prefs.getString("wifi_ssid", "DIRECT-NetFetch-AccessPoint")
                ?: "DIRECT-NetFetch-AccessPoint",
            passphrase = prefs.getString("wifi_passphrase", "82828282")
                ?: "82828282",
            proxyPort = prefs.getInt("proxy_port", 8282),
            socksPort = prefs.getInt("socks_port", 1080),
            socksUsername = prefs.getString("socks_username", "netfetch") ?: "netfetch",
            socksPassword = prefs.getString("socks_password", "netfetch1080") ?: "netfetch1080",
            mode = TetherMode.entries.getOrElse(prefs.getInt("mode", TetherMode.NORMAL.ordinal)) { TetherMode.NORMAL },
            udpForwarding = prefs.getBoolean("udp_forwarding", false),
            bandPreference = band,
            maxConnectedClients = prefs.getInt("max_clients", 10)
        )
    }

    private fun saveConfig(config: HotspotConfig) {
        preferences.edit()
            .putString("wifi_ssid", config.ssid)
            .putString("wifi_passphrase", config.passphrase)
            .putInt("proxy_port", config.proxyPort)
            .putInt("socks_port", config.socksPort)
            .putString("socks_username", config.socksUsername)
            .putString("socks_password", config.socksPassword)
            .putInt("mode", config.mode.ordinal)
            .putBoolean("udp_forwarding", config.udpForwarding)
            .putInt("band_preference", config.bandPreference.ordinal)
            .putInt("max_clients", config.maxConnectedClients)
            .apply()
    }

    private var hotspotService: HotspotService? = null
    private var isBound = false

    private val receiverStateFlow =
        mutableStateOf<NetfetchReceiverState>(NetfetchReceiverState.Idle)

    private var receiverLink: NetfetchReceiverLink? = null
    private var pendingReceiverConnection:
        NetfetchReceiverState.Connected? = null

    private val hotspotStateFlow = mutableStateOf<HotspotState>(HotspotState.Idle)
    private var pendingHotspotConfig: HotspotConfig? = null

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val config = pendingHotspotConfig
            pendingHotspotConfig = null

            if (result.resultCode == RESULT_OK) {
                startHotspotServiceAfterVpnPermission(config ?: configStateFlow.value)
            } else if (config != null) {
                hotspotStateFlow.value = HotspotState.Error(
                    "Provider VPN permission was not granted."
                )
            }
        }
    private val receiverVpnPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val connection = pendingReceiverConnection
            pendingReceiverConnection = null

            if (result.resultCode == RESULT_OK && connection != null) {
                startReceiverVpn(connection)
            } else if (connection != null) {
                receiverStateFlow.value =
                    NetfetchReceiverState.Error(
                        "Receiver VPN permission was not granted."
                    )
            }
        }

    private val configStateFlow = mutableStateOf(HotspotConfig())

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as HotspotService.LocalBinder
            hotspotService = binder.getService()
            isBound = true

            lifecycleScope.launch {
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

    private val requestReceiverPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            checkVpnAndStartReceiver()
        } else {
            android.widget.Toast.makeText(
                this,
                "Wi-Fi & Nearby device permissions are required to scan for NetFetch providers.",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = getSharedPreferences("netfetch_settings", Context.MODE_PRIVATE)
        configStateFlow.value = loadSavedConfig()

        receiverLink = NetfetchReceiverLink(
            context = this,
            requestedMode = {
                if (configStateFlow.value.mode == TetherMode.PRO) {
                    com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_PRO
                } else {
                    com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_NORMAL
                }
            },
            onStateChanged = { receiverState ->
                runOnUiThread {
                    receiverStateFlow.value = receiverState

                    when (receiverState) {
                        is NetfetchReceiverState.Connected -> {
                            if (receiverState.socksPort > 0) {
                                // SOCKS5 is available on the provider: tunnel device traffic via VPN
                                requestReceiverVpn(receiverState)
                            } else {
                                // Fallback: HTTP / PAC proxy mode
                                stopReceiverVpn()
                            }
                        }

                        NetfetchReceiverState.Idle,
                        NetfetchReceiverState.Searching,
                        is NetfetchReceiverState.ProviderFound,
                        NetfetchReceiverState.Connecting,
                        NetfetchReceiverState.Authenticating,
                        is NetfetchReceiverState.Reconnecting,
                        is NetfetchReceiverState.Unsupported,
                        is NetfetchReceiverState.Error -> {
                            stopReceiverVpn()
                        }
                    }
                }
            }
        )

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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CreamBackground)
                        ) {
                            NavigationBar(
                                windowInsets = WindowInsets(0, 0, 0, 0),
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
                        NetfetchWatermark()
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
                                    if (config.mode == mode) return@HomeScreen
                                    // A receiver link is mode-specific. Tear it down before
                                    // changing the source of truth so a stale VPN/proxy path
                                    // cannot remain active under the new label.
                                    stopReceiverConnection()
                                    config = config.copy(mode = mode)
                                    configStateFlow.value = config
                                    saveConfig(config)
                                    if (mode == TetherMode.PRO) {
                                        val vpnIntent = VpnService.prepare(this@MainActivity)
                                        if (vpnIntent != null) {
                                            pendingHotspotConfig = config
                                            vpnPermissionLauncher.launch(vpnIntent)
                                        }
                                    }
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
                                receiverState = receiverStateFlow.value,
                                onStartReceiver = { checkPermissionsAndStartReceiver() },
                                onStopReceiver = { stopReceiverConnection() },
                                onNavigateToDevices = { navController.navigate("devices") }
                            )
                        }
                        composable("devices") {
                            val clients = if (state is HotspotState.Active) (state as HotspotState.Active).connectedClients else emptyList()
                            DevicesScreen(connectedClients = clients, config = config)
                        }
                        composable("settings") {
                            val isVpnGranted = VpnService.prepare(this@MainActivity) == null
                            SettingsScreen(
                                config = config,
                                isVpnGranted = isVpnGranted,
                                onRequestVpnPermission = {
                                    val vpnIntent = VpnService.prepare(this@MainActivity)
                                    if (vpnIntent != null) {
                                        vpnPermissionLauncher.launch(vpnIntent)
                                    }
                                },
                                onUpdateConfig = { newConfig ->
                                    config = newConfig
                                    configStateFlow.value = newConfig
                                    saveConfig(newConfig)

                                    if (state is HotspotState.Active) {
                                        startHotspotService(newConfig)
                                    }
                                }
                            )
                        }
                        composable("help") {
                            HelpScreen(config = config)
                        }
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndToggle() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
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
        if (config.mode == TetherMode.PRO) {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                pendingHotspotConfig = config
                vpnPermissionLauncher.launch(vpnIntent)
                return
            }
        }
        startHotspotServiceAfterVpnPermission(config)
    }

    private fun startHotspotServiceAfterVpnPermission(config: HotspotConfig) {
        val intent = Intent(this, HotspotService::class.java).apply {
            action = HotspotService.ACTION_START
            putExtra(HotspotService.EXTRA_SSID, config.ssid)
            putExtra(HotspotService.EXTRA_PASSPHRASE, config.passphrase)
            putExtra(HotspotService.EXTRA_BAND, config.bandPreference.ordinal)
            putExtra(HotspotService.EXTRA_PORT, config.proxyPort)
            putExtra(HotspotService.EXTRA_SOCKS_PORT, config.socksPort)
            putExtra(HotspotService.EXTRA_SOCKS_USERNAME, config.socksUsername)
            putExtra(HotspotService.EXTRA_SOCKS_PASSWORD, config.socksPassword)
            putExtra(HotspotService.EXTRA_MODE, config.mode.ordinal)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun checkPermissionsAndStartReceiver() {
        val requiredPermissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestReceiverPermissionLauncher.launch(missing.toTypedArray())
        } else {
            checkVpnAndStartReceiver()
        }
    }

    private fun checkVpnAndStartReceiver() {
        if (configStateFlow.value.mode == TetherMode.PRO) {
            val vpnIntent = VpnService.prepare(this)
            if (vpnIntent != null) {
                receiverVpnPermissionLauncher.launch(vpnIntent)
                return
            }
        }
        startReceiverDiscovery()
    }

    private fun startReceiverDiscovery() {
        receiverLink?.stop()
        receiverStateFlow.value = NetfetchReceiverState.Searching
        receiverLink?.start()
    }

    private fun stopReceiverConnection() {
        receiverLink?.stop()

        val intent = Intent(
            this,
            NetfetchReceiverVpnService::class.java
        ).apply {
            action = NetfetchReceiverVpnService.ACTION_STOP
        }

        startService(intent)

        pendingReceiverConnection = null
        receiverStateFlow.value = NetfetchReceiverState.Idle
    }

    private fun stopReceiverVpn() {
        val intent = Intent(
            this,
            NetfetchReceiverVpnService::class.java
        ).apply {
            action = NetfetchReceiverVpnService.ACTION_STOP
        }

        runCatching {
            startService(intent)
        }.onFailure { error ->
            Log.w(
                "NetFetch",
                "Unable to stop receiver VPN: ${error.message}"
            )
        }

        pendingReceiverConnection = null
    }

    private fun requestReceiverVpn(
        connection: NetfetchReceiverState.Connected
    ) {
        pendingReceiverConnection = connection

        val vpnIntent = VpnService.prepare(this)

        if (vpnIntent != null) {
            receiverVpnPermissionLauncher.launch(vpnIntent)
        } else {
            startReceiverVpn(connection)
        }
    }

    private fun startReceiverVpn(
        connection: NetfetchReceiverState.Connected
    ) {
        val intent = Intent(
            this,
            NetfetchReceiverVpnService::class.java
        ).apply {
            action = NetfetchReceiverVpnService.ACTION_START
            putExtra(
                NetfetchReceiverVpnService.EXTRA_PROVIDER_HOST,
                connection.providerAddress
            )
            putExtra(
                NetfetchReceiverVpnService.EXTRA_PROVIDER_PORT,
                connection.socksPort
            )
            putExtra(
                NetfetchReceiverVpnService.EXTRA_USERNAME,
                "netfetch-session"
            )
            putExtra(
                NetfetchReceiverVpnService.EXTRA_PASSWORD,
                connection.sessionToken
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
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
