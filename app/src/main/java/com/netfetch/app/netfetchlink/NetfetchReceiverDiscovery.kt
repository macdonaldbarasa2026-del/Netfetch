package com.netfetch.app.netfetchlink

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.util.Log

/**
 * Discovers nearby NetFetch providers via Wi-Fi Direct DNS-SD.
 *
 * Discovers BOTH Normal and Pro providers. The receiver application
 * decides which mode it can connect to.
 */
class NetfetchReceiverDiscovery(
    context: Context,
    private val onProviderFound: (Provider) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "NetFetchReceiver"
    }

    data class Provider(
        val device: WifiP2pDevice,
        val ssid: String,
        /** [NetfetchLinkProtocol.MODE_NORMAL] or [NetfetchLinkProtocol.MODE_PRO]. */
        val mode: String,
        val socksPort: Int,
        val httpPort: Int,
        val pacPort: Int
    )

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE)
            as? WifiP2pManager

    private val channel =
        manager?.initialize(
            context,
            context.mainLooper,
            null
        )

    private var request: WifiP2pDnsSdServiceRequest? = null

    private val providers =
        HashMap<String, Provider>()

    @SuppressLint("MissingPermission")
    fun start() {
        val wifiManager = manager
        val p2pChannel = channel

        if (wifiManager == null || p2pChannel == null) {
            onError("Wi-Fi Direct is unavailable.")
            return
        }

        wifiManager.setDnsSdResponseListeners(
            p2pChannel,

            WifiP2pManager.DnsSdServiceResponseListener {
                    instanceName,
                    registrationType,
                    device ->

                if (
                    instanceName == NetfetchLinkProtocol.SERVICE_INSTANCE &&
                    registrationType.contains(
                        NetfetchLinkProtocol.SERVICE_TYPE.removeSuffix(".")
                    )
                ) {
                    val provider = providers[device.deviceAddress]

                    if (provider != null) {
                        Log.i(
                            TAG,
                            "NetFetch provider service found: ${device.deviceName} " +
                                "(mode=${provider.mode})"
                        )

                        onProviderFound(provider)
                    }
                }
            },

            WifiP2pManager.DnsSdTxtRecordListener {
                    _,
                    record,
                    device ->

                val app = record[NetfetchLinkProtocol.KEY_APP]
                val version = record[NetfetchLinkProtocol.KEY_VERSION]

                if (
                    app != NetfetchLinkProtocol.APP ||
                    version != NetfetchLinkProtocol.VERSION
                ) {
                    return@DnsSdTxtRecordListener
                }

                val mode = record[NetfetchLinkProtocol.KEY_MODE]
                    ?: return@DnsSdTxtRecordListener

                // Accept both NORMAL and PRO providers.
                if (
                    mode != NetfetchLinkProtocol.MODE_NORMAL &&
                    mode != NetfetchLinkProtocol.MODE_PRO
                ) {
                    return@DnsSdTxtRecordListener
                }

                val ssid = record[NetfetchLinkProtocol.KEY_SSID]
                    ?: return@DnsSdTxtRecordListener

                val httpPort = record[NetfetchLinkProtocol.KEY_HTTP_PORT]
                    ?.toIntOrNull()
                    ?: NetfetchLinkProtocol.DEFAULT_HTTP_PORT

                val pacPort = record[NetfetchLinkProtocol.KEY_PAC_PORT]
                    ?.toIntOrNull()
                    ?: NetfetchLinkProtocol.DEFAULT_PAC_PORT

                val socksPort = if (mode == NetfetchLinkProtocol.MODE_PRO) {
                    record[NetfetchLinkProtocol.KEY_SOCKS_PORT]
                        ?.toIntOrNull()
                        ?: NetfetchLinkProtocol.DEFAULT_SOCKS_PORT
                } else {
                    NetfetchLinkProtocol.DEFAULT_SOCKS_PORT
                }

                val provider = Provider(
                    device = device,
                    ssid = ssid,
                    mode = mode,
                    socksPort = socksPort,
                    httpPort = httpPort,
                    pacPort = pacPort
                )

                providers[device.deviceAddress] = provider

                Log.i(
                    TAG,
                    "Found NetFetch provider: ${device.deviceName} " +
                        "${device.deviceAddress} mode=$mode"
                )

                onProviderFound(provider)
            }
        )

        val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        request = serviceRequest

        wifiManager.addServiceRequest(
            p2pChannel,
            serviceRequest,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    discoverServices()
                }

                override fun onFailure(reason: Int) {
                    onError("Could not start NetFetch discovery: $reason")
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices() {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        wifiManager.discoverServices(
            p2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Searching for nearby NetFetch providers")
                }

                override fun onFailure(reason: Int) {
                    onError("NetFetch provider discovery failed: $reason")
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(provider: Provider) {
        val wifiManager =
            manager
                ?: run {
                    onError("Wi-Fi Direct unavailable.")
                    return
                }

        val p2pChannel =
            channel
                ?: run {
                    onError("Wi-Fi Direct channel unavailable.")
                    return
                }

        val config =
            WifiP2pConfig().apply {
                deviceAddress = provider.device.deviceAddress
            }

        wifiManager.connect(
            p2pChannel,
            config,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "Connecting to NetFetch provider")
                }

                override fun onFailure(reason: Int) {
                    onError("Could not connect to NetFetch provider: $reason")
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        request?.let { serviceRequest ->
            wifiManager.removeServiceRequest(
                p2pChannel,
                serviceRequest,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}

                    override fun onFailure(reason: Int) {
                        Log.w(TAG, "Service request cleanup failed: $reason")
                    }
                }
            )
        }

        request = null
        providers.clear()
    }
}
