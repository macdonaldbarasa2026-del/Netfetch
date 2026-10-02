package com.netfetch.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.ui.theme.*

@Composable
fun SettingsScreen(
    config: HotspotConfig,
    onUpdateConfig: (HotspotConfig) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var proxyPortInput by remember(config.proxyPort) { mutableStateOf(config.proxyPort.toString()) }
    var selectedBand by remember(config.bandPreference) { mutableStateOf(config.bandPreference) }
    var maxClients by remember(config.maxConnectedClients) { mutableStateOf(config.maxConnectedClients.toFloat()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlack
        )
        Text(
            text = "Customize Wi-Fi band, proxy ports, and performance",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Band Preference Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wi-Fi Frequency Band",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Choose 5 GHz for maximum speed or 2.4 GHz for extended range.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                BandPreference.entries.forEach { band ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedBand == band,
                            onClick = {
                                selectedBand = band
                                onUpdateConfig(config.copy(bandPreference = band))
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlack)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(band.displayName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Custom Proxy Port Settings Card
        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Proxy Server Port",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Default port is 8282.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = proxyPortInput,
                    onValueChange = { proxyPortInput = it },
                    label = { Text("HTTP/HTTPS Proxy Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlack,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = PrimaryBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val port = proxyPortInput.toIntOrNull()
                        if (port != null && port in 1024..65535) {
                            onUpdateConfig(config.copy(proxyPort = port))
                            Toast.makeText(context, "Proxy Port set to $port", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid Port (Must be 1024 - 65535)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack, contentColor = CardWhite),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Port")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Max Connections Limit Card
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
                        text = "Maximum Client Connections",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "${maxClients.toInt()} Devices",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = maxClients,
                    onValueChange = {
                        maxClients = it
                        onUpdateConfig(config.copy(maxConnectedClients = it.toInt()))
                    },
                    valueRange = 1f..30f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlack,
                        activeTrackColor = PrimaryBlack,
                        inactiveTrackColor = SurfaceBorder
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("NetFetch v1.0.0", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Text("Classic Light Theme Edition - 100% Free", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}
