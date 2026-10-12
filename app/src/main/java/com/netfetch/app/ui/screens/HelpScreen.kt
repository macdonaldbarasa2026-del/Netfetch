package com.netfetch.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
            .padding(20.dp)
    ) {
        Text(
            text = "Help & Device Guides",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlack
        )
        Text(
            text = "Step-by-step setup for PC, Mac, iPhone, Android, and gaming consoles",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── 1-Click Web Portal Setup (Fastest Method) ──
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.5.dp, PrimaryBlack),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "1-Click Setup (Fastest Method)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "1. Connect your laptop, PC, or phone to the NetFetch Wi-Fi network.\n" +
                            "2. Open any web browser and visit:\n" +
                            "   http://${config.hostIp}:${config.proxyPort}/\n" +
                            "3. Download the 1-Click Windows Broadband .bat setup script or view device guides instantly.",
                    fontSize = 13.sp,
                    color = TextDark,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Platform-Specific Setup Guides ──
        Text(
            text = "Manual Device Configuration",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlack
        )
        Spacer(modifier = Modifier.height(10.dp))

        DeviceGuideAccordion(
            title = "Windows 10 / 11 PC",
            icon = Icons.Default.Laptop,
            steps = listOf(
                "Connect to Wi-Fi '${config.ssid}' with password '${config.passphrase}'.",
                "Open Settings > Network & Internet > Proxy.",
                "Turn ON 'Use a proxy server' (or use Setup Script from the portal).",
                "Proxy IP: ${config.hostIp} | Port: ${config.proxyPort}.",
                "Save and browse freely at full speed."
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceGuideAccordion(
            title = "Apple iPhone & iPad",
            icon = Icons.Default.PhoneIphone,
            steps = listOf(
                "Connect to Wi-Fi '${config.ssid}' in iOS Settings.",
                "Tap the blue (i) info icon next to the network name.",
                "Scroll down to 'Configure Proxy' and select 'Manual'.",
                "Server: ${config.hostIp} | Port: ${config.proxyPort}.",
                "Tap 'Save' in top right corner."
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceGuideAccordion(
            title = "Apple Mac (macOS)",
            icon = Icons.Default.DesktopMac,
            steps = listOf(
                "Connect to Wi-Fi '${config.ssid}'.",
                "Open System Settings > Network > Wi-Fi > Details > Proxies.",
                "Check 'Web Proxy (HTTP)' and 'Secure Web Proxy (HTTPS)'.",
                "Server: ${config.hostIp} | Port: ${config.proxyPort}.",
                "Click OK, then Apply."
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceGuideAccordion(
            title = "Android Phones & Tablets",
            icon = Icons.Default.Android,
            steps = listOf(
                "Connect to Wi-Fi '${config.ssid}'.",
                "Tap the gear icon next to the Wi-Fi network > Advanced.",
                "Set Proxy to 'Manual'.",
                "Proxy hostname: ${config.hostIp} | Proxy port: ${config.proxyPort}.",
                "Tap Save."
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        DeviceGuideAccordion(
            title = "PlayStation, Xbox & Nintendo Switch",
            icon = Icons.Default.SportsEsports,
            steps = listOf(
                "Set up Internet Connection > Custom / Manual.",
                "Connect to Wi-Fi '${config.ssid}'.",
                "When prompted for Proxy Server, select 'Use'.",
                "Address: ${config.hostIp} | Port: ${config.proxyPort}.",
                "Complete connection test."
            )
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ── Frequently Asked Questions ──
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Frequently Asked Questions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(12.dp))

                FaqItem(
                    question = "How does NetFetch bypass carrier hotspot limits?",
                    answer = "NetFetch runs a native userspace proxy with TTL 64 mangling on your Android phone. Carrier cellular towers see all transmitted packets as origin traffic directly from this phone, never deducting from your hotspot quota."
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceBorder)

                FaqItem(
                    question = "Why does Wi-Fi Direct disappear when turned off?",
                    answer = "When you stop NetFetch, it cleanly purges persistent wpa_supplicant groups and tears down the P2P radio channel, making the network vanish from nearby devices immediately like a real router."
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = SurfaceBorder)

                FaqItem(
                    question = "What is Normal vs Pro mode?",
                    answer = "Normal mode serves an ultra-fast HTTP/HTTPS proxy and PAC auto-config script. Pro mode adds an authenticated SOCKS5 tunnel with receiver VPN support for apps that require a dedicated TUN interface."
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NetFetch Support",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark
            )
            Text(
                text = "© 2026 Created by MacDonald | Powered by Mixfia",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun DeviceGuideAccordion(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    steps: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    steps.forEachIndexed { index, step ->
                        Row(
                            modifier = Modifier.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "${index + 1}. ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                            Text(
                                text = step,
                                fontSize = 12.sp,
                                color = TextDark,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaqItem(question: String, answer: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(question, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        Spacer(modifier = Modifier.height(2.dp))
        Text(answer, fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp)
    }
}

