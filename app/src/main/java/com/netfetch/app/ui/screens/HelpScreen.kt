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

@Composable
fun HelpScreen() {
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
                ArchBox("NetFetch  (HTTP Proxy :8282 / SOCKS5 :1080)")
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
                "Address: 192.168.49.1  |  Port: 8282",
                "Save → Done"
            ),
            steps = listOf(
                "1. Connect your Windows PC to the NetFetch Wi-Fi network.",
                "2. Open Settings → Network & Internet → Proxy.",
                "3. Under 'Manual proxy setup', toggle ON 'Use a proxy server'.",
                "4. Enter Address: 192.168.49.1 and Port: 8282.",
                "5. Click Save. Internet should now work through NetFetch.",
                "",
                "Alternative — PAC URL method:",
                "In the Proxy settings, choose 'Use setup script'.",
                "Enter: http://192.168.49.1:8283/wpad.dat",
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
                "Server: 192.168.49.1  |  Port: 8282",
                "Tap Save"
            ),
            steps = listOf(
                "1. Connect your iPhone/iPad to the NetFetch Wi-Fi network.",
                "2. Go to Settings → Wi-Fi.",
                "3. Tap the blue (i) icon next to the NetFetch network name.",
                "4. Scroll down and tap 'Configure Proxy'.",
                "5. Select 'Manual'. Enter Server: 192.168.49.1 and Port: 8282.",
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
                "Server: 192.168.49.1  |  Port: 8282"
            ),
            steps = listOf(
                "1. Connect your Mac to the NetFetch Wi-Fi network.",
                "2. Go to System Settings → Network → Wi-Fi → Details...",
                "3. Click the 'Proxies' tab.",
                "4. Enable 'Web Proxy (HTTP)'. Set server: 192.168.49.1 port: 8282.",
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
                "HTTP_PROXY=http://192.168.49.1:8282",
                "HTTPS_PROXY=http://192.168.49.1:8282"
            ),
            steps = listOf(
                "GNOME:",
                "1. Settings → Network → Network Proxy → Manual.",
                "2. HTTP Proxy: 192.168.49.1  Port: 8282.",
                "3. HTTPS Proxy: 192.168.49.1  Port: 8282. Apply.",
                "",
                "Terminal (per-session):",
                "export HTTP_PROXY=http://192.168.49.1:8282",
                "export HTTPS_PROXY=http://192.168.49.1:8282",
                "export http_proxy=http://192.168.49.1:8282",
                "export https_proxy=http://192.168.49.1:8282",
                "",
                "For PAC URL support, enter in browser proxy settings:",
                "http://192.168.49.1:8283/wpad.dat"
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
                "Proxy Host: 192.168.49.1  |  Port: 8282"
            ),
            steps = listOf(
                "1. Connect your Android device to the NetFetch Wi-Fi network.",
                "2. Go to Wi-Fi settings and long-press the NetFetch network name.",
                "3. Tap 'Modify Network' or the pencil/edit icon.",
                "4. Expand 'Advanced Options' or 'Proxy Settings'.",
                "5. Change Proxy from 'None' to 'Manual'.",
                "6. Proxy hostname: 192.168.49.1, Proxy port: 8282.",
                "7. Save. Web browsers and most apps will now use the proxy.",
                "",
                "Note: Apps using raw sockets (some games) may need SOCKS5 mode (Pro).",
                "SOCKS5: 192.168.49.1:1080 (TCP CONNECT only — UDP not supported)"
            )
        )

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
                    question = "The gateway IP is not 192.168.49.1 — what should I enter?",
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
