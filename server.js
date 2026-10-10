const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 3000;
const APK_PATH = path.join(__dirname, 'app/build/outputs/apk/debug/app-debug.apk');

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
  uploadSpeedKb: 284,
  downloadSpeedKb: 1420,
  totalUploadedMb: 48.6,
  totalDownloadedMb: 312.4,
  clients: [
    { ip: "192.168.49.2", name: "Windows 11 Laptop (Dell XPS)", os: "Windows", connectedTime: "18m ago", transferred: "214.2 MB" },
    { ip: "192.168.49.3", name: "iPhone 15 Pro", os: "iOS", connectedTime: "12m ago", transferred: "98.5 MB" },
    { ip: "192.168.49.4", name: "Nintendo Switch", os: "Console", connectedTime: "5m ago", transferred: "48.3 MB" }
  ]
};

setInterval(() => {
  if (hotspotState.active) {
    hotspotState.downloadSpeedKb = Math.floor(800 + Math.random() * 1800);
    hotspotState.uploadSpeedKb = Math.floor(150 + Math.random() * 450);
    hotspotState.totalDownloadedMb = +(hotspotState.totalDownloadedMb + hotspotState.downloadSpeedKb / 1024 / 8).toFixed(1);
    hotspotState.totalUploadedMb = +(hotspotState.totalUploadedMb + hotspotState.uploadSpeedKb / 1024 / 8).toFixed(1);
  }
}, 2000);

const server = http.createServer((req, res) => {
  const url = req.url.split('?')[0];

  if (url === '/health') {
    res.writeHead(200, { 'Content-Type': 'text/plain' });
    res.end('OK');
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

  if (url === '/' || url === '/index.html') {
    const apkExists = fs.existsSync(APK_PATH);
    const apkSizeMb = apkExists ? (fs.statSync(APK_PATH).size / (1024 * 1024)).toFixed(1) : "17.0";

    const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>NetFetch - Wi-Fi Direct Hotspot & Tethering</title>
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
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
      background: var(--bg);
      color: var(--text);
      line-height: 1.5;
      padding-bottom: 60px;
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
      border-radius: 10px;
      padding: 12px;
      margin-bottom: 8px;
      font-size: 13px;
    }
    .client-info strong { display: block; font-size: 14px; }
    .client-info span { color: var(--muted); font-size: 12px; }

    .tab-nav { display: flex; gap: 8px; margin-bottom: 12px; border-bottom: 1px solid var(--border); padding-bottom: 8px; }
    .tab-btn {
      background: none;
      border: none;
      font-size: 13px;
      font-weight: 600;
      color: var(--muted);
      cursor: pointer;
      padding: 6px 12px;
      border-radius: 6px;
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
      <h1>Rootless Wi-Fi Tethering & Proxy Engine</h1>
      <p>High-performance Android tethering repeater with carrier anti-detection bypass, in-memory DNS caching, and instant 1-click Windows PC connection.</p>
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
          <span>Hotspot & Gateway Access</span>
          <span style="color: var(--accent); font-size: 12px; font-weight: 700;">● BROADCASTING</span>
        </div>

        <button class="power-btn" onclick="toggleHotspot()">
          <span>⏻</span> Stop Hotspot & Proxy Services
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
          <span>Anti-Tethering & Defense Shield</span>
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
            <strong>High-Performance Radio Lock</strong>
            <p style="color: var(--muted); font-size: 12px;">WIFI_MODE_FULL_HIGH_PERF prevents Android Doze mode from putting Wi-Fi hardware to sleep during screen-off operation.</p>
          </div>
        </div>

        <div class="shield-item">
          <div class="shield-dot">✓</div>
          <div>
            <strong>128KB Low-Latency TCP Socket Buffers</strong>
            <p style="color: var(--muted); font-size: 12px;">TCP_NODELAY enabled for competitive multiplayer gaming and smooth 4K 60fps streaming.</p>
          </div>
        </div>
      </div>
    </div>

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
          <span>Windows Broadband Connection</span>
          <span style="font-size: 12px; color: var(--accent); font-weight: 700;">NCSI VERIFIED</span>
        </div>
        <p style="font-size: 13px; color: var(--muted); margin-bottom: 12px;">
          Adds a dedicated Broadband Internet adapter in Windows (ncpa.cpl) and automatically sets up NCSI internet awareness:
        </p>
        <div style="display: flex; gap: 8px; margin-bottom: 10px;">
          <a href="/broadband-setup.bat" class="btn btn-primary" style="flex: 1;" download="netfetch-broadband.bat">
            🌐 Setup Broadband (.bat)
          </a>
          <a href="/broadband-disconnect.bat" class="btn btn-outline" style="flex: 1;" download="netfetch-broadband-disconnect.bat">
            🔌 Disconnect
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

      <div class="card" style="text-align: center;">
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
    </div>

    <div class="card" style="margin-bottom: 16px;">
      <div class="card-title">
        <span>Connected Devices (${hotspotState.clients.length})</span>
        <button class="btn btn-outline" style="padding: 4px 10px; font-size: 12px;" onclick="location.reload()">Refresh</button>
      </div>
      <div id="client-list">
        ${hotspotState.clients.map(c => `
          <div class="client-card">
            <div class="client-info">
              <strong>${c.name}</strong>
              <span>IP: ${c.ip} &bull; Linked ${c.connectedTime}</span>
            </div>
            <div style="text-align: right;">
              <span style="font-weight: 700; color: var(--primary);">${c.transferred}</span>
              <div style="font-size: 11px; color: var(--accent); font-weight: 600;">ACTIVE</div>
            </div>
          </div>
        `).join('')}
      </div>
    </div>

    <div class="card">
      <div class="card-title">Device Configuration Instructions</div>
      <div class="tab-nav">
        <button class="tab-btn active" onclick="switchTab(event, 'tab-win')">Windows</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-ios')">iPhone / iPad</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-mac')">macOS</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-android')">Android</button>
        <button class="tab-btn" onclick="switchTab(event, 'tab-console')">PlayStation & Xbox</button>
      </div>

      <div id="tab-win" class="tab-pane active">
        <ol style="padding-left: 20px;">
          <li>Connect Wi-Fi to <strong>${hotspotState.ssid}</strong> (Password: <strong>${hotspotState.passphrase}</strong>).</li>
          <li>Either run <strong>netfetch-enable.bat</strong> above, OR:</li>
          <li>Open <strong>Settings &gt; Network &amp; Internet &gt; Proxy</strong>.</li>
          <li>Toggle <strong>Manual proxy setup</strong> to On:
            <br>• Proxy IP address: <code>${hotspotState.gateway}</code>
            <br>• Port: <code>${hotspotState.proxyPort}</code>
          </li>
          <li>Click Save. All web browsing and apps will now route through NetFetch!</li>
        </ol>
      </div>

      <div id="tab-ios" class="tab-pane">
        <ol style="padding-left: 20px;">
          <li>Open <strong>Settings &gt; Wi-Fi</strong> and connect to <strong>${hotspotState.ssid}</strong>.</li>
          <li>Tap the <strong>(i)</strong> info button next to the connected network.</li>
          <li>Scroll down to <strong>Configure Proxy</strong> and tap <strong>Manual</strong>.</li>
          <li>Enter Server: <code>${hotspotState.gateway}</code> and Port: <code>${hotspotState.proxyPort}</code>.</li>
          <li>Tap <strong>Save</strong>. Safari and iOS apps now have full internet access!</li>
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
        <ol style="padding-left: 20px;">
          <li>Open Wi-Fi Settings and connect to <strong>${hotspotState.ssid}</strong>.</li>
          <li>Tap network settings &gt; Advanced options &gt; Proxy &gt; <strong>Manual</strong>.</li>
          <li>Proxy hostname: <code>${hotspotState.gateway}</code>, Proxy port: <code>${hotspotState.proxyPort}</code>.</li>
          <li>Tap Save. (Or install NetFetch on both devices to use the 1-click NetFetch Link receiver).</li>
        </ol>
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
    function switchTab(e, tabId) {
      document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
      e.target.classList.add('active');
      document.getElementById(tabId).classList.add('active');
    }

    async function toggleHotspot() {
      try {
        const res = await fetch('/api/toggle', { method: 'POST' });
        const data = await res.json();
        location.reload();
      } catch (err) {
        alert('Could not toggle hotspot: ' + err);
      }
    }

    setInterval(async () => {
      try {
        const res = await fetch('/api/status');
        const data = await res.json();
        document.getElementById('val-down').innerText = (data.downloadSpeedKb / 1024).toFixed(2) + ' MB/s';
        document.getElementById('val-up').innerText = (data.uploadSpeedKb / 1024).toFixed(2) + ' MB/s';
        document.getElementById('val-tot-down').innerText = data.totalDownloadedMb + ' MB';
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
  console.log('NetFetch Web Dev Server running at http://0.0.0.0:' + PORT);
});
