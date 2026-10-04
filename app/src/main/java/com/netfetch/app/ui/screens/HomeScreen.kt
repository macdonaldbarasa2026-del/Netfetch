package com.netfetch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    onStartReceiver: () -> Unit,
    onStopReceiver: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isActive = state is HotspotState.Active
    val isStarting = state is HotspotState.Starting

    val activeState = state as? HotspotState.Active
    var showSocksPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlack
                )
                Text(
                    text = "No-Root Mobile Tethering",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isActive) GreenSuccess.copy(alpha = 0.12f) else SurfaceBorder,
                border = BorderStroke(1.dp, if (isActive) GreenSuccess else SurfaceBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                            isActive -> if (config.mode == TetherMode.PRO) "PRO ACTIVE" else "ACTIVE"
                            isStarting -> "STARTING"
                            else -> "OFFLINE"
                        },
                        fontSize = 12.sp,
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

        Spacer(modifier = Modifier.height(20.dp))

        // ── Mode Selector ──
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TetherMode.entries.forEach { mode ->
                    val selected = config.mode == mode
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) PrimaryBlack else CreamBackground,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onModeChange(mode) }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mode.displayName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) CardWhite else TextDark
                            )
                            Text(
                                text = if (mode == TetherMode.NORMAL) "HTTP/HTTPS Proxy" else "SOCKS5 TCP Tunnel",
                                fontSize = 10.sp,
                                color = if (selected) CreamBackground.copy(alpha = 0.8f) else TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Power Button ──
        Box(
            modifier = Modifier
                .size(130.dp)
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

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                isActive -> "Tap to Stop NetFetch"
                isStarting -> "Starting Services..."
                else -> "Tap to Start NetFetch"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── NetFetch-to-NetFetch Receiver ──
        // Available in BOTH Normal and Pro modes.
        NetfetchReceiverCard(
            state = receiverState,
            onStart = onStartReceiver,
            onStop = onStopReceiver
        )

        Spacer(modifier = Modifier.height(8.dp))


        // ── Status Dashboard (Active only) ──
        if (activeState != null) {

            // Internet & Upstream Status Card
            val internetColor = if (activeState.internetVerified) GreenSuccess else AmberWarning
            val internetText = if (activeState.internetVerified) "INTERNET: VERIFIED ✓" else "INTERNET: CHECKING..."
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, internetColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NETFETCH STATUS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Text(
                            text = internetText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = internetColor
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    StatusRow("Upstream", activeState.upstreamState.displayName)
                    StatusRow("Gateway", activeState.gatewayAddress)
                    StatusRow("HTTP Proxy", "${activeState.gatewayAddress}:${activeState.config.proxyPort}")
                    if (activeState.config.mode == TetherMode.PRO) {
                        StatusRow("SOCKS5", "${activeState.gatewayAddress}:${activeState.config.socksPort}")
                    }
                    StatusRow("PAC URL", "http://${activeState.gatewayAddress}:${activeState.config.pacPort}/wpad.dat")
                    StatusRow("Clients", "${activeState.connectedClients.size} connected")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Speed / Devices Card
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
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = GreenSuccess, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download", fontSize = 12.sp, color = TextMuted)
                        }
                        Text(
                            text = "${activeState.downloadSpeedBps / 1024} KB/s",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Divider(modifier = Modifier.height(36.dp).width(1.dp), color = SurfaceBorder)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload", fontSize = 12.sp, color = TextMuted)
                        }
                        Text(
                            text = "${activeState.uploadSpeedBps / 1024} KB/s",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Divider(modifier = Modifier.height(36.dp).width(1.dp), color = SurfaceBorder)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onNavigateToDevices() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Devices", fontSize = 12.sp, color = TextMuted)
                        }
                        Text(
                            text = "${activeState.connectedClients.size} Active",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Hotspot & Proxy Credentials Card ──
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hotspot & Proxy Credentials",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = config.mode.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )
                    IconButton(
                        onClick = {
                            val sb = StringBuilder()
                            sb.appendLine("NetFetch Connection Details")
                            sb.appendLine("Wi-Fi Name: ${config.ssid}")
                            sb.appendLine("Wi-Fi Password: ${config.passphrase}")
                            sb.appendLine("Proxy IP: ${config.hostIp}")
                            sb.appendLine("HTTP Proxy Port: ${config.proxyPort}")
                            sb.appendLine("PAC URL: http://${config.hostIp}:${config.pacPort}/wpad.dat")
                            if (config.mode == TetherMode.PRO) {
                                sb.appendLine("SOCKS5 Port: ${config.socksPort}")
                                sb.appendLine("NetFetch receivers discover and authenticate to this provider in-app.")
                                sb.appendLine("Do not share proxy credentials in messages.")
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "NetFetch Connection Details")
                                putExtra(Intent.EXTRA_TEXT, sb.toString().trim())
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Connection Details")
                            )
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share credentials",
                            tint = PrimaryBlack,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CredentialItem(label = "Wi-Fi Name (SSID)", value = config.ssid, context = context)
                CredentialItem(label = "Wi-Fi Key", value = config.passphrase, context = context)
                CredentialItem(label = "Proxy / Gateway IP", value = config.hostIp, context = context)
                CredentialItem(label = "HTTP Proxy Port", value = config.proxyPort.toString(), context = context)

                if (config.mode == TetherMode.PRO) {
                    CredentialItem(label = "SOCKS5 Port (TCP only)", value = config.socksPort.toString(), context = context)
                    CredentialItem(label = "SOCKS5 Username", value = config.socksUsername, context = context)
                    CredentialItem(
                        label = "SOCKS5 Password",
                        value = if (showSocksPassword) config.socksPassword else "••••••••",
                        context = context
                    )
                    TextButton(
                        onClick = { showSocksPassword = !showSocksPassword },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            if (showSocksPassword) "Hide password" else "Show password",
                            color = PrimaryBlack
                        )
                    }
                }

                CredentialItem(label = "PAC URL", value = "http://${config.hostIp}:${config.pacPort}/wpad.dat", context = context)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Band Selection Card ──
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CellTower, contentDescription = null, tint = PrimaryBlack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Wi-Fi Frequency Band", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BandPreference.entries.forEach { band ->
                        val selected = config.bandPreference == band
                        FilterChip(
                            selected = selected,
                            onClick = { onBandChange(band) },
                            label = { Text(band.name.replace("BAND_", "").replace("_", " ")) },
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

        // ── Error Card ──
        if (state is HotspotState.Error) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = RedError.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, RedError),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = RedError)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(state.errorMessage, fontSize = 13.sp, color = TextDark)
                }
            }
        }
    }
}

@Composable
private fun NetfetchReceiverCard(
    state: NetfetchReceiverState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "NetFetch-to-NetFetch",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlack
            )

            Text(
                text = "Discover a nearby provider for the selected mode. Pro starts a TCP VPN tunnel; Normal provides authenticated HTTP/PAC details for proxy configuration.",
                fontSize = 12.sp,
                color = TextMuted
            )

            when (state) {
                NetfetchReceiverState.Idle -> {
                    ReceiverActionButton(
                        text = "Connect to NetFetch Provider",
                        onClick = onStart
                    )
                }

                NetfetchReceiverState.Searching -> {
                    ReceiverStatusText(
                        text = "Searching for a nearby NetFetch provider..."
                    )
                }

                is NetfetchReceiverState.ProviderFound -> {
                    val modeLabel = if (state.providerMode == "PRO") "Pro" else "Normal"
                    ReceiverStatusText(
                        text = "Provider found: ${state.deviceName} [$modeLabel]\nConnecting..."
                    )
                }

                NetfetchReceiverState.Connecting -> {
                    ReceiverStatusText(text = "Connecting to NetFetch provider...")
                }

                NetfetchReceiverState.Authenticating -> {
                    ReceiverStatusText(text = "Authenticating with provider...")
                }

                is NetfetchReceiverState.Connected -> {
                    val modeLabel = if (state.providerMode == "PRO") "Pro" else "Normal"
                    val transportLabel = if (state.providerMode == "PRO") {
                        "Provider session established; receiver TCP VPN is starting"
                    } else {
                        "Provider session established. Configure this device's HTTP proxy or PAC before browsing:\n" +
                            "HTTP ${state.providerAddress}:${state.httpPort}\nPAC: http://${state.providerAddress}:${state.pacPort}/wpad.dat"
                    }

                    ReceiverStatusText(
                        text = "Connected [$modeLabel] — ${state.providerAddress}\n$transportLabel"
                    )

                    ReceiverActionButton(
                        text = "Disconnect",
                        onClick = onStop
                    )
                }

                is NetfetchReceiverState.Unsupported -> {
                    Text(
                        text = "Unsupported provider: ${state.reason}",
                        fontSize = 12.sp,
                        color = AmberWarning
                    )

                    ReceiverActionButton(
                        text = "Try Again",
                        onClick = onStart
                    )
                }

                is NetfetchReceiverState.Reconnecting -> {
                    ReceiverStatusText(
                        text = "Reconnecting... ${state.reason}"
                    )
                }

                is NetfetchReceiverState.Error -> {
                    Text(
                        text = state.message,
                        fontSize = 12.sp,
                        color = RedError
                    )

                    ReceiverActionButton(
                        text = "Try Again",
                        onClick = onStart
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiverStatusText(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = PrimaryBlack
    )
}

@Composable
private fun ReceiverActionButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Text(text)
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = TextMuted, modifier = Modifier.weight(0.4f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark, modifier = Modifier.weight(0.6f))
    }
}

@Composable
fun CredentialItem(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 11.sp, color = TextMuted)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        }

        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(label, value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrimaryBlack, modifier = Modifier.size(16.dp))
        }
    }
}
