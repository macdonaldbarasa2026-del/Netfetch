package com.netfetch.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netfetch.app.R
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.ClientDevice
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.HotspotState
import com.netfetch.app.model.NetfetchClientRegistry
import com.netfetch.app.model.TetherMode
import com.netfetch.app.network.ConnectivityTester
import com.netfetch.app.network.NetfetchUpstreamRuntime
import com.netfetch.app.network.UpstreamNetworkManager
import com.netfetch.app.network.WifiDirectManager
import com.netfetch.app.proxy.HttpProxyServer
import com.netfetch.app.proxy.PacServer
import com.netfetch.app.netfetchlink.NetfetchLinkServer
import com.netfetch.app.netfetchlink.NetfetchProviderDiscovery
import com.netfetch.app.proxy.Socks5ProxyServer
import com.netfetch.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * HotspotService — foreground service that orchestrates the full NetFetch stack.
 *
 * Startup sequence:
 *   1. Detect upstream network (Wi-Fi / Mobile Data)
 *   2. Start Wi-Fi Direct group
 *   3. Detect gateway address
 *   4. Start HTTP proxy (port 8282) with upstream binding
 *   5. Start SOCKS5 proxy (port 1080) in Pro mode with upstream binding
 *   6. Start PAC server (port 8283)
 *   7. Start client monitoring
 *   8. Start internet connectivity monitoring
 *   9. Update UI state
 *
 * Shutdown sequence:
 *   1. Stop internet monitoring
 *   2. Stop client monitoring
 *   3. Stop PAC server
 *   4. Stop SOCKS5
 *   5. Stop HTTP proxy
 *   6. Release Wi-Fi Direct group
 *   7. Release upstream network callbacks
 *   8. Release wake lock
 *   9. Update UI state
 */
class HotspotService : Service() {
    private val TAG = "NetFetchService"
    private val CHANNEL_ID = "netfetch_hotspot_channel"
    private val NOTIF_ID = 8282

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _hotspotState = MutableStateFlow<HotspotState>(HotspotState.Idle)
    val hotspotState: StateFlow<HotspotState> = _hotspotState.asStateFlow()

    private var upstreamNetworkManager: UpstreamNetworkManager? = null
    private var wifiDirectManager: WifiDirectManager? = null
    private var proxyServer: HttpProxyServer? = null
    private var socks5Server: Socks5ProxyServer? = null
    private var linkServer: NetfetchLinkServer? = null
    private var providerDiscovery: NetfetchProviderDiscovery? = null
    private var pacServer: PacServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var currentConfig = HotspotConfig()
    private var activeClients = emptyList<ClientDevice>()

    /**
     * Single authoritative registry for downstream clients.
     *
     * Wi-Fi Direct, HTTP proxy and SOCKS5 can observe the same physical
     * device through different identities. The registry reconciles them.
     */
    private val clientRegistry = NetfetchClientRegistry()
    private var currentUpSpeed = 0L
    private var currentDownSpeed = 0L
    private var currentGateway = "192.168.49.1"
    private var currentUpstreamState = UpstreamNetworkManager.UpstreamState()
    private var internetVerified = false

    private var internetMonitorJob: Job? = null
    private var upstreamMonitorJob: Job? = null
    private var gatewayStartJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): HotspotService = this@HotspotService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val bandOrdinal = intent.getIntExtra(EXTRA_BAND, BandPreference.AUTO.ordinal)
                val ssid = intent.getStringExtra(EXTRA_SSID) ?: currentConfig.ssid
                val passphrase = intent.getStringExtra(EXTRA_PASSPHRASE) ?: currentConfig.passphrase
                val port = intent.getIntExtra(EXTRA_PORT, 8282)
                val socksPort = intent.getIntExtra(EXTRA_SOCKS_PORT, 1080)
                val socksUsername = intent.getStringExtra(EXTRA_SOCKS_USERNAME) ?: currentConfig.socksUsername
                val socksPassword = intent.getStringExtra(EXTRA_SOCKS_PASSWORD) ?: currentConfig.socksPassword
                val modeOrdinal = intent.getIntExtra(EXTRA_MODE, TetherMode.NORMAL.ordinal)
                val band = BandPreference.fromOrdinal(bandOrdinal)
                val mode = TetherMode.entries.getOrElse(modeOrdinal) { TetherMode.NORMAL }

                currentConfig = currentConfig.copy(
                    ssid = ssid,
                    passphrase = passphrase,
                    bandPreference = band,
                    proxyPort = port,
                    socksPort = socksPort,
                    socksUsername = socksUsername,
                    socksPassword = socksPassword,
                    mode = mode
                )
                startHotspot(currentConfig)
            }
            ACTION_STOP -> {
                stopHotspot()
                stopSelf()
            }
        }
        return START_STICKY
    }

    fun startHotspot(config: HotspotConfig) {
        currentConfig = config
        _hotspotState.value = HotspotState.Starting
        if (!startForegroundServiceNotification("Starting NetFetch...")) {
            return
        }

        stopRunningStackForRestart()

        // 1. Start upstream network detection
        upstreamNetworkManager?.let { NetfetchUpstreamRuntime.stop(it) }
        upstreamNetworkManager = NetfetchUpstreamRuntime.start(this)

        // Monitor upstream state changes
        upstreamMonitorJob?.cancel()
        upstreamMonitorJob = serviceScope.launch {
            upstreamNetworkManager?.upstreamState?.collect { upstream ->
                currentUpstreamState = upstream
                Log.i(TAG, "Upstream state: ${upstream.displayName}")
                updateActiveState()
                // Trigger internet verification on upstream change
                verifyInternet()
            }
        }

        // 2. Start HTTP proxy (binds to upstream network)
        proxyServer = HttpProxyServer(
            port = config.proxyPort,
            upstreamNetworkProvider = { NetfetchUpstreamRuntime.currentNetwork() },
            clientAuthorizer = { address -> isClientAuthorized(address) },
            onClientActivity = { clientsMap ->
                activeClients =
                    clientRegistry.publishTrafficClients(
                        clientsMap.values.toList()
                    )

                Log.i(
                    TAG,
                    "HTTP proxy clients updated: ${activeClients.size}"
                )

                updateActiveState()
            },
            onBandwidthUpdate = { upSpeed, downSpeed, _ ->
                currentUpSpeed = upSpeed
                currentDownSpeed = downSpeed
                updateActiveState()
                updateNotification()
            }
        ).also { it.start() }

        // 3. Start NetFetch session-link server in BOTH Normal and Pro modes.
        //    Normal mode uses it to share HTTP proxy and PAC details automatically.
        //    Pro mode also uses it for SOCKS5 session token issuance.
        linkServer = NetfetchLinkServer(
            mode = if (config.mode == TetherMode.PRO)
                com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_PRO
            else
                com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_NORMAL,
            socksPort = config.socksPort,
            httpPort = config.proxyPort,
            pacPort = config.pacPort
        ).also { it.start() }

        // 4. Start SOCKS5 proxy server (available for companion receivers and SOCKS5 clients)
        socks5Server = Socks5ProxyServer(
            socksPort = config.socksPort,
            username = config.socksUsername,
            password = config.socksPassword,
            sessionValidator = { token, address ->
                // Allow all local hotspot clients or validated NetFetch sessions
                address.startsWith("192.168.") || address.startsWith("10.") || address == "127.0.0.1" ||
                    linkServer?.validateSession(token, address) == true
            },
            upstreamNetworkProvider = { NetfetchUpstreamRuntime.currentNetwork() },
            onClientActivity = { clientsMap ->
                activeClients =
                    clientRegistry.publishTrafficClients(
                        clientsMap.values.toList()
                    )

                Log.i(
                    TAG,
                    "SOCKS5 clients updated: ${activeClients.size}"
                )

                updateActiveState()
            },
            onBandwidthUpdate = { _, _, _ -> }
        ).also { it.start() }

        // 5. Start PAC server (initially with default gateway; updated after Wi-Fi Direct starts)
        startPacServer(config, currentGateway)

        // 6. Start Wi-Fi Direct group
        wifiDirectManager = WifiDirectManager(
            context = this,
            onGroupInfoAvailable = { group, ssid, passphrase, gateway ->
                currentGateway = gateway
                currentConfig = currentConfig.copy(
                    ssid = ssid,
                    passphrase = passphrase,
                    hostIp = gateway
                )
                // Restart PAC server with correct gateway address
                pacServer?.stop()
                startPacServer(currentConfig, gateway)

                // Advertise provider via DNS-SD for both Normal and Pro modes.
                providerDiscovery?.stop()
                providerDiscovery = NetfetchProviderDiscovery(this@HotspotService).also {
                    it.start(
                        ssid = ssid,
                        mode = if (currentConfig.mode == TetherMode.PRO)
                            com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_PRO
                        else
                            com.netfetch.app.netfetchlink.NetfetchLinkProtocol.MODE_NORMAL,
                        socksPort = currentConfig.socksPort,
                        httpPort = currentConfig.proxyPort,
                        pacPort = currentConfig.pacPort
                    )
                }

                updateActiveState()
                updateNotification()
            },
            onClientsChanged = { p2pClients ->
                activeClients =
                    clientRegistry.publishP2pClients(
                        p2pClients
                    )

                Log.i(
                    TAG,
                    "Wi-Fi Direct clients updated: ${activeClients.size}"
                )

                updateActiveState()
            },
            onError = { errorMsg ->
                _hotspotState.value = HotspotState.Error(errorMsg)
                updateNotification("Error: $errorMsg")
            }
        )
        wifiDirectManager?.startGroup(currentConfig)

        // 7. Wait for a selected and verified upstream before
        // starting the real IPv4 TUN gateway.
        gatewayStartJob?.cancel()
        gatewayStartJob = serviceScope.launch {
            val manager = upstreamNetworkManager
            if (manager == null) {
                Log.e(TAG, "Cannot start gateway: upstream manager is null")
                return@launch
            }

            manager.upstreamState.first { state ->
                state.network != null && state.hasInternet
            }

            if (!isActive) return@launch

            Log.i(TAG, "Verified upstream is ready; provider proxy services can accept traffic")

            // 7. Periodic internet verification
            startInternetMonitor()
        }
    }

    private fun isClientAuthorized(address: String): Boolean {
        if (address.isBlank()) return false
        // Check if explicitly blocked in client registry
        val client = clientRegistry.snapshot().find { it.ipAddress == address }
        if (client?.isBlocked == true) return false

        // Allow all devices on the local hotspot Wi-Fi network
        return true
    }

    private fun startPacServer(config: HotspotConfig, gateway: String) {
        pacServer = PacServer(
            pacPort = config.pacPort,
            proxyHost = gateway,
            proxyPort = config.proxyPort,
            clientAuthorizer = { address -> isClientAuthorized(address) }
        ).also { it.start() }
    }

    private fun stopRunningStackForRestart() {
        internetMonitorJob?.cancel()
        internetMonitorJob = null

        upstreamMonitorJob?.cancel()
        upstreamMonitorJob = null

        gatewayStartJob?.cancel()
        gatewayStartJob = null

        stopVpnGateway()
        stopProxyServers()

        wifiDirectManager?.stopGroup()
        wifiDirectManager = null

        upstreamNetworkManager?.let { NetfetchUpstreamRuntime.stop(it) }
        upstreamNetworkManager = null

        clientRegistry.clear()
        activeClients = emptyList()
        currentUpSpeed = 0L
        currentDownSpeed = 0L
        internetVerified = false
        currentGateway = "192.168.49.1"
        currentUpstreamState = UpstreamNetworkManager.UpstreamState()
    }

    private fun startInternetMonitor() {
        internetMonitorJob?.cancel()
        internetMonitorJob = serviceScope.launch {
            while (isActive) {
                verifyInternet()
                delay(30_000) // Re-check every 30 seconds
            }
        }
    }

    private fun verifyInternet() {
        serviceScope.launch(Dispatchers.IO) {
            val network = upstreamNetworkManager?.currentNetwork
            val result = ConnectivityTester.testConnectivity(network)

            if (!result && network != null) {
                Log.w(
                    TAG,
                    "Internet verification failed for $network, requesting upstream recovery"
                )
                upstreamNetworkManager?.reportConnectivityFailure(network)
            }

            if (result != internetVerified) {
                internetVerified = result
                Log.i(TAG, "Internet verified: $result")
                withContext(Dispatchers.Main) {
                    updateActiveState()
                }
            }
        }
    }

    fun stopHotspot() {
        internetMonitorJob?.cancel()
        internetMonitorJob = null

        upstreamMonitorJob?.cancel()
        upstreamMonitorJob = null

        gatewayStartJob?.cancel()
        gatewayStartJob = null

        stopVpnGateway()
        stopProxyServers()

        wifiDirectManager?.stopGroup()
        clientRegistry.clear()
        wifiDirectManager = null

        upstreamNetworkManager?.let { NetfetchUpstreamRuntime.stop(it) }
        upstreamNetworkManager = null

        activeClients = emptyList()
        currentUpSpeed = 0L
        currentDownSpeed = 0L
        internetVerified = false
        currentGateway = "192.168.49.1"

        _hotspotState.value = HotspotState.Idle
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun stopProxyServers() {
        // Stop DNS-SD advertisement first so no new receivers attempt to connect
        // while the session and proxy servers are shutting down.
        providerDiscovery?.stop()
        linkServer?.stop()
        pacServer?.stop()
        socks5Server?.stop()
        proxyServer?.stop()
        proxyServer = null
        socks5Server = null
        linkServer = null
        providerDiscovery = null
        pacServer = null
    }

    private fun updateActiveState() {
        _hotspotState.value = HotspotState.Active(
            config = currentConfig,
            connectedClients = activeClients,
            downloadSpeedBps = currentDownSpeed,
            uploadSpeedBps = currentUpSpeed,
            upstreamState = currentUpstreamState,
            internetVerified = internetVerified,
            gatewayAddress = currentGateway
        )
    }

    private fun startForegroundServiceNotification(title: String): Boolean {
        val notification = buildNotification(title, "Initialising network & proxy engine...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Unable to promote HotspotService to foreground", e)
            _hotspotState.value = HotspotState.Error(
                "NetFetch could not start its foreground network service: ${e.message}"
            )
            stopSelf()
            false
        }
    }

    private fun updateNotification(customText: String? = null) {
        val modeStr = if (currentConfig.mode == TetherMode.PRO) "Pro" else "Normal"
        val text = customText ?: run {
            val count = activeClients.size
            val downSpeedKb = currentDownSpeed / 1024
            val upSpeedKb = currentUpSpeed / 1024
            val internetStr = if (internetVerified) "✓ Internet" else "No Internet"
            "[$modeStr | $internetStr] Clients: $count | ↓${downSpeedKb}KB/s ↑${upSpeedKb}KB/s"
        }
        val notification = buildNotification("NetFetch Active (${currentConfig.ssid})", text)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIF_ID, notification)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HotspotService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "NetFetch Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "NetFetch Wi-Fi Direct Hotspot & Proxy Background Service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NetFetch::HotspotWakeLock"
        ).apply {
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopHotspot()
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceScope.cancel()
    }

    companion object {
        const val ACTION_START = "com.netfetch.action.START"
        const val ACTION_STOP = "com.netfetch.action.STOP"

        const val EXTRA_SOCKS_PORT = "com.netfetch.app.extra.SOCKS_PORT"
        const val EXTRA_SOCKS_USERNAME = "com.netfetch.app.extra.SOCKS_USERNAME"
        const val EXTRA_SOCKS_PASSWORD = "com.netfetch.app.extra.SOCKS_PASSWORD"
        const val EXTRA_SSID = "com.netfetch.app.extra.SSID"
        const val EXTRA_PASSPHRASE = "com.netfetch.app.extra.PASSPHRASE"
        const val EXTRA_BAND = "extra_band"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_MODE = "extra_mode"
    }

    private fun startVpnGateway() {
        val intent = Intent(this, VpnGatewayService::class.java).apply {
            action = VpnGatewayService.ACTION_START
        }

        // Android's VpnService lifecycle starts the VPN with startService().
        // The VPN service then promotes itself to the foreground.
        startService(intent)
    }

    private fun stopVpnGateway() {
        val intent = Intent(this, VpnGatewayService::class.java).apply {
            action = VpnGatewayService.ACTION_STOP
        }
        startService(intent)
    }

}
