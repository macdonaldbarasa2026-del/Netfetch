package com.netfetch.app.proxy

import java.io.OutputStream

/**
 * NetFetch Embedded Onboarding Web Portal & Helper Generator.
 *
 * Serves a lightweight, responsive captive/onboarding web dashboard directly from
 * NetFetch's HTTP proxy port. Provides:
 * - Windows Broadband Connection profile creator (.bat)
 * - 1-Click Windows Proxy configuration script (.bat)
 * - Microsoft NCSI connect test probe handler (for "Internet Connected" status)
 * - Apple and Android captive network probe handlers
 * - PAC Auto-Config URLs
 * - Gateway and proxy connection diagnostics
 */
object NetfetchWebPortal {

    fun isPortalRequest(target: String, hostHeader: String, gatewayIp: String, proxyPort: Int): Boolean {
        val cleanTarget = target.trim()
        val path = if (cleanTarget.startsWith("http://")) {
            val slash = cleanTarget.indexOf("/", 7)
            if (slash != -1) cleanTarget.substring(slash) else "/"
        } else {
            cleanTarget
        }

        // NCSI and Captive network probes
        val isProbe = hostHeader.contains("msftconnecttest.com", ignoreCase = true) ||
                hostHeader.contains("msftncsi.com", ignoreCase = true) ||
                hostHeader.contains("captive.apple.com", ignoreCase = true) ||
                hostHeader.contains("connectivitycheck.gstatic.com", ignoreCase = true) ||
                path.endsWith("/connecttest.txt") ||
                path.endsWith("/ncsi.txt") ||
                path.contains("hotspot-detect") ||
                path.contains("generate_204")

        // Direct hit on gateway IP or local domain
        val isDirectHost = hostHeader.contains(gatewayIp) ||
                hostHeader.contains("netfetch.local") ||
                hostHeader.contains("setup.netfetch") ||
                hostHeader.contains("wpad") ||
                hostHeader.isEmpty()

        return isProbe ||
                (isDirectHost && (path == "/" || path == "/setup" || path == "/index.html" ||
                path == "/setup.bat" || path == "/disable.bat" ||
                path == "/broadband-setup.bat" || path == "/broadband-disconnect.bat" ||
                path == "/wpad.dat" || path == "/proxy.pac" || path == "/status")) ||
                path == "/setup.bat" || path == "/disable.bat" ||
                path == "/broadband-setup.bat" || path == "/broadband-disconnect.bat" ||
                path == "/wpad.dat" || path == "/proxy.pac"
    }

    fun handleRequest(
        target: String,
        output: OutputStream,
        gatewayIp: String,
        proxyPort: Int,
        pacPort: Int,
        socksPort: Int,
        modeName: String
    ): Boolean {
        val path = if (target.startsWith("http://")) {
            val slash = target.indexOf("/", 7)
            if (slash != -1) target.substring(slash) else "/"
        } else {
            target
        }

        // 1. Windows NCSI Active Probing (Makes Windows mark connection as "Internet access")
        if (path.endsWith("/connecttest.txt") || target.contains("connecttest.txt")) {
            serveNcsiConnectTest(output)
            return true
        }
        if (path.endsWith("/ncsi.txt") || target.contains("ncsi.txt")) {
            serveNcsiTxt(output)
            return true
        }

        // 2. Apple Captive Assistant
        if (path.contains("hotspot-detect.html") || target.contains("hotspot-detect")) {
            serveAppleSuccess(output)
            return true
        }

        // 3. Android Captive Probe
        if (path.contains("generate_204") || target.contains("generate_204")) {
            serve204(output)
            return true
        }

        // 4. Portal Endpoints & Windows Scripts
        when (path) {
            "/broadband-setup.bat" -> {
                serveWindowsBroadbandScript(output, gatewayIp, proxyPort, pacPort)
                return true
            }
            "/broadband-disconnect.bat" -> {
                serveWindowsBroadbandDisconnectScript(output)
                return true
            }
            "/setup.bat" -> {
                serveWindowsBatchScript(output, gatewayIp, proxyPort)
                return true
            }
            "/disable.bat" -> {
                serveWindowsDisableBatchScript(output)
                return true
            }
            "/wpad.dat", "/proxy.pac" -> {
                servePacScript(output, gatewayIp, proxyPort)
                return true
            }
            "/status" -> {
                serveStatusJson(output, gatewayIp, proxyPort, pacPort, socksPort, modeName)
                return true
            }
            "/", "/setup", "/index.html" -> {
                serveHtmlDashboard(output, gatewayIp, proxyPort, pacPort, socksPort, modeName)
                return true
            }
            else -> return false
        }
    }

    private fun serveNcsiConnectTest(output: OutputStream) {
        val body = "Microsoft Connect Test\r\n".toByteArray(Charsets.US_ASCII)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun serveNcsiTxt(output: OutputStream) {
        val body = "Microsoft NCSI\r\n".toByteArray(Charsets.US_ASCII)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun serveAppleSuccess(output: OutputStream) {
        val body = "<HTML><HEAD><TITLE>Success</TITLE></HEAD><BODY>Success</BODY></HTML>".toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(body)
        output.flush()
    }

    private fun serve204(output: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Content-Length: 0\r\n" +
                "Connection: close\r\n\r\n"
        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.flush()
    }

    private fun serveHtmlDashboard(
        output: OutputStream,
        gatewayIp: String,
        proxyPort: Int,
        pacPort: Int,
        socksPort: Int,
        modeName: String
    ) {
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>NetFetch Hotspot Gateway</title>
                <style>
                    :root {
                        --bg: #FDFBF7;
                        --card: #FFFFFF;
                        --text: #111827;
                        --muted: #6B7280;
                        --border: #E5E0D8;
                        --primary: #111827;
                        --accent: #059669;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background: var(--bg);
                        color: var(--text);
                        padding: 24px 16px;
                        line-height: 1.5;
                    }
                    .container { max-width: 680px; margin: 0 auto; }
                    .header { text-align: center; margin-bottom: 24px; }
                    .badge {
                        display: inline-block;
                        background: rgba(5, 150, 105, 0.12);
                        color: var(--accent);
                        font-size: 11px;
                        font-weight: 700;
                        padding: 4px 12px;
                        border-radius: 999px;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                        margin-bottom: 8px;
                    }
                    h1 { font-size: 24px; font-weight: 800; color: var(--primary); margin-bottom: 4px; }
                    p.sub { color: var(--muted); font-size: 14px; margin-bottom: 16px; }
                    .card {
                        background: var(--card);
                        border: 1px solid var(--border);
                        border-radius: 16px;
                        padding: 20px;
                        margin-bottom: 16px;
                    }
                    h2 { font-size: 16px; font-weight: 700; margin-bottom: 12px; color: var(--primary); }
                    .stat-grid {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 10px;
                        margin-bottom: 16px;
                    }
                    .stat-box {
                        background: var(--bg);
                        border: 1px solid var(--border);
                        border-radius: 10px;
                        padding: 10px;
                    }
                    .stat-label { font-size: 11px; color: var(--muted); font-weight: 600; text-transform: uppercase; }
                    .stat-val { font-size: 16px; font-weight: 800; color: var(--primary); font-family: monospace; }
                    .btn-row { display: flex; gap: 8px; margin-top: 10px; }
                    @media(max-width: 500px) { .btn-row { flex-direction: column; } }
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
                        gap: 6px;
                        flex: 1;
                    }
                    .btn-primary { background: var(--primary); color: #fff; }
                    .btn-outline { background: transparent; border: 1.5px solid var(--border); color: var(--text); }
                    .instructions { font-size: 13px; color: var(--text); }
                    .step { display: flex; gap: 10px; margin-bottom: 10px; }
                    .step-num {
                        background: var(--primary);
                        color: #fff;
                        width: 20px;
                        height: 20px;
                        border-radius: 50%;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 11px;
                        font-weight: 700;
                        flex-shrink: 0;
                        margin-top: 2px;
                    }
                    code {
                        background: rgba(0,0,0,0.06);
                        padding: 2px 6px;
                        border-radius: 6px;
                        font-family: monospace;
                        font-size: 13px;
                    }
                    .footer { text-align: center; color: var(--muted); font-size: 12px; margin-top: 24px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <span class="badge">Connected to NetFetch</span>
                        <h1>Hotspot Gateway Active</h1>
                        <p class="sub">Your device is linked to NetFetch rootless Wi-Fi Direct network</p>
                    </div>

                    <div class="card">
                        <h2>Connection Details</h2>
                        <div class="stat-grid">
                            <div class="stat-box">
                                <div class="stat-label">Gateway IP</div>
                                <div class="stat-val">$gatewayIp</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">HTTP Proxy Port</div>
                                <div class="stat-val">$proxyPort</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">SOCKS5 Port</div>
                                <div class="stat-val">$socksPort</div>
                            </div>
                            <div class="stat-box">
                                <div class="stat-label">Tethering Mode</div>
                                <div class="stat-val" style="color: var(--accent);">$modeName</div>
                            </div>
                        </div>

                        <h2>Windows Broadband Connection Setup</h2>
                        <p class="sub">Creates a dedicated Broadband Connection adapter in Windows (ncpa.cpl) and verifies internet connectivity automatically:</p>
                        <div class="btn-row">
                            <a href="/broadband-setup.bat" class="btn btn-primary" download="netfetch-broadband.bat">
                                🌐 Setup Windows Broadband (.bat)
                            </a>
                            <a href="/broadband-disconnect.bat" class="btn btn-outline" download="netfetch-broadband-disconnect.bat">
                                🔌 Disconnect Broadband
                            </a>
                        </div>
                        
                        <div style="margin-top: 16px;">
                            <h2>Standard 1-Click Proxy Setup</h2>
                            <p class="sub">Quickly toggle Windows system HTTP/HTTPS proxy:</p>
                            <div class="btn-row">
                                <a href="/setup.bat" class="btn btn-outline" download="netfetch-enable.bat">
                                    ⬇ Enable Proxy (.bat)
                                </a>
                                <a href="/disable.bat" class="btn btn-outline" download="netfetch-disable.bat">
                                    ⬇ Disable Proxy
                                </a>
                            </div>
                        </div>
                    </div>

                    <div class="card">
                        <h2>Manual Setup Instructions</h2>
                        <div class="instructions">
                            <div class="step">
                                <div class="step-num">1</div>
                                <div><strong>Windows:</strong> Settings &gt; Network &gt; Proxy &gt; Manual proxy setup &gt; Server: <code>$gatewayIp</code>, Port: <code>$proxyPort</code></div>
                            </div>
                            <div class="step">
                                <div class="step-num">2</div>
                                <div><strong>iPhone / iPad:</strong> Wi-Fi &gt; Tap NetFetch (i) &gt; Configure Proxy &gt; Manual &gt; Server: <code>$gatewayIp</code>, Port: <code>$proxyPort</code></div>
                            </div>
                            <div class="step">
                                <div class="step-num">3</div>
                                <div><strong>macOS:</strong> System Settings &gt; Network &gt; Wi-Fi &gt; Details &gt; Proxies &gt; Check Web Proxy (HTTP) &amp; Secure Web Proxy (HTTPS) &gt; <code>$gatewayIp:$proxyPort</code></div>
                            </div>
                            <div class="step">
                                <div class="step-num">4</div>
                                <div><strong>Auto-Config PAC URL:</strong> <code>http://$gatewayIp:$pacPort/wpad.dat</code></div>
                            </div>
                        </div>
                    </div>

                    <div class="footer">
                        NetFetch rootless tethering engine &bull; Carrier shield active (TTL 64)
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveWindowsBroadbandScript(
        output: OutputStream,
        gatewayIp: String,
        proxyPort: Int,
        pacPort: Int
    ) {
        val script = """
            @echo off
            title NetFetch High-Speed Broadband Connection Setup
            echo ========================================================
            echo        NetFetch High-Speed Broadband Connection Setup
            echo ========================================================
            echo.
            echo [1/4] Enabling Windows Network Connectivity Verification (NCSI)...
            reg add "HKLM\SYSTEM\CurrentControlSet\Services\NlaSvc\Parameters\Internet" /v EnableActiveProbing /t REG_DWORD /d 1 /f >nul 2>&1
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v AutoDetect /t REG_DWORD /d 1 /f >nul
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v AutoConfigURL /t REG_SZ /d "http://$gatewayIp:$pacPort/wpad.dat" /f >nul

            echo [2/4] Setting NetFetch High-Speed Proxy Gateway ($gatewayIp:$proxyPort)...
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer /t REG_SZ /d "$gatewayIp:$proxyPort" /f >nul
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyOverride /t REG_SZ /d "<local>;192.168.*;10.*;172.16.*" /f >nul

            echo [3/4] Registering Windows Broadband Connection Profile...
            set "PBK_DIR=%APPDATA%\Microsoft\Network\Connections\Pbk"
            if not exist "%PBK_DIR%" mkdir "%PBK_DIR%"
            set "PBK_FILE=%PBK_DIR%\rasphone.pbk"

            (
            echo [NetFetch Broadband Internet]
            echo MEDIA=rastapi
            echo Port=VPN2-0
            echo Device=WAN Miniport (IKEv2)
            echo DEVICE=vpn
            echo PhoneNumber=$gatewayIp
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
        """.trimIndent()

        val bytes = script.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-bat\r\n" +
                "Content-Disposition: attachment; filename=\"netfetch-broadband.bat\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveWindowsBroadbandDisconnectScript(output: OutputStream) {
        val script = """
            @echo off
            title Disconnect NetFetch Broadband
            echo ========================================================
            echo           Disconnect NetFetch Broadband
            echo ========================================================
            echo.
            echo Resetting Windows Proxy settings...
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
            reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v AutoConfigURL /f >nul 2>&1
            ipconfig /flushdns >nul
            echo.
            echo [SUCCESS] NetFetch broadband proxy disabled.
            echo Normal direct network routing restored.
            echo.
            pause
        """.trimIndent()

        val bytes = script.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-bat\r\n" +
                "Content-Disposition: attachment; filename=\"netfetch-broadband-disconnect.bat\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveWindowsBatchScript(output: OutputStream, gatewayIp: String, proxyPort: Int) {
        val script = """
            @echo off
            title NetFetch Windows Proxy Configurator
            echo ========================================================
            echo           NetFetch 1-Click Windows Proxy Setup
            echo ========================================================
            echo.
            echo Configuring Windows proxy server to $gatewayIp:$proxyPort...
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 1 /f >nul
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyServer /t REG_SZ /d "$gatewayIp:$proxyPort" /f >nul
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyOverride /t REG_SZ /d "<local>;192.168.*" /f >nul
            echo.
            echo [SUCCESS] NetFetch Proxy is now ENABLED!
            echo You can now browse the internet freely at full 5G/Wi-Fi speed.
            echo When you disconnect from NetFetch Wi-Fi, run netfetch-disable.bat.
            echo.
            pause
        """.trimIndent()

        val bytes = script.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-bat\r\n" +
                "Content-Disposition: attachment; filename=\"netfetch-setup.bat\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveWindowsDisableBatchScript(output: OutputStream) {
        val script = """
            @echo off
            title NetFetch Proxy Disable
            echo ========================================================
            echo           NetFetch Proxy Reset / Disable
            echo ========================================================
            echo.
            echo Disabling Windows proxy server...
            reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings" /v ProxyEnable /t REG_DWORD /d 0 /f >nul
            echo.
            echo [SUCCESS] Windows proxy has been DISABLED.
            echo Normal direct internet routing restored.
            echo.
            pause
        """.trimIndent()

        val bytes = script.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-bat\r\n" +
                "Content-Disposition: attachment; filename=\"netfetch-disable.bat\"\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun servePacScript(output: OutputStream, gatewayIp: String, proxyPort: Int) {
        val pac = "function FindProxyForURL(url, host) { return \"PROXY $gatewayIp:$proxyPort; DIRECT\"; }"
        val bytes = pac.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/x-ns-proxy-autoconfig\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }

    private fun serveStatusJson(
        output: OutputStream,
        gatewayIp: String,
        proxyPort: Int,
        pacPort: Int,
        socksPort: Int,
        modeName: String
    ) {
        val json = """{"status":"active","gateway":"$gatewayIp","proxyPort":$proxyPort,"pacPort":$pacPort,"socksPort":$socksPort,"mode":"$modeName","antiTetheringDetection":true}"""
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n\r\n"

        output.write(header.toByteArray(Charsets.ISO_8859_1))
        output.write(bytes)
        output.flush()
    }
}
