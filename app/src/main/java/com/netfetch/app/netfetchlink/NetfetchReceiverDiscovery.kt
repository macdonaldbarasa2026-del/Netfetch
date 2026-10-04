package com.netfetch.app.netfetchlink

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDnsSdServiceRequest
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

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
        val socksPort: Int
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

    private val devices =
        HashMap<String, WifiP2pDevice>()

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
                    val provider =
                        devices[device.deviceAddress]

                    if (provider != null) {
                        onProviderFound(provider)
                    }
                }
            },

            WifiP2pManager.DnsSdTxtRecordListener {
                    _,
                    record,
                    device ->

                val app =
                    record[NetfetchLinkProtocol.KEY_APP]

                val version =
                    record[NetfetchLinkProtocol.KEY_VERSION]

                if (
                    app != NetfetchLinkProtocol.APP ||
                    version != NetfetchLinkProtocol.VERSION
                ) {
                    return@DnsSdTxtRecordListener
                }

                val mode =
                    record[NetfetchLinkProtocol.KEY_MODE]
                        ?: return@DnsSdTxtRecordListener

                if (mode != "PRO") {
                    return@DnsSdTxtRecordListener
                }

                val ssid =
                    record[NetfetchLinkProtocol.KEY_SSID]
                        ?: return@DnsSdTxtRecordListener

                val port =
                    record[
                        NetfetchLinkProtocol.KEY_SOCKS_PORT
                    ]?.toIntOrNull()
                        ?: NetfetchLinkProtocol.DEFAULT_SOCKS_PORT

                devices[device.deviceAddress] =
                    Provider(
                        device = device,
                        ssid = ssid,
                        socksPort = port
                    )

                Log.i(
                    TAG,
                    "Found NetFetch provider: " +
                        "${device.deviceName} " +
                        "${device.deviceAddress}"
                )
            }
        )

        request =
            WifiP2pDnsSdServiceRequest.newInstance()

        wifiManager.addServiceRequest(
            p2pChannel,
            request,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    discoverServices()
                }

                override fun onFailure(reason: Int) {
                    onError(
                        "Could not start NetFetch discovery: $reason"
                    )
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
                    Log.i(
                        TAG,
                        "Searching for nearby NetFetch providers"
                    )
                }

                override fun onFailure(reason: Int) {
                    onError(
                        "NetFetch provider discovery failed: $reason"
                    )
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun connect(provider: Provider) {
        val wifiManager = manager
            ?: run {
                onError("Wi-Fi Direct unavailable.")
                return
            }

        val p2pChannel = channel
            ?: run {
                onError("Wi-Fi Direct channel unavailable.")
                return
            }

        val config =
            WifiP2pConfig().apply {
                deviceAddress =
                    provider.device.deviceAddress
            }

        wifiManager.connect(
            p2pChannel,
            config,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(
                        TAG,
                        "Connecting to NetFetch provider"
                    )
                }

                override fun onFailure(reason: Int) {
                    onError(
                        "Could not connect to NetFetch provider: $reason"
                    )
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        request?.let {
            wifiManager.removeServiceRequest(
                p2pChannel,
                it,
                object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {}

                    override fun onFailure(reason: Int) {
                        Log.w(
                            TAG,
                            "Service request cleanup failed: $reason"
                        )
                    }
                }
            )
        }

        request = null
        devices.clear()
    }
}
