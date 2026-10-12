package com.netfetch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.ClientDevice
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.ui.components.QrCodeDialog
import com.netfetch.app.ui.components.QrType
import com.netfetch.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DevicesScreen(
    connectedClients: List<ClientDevice>,
    config: HotspotConfig
) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }
    var showDemoDevices by remember { mutableStateOf(false) }

    // Predefined simulated devices for testing and verification
    val demoClients = remember {
        listOf(
            ClientDevice(
                ipAddress = "192.168.49.2",
                macAddress = "DC:A6:32:8B:11:02",
                deviceName = "Windows 11 Laptop (Dell XPS)",
                connectedTimestamp = System.currentTimeMillis() - 18 * 60 * 1000,
                bytesUploaded = 45 * 1024 * 1024,
                bytesDownloaded = 214 * 1024 * 1024,
                isBlocked = false
            ),
            ClientDevice(
                ipAddress = "192.168.49.3",
                macAddress = "3C:22:FB:4A:91:E7",
                deviceName = "iPhone 15 Pro (iOS)",
                connectedTimestamp = System.currentTimeMillis() - 12 * 60 * 1000,
                bytesUploaded = 12 * 1024 * 1024,
                bytesDownloaded = 98 * 1024 * 1024,
                isBlocked = false
            ),
            ClientDevice(
                ipAddress = "192.168.49.4",
                macAddress = "98:B6:E9:12:33:4A",
                deviceName = "Nintendo Switch (Console)",
                connectedTimestamp = System.currentTimeMillis() - 8 * 60 * 1000,
                bytesUploaded = 8 * 1024 * 1024,
                bytesDownloaded = 48 * 1024 * 1024,
                isBlocked = false
            ),
            ClientDevice(
                ipAddress = "192.168.49.5",
                macAddress = "70:85:C2:55:A3:10",
                deviceName = "Android Tablet (Samsung Tab)",
                connectedTimestamp = System.currentTimeMillis() - 3 * 60 * 1000,
                bytesUploaded = 0L,
                bytesDownloaded = 0L,
                isBlocked = false
            ),
            ClientDevice(
                ipAddress = "192.168.49.9",
                macAddress = "B8:27:EB:12:34:56",
                deviceName = "Disconnected Device (Former Client)",
                connectedTimestamp = System.currentTimeMillis() - 45 * 60 * 1000,
                bytesUploaded = 0L,
                bytesDownloaded = 0L,
                isBlocked = true
            )
        )
    }

    val displayClients = if (showDemoDevices) {
        if (connectedClients.isNotEmpty()) connectedClients + demoClients else demoClients
    } else {
        connectedClients
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Connected Devices",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )
                Text(
                    text = "Real-time client connections & Net traffic status",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Surface(
                shape = CircleShape,
                color = PrimaryBlack,
                contentColor = CardWhite
            ) {
                Text(
                    text = "${displayClients.size}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Demo vs Live filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !showDemoDevices,
                onClick = { showDemoDevices = false },
                label = { Text("Live Only (${connectedClients.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlack,
                    selectedLabelColor = CardWhite,
                    containerColor = CardWhite,
                    labelColor = TextDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = SurfaceBorder,
                    selectedBorderColor = PrimaryBlack,
                    enabled = true,
                    selected = !showDemoDevices
                )
            )

            FilterChip(
                selected = showDemoDevices,
                onClick = { showDemoDevices = true },
                label = { Text("Preview Demo Devices", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryBlack,
                    selectedLabelColor = CardWhite,
                    containerColor = CardWhite,
                    labelColor = TextDark
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = SurfaceBorder,
                    selectedBorderColor = PrimaryBlack,
                    enabled = true,
                    selected = showDemoDevices
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Connection information for devices using the NetFetch proxy.
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, PrimaryBlack.copy(alpha = 0.18f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NetFetch Gateway",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GreenSuccess.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "ANTI-JAM EQUALIZER ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Every connected device routes traffic via the local proxy to bypass carrier hotspot limits.",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CreamBackground,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Proxy Gateway Address",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "${config.hostIp}:${config.proxyPort}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("NetFetch Proxy", "${config.hostIp}:${config.proxyPort}"))
                                Toast.makeText(context, "Proxy address copied", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy proxy address", tint = PrimaryBlack, modifier = Modifier.size(18.dp))
                        }

                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Default.QrCode, contentDescription = "Show Wi-Fi QR Code", tint = PrimaryBlack, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Competitive Advantage Action Shortcuts (iOS 1-Tap & Net Diagnostics)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val url = "http://${config.hostIp}:3000/ios-profile.mobileconfig"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            try { context.startActivity(intent) } catch (_: Exception) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("iOS Profile URL", url))
                                Toast.makeText(context, "iOS profile URL copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = CardWhite, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("iOS 1-Tap Profile", fontSize = 11.sp, color = CardWhite, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val url = "http://${config.hostIp}:3000/test"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            try { context.startActivity(intent) } catch (_: Exception) {
                                Toast.makeText(context, "Open http://${config.hostIp}:3000/test on connected devices", Toast.LENGTH_SHORT).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PrimaryBlack),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Net Test Portal", fontSize = 11.sp, color = PrimaryBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (displayClients.isNotEmpty()) {
            val totalUp = displayClients.sumOf { it.bytesUploaded }
            val totalDown = displayClients.sumOf { it.bytesDownloaded }
            val totalSum = totalUp + totalDown

            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, PrimaryBlack),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Devices Shared Total", fontSize = 11.sp, color = TextMuted)
                        Text(formatBytes(totalSum), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("↑ Out: ${formatBytes(totalUp)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        Text("↓ In: ${formatBytes(totalDown)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (displayClients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Devices Connected Yet",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Connect a phone, laptop or gaming console to Wi-Fi ${config.ssid} and set the proxy to ${config.hostIp}:${config.proxyPort}.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showDemoDevices = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("+ Preview Sample Demo Devices", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // Ensures list never cuts off behind elevated bottom dock!
            ) {
                items(displayClients) { client ->
                    ClientDeviceCard(client = client, context = context, proxyHost = config.hostIp, proxyPort = config.proxyPort)
                }
            }
        }
    }

    if (showQrDialog) {
        QrCodeDialog(
            config = config,
            initialType = QrType.WIFI_CONNECT,
            onDismissRequest = { showQrDialog = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
fun ClientDeviceCard(client: ClientDevice, context: Context, proxyHost: String, proxyPort: Int) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val connectedTimeStr = sdf.format(Date(client.connectedTimestamp))

    val hasTraffic = (client.bytesDownloaded + client.bytesUploaded) > 0
    val isDisconnected = client.isBlocked

    val deviceIcon = when {
        client.deviceName.contains("Laptop", ignoreCase = true) || client.deviceName.contains("Dell", ignoreCase = true) || client.deviceName.contains("Windows", ignoreCase = true) || client.deviceName.contains("Mac", ignoreCase = true) -> Icons.Default.Laptop
        client.deviceName.contains("iPhone", ignoreCase = true) || client.deviceName.contains("iOS", ignoreCase = true) || client.deviceName.contains("Phone", ignoreCase = true) -> Icons.Default.PhoneIphone
        client.deviceName.contains("Switch", ignoreCase = true) || client.deviceName.contains("Console", ignoreCase = true) || client.deviceName.contains("Steam", ignoreCase = true) -> Icons.Default.SportsEsports
        client.deviceName.contains("Tablet", ignoreCase = true) || client.deviceName.contains("Tab", ignoreCase = true) || client.deviceName.contains("iPad", ignoreCase = true) -> Icons.Default.TabletAndroid
        else -> Icons.Default.DevicesOther
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(
            1.dp,
            when {
                hasTraffic -> GreenSuccess.copy(alpha = 0.5f)
                isDisconnected -> SurfaceBorder
                else -> AmberWarning.copy(alpha = 0.6f)
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            when {
                                hasTraffic -> GreenSuccess.copy(alpha = 0.12f)
                                isDisconnected -> CreamBackground
                                else -> AmberWarning.copy(alpha = 0.12f)
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = deviceIcon,
                        contentDescription = null,
                        tint = when {
                            hasTraffic -> GreenSuccess
                            isDisconnected -> TextMuted
                            else -> AmberWarning
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = client.deviceName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDisconnected) TextMuted else TextDark
                    )
                    Text(
                        text = if (client.ipAddress == "Awaiting IP") "IP: awaiting network traffic" else "IP: ${client.ipAddress}",
                        fontSize = 12.sp,
                        color = PrimaryBlack,
                        fontWeight = FontWeight.Medium
                    )
                    if (client.macAddress != "Unknown MAC") {
                        Text(
                            text = "MAC: ${client.macAddress}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    // Net Status Badge
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when {
                            hasTraffic -> GreenSuccess.copy(alpha = 0.12f)
                            isDisconnected -> TextMuted.copy(alpha = 0.12f)
                            else -> AmberWarning.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            1.dp,
                            when {
                                hasTraffic -> GreenSuccess.copy(alpha = 0.35f)
                                isDisconnected -> SurfaceBorder
                                else -> AmberWarning.copy(alpha = 0.35f)
                            }
                        )
                    ) {
                        Text(
                            text = when {
                                hasTraffic -> "● NET ONLINE"
                                isDisconnected -> "○ DISCONNECTED"
                                else -> "▲ AWAITING PROXY"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                hasTraffic -> GreenSuccess
                                isDisconnected -> TextMuted
                                else -> AmberWarning
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (hasTraffic) "↓${formatBytes(client.bytesDownloaded)}  ↑${formatBytes(client.bytesUploaded)}" else "0 B Routed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasTraffic) PrimaryBlack else TextMuted
                    )
                }
            }

            // Explanation notice if device is on Wi-Fi but Net isn't displaying / proxy isn't set yet!
            if (!hasTraffic && !isDisconnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberWarning.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Net not displaying on this device yet: Connected to Wi-Fi, but HTTP proxy must be set to $proxyHost:$proxyPort to route internet traffic.",
                            fontSize = 11.sp,
                            color = AmberWarning,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
