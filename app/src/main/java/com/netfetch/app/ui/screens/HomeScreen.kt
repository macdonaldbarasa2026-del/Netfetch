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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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

enum class HomeMode {
    SHARE_HOTSPOT,
    NETFETCH_CONNECT
}

enum class ReceiverConnectMode {
    AUTO_DISCOVERY,
    DIRECT_GATEWAY
}

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
    onStopReceiver: () -> Unit = {},
    onDirectConnectReceiver: (host: String, port: Int) -> Unit = { _, _ -> },
    onTestReceiverConnectivity: ((Boolean, Long, String) -> Unit) -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isActive = state is HotspotState.Active
    val isStarting = state is HotspotState.Starting
    val activeState = state as? HotspotState.Active

    // Auto-select CONNECT tab if receiver is active/connecting, else SHARE
    var selectedTab by remember {
        mutableStateOf(
            if (receiverState !is NetfetchReceiverState.Idle) HomeMode.NETFETCH_CONNECT else HomeMode.SHARE_HOTSPOT
        )
    }

    var showQrDialog by remember { mutableStateOf(false) }
    var qrDialogType by remember { mutableStateOf(QrType.WIFI_CONNECT) }

    // Direct gateway inputs for NetFetch Connect client
    var receiverConnectMode by remember { mutableStateOf(ReceiverConnectMode.AUTO_DISCOVERY) }
    var gatewayHostInput by remember { mutableStateOf("192.168.49.1") }
    var gatewayPortInput by remember {
        mutableStateOf(if (config.mode == TetherMode.PRO) "1080" else "8282")
    }

    // Ping test state
    var isTestingPing by remember { mutableStateOf(false) }
    var pingResult by remember { mutableStateOf<String?>(null) }
    var pingSuccess by remember { mutableStateOf<Boolean?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top App Bar ──
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
                color = when {
                    isActive -> GreenSuccess.copy(alpha = 0.12f)
                    receiverState is NetfetchReceiverState.Connected -> GreenSuccess.copy(alpha = 0.12f)
                    receiverState is NetfetchReceiverState.Searching ||
                    receiverState is NetfetchReceiverState.Connecting -> AmberWarning.copy(alpha = 0.12f)
                    isStarting -> AmberWarning.copy(alpha = 0.12f)
                    else -> CardWhite
                },
                border = BorderStroke(
                    1.dp,
                    when {
                        isActive || receiverState is NetfetchReceiverState.Connected -> GreenSuccess
                        receiverState is NetfetchReceiverState.Searching ||
                        receiverState is NetfetchReceiverState.Connecting ||
                        isStarting -> AmberWarning
                        else -> SurfaceBorder
                    }
                )
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
                                    isActive || receiverState is NetfetchReceiverState.Connected -> GreenSuccess
                                    isStarting ||
                                    receiverState is NetfetchReceiverState.Searching ||
                                    receiverState is NetfetchReceiverState.Connecting -> AmberWarning
                                    else -> TextMuted
                                },
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isActive -> "HOST ACTIVE"
                            receiverState is NetfetchReceiverState.Connected -> "CLIENT LINKED"
                            receiverState is NetfetchReceiverState.Searching -> "SCANNING..."
                            receiverState is NetfetchReceiverState.Connecting -> "CONNECTING..."
                            isStarting -> "STARTING..."
                            else -> "READY"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isActive || receiverState is NetfetchReceiverState.Connected -> GreenSuccess
                            isStarting ||
                            receiverState is NetfetchReceiverState.Searching ||
                            receiverState is NetfetchReceiverState.Connecting -> AmberWarning
                            else -> TextDark
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // ── Primary Segmented Selector: [ Share Hotspot ]  [ NetFetch Connect ] ──
        Surface(
            shape = RoundedCornerShape(14.dp),
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
                // Share Tab
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == HomeMode.SHARE_HOTSPOT) PrimaryBlack else CardWhite,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = HomeMode.SHARE_HOTSPOT }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = null,
                            tint = if (selectedTab == HomeMode.SHARE_HOTSPOT) CardWhite else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Share Hotspot",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == HomeMode.SHARE_HOTSPOT) CardWhite else TextDark
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GreenSuccess, CircleShape)
                            )
                        }
                    }
                }

                // Connect Tab (Android-to-Android receiver)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == HomeMode.NETFETCH_CONNECT) PrimaryBlack else CardWhite,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = HomeMode.NETFETCH_CONNECT }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = if (selectedTab == HomeMode.NETFETCH_CONNECT) CardWhite else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "NetFetch Connect",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == HomeMode.NETFETCH_CONNECT) CardWhite else TextDark
                        )
                        if (receiverState is NetfetchReceiverState.Connected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(GreenSuccess, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ══════════════════════════════════════════════════════════════
        // TAB 1: SHARE HOTSPOT (HOST MODE)
        // ══════════════════════════════════════════════════════════════
        if (selectedTab == HomeMode.SHARE_HOTSPOT) {

            // Hero Power Button
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

            // Mode Toggle (Normal vs Pro)
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

            Spacer(modifier = Modifier.height(18.dp))

            // Active Hotspot Details or Inactive Config
            if (activeState != null) {
                // 1. Wi-Fi Access & QR Card
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

                        // SSID Row
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
                                fontFamily = FontFamily.Monospace,
                                color = PrimaryBlack
                            )
                        }

                        // Proxy Gateway Row
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

                        // Quick Actions
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

                // 2. Performance & Devices Strip
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

                // Portal Banner
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
                                text = "Stealth TTL 64 Active • No-Root Bypass",
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
                // Inactive Configuration View
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
                            Text(config.passphrase, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = PrimaryBlack)
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

            Spacer(modifier = Modifier.height(14.dp))

            // Switch to Client Banner Callout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, SurfaceBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedTab = HomeMode.NETFETCH_CONNECT }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlack.copy(alpha = 0.08f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Sensors, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(16.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Receive from another NetFetch phone?",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                            Text(
                                text = "Switch to NetFetch Connect client mode",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        // ══════════════════════════════════════════════════════════════
        // TAB 2: NETFETCH CONNECT (CLIENT RECEIVER MODE)
        // ══════════════════════════════════════════════════════════════
        if (selectedTab == HomeMode.NETFETCH_CONNECT) {
            val isReceiverConnected = receiverState is NetfetchReceiverState.Connected
            val isReceiverBusy = receiverState is NetfetchReceiverState.Searching ||
                    receiverState is NetfetchReceiverState.Connecting ||
                    receiverState is NetfetchReceiverState.Authenticating

            // Hero Connect Button
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isReceiverConnected -> GreenSuccess
                            isReceiverBusy -> AmberWarning
                            else -> PrimaryBlack
                        }
                    )
                    .border(
                        BorderStroke(
                            4.dp,
                            when {
                                isReceiverConnected -> GreenSuccess.copy(alpha = 0.5f)
                                isReceiverBusy -> AmberWarning.copy(alpha = 0.5f)
                                else -> CardWhite
                            }
                        ),
                        shape = CircleShape
                    )
                    .clickable {
                        if (isReceiverConnected || isReceiverBusy) {
                            onStopReceiver()
                            pingResult = null
                            pingSuccess = null
                        } else {
                            if (receiverConnectMode == ReceiverConnectMode.AUTO_DISCOVERY) {
                                onStartReceiver()
                            } else {
                                val port = gatewayPortInput.toIntOrNull() ?: 1080
                                onDirectConnectReceiver(gatewayHostInput.trim(), port)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isReceiverBusy) {
                    CircularProgressIndicator(
                        color = CardWhite,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isReceiverConnected -> Icons.Default.CheckCircle
                            else -> Icons.Default.Link
                        },
                        contentDescription = "Receiver Action",
                        tint = CardWhite,
                        modifier = Modifier.size(52.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = when {
                    isReceiverConnected -> "Connected to NetFetch Host (Tap to Disconnect)"
                    receiverState is NetfetchReceiverState.Searching -> "Scanning for nearby NetFetch hosts..."
                    receiverState is NetfetchReceiverState.Connecting -> "Connecting to host..."
                    receiverState is NetfetchReceiverState.Authenticating -> "Verifying encrypted link handshake..."
                    receiverState is NetfetchReceiverState.Error -> "Connection failed (Tap to retry)"
                    else -> "Tap to Connect to NetFetch Host"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    isReceiverConnected -> GreenSuccess
                    isReceiverBusy -> AmberWarning
                    receiverState is NetfetchReceiverState.Error -> RedError
                    else -> PrimaryBlack
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Receiver Connection Method Selector
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
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (receiverConnectMode == ReceiverConnectMode.AUTO_DISCOVERY) PrimaryBlack else CardWhite,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isReceiverConnected && !isReceiverBusy) {
                                receiverConnectMode = ReceiverConnectMode.AUTO_DISCOVERY
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Sensors,
                                contentDescription = null,
                                tint = if (receiverConnectMode == ReceiverConnectMode.AUTO_DISCOVERY) CardWhite else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-Pair (P2P)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (receiverConnectMode == ReceiverConnectMode.AUTO_DISCOVERY) CardWhite else TextDark
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (receiverConnectMode == ReceiverConnectMode.DIRECT_GATEWAY) PrimaryBlack else CardWhite,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !isReceiverConnected && !isReceiverBusy) {
                                receiverConnectMode = ReceiverConnectMode.DIRECT_GATEWAY
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Lan,
                                contentDescription = null,
                                tint = if (receiverConnectMode == ReceiverConnectMode.DIRECT_GATEWAY) CardWhite else TextMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Direct Gateway IP",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (receiverConnectMode == ReceiverConnectMode.DIRECT_GATEWAY) CardWhite else TextDark
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Direct Gateway Configuration (if selected)
            if (receiverConnectMode == ReceiverConnectMode.DIRECT_GATEWAY && !isReceiverConnected) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardWhite),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Direct Gateway Host & Port",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                        Text(
                            text = "Use this when your Wi-Fi is already connected to the host's access point.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = gatewayHostInput,
                                onValueChange = { gatewayHostInput = it },
                                label = { Text("Host IP") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                modifier = Modifier.weight(2f),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = gatewayPortInput,
                                onValueChange = { gatewayPortInput = it },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = gatewayHostInput == "192.168.49.1" && gatewayPortInput == "1080",
                                onClick = {
                                    gatewayHostInput = "192.168.49.1"
                                    gatewayPortInput = "1080"
                                },
                                label = { Text("Default SOCKS5 (:1080)", fontSize = 10.sp) }
                            )

                            FilterChip(
                                selected = gatewayHostInput == "192.168.49.1" && gatewayPortInput == "8282",
                                onClick = {
                                    gatewayHostInput = "192.168.49.1"
                                    gatewayPortInput = "8282"
                                },
                                label = { Text("HTTP Proxy (:8282)", fontSize = 10.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Active Connected Telemetry & Diagnostics Card
            if (isReceiverConnected) {
                val connState = receiverState as NetfetchReceiverState.Connected
                Card(
                    colors = CardDefaults.cardColors(containerColor = GreenSuccess.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, GreenSuccess.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Encrypted Tunnel Active",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GreenSuccess.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "ROUTING ALL APPS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GreenSuccess,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Host Gateway", fontSize = 13.sp, color = TextMuted)
                            Text(
                                connState.providerAddress,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = PrimaryBlack
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Protocol", fontSize = 13.sp, color = TextMuted)
                            Text(
                                if (connState.socksPort > 0) "SOCKS5 Tunnel :${connState.socksPort}" else "HTTP Proxy :${connState.httpPort}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Tunnel Status", fontSize = 13.sp, color = TextMuted)
                            Text(
                                "Connected • Zero Manual Proxy",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryBlack
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Test Connectivity Button
                        Button(
                            onClick = {
                                isTestingPing = true
                                pingResult = "Checking connection..."
                                onTestReceiverConnectivity { success, latency, msg ->
                                    isTestingPing = false
                                    pingSuccess = success
                                    pingResult = if (success) "✓ Latency: ${latency}ms • Internet Working!" else "✗ $msg"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isTestingPing,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isTestingPing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CardWhite, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing Gateway...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Test Internet Connection", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (pingResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (pingSuccess == true) GreenSuccess.copy(alpha = 0.12f) else RedError.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = pingResult ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pingSuccess == true) GreenSuccess else RedError,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Step-by-Step Setup Guide Card (always visible for clarity)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Android-to-Android Setup Guide",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                    Text(
                        text = "How to receive internet from another NetFetch or NetShare phone:",
                        fontSize = 11.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SetupStepItem(
                        step = "1",
                        title = "Start Hotspot on Host Phone",
                        description = "Open NetFetch on the first phone and tap 'Tap to Start Hotspot' on the Share tab."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SetupStepItem(
                        step = "2",
                        title = "Connect This Phone",
                        description = "Join the host's Wi-Fi network (or choose Auto-Pair P2P to connect automatically)."
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SetupStepItem(
                        step = "3",
                        title = "Tap Connect",
                        description = "NetFetch starts an authenticated local VPN tunnel. All apps, games, YouTube, and browsing work immediately with zero manual proxy setup!"
                    )
                }
            }

            // Error Notice
            if (receiverState is NetfetchReceiverState.Error) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RedError.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, RedError.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = RedError, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Connection Error", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = RedError)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = receiverState.message,
                            fontSize = 11.sp,
                            color = RedError
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                if (receiverConnectMode == ReceiverConnectMode.AUTO_DISCOVERY) {
                                    onStartReceiver()
                                } else {
                                    val port = gatewayPortInput.toIntOrNull() ?: 1080
                                    onDirectConnectReceiver(gatewayHostInput.trim(), port)
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Retry Connection", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Hotspot Error Alert (Global)
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

        Spacer(modifier = Modifier.height(64.dp))
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

@Composable
private fun SetupStepItem(step: String, title: String, description: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = PrimaryBlack,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(step, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CardWhite)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlack)
            Spacer(modifier = Modifier.height(2.dp))
            Text(description, fontSize = 11.sp, color = TextMuted)
        }
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
