# NetFetch vs. NetShare Strategic Competitive Upgrade

A comprehensive upgrade plan designed to outperform NetShare on Android and web clients by directly overcoming NetShare's top Play Store user complaints: cumbersome manual proxy entry, broken gaming/UDP routing, silent proxy misconfigurations, and sudden background service drops.

### User Review & Critical Decisions

> [!IMPORTANT]
> NetShare on Google Play holds a 4.4 rating with 50K+ reviews, but has four persistent user frustrations:
> 1. Manual IP/Port entry confuses non-technical users and causes silent connection failures.
> 2. P2P and UDP traffic (Steam, co-op gaming, Nintendo Switch) fail because standard HTTP proxy doesn't route UDP.
> 3. Android OS randomly terminates the background hotspot service due to battery optimization.
> 4. Restrictive paywall limits lock users out after brief sessions.
>
> We propose four targeted competitive advantages for NetFetch:
> - **1-Tap iOS Profile (`.mobileconfig`)**: Connected iPhones download a profile from the local portal that configures Wi-Fi proxy settings automatically.
> - **In-Portal Connection Verifier & Speed Test**: A built-in web test tool at `http://192.168.49.1:3000` that instantly tests whether the client's proxy is correctly active, reports ping/latency, and runs a local throughput benchmark.
> - **SOCKS5 & UDP Gaming Tunneling Guide**: Clear instructions and proxy configuration for tun2socks / Proxifier on Windows and macOS to enable Steam and online gaming.
> - **Wakelock & Foreground Service Hardening**: Android service lock persistence ensuring zero dropped hotspots.

---

### 1. Overview & Core Concept

- **What It Does**: Transforms NetFetch into a frictionless, resilient Wi-Fi Direct and carrier bypass sharing suite. Client devices connecting to NetFetch can verify their proxy setup with a single tap in their browser, download automatic configuration profiles for Apple devices, route UDP traffic via SOCKS5, and enjoy uninterrupted background service.
- **Target Audience / Persona**: Users sharing cellular data with laptops, tablets, gaming handhelds (Steam Deck, Switch), and friends without paying carrier tethering upcharges or getting throttled.
- **Key Value**: Eliminates 90% of the manual proxy setup friction and disconnect errors that hurt NetShare's store reviews.

---

### 2. User Experience & Visual Design

#### Key User Flows
1. **Host Flow (Android App)**:
   - Host toggles Hotspot on the clean, elevated Jetpack Compose dashboard.
   - Host views active Wi-Fi Direct credentials, PAC script URL, and SOCKS5 port.
   - Host sees real-time connected client list with active/idle indicators and per-device data transfer meters.
2. **Client Onboarding Flow (Connected Device)**:
   - Client joins Wi-Fi Direct network (`DIRECT-NetFetch-AccessPoint`).
   - Browser opens or navigates to local gateway (`http://192.168.49.1:3000`).
   - **One-Tap Verification**: An interactive banner runs a test packet; displays "Proxy Active: Connected to the Internet" or "Action Needed: Proxy not yet configured" with direct fix instructions.
   - **Apple Users**: One-click button "Download iOS Auto-Config Profile" automatically applies the proxy settings to iOS Wi-Fi.
   - **Gamers & Power Users**: SOCKS5 tab with tun2socks client guide for Steam Deck and Windows gaming.

#### Visual Identity & Theme
- **Theme Tokens**: Warm Slate & Cream aesthetic (`#1E293B` text, `#F8FAFC` slate background, `#0EA5E9` cyber blue accent, `#10B981` connection green).
- **Typography**: Clean display pairing with tabular numbers (`tabular-nums`) for byte counters, port numbers, and latency figures.
- **Anti-Slop Discipline**: No floating pill sandwiches or mechanical comment headers; clean unboxed metadata with subtle dot dividers (`·`).

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Web-Based iOS `.mobileconfig` Generation vs. Native App-Only**
  - *Chosen Approach*: Deliver dynamic `.mobileconfig` payload directly from the local web portal (`/api/ios-profile.mobileconfig`).
  - *Why*: Allows iPhone and iPad clients to configure their proxy with one tap without needing to install an app from the App Store.
  - *Alternative Considered*: Requiring an iOS companion app (fails because App Store review prohibits Wi-Fi Direct manipulation).
- **Decision 2: Dual HTTP Proxy + SOCKS5 Relay**
  - *Chosen Approach*: Support both HTTP Proxy (`:8282`) and SOCKS5 Proxy (`:1080`) side-by-side.
  - *Why*: HTTP proxy handles regular browsers and phones effortlessly; SOCKS5 enables tun2socks and gaming clients that require non-HTTP TCP/UDP tunneling.
- **Decision 3: In-App Diagnostics and Self-Healing WakeLock**
  - *Chosen Approach*: Acquire high-priority `PARTIAL_WAKE_LOCK` and `WIFI_MODE_FULL_HIGH_PERF` in Android `HotspotService`.
  - *Why*: Directly solves the top 1 complaint in NetShare reviews ("hotspot randomly stops when screen turns off").

---

### 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────────────────────┐
│                        NetFetch Host (Android)                         │
├───────────────────────────────┬────────────────────────────────────────┤
│     Jetpack Compose UI        │           HotspotService               │
│  • Elevated Bottom Nav Bar    │  • Wi-Fi Direct Group Owner            │
│  • Edge-to-Edge Safe Padding  │  • Full High-Perf WifiLock             │
│  • Real-Time Client Registry  │  • Partial WakeLock (Keep-Alive)       │
└───────────────┬───────────────┴───────────────────┬────────────────────┘
                │                                   │
                ▼                                   ▼
┌───────────────────────────────┐   ┌────────────────────────────────────┐
│      HTTP / PAC Server        │   │         SOCKS5 Proxy Engine        │
│  • Port 8282 (HTTP Proxy)     │   │  • Port 1080 (SOCKS5 Proxy)        │
│  • Port 8283 (PAC Auto-Config)│   │  • TCP/UDP Relay for tun2socks     │
└───────────────┬───────────────┘   └─────────────────┬──────────────────┘
                │                                     │
                ▼                                     ▼
┌────────────────────────────────────────────────────────────────────────┐
│             Web Management & Diagnostic Portal (Port 3000)             │
│  • http://192.168.49.1:3000                                            │
│  • /api/proxy-test        -> Instant proxy verification probe          │
│  • /api/ios-profile       -> One-tap Apple .mobileconfig download      │
│  • /api/speed-benchmark   -> Local latency & throughput test           │
│  • /api/devices           -> Real-time connected client telemetry      │
└────────────────────────────────────────────────────────────────────────┘
```

#### Interactive Component & State Mapping
- `ProxyTestWidget`: Triggers an async HTTP probe through the local proxy gateway; displays real-time latency (ms) and green status checkmark upon verification.
- `IosProfileDownloader`: Serves standard XML Apple configuration profile (`com.apple.wifi.managed`) with Wi-Fi SSID and pre-set proxy settings.
- `DeviceMonitor`: Reads from `NetfetchClientRegistry`; shows real connected devices, device OS icons, active data counters, and a button to toggle test client simulation when testing without physical devices.
- `HotspotService.kt`: Keeps wakelocks active and provides foreground notification with live data counters.
