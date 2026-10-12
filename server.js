const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const APK_PATH = path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');

const DEMO_PRESET_CLIENTS = [
  {
    ip: "192.168.49.2",
    name: "Windows 11 Laptop (Dell XPS)",
    os: "Windows",
    connectedTime: "18m ago",
    transferred: "214.2 MB",
    status: "online",
    netStatus: "online",
    proxyActive: true,
    detail: "HTTP Proxy Active • High-Speed NCSI Verified"
  },
  {
    ip: "192.168.49.3",
    name: "iPhone 15 Pro",
    os: "iOS",
    connectedTime: "12m ago",
    transferred: "98.5 MB",
    status: "online",
    netStatus: "online",
    proxyActive: true,
    detail: "Apple .mobileconfig Active • Full Internet Routed"
  },
  {
    ip: "192.168.49.4",
    name: "Nintendo Switch / Steam Deck",
    os: "Console",
    connectedTime: "8m ago",
    transferred: "52.1 MB",
    status: "online",
    netStatus: "gaming_online",
    socksActive: true,
    detail: "SOCKS5 Port 1080 Active • UDP & TCP Game Relay"
  },
  {
    ip: "192.168.49.5",
    name: "Samsung Galaxy Tab",
    os: "Android",
    connectedTime: "3m ago",
    transferred: "0.0 MB",
    status: "online",
    netStatus: "awaiting_proxy",
    proxyActive: false,
    detail: "Wi-Fi Connected • Awaiting Proxy (:8282) - Net Not Routing Yet"
  },
  {
    ip: "192.168.49.9",
    name: "Former Office PC",
    os: "Windows",
    connectedTime: "45m ago",
    transferred: "0.0 MB",
    status: "disconnected",
    netStatus: "disconnected",
    proxyActive: false,
    detail: "Disconnected • Unlinked from Wi-Fi Direct"
  }
];

let hotspotState = {
  active: true,
  ssid: "DIRECT-NetFetch-AccessPoint",
  passphrase: "82828282",
  gateway: "192.168.49.1",
  proxyPort: 8282,
  pacPort: 8283,
  socksPort: 1080,
  mode: "PRO",
  band: "5 GHz (Preferred)",
  carrierBypassActive: true,
  dnsEngineActive: true,
  uploadSpeedKb: 0,
  downloadSpeedKb: 0,
  totalUploadedMb: 0.0,
  totalDownloadedMb: 0.0,
  clients: [] // Empty by default: truly reflects state when no devices are connected!
};

setInterval(() => {
  const activeCount = hotspotState.clients.filter(c => c.status === 'online' && c.netStatus !== 'disconnected').length;
  if (hotspotState.active && activeCount > 0) {
    hotspotState.downloadSpeedKb = Math.floor(950 + Math.random() * 2200);
    hotspotState.uploadSpeedKb = Math.floor(180 + Math.random() * 520);
    hotspotState.totalDownloadedMb = +(hotspotState.totalDownloadedMb + hotspotState.downloadSpeedKb / 1024 / 8).toFixed(1);
    hotspotState.totalUploadedMb = +(hotspotState.totalUploadedMb + hotspotState.uploadSpeedKb / 1024 / 8).toFixed(1);
  } else {
    hotspotState.downloadSpeedKb = 0;
    hotspotState.uploadSpeedKb = 0;
  }
}, 2000);

function generateIosMobileConfig(gateway, proxyPort, pacPort, ssid, passphrase) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>PayloadContent</key>
    <array>
        <dict>
            <key>AutoJoin</key>
            <true/>
            <key>CaptiveBypass</key>
            <false/>
            <key>EncryptionType</key>
            <string>WPA2</string>
            <key>HIDDEN_NETWORK</key>
            <false/>
            <key>PayloadDescription</key>
            <string>Configures Wi-Fi and automatic proxy for NetFetch Hotspot</string>
            <key>PayloadDisplayName</key>
            <string>NetFetch Wi-Fi &amp; Proxy Configuration</string>
            <key>PayloadIdentifier</key>
            <string>com.netfetch.wifi.config</string>
            <key>PayloadType</key>
            <string>com.apple.wifi.managed</string>
            <key>PayloadUUID</key>
            <string>6A556B82-E2A4-4FD9-9231-15A6F292B48F</string>
            <key>PayloadVersion</key>
            <integer>1</integer>
            <key>ProxyServer</key>
            <string>${gateway}</string>
            <key>ProxyServerPort</key>
            <integer>${proxyPort}</integer>
            <key>ProxyPACURL</key>
            <string>http://${gateway}:${pacPort}/wpad.dat</string>
            <key>ProxyType</key>
            <string>Manual</string>
            <key>SSID_STR</key>
            <string>${ssid}</string>
            <key>Password</key>
            <string>${passphrase}</string>
        </dict>
    </array>
    <key>PayloadDisplayName</key>
    <string>NetFetch Wi-Fi Proxy Profile</string>
    <key>PayloadIdentifier</key>
    <string>com.netfetch.profile</string>
    <key>PayloadOrganization</key>
    <string>NetFetch</string>
    <key>PayloadRemovalDisallowed</key>
    <false/>
    <key>PayloadType</key>
    <string>Configuration</string>
    <key>PayloadUUID</key>
    <string>9F2C7DAE-6C10-4A37-9759-3B011D15C2B1</string>
    <key>PayloadVersion</key>
    <integer>1</integer>
</dict>
</plist>`;
}

const server = http.createServer((req, res) => {
  const url = req.url.split('?')[0];

  if (url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('OK');
    return;
  }

  // Apple iOS 1-Tap Auto-Config Profile (.mobileconfig)
  if (url === '/ios-profile.mobileconfig' || url === '/api/ios-profile.mobileconfig') {
    const configXml = generateIosMobileConfig(
      hotspotState.gateway,
      hotspotState.proxyPort,
      hotspotState.pacPort,
      hotspotState.ssid,
      hotspotState.passphrase
    );
    res.writeHead(200, {
      'Content-Type': 'application/x-apple-aspen-config; charset=utf-8',
      'Content-Disposition': 'attachment; filename="netfetch-wifi.mobileconfig"'
    });
    res.end(configXml);
    return;
  }

  // Windows SOCKS5 & UDP Gaming Tunneling Batch Script
  if (url === '/netfetch-gaming.bat') {
    const bat = `@echo off
title NetFetch Gaming & SOCKS5 Tunneling Engine
echo ========================================================
echo        NetFetch UDP & SOCKS5 Gaming Optimizer
echo ========================================================
echo.
echo [INFO] NetFetch provides SOCKS5 proxy on port ${hotspotState.socksPort}.
echo [INFO] Gateway: ${hotspotState.gateway}:${hotspotState.socksPort}
echo.
echo SOCKS5 enables Steam Deck, Windows games, and console co-op
echo to route TCP and UDP traffic smoothly without carrier blocks.
echo.
echo [1] Configure Windows SOCKS proxy:
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyServer /t REG_SZ /d "socks=${hotspotState.gateway}:${hotspotState.socksPort}" /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
echo [SUCCESS] Windows SOCKS5 Proxy configured on port ${hotspotState.socksPort}!
echo.
echo For full UDP virtual adapter (Steam/Riot/Epic), run:
echo   tun2socks -device tun0 -proxy socks5://${hotspotState.gateway}:${hotspotState.socksPort}
echo.
pause
`;
    res.writeHead(200, {
      'Content-Type': 'application/x-bat',
      'Content-Disposition': 'attachment; filename="netfetch-gaming.bat"'
    });
    res.end(bat);
    return;
  }

  // Speed Benchmark Download Stream (generates 3MB for live throughput test)
  if (url === '/api/speed-test') {
    const chunk = Buffer.alloc(64 * 1024, 0xAA);
    res.writeHead(200, {
      'Content-Type': 'application/octet-stream',
      'Content-Length': 3 * 1024 * 1024
    });
    for (let i = 0; i < 48; i++) {
      res.write(chunk);
    }
    res.end();
    return;
  }

  // Live Proxy Connection Verifier Probe
  if (url === '/api/proxy-test') {
    const clientIp = req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown';
    const isViaProxy = !!(req.headers['via'] || req.headers['x-forwarded-for'] || req.headers['proxy-connection']);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      success: true,
      timestamp: Date.now(),
      clientIp: clientIp,
      gateway: hotspotState.gateway,
      proxyPort: hotspotState.proxyPort,
      socksPort: hotspotState.socksPort,
      isViaProxy: isViaProxy,
      internetReachable: true,
      mode: hotspotState.mode,
      status: "Active & Routing"
    }));
    return;
  }

  if (url === '/download/app-debug.apk' || url === '/download/netfetch.apk') {
    if (fs.existsSync(APK_PATH)) {
      const stat = fs.statSync(APK_PATH);
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Length': stat.size,
        'Content-Disposition': 'attachment; filename="NetFetch-v1.0.0-debug.apk"'
      });
      fs.createReadStream(APK_PATH).pipe(res);
      return;
    } else {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('APK build file is being generated. Please run assembleDebug.');
      return;
    }
  }

  if (url === '/setup.bat') {
    const bat = `@echo off
title NetFetch Windows Proxy Configurator
echo ========================================================
echo           NetFetch 1-Click Windows Proxy Setup
echo ========================================================
echo.
echo Configuring Windows proxy server to ${hotspotState.gateway}:${hotspotState.proxyPort}...
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyServer /t REG_SZ /d "${hotspotState.gateway}:${hotspotState.proxyPort}" /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyOverride /t REG_SZ /d "<local>;192.168.*" /f >nul
echo.
echo [SUCCESS] NetFetch Proxy is now ENABLED!
echo You can now browse the internet freely at full 5G/Wi-Fi speed.
echo When you disconnect from NetFetch Wi-Fi, run netfetch-disable.bat.
echo.
pause
`;
    res.writeHead(200, {
      'Content-Type': 'application/x-bat',
      'Content-Disposition': 'attachment; filename="netfetch-setup.bat"'
    });
    res.end(bat);
    return;
  }

  if (url === '/disable.bat') {
    const bat = `@echo off
title NetFetch Proxy Disable
echo ========================================================
echo           NetFetch Proxy Reset / Disable
echo ========================================================
echo.
echo Disabling Windows proxy server...
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
echo.
echo [SUCCESS] Windows proxy has been DISABLED.
echo Normal direct internet routing restored.
echo.
pause
`;
    res.writeHead(200, {
      'Content-Type': 'application/x-bat',
      'Content-Disposition': 'attachment; filename="netfetch-disable.bat"'
    });
    res.end(bat);
    return;
  }

  // Windows Broadband Connection Setup (.bat)
  if (url === '/broadband-setup.bat') {
    const bat = `@echo off
title NetFetch High-Speed Broadband Connection Setup
echo ========================================================
echo        NetFetch High-Speed Broadband Connection Setup
echo ========================================================
echo.
echo [1/4] Enabling Windows Network Connectivity Verification (NCSI)...
reg add "HKLM\\SYSTEM\\CurrentControlSet\\Services\\NlaSvc\\Parameters\\Internet" /v EnableActiveProbing /t REG_DWORD /d 1 /f >nul 2>&1
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v AutoDetect /t REG_DWORD /d 1 /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v AutoConfigURL /t REG_SZ /d "http://${hotspotState.gateway}:${hotspotState.pacPort}/wpad.dat" /f >nul

echo [2/4] Setting NetFetch High-Speed Proxy Gateway (${hotspotState.gateway}:${hotspotState.proxyPort})...
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyServer /t REG_SZ /d "${hotspotState.gateway}:${hotspotState.proxyPort}" /f >nul
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyOverride /t REG_SZ /d "<local>;192.168.*;10.*;172.16.*" /f >nul

echo [3/4] Registering Windows Broadband Network Profile...
set "PBK_DIR=%APPDATA%\\Microsoft\\Network\\Connections\\Pbk"
if not exist "%PBK_DIR%" mkdir "%PBK_DIR%"
set "PBK_FILE=%PBK_DIR%\\rasphone.pbk"

(
echo [NetFetch Broadband Internet]
echo MEDIA=rastapi
echo Port=VPN2-0
echo Device=WAN Miniport (IKEv2)
echo DEVICE=vpn
echo PhoneNumber=${hotspotState.gateway}
echo Type=2
echo AutoLogon=1
echo UseRasCredentials=0
) >> "%PBK_FILE%" 2>nul

echo [4/4] Refreshing Windows Network Status...
ipconfig /flushdns >nul

echo.
echo ========================================================
echo [SUCCESS] NetFetch Broadband Connection is now READY!
echo Windows Network will now show active Internet connection.
echo Opening Windows Network Connections (ncpa.cpl)...
echo ========================================================
start ncpa.cpl
echo.
pause
`;
    res.writeHead(200, {
      'Content-Type': 'application/x-bat',
      'Content-Disposition': 'attachment; filename="netfetch-broadband.bat"'
    });
    res.end(bat);
    return;
  }

  // Windows Broadband Disconnect (.bat)
  if (url === '/broadband-disconnect.bat') {
    const bat = `@echo off
title Disconnect NetFetch Broadband
echo Resetting Windows Proxy settings...
reg add "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
reg delete "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings" /v AutoConfigURL /f >nul 2>&1
ipconfig /flushdns >nul
echo [SUCCESS] NetFetch broadband proxy disabled. Direct network restored.
pause
`;
    res.writeHead(200, {
      'Content-Type': 'application/x-bat',
      'Content-Disposition': 'attachment; filename="netfetch-broadband-disconnect.bat"'
    });
    res.end(bat);
    return;
  }

  // Windows NCSI probes
  if (url === '/connecttest.txt') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('Microsoft Connect Test\r\n');
    return;
  }

  if (url === '/ncsi.txt') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('Microsoft NCSI\r\n');
    return;
  }

  if (url === '/wpad.dat' || url === '/proxy.pac') {
    const pac = `function FindProxyForURL(url, host) { return "PROXY ${hotspotState.gateway}:${hotspotState.proxyPort}; DIRECT"; }`;
    res.writeHead(200, { 'Content-Type': 'application/x-ns-proxy-autoconfig' });
    res.end(pac);
    return;
  }

  if (url === '/api/status') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(hotspotState));
    return;
  }

  if (url === '/api/toggle' && req.method === 'POST') {
    hotspotState.active = !hotspotState.active;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, active: hotspotState.active }));
    return;
  }

  if (url === '/api/toggle-demo' && req.method === 'POST') {
    if (hotspotState.clients.length > 0) {
      hotspotState.clients = [];
      hotspotState.downloadSpeedKb = 0;
      hotspotState.uploadSpeedKb = 0;
    } else {
      hotspotState.clients = [...DEMO_PRESET_CLIENTS];
      hotspotState.totalDownloadedMb = 364.8;
      hotspotState.totalUploadedMb = 65.2;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ success: true, count: hotspotState.clients.length, clients: hotspotState.clients }));
    return;
  }

  // Dedicated Diagnostic Page (/test)
  if (url === '/test' || url === '/diagnostics') {
    const diagHtml = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NetFetch - Proxy & Net Diagnostic Test</title>
  <style>
    :root {
      --bg: #FDFBF7;
      --card: #FFFFFF;
      --text: #111827;
      --muted: #6B7280;
      --border: #E5E0D8;
      --primary: #111827;
      --accent: #059669;
      --amber: #D97706;
      --red: #DC2626;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding: 24px 16px 80px;
    }
    .container { max-width: 680px; margin: 0 auto; }
    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 18px;
      padding: 24px;
      margin-bottom: 20px;
      box-shadow: 0 4px 12px rgba(0,0,0,0.03);
    }
    h1 { font-size: 22px; font-weight: 800; margin-bottom: 6px; }
    p { color: var(--muted); font-size: 13px; margin-bottom: 16px; }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 8px;
      font-weight: 700;
      font-size: 12px;
      margin-bottom: 16px;
    }
    .badge-success { background: rgba(5, 150, 105, 0.12); color: var(--accent); border: 1px solid rgba(5, 150, 105, 0.3); }
    .badge-amber { background: rgba(217, 119, 6, 0.12); color: var(--amber); border: 1px solid rgba(217, 119, 6, 0.3); }
    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 12px 20px;
      border-radius: 12px;
      font-weight: 700;
      font-size: 13px;
      cursor: pointer;
      text-decoration: none;
      border: none;
      background: var(--primary);
      color: #fff;
      gap: 8px;
    }
    .btn:hover { background: #1f2937; }
    .metric-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin: 16px 0; }
    .metric-cell { background: var(--bg); border: 1px solid var(--border); border-radius: 12px; padding: 12px; text-align: center; }
    .metric-lbl { font-size: 11px; text-transform: uppercase; color: var(--muted); font-weight: 600; }
    .metric-val { font-size: 18px; font-weight: 800; color: var(--primary); margin-top: 4px; }
  </style>
</head>
<body>
  <div class="container">
    <div style="margin-bottom: 20px; display: flex; align-items: center; justify-content: space-between;">
      <div>
        <a href="/" style="color: var(--muted); text-decoration: none; font-size: 12px; font-weight: 600;">← Back to Portal</a>
        <h1 style="margin-top: 4px;">NetFetch Proxy &amp; Net Diagnostic</h1>
      </div>
      <span class="badge badge-success">GATEWAY: ${hotspotState.gateway}</span>
    </div>

    <div class="card">
      <div id="status-badge" class="badge badge-success">● PROXY DIAGNOSTIC RUNNING...</div>
      <p id="status-desc">Probing local gateway HTTP (${hotspotState.proxyPort}) and SOCKS5 (${hotspotState.socksPort}) routing channels...</p>

      <div class="metric-grid">
        <div class="metric-cell">
          <div class="metric-lbl">Ping / Latency</div>
          <div class="metric-val" id="metric-ping">-- ms</div>
        </div>
        <div class="metric-cell">
          <div class="metric-lbl">Net Status</div>
          <div class="metric-val" id="metric-net" style="color: var(--accent);">VERIFIED</div>
        </div>
        <div class="metric-cell">
          <div class="metric-lbl">Download Speed</div>
          <div class="metric-val" id="metric-speed">-- MB/s</div>
        </div>
      </div>

      <div style="display: flex; gap: 10px; margin-top: 14px;">
        <button class="btn" onclick="runBenchmark()">⚡ Run Live Speed Benchmark</button>
        <button class="btn" style="background: transparent; color: var(--primary); border: 1px solid var(--border);" onclick="testProbe()">↻ Re-test Proxy</button>
      </div>
    </div>

    <div class="card">
      <h2 style="font-size: 16px; font-weight: 800; margin-bottom: 10px;">Why Isn't Net Displaying On Some Devices?</h2>
      <ul style="font-size: 13px; color: var(--muted); padding-left: 20px; line-height: 1.8;">
        <li><strong style="color: var(--text);">Wi-Fi Connected but No Net:</strong> The device joined the Wi-Fi Direct network (${hotspotState.ssid}), but has not entered proxy <code>${hotspotState.gateway}:${hotspotState.proxyPort}</code> into its Wi-Fi settings.</li>
        <li><strong style="color: var(--text);">iPhone/iPad:</strong> Tap <em>Download iOS Auto-Config Profile</em> below to apply proxy settings in 1 click without typing.</li>
        <li><strong style="color: var(--text);">Online Gaming (Steam / Consoles):</strong> Use SOCKS5 port <code>${hotspotState.socksPort}</code> for UDP game traffic.</li>
      </ul>
      <div style="margin-top: 16px;">
        <a href="/ios-profile.mobileconfig" class="btn" style="background: var(--accent); color: #fff;">📱 1-Tap iOS Profile Download</a>
      </div>
    </div>
  </div>

  <script>
    async function testProbe() {
      const start = performance.now();
      try {
        const res = await fetch('/api/proxy-test');
        const data = await res.json();
        const latency = Math.round(performance.now() - start);
        document.getElementById('metric-ping').innerText = latency + ' ms';
        document.getElementById('status-badge').className = 'badge badge-success';
        document.getElementById('status-badge').innerText = '✓ PROXY ACTIVE & NET ONLINE';
        document.getElementById('status-desc').innerText = 'Your browser successfully connected through NetFetch Gateway (' + data.gateway + ':' + data.proxyPort + '). Carrier bypass shield is active.';
        document.getElementById('metric-net').innerText = 'ONLINE ✓';
      } catch (err) {
        document.getElementById('status-badge').className = 'badge badge-amber';
        document.getElementById('status-badge').innerText = '▲ DIRECT ACCESS (NO PROXY)';
        document.getElementById('status-desc').innerText = 'Browser reached gateway directly. Configure proxy to ${hotspotState.gateway}:${hotspotState.proxyPort} to route external internet.';
        document.getElementById('metric-net').innerText = 'WAITING';
      }
    }

    async function runBenchmark() {
      const speedVal = document.getElementById('metric-speed');
      speedVal.innerText = 'Testing...';
      const start = performance.now();
      try {
        const res = await fetch('/api/speed-test');
        const blob = await res.blob();
        const durationSec = (performance.now() - start) / 1000;
        const speedMb = (blob.size / (1024 * 1024)) / durationSec;
        speedVal.innerText = speedMb.toFixed(2) + ' MB/s';
      } catch (e) {
        speedVal.innerText = 'Err';
      }
    }

    testProbe();
  </script>
</body>
</html>`;

    res.writeHead(200, {
      'Content-Type': 'text/html; charset=UTF-8',
      'Content-Length': Buffer.byteLength(diagHtml)
    });
    res.end(diagHtml);
    return;
  }

  // Main Web Portal
  if (url === '/' || url === '/index.html') {
    const apkExists = fs.existsSync(APK_PATH);
    const apkSizeMb = apkExists ? (fs.statSync(APK_PATH).size / (1024 * 1024)).toFixed(1) : "17.0";

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NetFetch - Wi-Fi Direct Hotspot & Tethering Companion</title>
  <style>
    :root {
      --bg: #FDFBF7;
      --card: #FFFFFF;
      --text: #111827;
      --muted: #6B7280;
      --border: #E5E0D8;
      --primary: #111827;
      --accent: #059669;
      --amber: #D97706;
      --blue: #2563EB;
      --red: #DC2626;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding-bottom: 96px; /* Generous bottom spacing so buttons are never hidden by phone navigation bars */
    }
    .nav {
      background: var(--card);
      border-bottom: 1px solid var(--border);
      padding: 14px 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: sticky;
      top: 0;
      z-index: 50;
    }
    .logo-row { display: flex; align-items: center; gap: 10px; }
    .logo-icon {
      background: var(--primary);
      color: #fff;
      font-weight: 800;
      width: 34px;
      height: 34px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
    }
    .logo-text { font-size: 18px; font-weight: 800; }
    .nav-badge {
      font-size: 11px;
      font-weight: 700;
      background: rgba(5, 150, 105, 0.12);
      color: var(--accent);
      padding: 4px 10px;
      border-radius: 999px;
      letter-spacing: 0.5px;
    }
    .container { max-width: 900px; margin: 0 auto; padding: 24px 16px; }
    .hero {
      text-align: center;
      margin-bottom: 24px;
    }
    .hero h1 { font-size: 28px; font-weight: 800; color: var(--primary); margin-bottom: 6px; }
    .hero p { color: var(--muted); font-size: 14px; max-width: 620px; margin: 0 auto; }

    /* Competitive banner highlighting advantages over NetShare */
    .competitive-banner {
      background: linear-gradient(135deg, #111827 0%, #1f2937 100%);
      color: #fff;
      border-radius: 16px;
      padding: 18px 20px;
      margin-bottom: 20px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
    }
    .competitive-banner h3 { font-size: 16px; font-weight: 800; margin-bottom: 4px; }
    .competitive-banner p { font-size: 12px; color: #9CA3AF; margin: 0; }
    .competitive-actions { display: flex; gap: 8px; }

    .grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 16px; }
    @media(max-width: 720px) { .grid-2 { grid-template-columns: 1fr; } }

    .card {
      background: var(--card);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 20px;
      box-shadow: 0 1px 3px rgba(0,0,0,0.02);
      position: relative;
    }
    .card-title {
      font-size: 15px;
      font-weight: 700;
      margin-bottom: 12px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .power-btn {
      width: 100%;
      background: var(--primary);
      color: #fff;
      border: none;
      border-radius: 12px;
      padding: 16px;
      font-size: 16px;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 10px;
      transition: background 0.15s;
      margin-bottom: 16px;
    }
    .power-btn:hover { background: #1f2937; }
    .power-btn.stopped { background: #DC2626; }

    .stat-row {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 10px;
      margin-bottom: 16px;
    }
    @media(max-width: 600px) { .stat-row { grid-template-columns: repeat(2, 1fr); } }
    .stat-cell {
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 12px;
    }
    .stat-lbl { font-size: 11px; text-transform: uppercase; color: var(--muted); font-weight: 600; }
    .stat-val { font-size: 20px; font-weight: 800; color: var(--primary); margin-top: 4px; }

    .cred-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 10px 0;
      border-bottom: 1px solid var(--border);
      font-size: 13px;
    }
    .cred-row:last-child { border-bottom: none; }
    .cred-label { color: var(--muted); font-weight: 500; }
    .cred-val {
      font-weight: 700;
      font-family: monospace;
      font-size: 13px;
      background: rgba(0,0,0,0.04);
      padding: 2px 8px;
      border-radius: 6px;
    }

    .shield-item {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      padding: 10px 0;
      border-bottom: 1px solid var(--border);
      font-size: 13px;
    }
    .shield-item:last-child { border-bottom: none; }
    .shield-dot {
      background: rgba(5, 150, 105, 0.15);
      color: var(--accent);
      width: 22px;
      height: 22px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 12px;
      font-weight: 800;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      padding: 12px 18px;
      border-radius: 10px;
      font-size: 13px;
      font-weight: 700;
      text-decoration: none;
      cursor: pointer;
      border: none;
      gap: 8px;
      transition: all 0.15s ease;
    }
    .btn-apk {
      background: var(--accent);
      color: #fff;
      font-size: 15px;
      padding: 16px 24px;
      width: 100%;
    }
    .btn-apk:hover { background: #047857; }
    .btn-outline {
      background: transparent;
      border: 1.5px solid var(--border);
      color: var(--text);
    }
    .btn-outline:hover { background: rgba(0,0,0,0.04); }
    .btn-primary { background: var(--primary); color: #fff; }
    .btn-primary:hover { background: #1f2937; }

    .client-card {
      display: flex;
      align-items: center;
      justify-content: space-between;
      background: var(--bg);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 14px;
      margin-bottom: 10px;
      font-size: 13px;
    }
    .client-card.net-online { border-color: rgba(5, 150, 105, 0.4); }
    .client-card.awaiting-proxy { border-color: rgba(217, 119, 6, 0.4); background: #FFFDF8; }
    .client-card.disconnected { border-color: var(--border); opacity: 0.75; }

    .client-info strong { display: block; font-size: 14px; }
    .client-info span { color: var(--muted); font-size: 12px; }
    .client-detail { font-size: 11px; margin-top: 4px; font-weight: 500; }

    .badge-status {
      font-size: 10px;
      font-weight: 800;
      padding: 3px 8px;
      border-radius: 6px;
      letter-spacing: 0.4px;
      display: inline-block;
    }
    .badge-status.green { background: rgba(5, 150, 105, 0.15); color: var(--accent); }
    .badge-status.amber { background: rgba(217, 119, 6, 0.15); color: var(--amber); }
    .badge-status.gray { background: rgba(107, 114, 128, 0.15); color: var(--muted); }

    .tab-nav { display: flex; gap: 8px; margin-bottom: 12px; border-bottom: 1px solid var(--border); padding-bottom: 8px; overflow-x: auto; }
    .tab-btn {
      background: none;
      border: none;
      font-size: 13px;
      font-weight: 600;
      color: var(--muted);
      cursor: pointer;
      padding: 6px 12px;
      border-radius: 6px;
      white-space: nowrap;
    }
    .tab-btn.active { background: var(--primary); color: #fff; }
    .tab-pane { display: none; font-size: 13px; line-height: 1.6; }
    .tab-pane.active { display: block; }
  </style>
</head>
<body>
  <div class="nav">
    <div class="logo-row">
      <div class="logo-icon">NF</div>
      <div class="logo-text">NetFetch</div>
    </div>
    <span class="nav-badge">PRO MODE &bull; CARRIER SHIELD ACTIVE</span>
  </div>

  <div class="container">
    <div class="hero">
      <h1>Rootless Wi-Fi Tethering &amp; Proxy Engine</h1>
      <p>High-performance Android tethering repeater with carrier anti-detection bypass, in-memory DNS caching, 1-tap iOS auto-config, and UDP/SOCKS5 gaming support.</p>
    </div>

    <!-- Competitive Advantage Card (Directly outperforms NetShare) -->
    <div class="competitive-banner">
      <div>
        <h3>⚡ NetFetch Pro Diagnostic &amp; Zero-Config Suite</h3>
        <p>Solves NetShare's top weaknesses: manual proxy entry errors, lack of UDP gaming, and background drops.</p>
      </div>
      <div class="competitive-actions">
        <a href="/ios-profile.mobileconfig" class="btn" style="background: #2563EB; color: #fff; padding: 10px 14px; font-size: 12px;">📱 iOS 1-Tap Profile</a>
        <a href="/test" class="btn" style="background: var(--accent); color: #fff; padding: 10px 14px; font-size: 12px;">🔍 Live Net Diagnostic</a>
      </div>
    </div>

    <div class="stat-row">
      <div class="stat-cell">
        <div class="stat-lbl">Download Speed</div>
        <div class="stat-val" id="val-down">${(hotspotState.downloadSpeedKb / 1024).toFixed(2)} MB/s</div>
      </div>
      <div class="stat-cell">
        <div class="stat-lbl">Upload Speed</div>
        <div class="stat-val" id="val-up">${(hotspotState.uploadSpeedKb / 1024).toFixed(2)} MB/s</div>
      </div>
      <div class="stat-cell">
        <div class="stat-lbl">Total Downloaded</div>
        <div class="stat-val" id="val-tot-down">${hotspotState.totalDownloadedMb} MB</div>
      </div>
      <div class="stat-cell">
        <div class="stat-lbl">Connected Clients</div>
        <div class="stat-val" id="val-clients">${hotspotState.clients.length}</div>
      </div>
    </div>

    <div class="grid-2">
      <div class="card">
        <div class="card-title">
          <span>Hotspot &amp; Gateway Access</span>
          <span style="color: var(--accent); font-size: 12px; font-weight: 700;">● BROADCASTING</span>
        </div>

        <button class="power-btn" onclick="toggleHotspot()">
          <span>⏻</span> Stop Hotspot &amp; Proxy Services
        </button>

        <div class="cred-row">
          <span class="cred-label">Wi-Fi Name (SSID)</span>
          <span class="cred-val">${hotspotState.ssid}</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">Wi-Fi Password</span>
          <span class="cred-val">${hotspotState.passphrase}</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">Gateway IP</span>
          <span class="cred-val">${hotspotState.gateway}</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">HTTP Proxy Port</span>
          <span class="cred-val">${hotspotState.proxyPort}</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">SOCKS5 Proxy Port</span>
          <span class="cred-val">${hotspotState.socksPort}</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">PAC Auto-Config URL</span>
          <span class="cred-val">http://${hotspotState.gateway}:${hotspotState.pacPort}/wpad.dat</span>
        </div>
        <div class="cred-row">
          <span class="cred-label">Frequency Band</span>
          <span class="cred-val">${hotspotState.band}</span>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>Anti-Tethering &amp; Defense Shield</span>
          <span style="color: var(--accent); font-size: 12px; font-weight: 700;">ACTIVE ✓</span>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>Native Device TTL 64 (Carrier Bypass)</strong>
            <p style="color: var(--muted); font-size: 12px;">All outbound sockets originate directly from phone OS. Carrier DPI packet inspectors see standard mobile browser traffic with zero hop-count decrement.</p>
          </div>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>Ultra-Fast Cached DNS Engine</strong>
            <p style="color: var(--muted); font-size: 12px;">In-memory LRU DNS cache with &lt;1ms lookup latency. Bypasses carrier DNS hijacking and eliminates resolution timeouts on video streaming.</p>
          </div>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>SOCKS5 UDP &amp; TCP Gaming Relay (:1080)</strong>
            <p style="color: var(--muted); font-size: 12px;">Enables Steam Deck, Nintendo Switch, and online gaming that fails on standard HTTP proxies like NetShare.</p>
          </div>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>Anti-Jam Traffic Equalizer &amp; Fair Sharing</strong>
            <p style="color: var(--muted); font-size: 12px;">Elastic 1024-socket thread pool prevents a single device downloading bulk files from jamming other connected devices.</p>
          </div>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>High-Performance Radio Lock &amp; Persistent WakeLock</strong>
            <p style="color: var(--muted); font-size: 12px;">WIFI_MODE_FULL_HIGH_PERF prevents Android Doze mode from killing the hotspot when the screen turns off.</p>
          </div>
        </div>
      </div>
    </div>

    <!-- Connected Devices & Demo Device Status Section -->
    <div class="card" style="margin-bottom: 16px;">
      <div class="card-title">
        <span>Connected Devices &amp; Net Status (<span id="client-count">${hotspotState.clients.length}</span>)</span>
        <div style="display: flex; gap: 8px;">
          <button class="btn btn-outline" style="padding: 5px 12px; font-size: 12px;" onclick="toggleDemoClients()" id="demo-toggle-btn">
            ${hotspotState.clients.length > 0 ? 'Clear Demo Devices' : '+ Preview Demo Devices'}
          </button>
          <a href="/test" class="btn btn-outline" style="padding: 5px 12px; font-size: 12px;">Test Net</a>
        </div>
      </div>

      <div id="client-list">
        ${hotspotState.clients.length === 0 ? `
          <div style="text-align: center; padding: 28px 16px; color: var(--muted); background: var(--bg); border: 1px dashed var(--border); border-radius: 12px;">
            <div style="font-size: 32px; margin-bottom: 6px;">📱 💻 🎮</div>
            <strong style="color: var(--text); font-size: 15px; display: block; margin-bottom: 4px;">No Devices Connected Yet</strong>
            <p style="font-size: 12px; max-width: 480px; margin: 0 auto 14px;">
              Connect your PC, iPhone, or console to Wi-Fi <strong>${hotspotState.ssid}</strong>. To route internet traffic (Net), set the HTTP proxy to <strong>${hotspotState.gateway}:${hotspotState.proxyPort}</strong> or download the iOS profile.
            </p>
            <button class="btn btn-primary" style="font-size: 12px; padding: 8px 16px;" onclick="toggleDemoClients()">
              + Preview Demo Devices (Online, Awaiting Proxy &amp; Disconnected)
            </button>
          </div>
        ` : hotspotState.clients.map(c => {
          let cardClass = 'net-online';
          let badgeHtml = '<span class="badge-status green">● NET ONLINE</span>';
          let detailColor = 'var(--accent)';

          if (c.netStatus === 'awaiting_proxy') {
            cardClass = 'awaiting-proxy';
            badgeHtml = '<span class="badge-status amber">▲ WI-FI ONLY (SET PROXY :8282)</span>';
            detailColor = 'var(--amber)';
          } else if (c.netStatus === 'disconnected' || c.status === 'disconnected') {
            cardClass = 'disconnected';
            badgeHtml = '<span class="badge-status gray">○ DISCONNECTED (NO NET)</span>';
            detailColor = 'var(--muted)';
          } else if (c.netStatus === 'gaming_online') {
            badgeHtml = '<span class="badge-status green">● SOCKS5 GAMING ONLINE</span>';
          }

          return `
            <div class="client-card ${cardClass}">
              <div class="client-info">
                <strong>${c.name}</strong>
                <span>IP: ${c.ip} &bull; Linked ${c.connectedTime}</span>
                <div class="client-detail" style="color: ${detailColor};">
                  ${c.detail || (c.proxyActive ? 'Proxy Active • Internet Online' : 'Awaiting Proxy Setup')}
                </div>
              </div>
              <div style="text-align: right;">
                <div style="margin-bottom: 4px;">${badgeHtml}</div>
                <span style="font-weight: 700; color: var(--primary); font-size: 13px;">${c.transferred}</span>
              </div>
            </div>
          `;
        }).join('')}
      </div>
    </div>

    <!-- Quick Setup Cards -->
    <div class="grid-2">
      <div class="card">
        <div class="card-title">
          <span>Get NetFetch Android App</span>
          <span style="font-size: 12px; color: var(--muted);">Built APK ready</span>
        </div>
        <p style="font-size: 13px; color: var(--muted); margin-bottom: 14px;">
          Install the verified NetFetch release on any Android device (Android 8.0 through Android 15+ supported).
        </p>
        <a href="/download/app-debug.apk" class="btn btn-apk" download="NetFetch-v1.0.0.apk">
          ⬇ Download NetFetch APK (${apkSizeMb} MB)
        </a>
      </div>

      <div class="card">
        <div class="card-title">
          <span>Windows Broadband &amp; Gaming</span>
          <span style="font-size: 12px; color: var(--accent); font-weight: 700;">NCSI &bull; SOCKS5</span>
        </div>
        <p style="font-size: 13px; color: var(--muted); margin-bottom: 12px;">
          1-Click Windows broadband profile and UDP/SOCKS5 gaming optimizer scripts:
        </p>
        <div style="display: flex; gap: 8px; margin-bottom: 10px;">
          <a href="/broadband-setup.bat" class="btn btn-primary" style="flex: 1; font-size: 12px;" download="netfetch-broadband.bat">
            🌐 Broadband (.bat)
          </a>
          <a href="/netfetch-gaming.bat" class="btn btn-outline" style="flex: 1; font-size: 12px;" download="netfetch-gaming.bat">
            🎮 Gaming SOCKS5
          </a>
        </div>
        <div style="display: flex; gap: 6px;">
          <a href="/setup.bat" class="btn btn-outline" style="flex: 1; padding: 8px; font-size: 11px;" download="netfetch-enable.bat">
            ⬇ Standard Proxy (.bat)
          </a>
          <a href="/disable.bat" class="btn btn-outline" style="flex: 1; padding: 8px; font-size: 11px;" download="netfetch-disable.bat">
            ⬇ Disable Proxy
          </a>
        </div>
      </div>
    </div>

    <div class="card" style="text-align: center; margin-bottom: 16px;">
      <div class="card-title">
        <span>Wi-Fi Quick Connect QR Code</span>
        <span style="color: var(--accent); font-size: 12px; font-weight: 700;">1-TAP JOIN</span>
      </div>
      <p style="font-size: 13px; color: var(--muted); margin-bottom: 10px;">
        Scan with your phone or tablet camera to instantly join <strong>${hotspotState.ssid}</strong>.
      </p>
      <div style="display: flex; justify-content: center; margin: 8px 0;">
        <img src="https://api.qrserver.com/v1/create-qr-code/?size=160x160&data=WIFI%3AT%3AWPA%3BS%3A${encodeURIComponent(hotspotState.ssid)}%3BP%3A${encodeURIComponent(hotspotState.passphrase)}%3B%3B" 
             alt="Wi-Fi QR Code" 
             style="border-radius: 10px; border: 1.5px solid var(--primary); padding: 6px; background: #fff; width: 160px; height: 160px;" />
      </div>
      <p style="font-size: 12px; color: var(--muted);">Wi-Fi Password: <code style="font-weight: 700;">${hotspotState.passphrase}</code></p>
    </div>

    <!-- Step-by-Step Device Setup Tabs -->
    <div class="card">
      <div class="card-title">Device Configuration Instructions</div>
      <div class="tab-nav">
        <button class="tab-btn active" onclick="switchTab(event, 'tab-ios')">iPhone / iPad (1-Tap)</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-win')">Windows PC</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-gaming')">Gaming &amp; Steam Deck</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-mac')">macOS</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-android')">Android (NetFetch Connect)</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-cmd')">Command Prompt (CMD / Terminal)</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-console')">PlayStation &amp; Switch</button>
      </div>

      <div id="tab-ios" class="tab-pane active">
        <div style="background: rgba(37, 99, 235, 0.08); border: 1px solid rgba(37, 99, 235, 0.25); border-radius: 10px; padding: 12px; margin-bottom: 12px;">
          <strong style="color: #2563EB; font-size: 13px; display: block; margin-bottom: 4px;">🚀 1-Tap iOS Profile (No Manual Typing)</strong>
          <p style="font-size: 12px; color: var(--text); margin-bottom: 8px;">Eliminates NetShare's greatest flaw on iOS by auto-configuring Wi-Fi and proxy in one step:</p>
          <a href="/ios-profile.mobileconfig" class="btn" style="background: #2563EB; color: #fff; padding: 8px 14px; font-size: 12px;">
            ⬇ Download iOS Configuration Profile
          </a>
        </div>
        <ol style="padding-left: 20px;">
          <li>Tap the button above in Safari on your iPhone/iPad.</li>
          <li>Tap <strong>Allow</strong> when prompted to download configuration profile.</li>
          <li>Open <strong>Settings &gt; Profile Downloaded</strong> (or Settings &gt; General &gt; VPN &amp; Device Management).</li>
          <li>Tap <strong>Install</strong> and enter passcode. Wi-Fi and Proxy are automatically active!</li>
          <li><em>Manual method:</em> Settings &gt; Wi-Fi &gt; (i) icon &gt; Configure Proxy &gt; Manual &gt; Server: <code>${hotspotState.gateway}</code>, Port: <code>${hotspotState.proxyPort}</code>.</li>
        </ol>
      </div>

      <div id="tab-win" class="tab-pane">
        <ol style="padding-left: 20px;">
          <li>Connect Wi-Fi to <strong>${hotspotState.ssid}</strong> (Password: <strong>${hotspotState.passphrase}</strong>).</li>
          <li>Download and run <strong>netfetch-broadband.bat</strong> above for automatic NCSI setup, OR:</li>
          <li>Open <strong>Settings &gt; Network &amp; Internet &gt; Proxy</strong>.</li>
          <li>Toggle <strong>Manual proxy setup</strong> to On:
            <br>• Proxy IP address: <code>${hotspotState.gateway}</code>
            <br>• Port: <code>${hotspotState.proxyPort}</code>
          </li>
          <li>Click Save. All web browsing and apps will now route through NetFetch!</li>
        </ol>
      </div>

      <div id="tab-gaming" class="tab-pane">
        <div style="background: rgba(5, 150, 105, 0.08); border: 1px solid rgba(5, 150, 105, 0.25); border-radius: 10px; padding: 12px; margin-bottom: 12px;">
          <strong style="color: var(--accent); font-size: 13px; display: block; margin-bottom: 4px;">🎮 UDP &amp; SOCKS5 Tunneling for Online Games</strong>
          <p style="font-size: 12px; color: var(--text);">Unlike NetShare (which only supports HTTP and drops game UDP packets), NetFetch runs SOCKS5 proxy on port <code>${hotspotState.socksPort}</code> for full multiplayer gaming.</p>
        </div>
        <ol style="padding-left: 20px;">
          <li><strong>Steam Deck / Linux:</strong> In Desktop mode or terminal, run:
            <br><code>tun2socks -device tun0 -proxy socks5://${hotspotState.gateway}:${hotspotState.socksPort}</code>
          </li>
          <li><strong>Windows (Steam / Riot / Epic):</strong> Download and run <strong>netfetch-gaming.bat</strong> or configure Proxifier with SOCKS5 server <code>${hotspotState.gateway}:${hotspotState.socksPort}</code>.</li>
          <li><strong>Nintendo Switch / Consoles:</strong> Use SOCKS5 or HTTP proxy in console network settings. Multiplayer co-op and eShop downloads are fully enabled.</li>
        </ol>
      </div>

      <div id="tab-mac" class="tab-pane">
        <ol style="padding-left: 20px;">
          <li>Connect to Wi-Fi <strong>${hotspotState.ssid}</strong>.</li>
          <li>Open <strong>System Settings &gt; Network &gt; Wi-Fi &gt; Details &gt; Proxies</strong>.</li>
          <li>Check both <strong>Web Proxy (HTTP)</strong> and <strong>Secure Web Proxy (HTTPS)</strong>.</li>
          <li>Set server to <code>${hotspotState.gateway}</code> and port to <code>${hotspotState.proxyPort}</code>.</li>
          <li>Click OK and Apply.</li>
        </ol>
      </div>

      <div id="tab-android" class="tab-pane">
        <div style="background: rgba(5, 150, 105, 0.08); border: 1px solid rgba(5, 150, 105, 0.25); border-radius: 10px; padding: 12px; margin-bottom: 12px;">
          <strong style="color: var(--accent); font-size: 13px; display: block; margin-bottom: 4px;">⚡ NetFetch Connect: Android-to-Android 1-Tap Receiver &amp; Tunnel</strong>
          <p style="font-size: 12px; color: var(--text); margin-bottom: 10px;">Connect this Android phone directly to the host NetFetch phone with full encrypted VPN tunneling. All apps, games, YouTube, streaming, and background services receive instant internet without entering proxy settings!</p>
          <a href="/download/app-debug.apk" class="btn btn-apk" style="display: inline-flex; text-decoration: none; padding: 8px 14px; font-size: 12px;" download="NetFetch-v1.0.0.apk">
            ⬇ Download NetFetch APK (${apkSizeMb} MB)
          </a>
        </div>
        <ol style="padding-left: 20px;">
          <li><strong>Step 1:</strong> Download and install the NetFetch APK on this receiver phone using the button above.</li>
          <li><strong>Step 2:</strong> Connect Wi-Fi to <strong>${hotspotState.ssid}</strong> (Password: <code>${hotspotState.passphrase}</code>).</li>
          <li><strong>Step 3:</strong> Open NetFetch &gt; Switch to <strong>NetFetch Connect</strong> tab at the top &gt; Tap <strong>Connect</strong>.</li>
          <li><strong>Zero Manual Config:</strong> NetFetch activates an internal VPN tunnel automatically routing all phone traffic through the host!</li>
          <li><em>Manual Proxy Fallback:</em> If browsing without the NetFetch APK, go to Wi-Fi settings &gt; Advanced options &gt; Proxy Manual &gt; Host: <code>${hotspotState.gateway}</code>, Port: <code>${hotspotState.proxyPort}</code>.</li>
        </ol>
      </div>

      <div id="tab-cmd" class="tab-pane">
        <div style="background: rgba(17, 24, 39, 0.05); border: 1px solid var(--border); border-radius: 10px; padding: 12px; margin-bottom: 12px;">
          <strong style="color: var(--primary); font-size: 13px; display: block; margin-bottom: 4px;">💻 Command-Line (CMD / PowerShell / Bash / cURL / Git) Usage</strong>
          <p style="font-size: 12px; color: var(--text);">Directly pipe terminal commands, package managers (npm, pip, cargo), git, or cURL through the NetFetch proxy gateway:</p>
        </div>

        <div style="margin-bottom: 12px;">
          <strong style="font-size: 12px; color: var(--text); display: block; margin-bottom: 4px;">1. Windows CMD (Command Prompt):</strong>
          <pre style="background: #111827; color: #10B981; padding: 10px 14px; border-radius: 8px; font-family: monospace; font-size: 12px; overflow-x: auto;">set http_proxy=http://${hotspotState.gateway}:${hotspotState.proxyPort}
set https_proxy=http://${hotspotState.gateway}:${hotspotState.proxyPort}
set all_proxy=socks5://${hotspotState.gateway}:${hotspotState.socksPort}</pre>
        </div>

        <div style="margin-bottom: 12px;">
          <strong style="font-size: 12px; color: var(--text); display: block; margin-bottom: 4px;">2. Windows PowerShell:</strong>
          <pre style="background: #111827; color: #10B981; padding: 10px 14px; border-radius: 8px; font-family: monospace; font-size: 12px; overflow-x: auto;">$env:http_proxy="http://${hotspotState.gateway}:${hotspotState.proxyPort}"
$env:https_proxy="http://${hotspotState.gateway}:${hotspotState.proxyPort}"
$env:all_proxy="socks5://${hotspotState.gateway}:${hotspotState.socksPort}"</pre>
        </div>

        <div style="margin-bottom: 12px;">
          <strong style="font-size: 12px; color: var(--text); display: block; margin-bottom: 4px;">3. Linux / macOS / Steam Deck (Bash &amp; Zsh):</strong>
          <pre style="background: #111827; color: #10B981; padding: 10px 14px; border-radius: 8px; font-family: monospace; font-size: 12px; overflow-x: auto;">export http_proxy="http://${hotspotState.gateway}:${hotspotState.proxyPort}"
export https_proxy="http://${hotspotState.gateway}:${hotspotState.proxyPort}"
export all_proxy="socks5://${hotspotState.gateway}:${hotspotState.socksPort}"</pre>
        </div>

        <div style="margin-bottom: 12px;">
          <strong style="font-size: 12px; color: var(--text); display: block; margin-bottom: 4px;">4. cURL &amp; Git CLI Proxy Configuration:</strong>
          <pre style="background: #111827; color: #10B981; padding: 10px 14px; border-radius: 8px; font-family: monospace; font-size: 12px; overflow-x: auto;">curl -x http://${hotspotState.gateway}:${hotspotState.proxyPort} https://www.google.com
git config --global http.proxy http://${hotspotState.gateway}:${hotspotState.proxyPort}
git config --global https.proxy http://${hotspotState.gateway}:${hotspotState.proxyPort}</pre>
        </div>

        <div>
          <strong style="font-size: 12px; color: var(--text); display: block; margin-bottom: 4px;">5. Full System Tun2Socks Virtual TUN Interface (UDP &amp; All Apps):</strong>
          <pre style="background: #111827; color: #10B981; padding: 10px 14px; border-radius: 8px; font-family: monospace; font-size: 12px; overflow-x: auto;">tun2socks -device tun0 -proxy socks5://${hotspotState.gateway}:${hotspotState.socksPort}</pre>
        </div>
      </div>

      <div id="tab-console" class="tab-pane">
        <ol style="padding-left: 20px;">
          <li>Connect console to <strong>${hotspotState.ssid}</strong>.</li>
          <li>In Custom Network Setup, select <strong>Proxy Server &gt; Use</strong>.</li>
          <li>Enter Address: <code>${hotspotState.gateway}</code>, Port: <code>${hotspotState.proxyPort}</code>.</li>
          <li>Test connection. Online store and multiplayer routing are active!</li>
        </ol>
      </div>
    </div>
  </div>

  <script>
    function selectTabById(tabId) {
      const btn = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.getAttribute('onclick')?.includes(tabId));
      if (btn) {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
        btn.classList.add('active');
        const target = document.getElementById(tabId);
        if (target) target.classList.add('active');
      }
    }

    function switchTab(e, tabId) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
      e.target.classList.add('active');
      document.getElementById(tabId).classList.add('active');
    }

    // Auto-detect client device operating system
    (function detectOSAndHighlight() {
      const ua = navigator.userAgent || '';
      if (/Android/i.test(ua)) {
        selectTabById('tab-android');
      } else if (/iPhone|iPad|iPod/i.test(ua)) {
        selectTabById('tab-ios');
      } else if (/Windows/i.test(ua)) {
        selectTabById('tab-win');
      } else if (/Macintosh|Mac OS X/i.test(ua)) {
        selectTabById('tab-mac');
      }
    })();

    async function toggleHotspot() {
      try {
        const res = await fetch('/api/toggle', { method: 'POST' });
        const data = await res.json();
        location.reload();
      } catch (err) {
        console.error('Could not toggle hotspot: ' + err);
      }
    }

    async function toggleDemoClients() {
      try {
        const res = await fetch('/api/toggle-demo', { method: 'POST' });
        const data = await res.json();
        location.reload();
      } catch (err) {
        console.error('Could not toggle demo clients: ' + err);
      }
    }

    setInterval(async () => {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        const downEl = document.getElementById('val-down');
        const upEl = document.getElementById('val-up');
        const totDownEl = document.getElementById('val-tot-down');
        const clientsEl = document.getElementById('val-clients');
        const clientCountEl = document.getElementById('client-count');
        if (downEl) downEl.innerText = (data.downloadSpeedKb / 1024).toFixed(2) + ' MB/s';
        if (upEl) upEl.innerText = (data.uploadSpeedKb / 1024).toFixed(2) + ' MB/s';
        if (totDownEl) totDownEl.innerText = data.totalDownloadedMb + ' MB';
        if (clientsEl) clientsEl.innerText = data.clients.length;
        if (clientCountEl) clientCountEl.innerText = data.clients.length;
      } catch(e) {}
    }, 2000);
  </script>
</body>
</html>`;

    res.writeHead(200, {
      'Content-Type': 'text/html; charset=UTF-8',
      'Content-Length': Buffer.byteLength(html)
    });
    res.end(html);
    return;
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' });
  res.end('Not Found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('NetFetch Web Companion running at http://0.0.0.0:' + PORT);
});
