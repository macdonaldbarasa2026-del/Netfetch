package com.netfetch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.TetherMode
import com.netfetch.app.ui.theme.*

@Composable
fun HelpScreen(config: HotspotConfig) {
    val scrollState = rememberScrollState()
    val isPro = config.mode == TetherMode.PRO

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Help & Setup Guides",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlack
        )

        Text(
            text = if (isPro) {
                "Pro routing, SOCKS5 and device setup"
            } else {
                "Normal HTTP/PAC proxy setup for connected devices"
            },
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Current mode card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(
                1.dp,
                if (isPro) PrimaryBlack else AmberWarning
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPro) Icons.Default.Speed else Icons.Default.Public,
                        contentDescription = null,
                        tint = if (isPro) PrimaryBlack else AmberWarning
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = if (isPro) "Pro Mode" else "Normal Mode",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPro) {
                        "Pro uses an authenticated Netfetch link, SOCKS5 TCP transport and a receiver Android VPN/TUN. " +
                                "UDP forwarding is not available in this release. Android asks the receiving device for VPN permission."
                    } else {
                        "Normal uses the HTTP/HTTPS proxy and PAC configuration. " +
                                "It does not use Android VPN. A Netfetch receiver discovers and authenticates first; Android does not let an ordinary app set a device-wide proxy automatically."
                    },
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Architecture
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, AmberWarning),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = AmberWarning
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        "How NetFetch Works",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isPro) {
                        "Pro mode uses the phone's active Wi-Fi or mobile-data connection as the upstream. " +
                        "A receiver creates an authenticated Wi-Fi Direct session and sends TCP through its VPN/TUN and the provider SOCKS5 transport. UDP is unavailable."
                    } else {
                        "Normal mode uses the phone's active Wi-Fi or mobile-data connection as the upstream. " +
                        "A Netfetch receiver discovers and authenticates to the provider, then uses the advertised HTTP/HTTPS proxy or PAC configuration."
                    },
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isPro) {
                    ArchBox("Your Phone Internet (Wi-Fi or Mobile Data)")
                    ArchBox("↓")
                    ArchBox("NetFetch Pro SOCKS5 TCP :${config.socksPort}")
                    ArchBox("↓")
                    ArchBox("Wi-Fi Direct (SSID: DIRECT-NetFetch-*)")
                    ArchBox("↓")
                    ArchBox("Client Device")
                    ArchBox("↓")
                    ArchBox("Routed IPv4 TCP Internet Traffic (UDP unavailable)")
                } else {
                    ArchBox("Your Phone Internet (Wi-Fi or Mobile Data)")
                    ArchBox("↓")
                    ArchBox("NetFetch HTTP Proxy :${config.proxyPort} / PAC :${config.pacPort}")
                    ArchBox("↓")
                    ArchBox("Wi-Fi Direct (SSID: DIRECT-NetFetch-*)")
                    ArchBox("↓")
                    ArchBox("Client Device (proxy configured)")
                    ArchBox("↓")
                    ArchBox("Internet Access via Proxy")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Pro-specific setup
        if (isPro) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, PrimaryBlack),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = PrimaryBlack
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            "Pro Mode Setup",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FaqItem(
                        question = "Why does Pro request VPN permission?",
                        answer = "The receiver TCP gateway uses Android VpnService to receive and route " +
                                "IPv4 TCP traffic. This requires the Android system VPN permission. " +
                                "No root access is required."
                    )

                    FaqItem(
                        question = "What does the Pro gateway support?",
                        answer = "The built-in receiver path handles IPv4 TCP traffic through the provider SOCKS5 server. " +
                                "UDP forwarding can be requested in Settings, but it remains unavailable until an authenticated provider/receiver UDP transport is negotiated. SOCKS5 UDP ASSOCIATE is not used."
                    )

                    FaqItem(
                        question = "Is SOCKS5 the same as the Pro gateway?",
                        answer = "No. SOCKS5 is an additional proxy interface on port ${config.socksPort}. " +
                                "The SOCKS5 implementation supports TCP CONNECT. The separate Pro TUN gateway " +
                                "handles routed IPv4 TCP traffic; UDP is unavailable."
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        VisualHelpGuideItem(
            icon = Icons.Default.DesktopWindows,
            title = "Windows 10 / 11 Setup",
            diagramTitle = "[ Windows Manual Proxy Setup ]",
            diagramBoxes = listOf(
                "Settings → Network & Internet → Proxy",
                "Manual Proxy Setup → Turn ON",
                "Address: ${config.hostIp}  |  Port: ${config.proxyPort}",
                "Save → Done"
            ),
            visualMockup = {
                WindowsVisualMockup(config.hostIp, config.proxyPort)
            },
            steps = listOf(
                "1. Connect your Windows PC to the NetFetch Wi-Fi network.",
                "2. Open Settings → Network & Internet → Proxy.",
                "3. Under 'Manual proxy setup', enable 'Use a proxy server'.",
                "4. Enter Address: ${config.hostIp} and Port: ${config.proxyPort}.",
                "5. Save the settings and test an HTTPS website.",
                "",
                "PAC URL method:",
                "Enable 'Use setup script' when supported.",
                "http://${config.hostIp}:${config.pacPort}/wpad.dat"
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneIphone,
            title = "iPhone / iPad Setup",
            diagramTitle = "[ iOS Wi-Fi Proxy Configuration ]",
            diagramBoxes = listOf(
                "Settings → Wi-Fi → Tap (i) next to NetFetch",
                "Configure Proxy → Manual",
                "Server: ${config.hostIp}  |  Port: ${config.proxyPort}",
                "Save"
            ),
            visualMockup = {
                IosVisualMockup(config.hostIp, config.proxyPort)
            },
            steps = listOf(
                "1. Connect to the NetFetch Wi-Fi network.",
                "2. Open Settings → Wi-Fi.",
                "3. Tap the information icon next to the NetFetch network.",
                "4. Tap 'Configure Proxy'.",
                "5. Select 'Manual'.",
                "6. Enter Server: ${config.hostIp} and Port: ${config.proxyPort}.",
                "7. Save and test an HTTPS website."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.LaptopMac,
            title = "macOS Setup",
            diagramTitle = "[ macOS Network Proxies ]",
            diagramBoxes = listOf(
                "System Settings → Network → Wi-Fi → Details",
                "Proxies tab",
                "Web Proxy (HTTP) + Secure Web Proxy (HTTPS)",
                "Server: ${config.hostIp}  |  Port: ${config.proxyPort}"
            ),
            visualMockup = {
                MacVisualMockup(config.hostIp, config.proxyPort, config.pacPort)
            },
            steps = listOf(
                "1. Connect your Mac to the NetFetch Wi-Fi network.",
                "2. Open System Settings → Network → Wi-Fi → Details.",
                "3. Open the 'Proxies' section.",
                "4. Enable Web Proxy (HTTP).",
                "5. Set server: ${config.hostIp} port: ${config.proxyPort}.",
                "6. Enable Secure Web Proxy (HTTPS) with the same address and port.",
                "7. Apply the settings."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.Laptop,
            title = "Linux Setup",
            diagramTitle = "[ Linux Proxy Configuration ]",
            diagramBoxes = listOf(
                "System Proxy → Manual",
                "HTTP: ${config.hostIp}:${config.proxyPort}",
                "HTTPS: ${config.hostIp}:${config.proxyPort}",
                "PAC: http://${config.hostIp}:${config.pacPort}/wpad.dat"
            ),
            steps = listOf(
                "GNOME / KDE:",
                "1. Open Network Proxy settings.",
                "2. Select Manual proxy configuration.",
                "3. HTTP Proxy: ${config.hostIp} Port ${config.proxyPort}.",
                "4. HTTPS Proxy: ${config.hostIp} Port ${config.proxyPort}.",
                "",
                "Terminal:",
                "export HTTP_PROXY=http://${config.hostIp}:${config.proxyPort}",
                "export HTTPS_PROXY=http://${config.hostIp}:${config.proxyPort}",
                "export http_proxy=http://${config.hostIp}:${config.proxyPort}",
                "export https_proxy=http://${config.hostIp}:${config.proxyPort}",
                "",
                "PAC URL:",
                "http://${config.hostIp}:${config.pacPort}/wpad.dat"
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneAndroid,
            title = "Android Client Setup",
            diagramTitle = "[ Android Wi-Fi Proxy Settings ]",
            diagramBoxes = listOf(
                "Wi-Fi → NetFetch network",
                "Modify Network → Advanced Options",
                "Proxy → Manual",
                "Host: ${config.hostIp}  |  Port: ${config.proxyPort}"
            ),
            visualMockup = {
                AndroidVisualMockup(config.hostIp, config.proxyPort)
            },
            steps = buildList {
                add("1. Connect the Android device to the NetFetch Wi-Fi network.")
                add("2. Open the Wi-Fi network details.")
                add("3. Select Modify/Edit network if available.")
                add("4. Open Advanced Options or Proxy Settings.")
                add("5. Set Proxy to Manual.")
                add("6. Enter Proxy hostname: ${config.hostIp}.")
                add("7. Enter Proxy port: ${config.proxyPort}.")
                add("8. Save the network settings.")
                add("9. Open a browser and test an HTTPS website.")
                add("")
                add("HTTP/HTTPS proxy:")
                add("${config.hostIp}:${config.proxyPort}")
                add("This works for browsers and apps that honor the Android Wi-Fi proxy.")
                add("")
                add("PAC:")
                add("http://${config.hostIp}:${config.pacPort}/wpad.dat")
                add("Use this when the device or browser supports automatic proxy configuration.")

                if (isPro) {
                    add("")
                    add("SOCKS5 (Pro):")
                    add("${config.hostIp}:${config.socksPort}")
                    add("Username: ${config.socksUsername}")
                    add("Password: ${config.socksPassword}")
                    add("SOCKS5 supports TCP CONNECT only; SOCKS5 UDP ASSOCIATE is unavailable.")
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.Share,
            title = "NetFetch Android Companion Receiver",
            diagramTitle = "[ Phone-to-Phone Fast Connection ]",
            diagramBoxes = listOf(
                "Step 1: Connect Phone B to Phone A's Wi-Fi network",
                "Step 2: Open NetFetch on Phone B",
                "Step 3: In Receiver section, tap 'Connect to Provider'",
                "Step 4: Grant Android VPN permission"
            ),
            visualMockup = {
                NetfetchCompanionVisualMockup(config.hostIp, config.socksPort)
            },
            steps = listOf(
                "1. Connect Phone B to Phone A's Wi-Fi Direct network (SSID: ${config.ssid}).",
                "2. Open NetFetch on Phone B.",
                "3. In the Home Screen, tap 'Connect to Provider' on the NetFetch Receiver card.",
                "4. When Android shows the VPN request prompt, tap OK.",
                "5. The entire phone now has full internet access with zero manual proxy configuration!"
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Troubleshooting
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Troubleshooting",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(12.dp))

                FaqItem(
                    question = "Connected to NetFetch but there is no internet",
                    answer = "First confirm that the NetFetch phone itself has working Internet. " +
                            "Then verify the client is connected to the correct NetFetch Wi-Fi network " +
                            "and that the displayed proxy or gateway information is being used."
                )

                FaqItem(
                    question = "Normal mode does not work",
                    answer = "Normal mode requires the client application to honor the HTTP, HTTPS or PAC proxy. " +
                            "Check that the proxy address is ${config.hostIp} and the HTTP proxy port is ${config.proxyPort}. " +
                            "Normal mode does not use the Android VPN gateway."
                )

                FaqItem(
                    question = "Pro mode does not start",
                    answer = "Android must grant NetFetch VPN permission for Pro mode. " +
                            "If the permission dialog was cancelled, stop NetFetch and start Pro again."
                )

                FaqItem(
                    question = "Pro mode is slower than Normal mode",
                    answer = "Pro performs additional routing through the Android TUN gateway and userspace TCP processing. " +
                            "That adds processing overhead compared with the simpler HTTP proxy path. " +
                            "The active upstream network still determines the available Internet speed."
                )

                FaqItem(
                    question = "SOCKS5 works for browsing but a game does not connect",
                    answer = "The SOCKS5 server supports TCP CONNECT. It does not implement SOCKS5 UDP ASSOCIATE. " +
                            "Applications that specifically use SOCKS5 UDP should use the Pro gateway path instead."
                )

                FaqItem(
                    question = "The NetFetch network is not visible",
                    answer = "Wi-Fi Direct availability depends on the Android device and its current network state. " +
                            "Keep Wi-Fi enabled, keep NetFetch active, and make sure Android has granted the required " +
                            "nearby Wi-Fi permissions."
                )

                FaqItem(
                    question = "5 GHz fails to connect or network is invisible on older devices",
                    answer = "5 GHz Wi-Fi Direct requires both host and client devices to support 5 GHz Wi-Fi Direct. " +
                            "If client devices cannot see the network or fail to connect, NetFetch automatically falls back to Auto/2.4 GHz. " +
                            "You can also change Band Preference to 'Auto' or '2.4 GHz' in Settings for maximum compatibility."
                )

                if (isPro) {
                    FaqItem(
                        question = "Does Pro require root?",
                        answer = "No. Pro uses Android's user-approved VpnService interface for its TUN gateway. " +
                                "Root access is not required."
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // FAQ
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Frequently Asked Questions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(12.dp))

                FaqItem(
                    question = "Why must Normal mode use a proxy?",
                    answer = "Normal mode is designed as the lightweight proxy path. " +
                            "Connected devices must configure the HTTP/HTTPS proxy or PAC settings. " +
                            "It does not request Android VPN permission."
                )

                FaqItem(
                    question = "What is the difference between Normal and Pro?",
                    answer = "Normal uses HTTP/HTTPS proxy and PAC configuration. " +
                            "Pro adds an authenticated SOCKS5 TCP service and a receiver Android TUN for IPv4 TCP routing; UDP is unavailable."
                )

                FaqItem(
                    question = "Why does Pro need VPN permission?",
                    answer = "Android requires user approval before an application can create a VpnService tunnel. " +
                            "NetFetch uses that approved tunnel for the Pro TCP gateway."
                )

                FaqItem(
                    question = "What should I enter when the gateway IP changes?",
                    answer = "Use the Gateway or proxy address displayed on the NetFetch Home screen. " +
                            "Wi-Fi Direct interface addresses can change between sessions."
                )

                FaqItem(
                    question = "Is NetFetch completely free?",
                    answer = "Yes. NetFetch is 100% free and open-source with no subscriptions or locks."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ArchBox(text: String) {
    if (text == "↓") {
        Text(
            text = "↓",
            fontSize = 16.sp,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            textAlign = TextAlign.Center
        )
    } else {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextDark,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun VisualHelpGuideItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    diagramTitle: String,
    diagramBoxes: List<String>,
    steps: List<String>,
    visualMockup: (@Composable () -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlack)

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
                    tint = TextMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (visualMockup != null) {
                        visualMockup()
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                CreamBackground,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                BorderStroke(1.dp, PrimaryBlack),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = diagramTitle,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            diagramBoxes.forEach { box ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CardWhite,
                                    border = BorderStroke(1.dp, SurfaceBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        text = box,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextDark,
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 6.dp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    steps.forEach { step ->
                        val isCode = step.startsWith("export") ||
                                step.startsWith("http") ||
                                step.matches(Regex("^\\S+:\\d+$"))

                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = TextDark,
                            fontFamily = if (isCode) {
                                FontFamily.Monospace
                            } else {
                                FontFamily.Default
                            },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WindowsVisualMockup(hostIp: String, proxyPort: Int) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = androidx.compose.ui.graphics.Color(0xFF202020),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DesktopWindows,
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color(0xFF60A5FA),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Windows Settings > Network & internet > Proxy",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = androidx.compose.ui.graphics.Color.White
                    )
                }
                Text("— □ ✕", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.LightGray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF2D2D2D),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Use a proxy server",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = androidx.compose.ui.graphics.Color(0xFF10B981)
                        ) {
                            Text(
                                "ON",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Proxy IP address", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.LightGray)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            hostIp,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = androidx.compose.ui.graphics.Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Port", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.LightGray)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF3B82F6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            proxyPort.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = androidx.compose.ui.graphics.Color(0xFF93C5FD),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = androidx.compose.ui.graphics.Color(0xFF2563EB),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            "Save",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun IosVisualMockup(hostIp: String, proxyPort: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xFFF2F2F7),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFD1D1D6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("< Wi-Fi", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF007AFF), fontWeight = FontWeight.Medium)
                Text("Configure Proxy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black)
                Text("Save", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color(0xFF007AFF), fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color(0xFFE5E5EA),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("Off", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        shadowElevation = 1.dp
                    ) {
                        Text("Manual ✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.Black, modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp))
                    }
                    Text("Auto", fontSize = 11.sp, color = androidx.compose.ui.graphics.Color.Gray, modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Server", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Black)
                        Text(hostIp, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFF007AFF))
                    }
                    Divider(color = androidx.compose.ui.graphics.Color(0xFFE5E5EA), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Port", fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Black)
                        Text(proxyPort.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFF007AFF))
                    }
                }
            }
        }
    }
}

@Composable
fun MacVisualMockup(hostIp: String, proxyPort: Int, pacPort: Int) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = androidx.compose.ui.graphics.Color(0xFF282828),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF505050)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.size(9.dp).background(androidx.compose.ui.graphics.Color(0xFFFF5F56), shape = androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                Box(modifier = Modifier.size(9.dp).background(androidx.compose.ui.graphics.Color(0xFFFFBD2E), shape = androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(5.dp))
                Box(modifier = Modifier.size(9.dp).background(androidx.compose.ui.graphics.Color(0xFF27C93F), shape = androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Network > Wi-Fi > Proxies", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Active Proxy Configurations:", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("[✓] Web Proxy (HTTP): $hostIp : $proxyPort", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF6EE7B7))
                    Text("[✓] Secure Web Proxy (HTTPS): $hostIp : $proxyPort", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = androidx.compose.ui.graphics.Color(0xFF6EE7B7))
                    Text("[✓] Auto Proxy (PAC): http://$hostIp:$pacPort/wpad.dat", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF93C5FD))
                }
            }
        }
    }
}

@Composable
fun AndroidVisualMockup(hostIp: String, proxyPort: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xFF1E293B),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF38BDF8)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Android Network Details > Proxy", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Proxy: Manual", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color(0xFFFBBF24))
            Spacer(modifier = Modifier.height(4.dp))

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF0F172A),
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF475569)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("Proxy hostname", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    Text(hostIp, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFF38BDF8))
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF0F172A),
                border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF475569)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    Text("Proxy port", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.Gray)
                    Text(proxyPort.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color(0xFF38BDF8))
                }
            }
        }
    }
}

@Composable
fun NetfetchCompanionVisualMockup(hostIp: String, socksPort: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = androidx.compose.ui.graphics.Color(0xFF0F172A),
        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFF10B981)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Phone-to-Phone NetFetch Fast Connect",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color(0xFF34D399)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                    Text("Provider", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    Text("Hotspot ON", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF34D399))
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("))) Wi-Fi Direct (((", fontSize = 10.sp, color = androidx.compose.ui.graphics.Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
                    Text("Auto Encrypted TCP", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color.LightGray)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                    Text("Receiver", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    Text("VPN Active ✓", fontSize = 9.sp, color = androidx.compose.ui.graphics.Color(0xFF34D399))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = androidx.compose.ui.graphics.Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "No manual proxy configuration needed on receiver Android device!",
                    fontSize = 10.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFA7F3D0),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            question,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextDark
        )

        Text(
            answer,
            fontSize = 12.sp,
            color = TextMuted
        )
    }
}
