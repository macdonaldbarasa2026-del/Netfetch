package com.netfetch.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netfetch.app.gateway.net.NetfetchDirectConnector
import com.netfetch.app.gateway.net.NetfetchSocketProtector
import com.netfetch.app.gateway.net.NetfetchTunEngine
import com.netfetch.app.network.NetfetchUpstreamRuntime

class VpnGatewayService : VpnService() {

    companion object {
        private const val TAG = "NetfetchVpnGateway"

        const val ACTION_START =
            "com.netfetch.app.action.START_VPN"

        const val ACTION_STOP =
            "com.netfetch.app.action.STOP_VPN"

        private const val VPN_ADDRESS = "10.77.0.2"
        private const val VPN_PREFIX = 32
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX = 0
        private const val VPN_MTU = 1500

        private const val NOTIFICATION_CHANNEL = "netfetch_gateway"
        private const val NOTIFICATION_ID = 8282
    }

    private var tunEngine: NetfetchTunEngine? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START,
            null -> {
                startGateway()
            }
        }

        return START_STICKY
    }

    private fun startGateway() {
        if (tunEngine != null) {
            Log.i(TAG, "VPN gateway already running")
            return
        }

        try {
            startForegroundGatewayNotification()

            val upstreamManager = NetfetchUpstreamRuntime.get()

            if (upstreamManager == null) {
                throw IllegalStateException(
                    "Netfetch upstream manager is not running"
                )
            }

            if (upstreamManager.currentNetwork == null) {
                Log.w(
                    TAG,
                    "VPN starting before an upstream network is selected; waiting for upstream"
                )
            }

            val prepareIntent = prepare(this)

            if (prepareIntent != null) {
                throw IllegalStateException(
                    "VPN permission has not been granted"
                )
            }

            val tunInterface =
                Builder()
                    .setSession("Netfetch Gateway")
                    .setMtu(VPN_MTU)
                    .addAddress(VPN_ADDRESS, VPN_PREFIX)
                    .addRoute(VPN_ROUTE, VPN_ROUTE_PREFIX)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .establish()
                    ?: throw IllegalStateException(
                        "Android failed to establish the VPN interface"
                    )

            val protector =
                NetfetchSocketProtector(
                    vpnService = this,
                    networkProvider = {
                        NetfetchUpstreamRuntime.currentNetwork()
                    }
                )

            val connector =
                NetfetchDirectConnector(
                    socketProtector = protector
                )

            tunEngine =
                NetfetchTunEngine(
                    tunInterface = tunInterface,
                    connector = connector
                ).also {
                    it.start()
                }

            Log.i(TAG, "Netfetch TUN gateway started")
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start TUN gateway", e)
            stopGateway()
            stopSelf()
        }
    }

    private fun startForegroundGatewayNotification() {
        val notificationManager =
            getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Netfetch Gateway",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Netfetch internet gateway status"
            }

            notificationManager.createNotificationChannel(channel)
        }

        val notification =
            NotificationCompat.Builder(
                this,
                NOTIFICATION_CHANNEL
            )
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("Netfetch Gateway")
                .setContentText("Internet gateway is running")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun stopGateway() {
        Log.i(TAG, "Stopping Netfetch TUN gateway")

        runCatching {
            tunEngine?.stop()
        }

        tunEngine = null
    }

    override fun onRevoke() {
        Log.i(TAG, "VPN permission revoked")
        stopGateway()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopGateway()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return super.onBind(intent)
    }

    fun tcpSessionCount(): Int =
        tunEngine?.tcpSessionCount() ?: 0

    fun udpSessionCount(): Int =
        tunEngine?.udpSessionCount() ?: 0
}
