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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.ui.theme.*
import com.netfetch.app.model.HotspotConfig

@Composable
fun HelpScreen(config: HotspotConfig) {
    val scrollState = rememberScrollState()

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
            text = "Step-by-step proxy configuration for each platform",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Architecture Info Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, AmberWarning),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning)
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
                    text = "NetFetch shares your phone's internet connection over Wi-Fi Direct " +
                            "using an HTTP/SOCKS5 proxy. This requires no root access. " +
                            "Connected devices must configure their proxy manually — " +
                            "NetFetch cannot create transparent IP routing without root.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                ArchBox("Your Phone Internet  (Wi-Fi or Mobile Data)")
                ArchBox("↓")
                ArchBox("NetFetch  (HTTP Proxy :${config.proxyPort} / SOCKS5 :${config.socksPort})")
                ArchBox("↓")
                ArchBox("Wi-Fi Direct  (SSID: DIRECT-NetFetch-*)")
                ArchBox("↓")
                ArchBox("Client Device  (proxy configured)")
                ArchBox("↓")
                ArchBox("Internet Access via Proxy")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
            steps = listOf(
                "1. Connect your Windows PC to the NetFetch Wi-Fi network.",
                "2. Open Settings → Network & Internet → Proxy.",
                "3. Under 'Manual proxy setup', toggle ON 'Use a proxy server'.",
                "4. Enter Address: ${config.hostIp} and Port: ${config.proxyPort}.",
                "5. Click Save. Internet should now work through NetFetch.",
                "",
                "Alternative — PAC URL method:",
                "In the Proxy settings, choose 'Use setup script'.",
                "Enter: http://${config.hostIp}:${config.pacPort}/wpad.dat",
                "Click Save."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneIphone,
            title = "iPhone / iPad (iOS) Setup",
            diagramTitle = "[ iOS Wi-Fi Proxy Configuration ]",
            diagramBoxes = listOf(
                "Settings → Wi-Fi → Tap (i) next to NetFetch",
                "Scroll down → Configure Proxy → Manual",
                "Server: ${config.hostIp}  |  Port: ${config.proxyPort}",
                "Tap Save"
            ),
            steps = listOf(
                "1. Connect your iPhone/iPad to the NetFetch Wi-Fi network.",
                "2. Go to Settings → Wi-Fi.",
                "3. Tap the blue (i) icon next to the NetFetch network name.",
                "4. Scroll down and tap 'Configure Proxy'.",
                "5. Select 'Manual'. Enter Server: ${config.hostIp} and Port: ${config.proxyPort}.",
                "6. Tap Save (top right). Internet should now work through NetFetch."
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
                "Enable: Web Proxy (HTTP) + Secure Proxy (HTTPS)",
                "Server: ${config.hostIp}  |  Port: ${config.proxyPort}"
            ),
            steps = listOf(
                "1. Connect your Mac to the NetFetch Wi-Fi network.",
                "2. Go to System Settings → Network → Wi-Fi → Details...",
                "3. Click the 'Proxies' tab.",
                "4. Enable 'Web Proxy (HTTP)'. Set server: ${config.hostIp} port: ${config.proxyPort}.",
                "5. Enable 'Secure Web Proxy (HTTPS)'. Use the same address and port.",
                "6. Click OK, then Apply."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.Laptop,
            title = "Linux Setup",
            diagramTitle = "[ Linux Proxy Configuration ]",
            diagramBoxes = listOf(
                "Option A: System proxy via GNOME / KDE Settings",
                "Option B: Environment variables",
                "HTTP_PROXY=http://${config.hostIp}:${config.proxyPort}",
                "HTTPS_PROXY=http://${config.hostIp}:${config.proxyPort}"
            ),
            steps = listOf(
                "GNOME:",
                "1. Settings → Network → Network Proxy → Manual.",
                "2. HTTP Proxy: ${config.hostIp}  Port: ${config.proxyPort}.",
                "3. HTTPS Proxy: ${config.hostIp}  Port: ${config.proxyPort}. Apply.",
                "",
                "Terminal (per-session):",
                "export HTTP_PROXY=http://${config.hostIp}:${config.proxyPort}",
                "export HTTPS_PROXY=http://${config.hostIp}:${config.proxyPort}",
                "export http_proxy=http://${config.hostIp}:${config.proxyPort}",
                "export https_proxy=http://${config.hostIp}:${config.proxyPort}",
                "",
                "For PAC URL support, enter in browser proxy settings:",
                "http://${config.hostIp}:${config.pacPort}/wpad.dat"
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneAndroid,
            title = "Android Client Setup",
            diagramTitle = "[ Android Wi-Fi Proxy Settings ]",
            diagramBoxes = listOf(
                "Wi-Fi → Long-press NetFetch network",
                "Modify Network → Advanced Options",
                "Proxy → Manual",
                "Proxy Host: ${config.hostIp}  |  Port: ${config.proxyPort}"
            ),
            steps = listOf(
                "1. Connect your Android device to the NetFetch Wi-Fi network.",
                "2. Go to Wi-Fi settings and long-press the NetFetch network name.",
                "3. Tap 'Modify Network' or the pencil/edit icon.",
                "4. Expand 'Advanced Options' or 'Proxy Settings'.",
                "5. Change Proxy from 'None' to 'Manual'.",
                "6. Proxy hostname: ${config.hostIp}, Proxy port: ${config.proxyPort}.",
                "7. Save the network settings.",
                "8. Open a browser and test an HTTPS website.",
                "",
                "HTTP/HTTPS proxy:",
                "${config.hostIp}:${config.proxyPort}",
                "Best for browsers and apps that honor the Android Wi-Fi proxy.",
                "",
                "PAC configuration:",
                "http://${config.hostIp}:${config.pacPort}/wpad.dat",
                "Use this when the device or browser supports automatic proxy configuration.",
                "",
                "SOCKS5 (Pro):",
                "${config.hostIp}:${config.socksPort}",
                "TCP CONNECT only. UDP ASSOCIATE is not supported.",
                "Use a SOCKS5-capable app when you need SOCKS5 instead of the system HTTP proxy."
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
                    answer = "First confirm that the NetFetch phone itself has working internet. " +
                            "Then check that the client is connected to the correct NetFetch network " +
                            "and that the proxy address and port match the values shown on the Home screen."
                )

                FaqItem(
                    question = "The proxy does not connect",
                    answer = "Make sure NetFetch is Active and the displayed gateway address is reachable " +
                            "from the client device. Check that the HTTP proxy port is correct. " +
                            "If using SOCKS5, verify the SOCKS5 username and password in NetFetch Settings."
                )

                FaqItem(
                    question = "Some apps work but other apps do not",
                    answer = "This is expected with proxy-based networking. Only applications that honor the " +
                            "configured HTTP, HTTPS, PAC, or SOCKS5 proxy can use that path. " +
                            "Apps that require direct networking or UDP may not work."
                )

                FaqItem(
                    question = "SOCKS5 works for browsing but a game does not connect",
                    answer = "NetFetch SOCKS5 currently supports TCP CONNECT only. " +
                            "Games or applications that require UDP traffic cannot use the SOCKS5 tunnel."
                )

                FaqItem(
                    question = "The NetFetch network is not visible",
                    answer = "Wi-Fi Direct availability depends on the Android device and its current network state. " +
                            "Keep Wi-Fi enabled, keep NetFetch Active, and check that Android has granted " +
                            "the required nearby Wi-Fi permissions."
                )
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
                    question = "Why must I configure a proxy manually?",
                    answer = "Android does not allow unrooted apps to create transparent NAT routing. " +
                            "NetFetch runs an HTTP/SOCKS5 proxy server instead, which requires manual " +
                            "configuration on connected devices. Root is not needed."
                )

                FaqItem(
                    question = "Why does SOCKS5 say 'TCP only'?",
                    answer = "Android prevents non-root apps from relaying UDP packets for arbitrary " +
                            "clients on Wi-Fi Direct. UDP ASSOCIATE is therefore not implemented. " +
                            "Applications requiring UDP (some games) will not work over SOCKS5."
                )

                FaqItem(
                    question = "The gateway IP is different — what should I enter?",
                    answer = "Check the 'Gateway' field in the NetFetch status dashboard on the Home screen. " +
                            "NetFetch detects the actual Wi-Fi Direct interface address and shows it there."
                )

                FaqItem(
                    question = "Is NetFetch completely free?",
                    answer = "Yes. NetFetch is 100% free and open-source with no subscriptions or locks."
                )
            }
        }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
    steps: List<String>
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
                    Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CreamBackground, shape = RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, PrimaryBlack), shape = RoundedCornerShape(12.dp))
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
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    steps.forEach { step ->
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = if (step.startsWith("export") || step.startsWith("http")) TextDark.copy(alpha = 0.85f) else TextDark,
                            fontFamily = if (step.startsWith("export") || step.startsWith("http")) androidx.compose.ui.text.font.FontFamily.Monospace else androidx.compose.ui.text.font.FontFamily.Default,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(question, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        Text(answer, fontSize = 12.sp, color = TextMuted)
    }
}
