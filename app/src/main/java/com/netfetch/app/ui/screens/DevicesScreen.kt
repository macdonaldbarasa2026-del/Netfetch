package com.netfetch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .padding(16.dp)
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
                    text = "Real-time client connections & traffic monitor",
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
                    text = "${connectedClients.size}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection information for devices using the NetFetch proxy.
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, PrimaryBlack.copy(alpha = 0.18f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Internet Connection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Connect the device to the NetFetch Wi-Fi, then set its HTTP/HTTPS proxy to:",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CreamBackground,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Proxy server",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "${config.hostIp}:${config.proxyPort}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager

                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "NetFetch Proxy",
                                        "${config.hostIp}:${config.proxyPort}"
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    "Proxy address copied",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy proxy address",
                                tint = PrimaryBlack
                            )
                        }

                        IconButton(
                            onClick = { showQrDialog = true }
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Show Wi-Fi QR Code",
                                tint = PrimaryBlack
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "PAC URL: http://${config.hostIp}:${config.pacPort}/wpad.dat",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Web Portal: http://${config.hostIp}:${config.proxyPort}/ (Open in browser for 1-click Windows .bat setup)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Important: NetFetch is a rootless proxy. The connected device must use the displayed proxy settings. Apps that ignore system HTTP/HTTPS proxy settings may not use the connection.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (connectedClients.isNotEmpty()) {
            val totalUp = connectedClients.sumOf { it.bytesUploaded }
            val totalDown = connectedClients.sumOf { it.bytesDownloaded }
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
                        Text(formatBytes(totalSum), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("↑ Out: ${formatBytes(totalUp)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlack)
                        Text("↓ In: ${formatBytes(totalDown)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GreenSuccess)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (connectedClients.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DevicesOther,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Devices Connected Yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect client devices to the NetFetch Wi-Fi network and set the proxy to ${config.hostIp}:${config.proxyPort}.",
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(connectedClients) { client ->
                    ClientDeviceCard(client = client, context = context)
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
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

@Composable
fun ClientDeviceCard(client: ClientDevice, context: Context) {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val connectedTimeStr = sdf.format(Date(client.connectedTimestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(CreamBackground, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Laptop,
                    contentDescription = null,
                    tint = PrimaryBlack,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.deviceName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Text(
                    text = if (client.ipAddress == "Awaiting IP") {
                        "IP: awaiting network traffic"
                    } else {
                        "IP: ${client.ipAddress}"
                    },
                    fontSize = 13.sp,
                    color = PrimaryBlack,
                    fontWeight = FontWeight.Medium
                )

                if (client.macAddress != "Unknown MAC") {
                    Text(
                        text = "MAC: ${client.macAddress}",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }

                Text(
                    text = "Connected since $connectedTimeStr",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CreamBackground,
                    border = BorderStroke(1.dp, SurfaceBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "↓ ${formatBytes(client.bytesDownloaded)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                        Text(
                            text = "↑ ${formatBytes(client.bytesUploaded)}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Client IP", client.ipAddress)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied IP ${client.ipAddress}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = TextMuted, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

/*
 * © 2026 Created by MacDonald | Powered by Mixfia
 */
