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

class WifiDirectManager(
    private val context: Context,
    private val onGroupInfoAvailable: (WifiP2pGroup?, String, String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val TAG = "NetFetchP2P"

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null

    private var receiver: BroadcastReceiver? = null
    var currentGroup: WifiP2pGroup? = null
        private set

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

        // Remove existing group if present before creating a new one
        wifiP2pManager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                createNewGroup(config)
            }

            override fun onFailure(reason: Int) {
                createNewGroup(config)
            }
        })
    }

    @SuppressLint("MissingPermission")
    private fun createNewGroup(config: HotspotConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) supports custom WifiP2pConfig setting band & network name
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
        // Fallback for older devices or vendor restrictions
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
        wifiP2pManager?.requestGroupInfo(channel) { group ->
            if (group != null) {
                currentGroup = group
                val ssid = group.networkName
                val passphrase = group.passphrase
                Log.i(TAG, "Group active - SSID: $ssid, Passphrase: $passphrase")
                onGroupInfoAvailable(group, ssid, passphrase)
            } else {
                Log.w(TAG, "Group info returned null")
            }
        }
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
                    Log.i(TAG, "Set Wi-Fi Direct frequency channel to $channelFreq via reflection succeeded")
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
        unregisterReceiver()
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.i(TAG, "Wi-Fi Direct group stopped successfully")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "Failed to remove group: $reason")
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
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {}
            receiver = null
        }
    }
}
