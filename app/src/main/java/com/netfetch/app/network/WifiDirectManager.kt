package com.netfetch.app.network

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.util.Log
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import java.lang.reflect.Method
import java.net.NetworkInterface

/**
 * Wi-Fi Direct Manager
 *
 * Creates and manages the Wi-Fi P2P group that other devices connect to.
 * Reports the actual gateway address by querying the p2p network interface.
 *
 * Important:
 * - Wi-Fi Direct creates a separate network interface (typically p2p0 or p2p-wlan0-*)
 * - The group owner address is typically 192.168.49.1 on most Android devices,
 *   but we detect it from the actual interface rather than assuming it
 * - Not all Android devices support simultaneous Wi-Fi upstream + Wi-Fi Direct.
 *   When not supported, the upstream reverts to mobile data.
 */
class WifiDirectManager(
    private val context: Context,
    private val onGroupInfoAvailable: (WifiP2pGroup?, String, String, String) -> Unit,
    private val onError: (String) -> Unit
) {
    private var activePassphrase: String = "82828282"
    private val TAG = "NetFetchP2P"

    // Fallback gateway if we cannot detect the actual one
    private val FALLBACK_GATEWAY = "192.168.49.1"

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private var receiver: BroadcastReceiver? = null
    var currentGroup: WifiP2pGroup? = null
        private set

    @Volatile
    private var groupStarting = false

    private var groupInfoRetryCount = 0

    private val groupInfoRetryDelaysMs =
        longArrayOf(500L, 1000L, 2000L, 4000L, 6000L)

    init {
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
    }

    @SuppressLint("MissingPermission")
    fun startGroup(config: HotspotConfig) {
        if (wifiP2pManager == null || channel == null) {
            onError("Wi-Fi Direct (P2P) is not supported on this device.")
            return
        }

        registerReceiver()

        groupStarting = true
        groupInfoRetryCount = 0
        currentGroup = null

        Log.i(
            TAG,
            "Starting Wi-Fi Direct group: ssid=${config.ssid} " +
                "band=${config.bandPreference}"
        )

        // Remove any previous group before creating the new one.
        wifiP2pManager.removeGroup(
            channel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    createNewGroup(config)
                }

                override fun onFailure(reason: Int) {
                    // No existing group is also a valid starting condition.
                    createNewGroup(config)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun createNewGroup(config: HotspotConfig) {
        activePassphrase = config.passphrase

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val p2pConfigBuilder = WifiP2pConfig.Builder()
                .setNetworkName(config.ssid)
                .setPassphrase(config.passphrase)

            when (config.bandPreference) {
                BandPreference.BAND_2GHZ -> p2pConfigBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_2GHZ)
                BandPreference.BAND_5GHZ -> p2pConfigBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_5GHZ)
                BandPreference.AUTO -> p2pConfigBuilder.setGroupOperatingBand(WifiP2pConfig.GROUP_OWNER_BAND_AUTO)
            }

            try {
                wifiP2pManager?.createGroup(channel ?: return, p2pConfigBuilder.build(), object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        Log.i(TAG, "Wi-Fi Direct group created successfully via Builder API")
                        fetchGroupDetails()
                    }

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "Custom group creation failed ($reason), falling back to standard createGroup")
                        fallbackCreateGroup(config)
                    }
                })
                return
            } catch (e: Exception) {
                Log.e(TAG, "Error using WifiP2pConfig Builder: ${e.message}")
            }
        }

        fallbackCreateGroup(config)
    }

    @SuppressLint("MissingPermission")
    private fun fallbackCreateGroup(config: HotspotConfig) {
        tryApplyBandReflection(config.bandPreference)

        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct group created successfully")
                fetchGroupDetails()
            }

            override fun onFailure(reasonCode: Int) {
                val errorStr = when (reasonCode) {
                    WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct is unsupported on this device."
                    WifiP2pManager.BUSY -> "Wi-Fi Direct system service is busy. Please try again."
                    WifiP2pManager.ERROR -> "Internal Wi-Fi Direct system error occurred."
                    else -> "Failed to start Wi-Fi Direct group (Code $reasonCode)"
                }
                Log.e(TAG, errorStr)
                onError(errorStr)
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun fetchGroupDetails() {
        val manager = wifiP2pManager ?: return
        val p2pChannel = channel ?: return

        manager.requestGroupInfo(p2pChannel) { group ->
            if (group != null) {
                currentGroup = group
                groupStarting = false
                groupInfoRetryCount = 0

                val ssid =
                    group.networkName
                        ?: "DIRECT-NetFetch-AccessPoint"

                val passphrase =
                    group.passphrase
                        ?: activePassphrase

                val gateway =
                    detectGatewayAddress(ssid)

                Log.i(
                    TAG,
                    "Wi-Fi Direct group READY - " +
                        "SSID=$ssid gateway=$gateway clients=${group.clientList.size}"
                )

                onGroupInfoAvailable(
                    group,
                    ssid,
                    passphrase,
                    gateway
                )
            } else {
                retryGroupInfo()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun retryGroupInfo() {
        if (!groupStarting) {
            Log.d(TAG, "Group info unavailable and group is not starting")
            return
        }

        if (groupInfoRetryCount >= groupInfoRetryDelaysMs.size) {
            Log.e(
                TAG,
                "Wi-Fi Direct group was created but group information " +
                    "could not be obtained"
            )

            onError(
                "Wi-Fi Direct started, but NetFetch could not obtain " +
                    "the hotspot gateway. Please stop and start NetFetch again."
            )
            return
        }

        val delayMs =
            groupInfoRetryDelaysMs[groupInfoRetryCount]

        groupInfoRetryCount++

        Log.d(
            TAG,
            "Group information not ready; retry " +
                "$groupInfoRetryCount/${groupInfoRetryDelaysMs.size} " +
                "in ${delayMs}ms"
        )

        Thread {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                return@Thread
            }

            if (groupStarting) {
                fetchGroupDetails()
            }
        }.start()
    }

    /**
     * Detect the actual gateway IP address for the Wi-Fi Direct group.
     *
     * On most Android devices this is 192.168.49.1 on interface p2p-wlan0-*
     * or p2p0. We scan network interfaces to find the actual address rather
     * than assuming. Falls back to 192.168.49.1 if not found.
     */
    private fun detectGatewayAddress(ssid: String): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val name = iface.name ?: continue

                // Wi-Fi Direct interfaces are typically named p2p-* or wlan1 etc.
                if (!name.startsWith("p2p") && !name.contains("p2p")) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr.isLoopbackAddress) continue
                    if (addr is java.net.Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        Log.i(TAG, "Detected Wi-Fi Direct gateway: $ip on interface $name")
                        return ip
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not detect gateway from network interfaces: ${e.message}")
        }
        Log.i(TAG, "Using fallback gateway address: $FALLBACK_GATEWAY")
        return FALLBACK_GATEWAY
    }

    private fun tryApplyBandReflection(bandPreference: BandPreference) {
        if (bandPreference == BandPreference.AUTO || channel == null || wifiP2pManager == null) return

        try {
            val setWfdInfoMethod: Method? = wifiP2pManager.javaClass.getMethod(
                "setWifiP2pChannels",
                WifiP2pManager.Channel::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                WifiP2pManager.ActionListener::class.java
            )

            val channelFreq = if (bandPreference == BandPreference.BAND_5GHZ) 36 else 6
            setWfdInfoMethod?.invoke(wifiP2pManager, channel, 0, channelFreq, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Set Wi-Fi Direct frequency channel to $channelFreq via reflection")
                }
                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Set Wi-Fi Direct frequency channel failed: $reason")
                }
            })
        } catch (e: Exception) {
            Log.d(TAG, "Reflection for frequency band unavailable: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopGroup() {
        groupStarting = false
        groupInfoRetryCount = 0
        unregisterReceiver()

        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct group stopped successfully")
            }
            override fun onFailure(reason: Int) {
                Log.w(TAG, "Failed to remove Wi-Fi Direct group: $reason")
            }
        })
        currentGroup = null
    }

    private fun registerReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            onError("Wi-Fi is turned off. Please turn on Wi-Fi to use NetFetch.")
                        }
                    }

                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        @Suppress("DEPRECATION")
                        val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            fetchGroupDetails()
                        }
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    private fun unregisterReceiver() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            receiver = null
        }
    }
}

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
