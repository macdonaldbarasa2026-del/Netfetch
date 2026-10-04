package com.netfetch.app.netfetchlink

/**
 * Receiver connection lifecycle states.
 *
 * These states cover both Normal (HTTP/PAC proxy) and Pro (VPN/SOCKS5) modes.
 * The UI must only display a "connected" state when the underlying transport
 * is actually established — not when discovery alone has completed.
 */
sealed class NetfetchReceiverState {

    /** No active receiver session. */
    data object Idle : NetfetchReceiverState()

    /** Scanning for a nearby NetFetch provider via Wi-Fi Direct DNS-SD. */
    data object Searching : NetfetchReceiverState()

    /** A compatible provider has been found; connecting to it. */
    data class ProviderFound(
        val deviceName: String,
        val deviceAddress: String,
        val providerMode: String = NetfetchLinkProtocol.MODE_PRO
    ) : NetfetchReceiverState()

    /** Wi-Fi Direct peer connection requested; waiting for group to form. */
    data object Connecting : NetfetchReceiverState()

    /**
     * Authenticating — Wi-Fi Direct link formed; HELLO handshake in progress.
     */
    data object Authenticating : NetfetchReceiverState()

    /**
     * Provider session established.
     *
     * For Normal providers: configure system HTTP proxy to [providerAddress]:[httpPort]
     * or use PAC URL http://[providerAddress]:[pacPort]/wpad.dat.
     *
     * For Pro providers: start receiver VPN using SOCKS5 at [providerAddress]:[socksPort].
     */
    data class Connected(
        val providerAddress: String,
        /** Provider mode: [NetfetchLinkProtocol.MODE_NORMAL] or [NetfetchLinkProtocol.MODE_PRO]. */
        val providerMode: String,
        val httpPort: Int,
        val pacPort: Int,
        val socksPort: Int,
        val sessionToken: String
    ) : NetfetchReceiverState()

    /**
     * Incompatible provider detected.
     *
     * For example: receiver is in Normal mode but found a Pro-only provider
     * that requires VPN/TUN, which the Normal receiver cannot use.
     */
    data class Unsupported(
        val reason: String
    ) : NetfetchReceiverState()

    /** Reconnecting after a temporary connection failure. */
    data class Reconnecting(
        val reason: String
    ) : NetfetchReceiverState()

    /** Unrecoverable or user-facing error. */
    data class Error(
        val message: String
    ) : NetfetchReceiverState()
}
