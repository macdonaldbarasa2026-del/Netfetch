package com.netfetch.app.netfetchlink

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

class NetfetchProviderDiscovery(
    private val context: Context
) {
    companion object {
        private const val TAG = "NetFetchProvider"
    }

    private val manager =
        context.getSystemService(Context.WIFI_P2P_SERVICE)
            as? WifiP2pManager

    private val channel =
        manager?.initialize(
            context,
            context.mainLooper,
            null
        )

    private var serviceInfo: WifiP2pDnsSdServiceInfo? = null

    @SuppressLint("MissingPermission")
    fun start(
        ssid: String,
        socksPort: Int
    ) {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        val record = mapOf(
            NetfetchLinkProtocol.KEY_VERSION
                to NetfetchLinkProtocol.VERSION,

            NetfetchLinkProtocol.KEY_APP
                to NetfetchLinkProtocol.APP,

            NetfetchLinkProtocol.KEY_MODE
                to "PRO",

            NetfetchLinkProtocol.KEY_SOCKS_PORT
                to socksPort.toString(),

            NetfetchLinkProtocol.KEY_SSID
                to ssid
        )

        val info =
            WifiP2pDnsSdServiceInfo.newInstance(
                NetfetchLinkProtocol.SERVICE_INSTANCE,
                NetfetchLinkProtocol.SERVICE_TYPE,
                record
            )

        serviceInfo = info

        wifiManager.clearLocalServices(
            p2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    register(info)
                }

                override fun onFailure(reason: Int) {
                    register(info)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    private fun register(
        info: WifiP2pDnsSdServiceInfo
    ) {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        wifiManager.addLocalService(
            p2pChannel,
            info,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(
                        TAG,
                        "NetFetch provider service advertised"
                    )
                }

                override fun onFailure(reason: Int) {
                    Log.e(
                        TAG,
                        "Unable to advertise NetFetch service: $reason"
                    )
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        wifiManager.clearLocalServices(
            p2pChannel,
            object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.i(TAG, "NetFetch provider service removed")
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "Service cleanup failed: $reason")
                }
            }
        )

        serviceInfo = null
    }
}
