package com.netfetch.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.netfetch.app.gateway.net.NetfetchReceiverConnector
import com.netfetch.app.gateway.net.NetfetchTunEngine

class NetfetchReceiverVpnService : VpnService() {

    companion object {
        private const val TAG = "NetFetchReceiverVpn"

        const val ACTION_START =
            "com.netfetch.app.action.START_RECEIVER_VPN"

        const val ACTION_STOP =
            "com.netfetch.app.action.STOP_RECEIVER_VPN"

        const val EXTRA_PROVIDER_HOST =
            "provider_host"

        const val EXTRA_PROVIDER_PORT =
            "provider_port"

        const val EXTRA_USERNAME =
            "provider_username"

        const val EXTRA_PASSWORD =
            "provider_password"

        private const val VPN_ADDRESS = "10.78.0.2"
        private const val VPN_PREFIX = 32

        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_ROUTE_PREFIX = 0

        private const val VPN_MTU = 1500

        private const val CHANNEL_ID =
            "netfetch_receiver_vpn"

        private const val NOTIFICATION_ID = 8283
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunEngine: NetfetchTunEngine? = null

    @Volatile
    private var running = false

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopReceiverVpn()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START,
            null -> {
                startReceiverVpn(intent)
            }
        }

        return START_STICKY
    }

    private fun startReceiverVpn(intent: Intent?) {
        if (running) {
            Log.i(TAG, "Receiver VPN already running")
            return
        }

        try {
            val providerHost =
                intent?.getStringExtra(EXTRA_PROVIDER_HOST)
                    ?.trim()

            val providerPort =
                intent?.getIntExtra(
                    EXTRA_PROVIDER_PORT,
                    1080
                ) ?: 1080

            val username =
                intent?.getStringExtra(EXTRA_USERNAME)
                    ?.trim()

            val password =
                intent?.getStringExtra(EXTRA_PASSWORD)

            require(!providerHost.isNullOrBlank()) {
                "Provider address is missing"
            }

            require(providerPort in 1..65535) {
                "Invalid provider SOCKS port"
            }

            require(!username.isNullOrBlank()) {
                "Provider username is missing"
            }

            require(!password.isNullOrEmpty()) {
                "Provider password is missing"
            }

            startForegroundNotification()

            if (prepare(this) != null) {
                throw IllegalStateException(
                    "NetFetch receiver VPN permission has not been granted"
                )
            }

            val builder =
                Builder()
                    .setSession("NetFetch Receiver")
                    .setMtu(VPN_MTU)
                    .addAddress(
                        VPN_ADDRESS,
                        VPN_PREFIX
                    )
                    .addRoute(
                        VPN_ROUTE,
                        VPN_ROUTE_PREFIX
                    )
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")

            tunInterface =
                builder.establish()
                    ?: throw IllegalStateException(
                        "Android failed to establish receiver VPN"
                    )

            val connector =
                NetfetchReceiverConnector(
                    vpnService = this,
                    providerHost = providerHost,
                    providerPort = providerPort,
                    username = username,
                    password = password
                )

            val engine =
                NetfetchTunEngine(
                    tunInterface = tunInterface!!,
                    connector = connector,
                    enableUdp = false
                )

            tunEngine = engine
            running = true
            engine.start()

            Log.i(
                TAG,
                "Receiver VPN active: provider=$providerHost:$providerPort TCP=true UDP=false"
            )
        } catch (e: Exception) {
            Log.e(
                TAG,
                "Unable to start receiver VPN",
                e
            )

            stopReceiverVpn()
            stopSelf()
        }
    }

    private fun startForegroundNotification() {
        val notificationManager =
            getSystemService(
                NotificationManager::class.java
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "NetFetch Receiver",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification =
            NotificationCompat.Builder(
                this,
                CHANNEL_ID
            )
                .setContentTitle("NetFetch Receiver")
                .setContentText(
                    "Connected through a NetFetch provider"
                )
                .setSmallIcon(
                    android.R.drawable.stat_sys_upload
                )
                .setOngoing(true)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )
    }

    private fun stopReceiverVpn() {
        running = false

        Log.i(
            TAG,
            "Stopping NetFetch receiver VPN"
        )

        runCatching {
            tunEngine?.stop()
        }

        tunEngine = null

        runCatching {
            tunInterface?.close()
        }

        tunInterface = null
    }

    override fun onRevoke() {
        Log.i(
            TAG,
            "Receiver VPN permission revoked"
        )

        stopReceiverVpn()
        stopSelf()

        super.onRevoke()
    }

    override fun onDestroy() {
        stopReceiverVpn()
        super.onDestroy()
    }

    override fun onBind(
        intent: Intent
    ): IBinder? {
        return super.onBind(intent)
    }
}
