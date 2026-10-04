package com.netfetch.app.netfetchlink

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.util.Log

/**
 * Advertises the local NetFetch provider via Wi-Fi Direct DNS-SD.
 *
 * Works for both Normal and Pro modes. The advertised TXT record includes:
 *   - app / version identifiers
 *   - mode (NORMAL or PRO)
 *   - HTTP proxy port (both modes)
 *   - PAC port (both modes)
 *   - SOCKS5 port (Pro mode only — omitted for Normal)
 *   - SSID of the Wi-Fi Direct group
 */
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
        mode: String,
        socksPort: Int = NetfetchLinkProtocol.DEFAULT_SOCKS_PORT,
        httpPort: Int = NetfetchLinkProtocol.DEFAULT_HTTP_PORT,
        pacPort: Int = NetfetchLinkProtocol.DEFAULT_PAC_PORT
    ) {
        val wifiManager = manager ?: return
        val p2pChannel = channel ?: return

        val record = buildMap<String, String> {
            put(NetfetchLinkProtocol.KEY_VERSION, NetfetchLinkProtocol.VERSION)
            put(NetfetchLinkProtocol.KEY_APP, NetfetchLinkProtocol.APP)
            put(NetfetchLinkProtocol.KEY_MODE, mode)
            put(NetfetchLinkProtocol.KEY_HTTP_PORT, httpPort.toString())
            put(NetfetchLinkProtocol.KEY_PAC_PORT, pacPort.toString())
            put(NetfetchLinkProtocol.KEY_SSID, ssid)

            // SOCKS port only meaningful in Pro mode.
            if (mode == NetfetchLinkProtocol.MODE_PRO) {
                put(NetfetchLinkProtocol.KEY_SOCKS_PORT, socksPort.toString())
            }
        }

        val info = WifiP2pDnsSdServiceInfo.newInstance(
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
                    // Register anyway even if clear failed.
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
