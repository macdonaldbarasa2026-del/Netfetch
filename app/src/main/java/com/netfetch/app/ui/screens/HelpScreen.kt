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
            text = "Help & Visual Setup Guides",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlack
        )
        Text(
            text = "Step-by-step visual instructions to configure client devices",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.DesktopWindows,
            title = "Windows 10 / 11 Setup Guide",
            diagramTitle = "[ Windows Proxy Setup Diagram ]",
            diagramBoxes = listOf(
                "Manual Proxy Setup -> TURN ON",
                "Proxy IP Address: 192.168.49.1",
                "Port Number: 8282"
            ),
            steps = listOf(
                "1. Connect Windows PC to NetFetch Wi-Fi network.",
                "2. Open Settings -> Network & Internet -> Proxy.",
                "3. Turn ON 'Use a proxy server' under Manual proxy setup.",
                "4. Enter IP Address: 192.168.49.1 and Port: 8282.",
                "5. Click Save. Your Windows PC is now connected to the internet!"
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneIphone,
            title = "iPhone / iPad (iOS) Setup Guide",
            diagramTitle = "[ iOS Wi-Fi Proxy Diagram ]",
            diagramBoxes = listOf(
                "Wi-Fi -> Tap (i) -> Configure Proxy",
                "Select -> 'Manual'",
                "Server: 192.168.49.1  |  Port: 8282"
            ),
            steps = listOf(
                "1. Connect iPhone/iPad to the NetFetch Wi-Fi network.",
                "2. Tap the blue (i) info button next to NetFetch network name.",
                "3. Scroll down and tap 'Configure Proxy'.",
                "4. Select 'Manual' and enter Server: 192.168.49.1, Port: 8282.",
                "5. Tap Save in the top right corner."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.LaptopMac,
            title = "macOS (MacBook / Mac) Setup Guide",
            diagramTitle = "[ Mac Network Proxies Diagram ]",
            diagramBoxes = listOf(
                "System Settings -> Network -> Proxies",
                "Enable: Web Proxy (HTTP) & Secure Proxy (HTTPS)",
                "Server: 192.168.49.1  |  Port: 8282"
            ),
            steps = listOf(
                "1. Connect Mac to NetFetch Wi-Fi network.",
                "2. Go to System Settings -> Network -> Wi-Fi -> Details -> Proxies.",
                "3. Enable both Web Proxy (HTTP) and Secure Web Proxy (HTTPS).",
                "4. Enter Server IP: 192.168.49.1 and Port: 8282.",
                "5. Click OK & Apply."
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        VisualHelpGuideItem(
            icon = Icons.Default.PhoneAndroid,
            title = "Android Client Setup Guide",
            diagramTitle = "[ Android Proxy Settings Diagram ]",
            diagramBoxes = listOf(
                "Network Details -> Pencil/Edit -> Advanced",
                "Proxy -> Select 'Manual'",
                "Proxy Host: 192.168.49.1  |  Port: 8282"
            ),
            steps = listOf(
                "1. Connect client Android phone to NetFetch Wi-Fi network.",
                "2. Tap Network Settings -> Edit network -> Advanced options.",
                "3. Change Proxy from None to Manual.",
                "4. Enter Proxy hostname: 192.168.49.1 and Proxy port: 8282.",
                "5. Save settings."
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // FAQ Section
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
                    question = "Why is manual proxy setup required?",
                    answer = "NetFetch avoids carrier tethering blocks and root requirements by running a local proxy server over Wi-Fi Direct. Setting the proxy routes traffic cleanly through your phone."
                )

                FaqItem(
                    question = "Is NetFetch completely free?",
                    answer = "Yes! NetFetch is 100% free and open-source forever with zero subscription fees or premium locks."
                )
            }
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
                    // Visual Mock Diagram Box (Classic Black Line Drawing Style)
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
                            color = TextDark,
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
