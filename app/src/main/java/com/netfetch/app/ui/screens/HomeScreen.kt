package com.netfetch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.HotspotState
import com.netfetch.app.model.TetherMode
import com.netfetch.app.netfetchlink.NetfetchReceiverState
import com.netfetch.app.ui.components.QrCodeDialog
import com.netfetch.app.ui.components.QrType
import com.netfetch.app.ui.theme.*

@Composable
fun HomeScreen(
    state: HotspotState,
    config: HotspotConfig,
    receiverState: NetfetchReceiverState,
    onToggleHotspot: () -> Unit,
    onModeChange: (TetherMode) -> Unit,
    onBandChange: (BandPreference) -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToProxyConfig: () -> Unit = {},
    onStartReceiver: () -> Unit = {},
    onStopReceiver: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isActive = state is HotspotState.Active
    val isStarting = state is HotspotState.Starting
    val activeState = state as? HotspotState.Active

    var showQrDialog by remember { mutableStateOf(false) }
    var qrDialogType by remember { mutableStateOf(QrType.WIFI_CONNECT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top Header ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "NetFetch",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )
                Text(
                    text = "No-Root Tethering & Proxy Engine",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isActive) GreenSuccess.copy(alpha = 0.12f) else CardWhite,
                border = BorderStroke(1.dp, if (isActive) GreenSuccess else SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = when {
                                    isActive -> GreenSuccess
                                    isStarting -> AmberWarning
                                    else -> TextMuted
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isActive -> "HOTSPOT ACTIVE"
                            isStarting -> "STARTING..."
                            else -> "OFFLINE"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive -> GreenSuccess
                            isStarting -> AmberWarning
                            else -> TextDark
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // ── Hero Power Button ──
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(CircleShape)
                .background(if (isActive) PrimaryBlack else CardWhite)
                .border(
                    BorderStroke(4.dp, if (isActive) GreenSuccess else PrimaryBlack),
                    shape = CircleShape
                )
                .clickable(enabled = !isStarting) { onToggleHotspot() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.PowerSettingsNew else Icons.Default.WifiTethering,
                contentDescription = "Toggle Hotspot",
                tint = if (isActive) CardWhite else PrimaryBlack,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = when {
                isActive -> "Tap to Stop Hotspot"
                isStarting -> "Starting network services..."
                else -> "Tap to Start Hotspot"
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = PrimaryBlack
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ── Compact Mode Toggle (Normal vs Pro) ──
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TetherMode.entries.forEach { mode ->
                    val selected = config.mode == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) PrimaryBlack else CardWhite,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onModeChange(mode) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (mode == TetherMode.NORMAL) Icons.Default.Public else Icons.Default.Security,
                                contentDescription = null,
                                tint = if (selected) CardWhite else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (mode == TetherMode.NORMAL) "Normal (HTTP/PAC)" else "Pro (Tunnel)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) CardWhite else TextDark
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Unified Active Hotspot Status Dashboard ──
        if (activeState != null) {

            // 1. Unified Wi-Fi Access & QR Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Wi-Fi Connection Details",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    qrDialogType = QrType.WIFI_CONNECT
                                    showQrDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.QrCode,
                                    contentDescription = "Show QR Code",
                                    tint = PrimaryBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = onNavigateToProxyConfig,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = "Configure Ports",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Wi-Fi SSID Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Network (SSID)", fontSize = 13.sp, color = TextMuted)
                        Text(
                            text = config.ssid,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    // Password Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Password", fontSize = 13.sp, color = TextMuted)
                        Text(
                            text = config.passphrase,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = PrimaryBlack
                        )
                    }

                    // Gateway Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Proxy Gateway", fontSize = 13.sp, color = TextMuted)
                        Text(
                            text = "${activeState.gatewayAddress}:${config.proxyPort}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                qrDialogType = QrType.WIFI_CONNECT
                                showQrDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Show QR Code", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("NetFetch Password", config.passphrase)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Password copied!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy Key", fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Unified Live Performance & Devices Strip
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Speeds
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Speed", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${activeState.downloadSpeedBps / 1024} KB/s",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    HorizontalDivider(modifier = Modifier.height(32.dp).width(1.dp), color = SurfaceBorder)

                    // Total Transferred
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DataUsage, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Total", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatBytes(activeState.totalBytesTransferred),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    HorizontalDivider(modifier = Modifier.height(32.dp).width(1.dp), color = SurfaceBorder)

                    // Connected Devices (Clickable)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateToDevices() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Devices", fontSize = 11.sp, color = TextMuted)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${activeState.connectedClients.size} Online",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GreenSuccess
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Compact Stealth & PC Broadband Badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = GreenSuccess.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stealth TTL 64 Active • PC Broadband Ready",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = GreenSuccess
                        )
                    }

                    Text(
                        text = "PORTAL ↗",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack,
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://${activeState.gatewayAddress}:${config.proxyPort}/"))
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                    )
                }
            }

        } else {

            // ── Inactive (Idle) View ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Hotspot Configuration",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )

                        IconButton(
                            onClick = {
                                qrDialogType = QrType.WIFI_CONNECT
                                showQrDialog = true
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "Show QR Code",
                                tint = PrimaryBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Wi-Fi Name", fontSize = 13.sp, color = TextMuted)
                        Text(config.ssid, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Password", fontSize = 13.sp, color = TextMuted)
                        Text(config.passphrase, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = PrimaryBlack)
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Wi-Fi Band Selector
                    Text("Wi-Fi Frequency Band", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BandPreference.entries.forEach { band ->
                            val selected = config.bandPreference == band
                            FilterChip(
                                selected = selected,
                                onClick = { onBandChange(band) },
                                label = { Text(band.name.replace("BAND_", "").replace("_", " "), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlack,
                                    selectedLabelColor = CardWhite,
                                    containerColor = CreamBackground,
                                    labelColor = TextDark
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderColor = SurfaceBorder,
                                    selectedBorderColor = PrimaryBlack,
                                    enabled = true,
                                    selected = selected
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ── Error Alert ──
        if (state is HotspotState.Error) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RedError.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RedError, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.errorMessage, fontSize = 12.sp, color = RedError, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        QrCodeDialog(
            config = config,
            initialType = qrDialogType,
            onDismissRequest = { showQrDialog = false }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
