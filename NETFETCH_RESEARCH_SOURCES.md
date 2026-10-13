# Netfetch: Network Reliability Engineering — Research Sources Register

This register documents official references, technical specifications, and standards investigated during the Netfetch networking audit and throughput repair.

---

### Source 1: Android Developers — Connectivity & Sockets Architecture
* **Title:** Connect to networks & read network state
* **URL:** https://developer.android.com/develop/connectivity
* **Accessed:** October 13, 2026
* **Key Findings:**
  * Sockets used for outbound upstream connectivity must be explicitly bound via `Network.bindSocket(Socket)` or protected via `VpnService.protect(Socket)` to avoid circular packet interception and routing deadlocks.
  * For mobile and Wi-Fi networks, default socket buffers allocated by the Linux kernel may be constrained; setting explicit `sendBufferSize` and `receiveBufferSize` hints (e.g. 256 KB) alongside `tcpNoDelay = true` ensures the TCP window scales properly without introducing artificial delays.
* **Supported Issues:** ISSUE-01 (Throughput Lag & Fairness), ISSUE-03 (Socket Buffer & TCP Window Scaling).

---

### Source 2: Android Developers — VpnService API Reference & Architecture
* **Title:** Android VpnService Class Overview & Socket Protection
* **URL:** https://developer.android.com/reference/android/net/VpnService
* **Accessed:** October 13, 2026
* **Key Findings:**
  * `VpnService.protect(int socket)` and `VpnService.protect(Socket socket)` bypass the VPN TUN interface, ensuring traffic intended for the real gateway or upstream ISP is routed directly over physical interfaces.
  * Calling `setUnderlyingNetworks(Network[])` informs the Android system which physical networks carry VPN payload traffic, enabling system-level icon synchronization and power/cellular accounting.
  * When `VpnService.Builder.establish()` returns a `ParcelFileDescriptor`, write operations to the TUN device represent individual IP packet datagrams and must not block upstream TCP worker threads.
* **Supported Issues:** ISSUE-04 (TUN Packet Write Queue & Non-blocking Flow Schedulers).

---

### Source 3: Android Developers — Reading Network State & Capabilities
* **Title:** Read network state and monitor connectivity changes
* **URL:** https://developer.android.com/develop/connectivity/network-ops/reading-network-state
* **Accessed:** October 13, 2026
* **Key Findings:**
  * Active internet validation is distinct from basic network attachment; `NetworkCapabilities.NET_CAPABILITY_VALIDATED` confirms end-to-end internet access.
  * Diagnostics must distinguish between missing upstream attachment, DNS resolution failures, and transport-level timeouts rather than relying on ICMP echo requests (which carriers frequently drop).
* **Supported Issues:** ISSUE-05 (Structured Diagnostics & Telemetry).

---

### Source 4: Android Developers — Wi-Fi P2P (Wi-Fi Direct) Overview
* **Title:** Create P2P connections with Wi-Fi Direct
* **URL:** https://developer.android.com/develop/connectivity/wifi/wifi-direct
* **Accessed:** October 13, 2026
* **Key Findings:**
  * In Wi-Fi Direct Autonomous Group Owner mode, Android assigns `192.168.49.1` as the group owner gateway IP.
  * Clients attached to the group owner access the internet either via an HTTP/HTTPS proxy (:8282), SOCKS5 proxy (:1080), or an encrypted app-to-app VPN tunnel (NetFetch Connect).
  * Socket accept backlogs should be configured to absorb multi-device connection surges (e.g., 1024 backlog on ServerSocket).
* **Supported Issues:** ISSUE-03 (ServerSocket Tuning), ISSUE-06 (Web Companion & Hotspot Coordination).

---

### Source 5: RFC 1928 — SOCKS Protocol Version 5
* **Title:** SOCKS Protocol Version 5
* **URL:** https://www.ietf.org/rfc/rfc1928.txt
* **Accessed:** October 13, 2026
* **Key Findings:**
  * RFC 1928 specifies TCP connection negotiation with authentication methods (0x00 No Auth, 0x02 Username/Password).
  * For local hotspot clients, zero-auth fast negotiation minimizes connection latency for desktop and console gaming.
  * Multi-stream forwarding requires clean duplex pipe teardown: closing both client and target sockets upon termination unblocks blocking IO reads cleanly.
* **Supported Issues:** ISSUE-01 (SOCKS5 Throughput Tuning & Pipe Cleanup).

---

### Source 6: ESnet — High Performance Network Tuning Guidelines
* **Title:** Host Tuning — Linux / Android TCP Window Sizing & Buffer Tuning
* **URL:** https://fasterdata.es.net/host-tuning/linux/
* **Accessed:** October 13, 2026
* **Key Findings:**
  * Throughput is bounded by `WindowSize / RTT`. If socket receive buffers are undersized (e.g., 32KB on a 50ms link), throughput cannot exceed ~5 Mbps regardless of Wi-Fi bandwidth.
  * Setting 256 KB buffers on both ServerSocket and connected sockets allows full TCP BDP (Bandwidth Delay Product) saturation.
  * Artificially yielding CPU (`Thread.yield()`) inside streaming packet loops severely degrades throughput by forcing thousands of voluntary scheduler context switches.
* **Supported Issues:** ISSUE-01 (Anti-Jam Thread.yield Bottleneck), ISSUE-03 (Socket Buffer Sizes).

---

### Source 7: NetShare Architectural Analysis & Functional Reference
* **Title:** NetShare Wi-Fi Hotspot & Tethering Reference
* **URL:** https://netshare.app/
* **Accessed:** October 13, 2026
* **Key Findings:**
  * NetShare operates a user-space proxy server on the group owner device (ports 8282 / 1080).
  * NetShare client apps establish a local VpnService on receiver devices to transparently forward all device traffic to the host proxy without requiring manual per-app proxy configuration.
  * High-speed transfers in NetShare achieve wire speed by utilizing native OS socket multiplexing without synthetic yield delays.
* **Supported Issues:** ISSUE-01 (Parity with NetShare Transfer Speeds), ISSUE-06 (Web Companion One-Tap Profiles).
