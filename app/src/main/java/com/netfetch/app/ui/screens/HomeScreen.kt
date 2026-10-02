package com.netfetch.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.HotspotState
import com.netfetch.app.model.TetherMode
import com.netfetch.app.ui.theme.*

@Composable
fun HomeScreen(
    state: HotspotState,
    config: HotspotConfig,
    onToggleHotspot: () -> Unit,
    onModeChange: (TetherMode) -> Unit,
    onBandChange: (BandPreference) -> Unit,
    onNavigateToDevices: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val isActive = state is HotspotState.Active
    val isStarting = state is HotspotState.Starting

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
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

        // Normal vs Pro Mode Selector Switch Card
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
                                text = if (mode == TetherMode.NORMAL) "HTTP/HTTPS Proxy" else "SOCKS5 Unblocked Internet",
                                fontSize = 10.sp,
                                color = if (selected) CreamBackground.copy(alpha = 0.8f) else TextMuted
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Power Toggle Button
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
                isActive -> "Tap to Stop Hotspot"
                isStarting -> "Starting Services..."
                else -> "Tap to Start Hotspot"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = TextDark
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Speedometer Card
        if (state is HotspotState.Active) {
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
                            text = "${state.downloadSpeedBps / 1024} KB/s",
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
                            text = "${state.uploadSpeedBps / 1024} KB/s",
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
                            text = "${state.connectedClients.size} Active",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Hotspot & Proxy Credentials Card
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
                        color = PrimaryBlack
                    )
                    Text(
                        text = config.mode.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GreenSuccess
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CredentialItem(label = "Wi-Fi Name (SSID)", value = config.ssid, context = context)
                CredentialItem(label = "Password", value = config.passphrase, context = context)
                CredentialItem(label = "Proxy / Host IP", value = config.hostIp, context = context)
                CredentialItem(label = "HTTP Proxy Port", value = config.proxyPort.toString(), context = context)
                
                if (config.mode == TetherMode.PRO) {
                    CredentialItem(label = "Pro Mode SOCKS5 Port", value = config.socksPort.toString(), context = context)
                }
                
                CredentialItem(label = "Auto PAC URL", value = "http://${config.hostIp}:${config.pacPort}/wpad.dat", context = context)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Band Selection Card
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
fun CredentialItem(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
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
