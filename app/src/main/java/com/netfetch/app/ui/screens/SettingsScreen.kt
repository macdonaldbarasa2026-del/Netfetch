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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netfetch.app.model.BandPreference
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.TetherMode
import com.netfetch.app.netfetchlink.NetfetchLinkProtocol
import com.netfetch.app.ui.theme.*

@Composable
fun SettingsScreen(
    config: HotspotConfig,
    onUpdateConfig: (HotspotConfig) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var proxyPortInput by remember(config.proxyPort) {
        mutableStateOf(config.proxyPort.toString())
    }

    var socksPortInput by remember(config.socksPort) {
        mutableStateOf(config.socksPort.toString())
    }

    var socksUsername by remember(config.socksUsername) {
        mutableStateOf(config.socksUsername)
    }

    var socksPassword by remember(config.socksPassword) {
        mutableStateOf(config.socksPassword)
    }

    var showSocksPassword by remember {
        mutableStateOf(false)
    }

    var wifiSsid by remember(config.ssid) {
        mutableStateOf(config.ssid)
    }

    var wifiPassphrase by remember(config.passphrase) {
        mutableStateOf(config.passphrase)
    }

    var showWifiPassword by remember {
        mutableStateOf(false)
    }

    var selectedBand by remember(config.bandPreference) {
        mutableStateOf(config.bandPreference)
    }

    var maxClients by remember(config.maxConnectedClients) {
        mutableStateOf(config.maxConnectedClients.toFloat())
    }

    var udpForwarding by remember(config.udpForwarding) {
        mutableStateOf(config.udpForwarding)
    }

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
            text = "Customize Wi-Fi band, proxy ports, credentials, and performance",
            fontSize = 12.sp,
            color = TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Pro transport", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextDark)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = udpForwarding,
                        onCheckedChange = { enabled ->
                            udpForwarding = enabled
                            onUpdateConfig(config.copy(udpForwarding = enabled))
                        },
                        enabled = config.mode == TetherMode.PRO
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("UDP forwarding", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (NetfetchLinkProtocol.UDP_TRANSPORT_AVAILABLE) "Available when negotiated with the provider."
                            else "UDP unavailable on this connection. The preference is retained but TCP-only routing remains active.",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Wi-Fi Sharing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Set the Wi-Fi name and 8-digit key that other devices use to connect to NetFetch.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wifiSsid,
                    onValueChange = { wifiSsid = it },
                    label = { Text("Wi-Fi Name (SSID)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlack,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = PrimaryBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = wifiPassphrase,
                    onValueChange = {
                        if (it.length <= 8 && it.all(Char::isDigit)) {
                            wifiPassphrase = it
                        }
                    },
                    label = { Text("Wi-Fi Key (8 digits)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword
                    ),
                    visualTransformation = if (showWifiPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                showWifiPassword = !showWifiPassword
                            }
                        ) {
                            Icon(
                                imageVector = if (showWifiPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showWifiPassword) {
                                    "Hide Wi-Fi key"
                                } else {
                                    "Show Wi-Fi key"
                                }
                            )
                        }
                    },
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
                        when {
                            wifiSsid.isBlank() -> {
                                Toast.makeText(
                                    context,
                                    "Wi-Fi name cannot be empty",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            !wifiPassphrase.matches(Regex("\\d{8}")) -> {
                                Toast.makeText(
                                    context,
                                    "Wi-Fi key must contain exactly 8 digits",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            else -> {
                                onUpdateConfig(
                                    config.copy(
                                        ssid = wifiSsid,
                                        passphrase = wifiPassphrase
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    "Wi-Fi sharing key saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = CardWhite
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Wi-Fi Key")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                                onUpdateConfig(
                                    config.copy(bandPreference = band)
                                )
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryBlack
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            band.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberWarning.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AmberWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("5 GHz Compatibility Note", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "5 GHz provides the fastest throughput, but requires hardware support on both this device and connecting clients. If client devices fail to discover or connect to the 5 GHz hotspot, NetFetch automatically falls back to Auto/2.4 GHz, or switch directly to Auto.",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "HTTP/HTTPS Proxy",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Devices can use this proxy for normal HTTP and HTTPS traffic.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = proxyPortInput,
                    onValueChange = { proxyPortInput = it },
                    label = { Text("HTTP/HTTPS Proxy Port") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
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
                            onUpdateConfig(
                                config.copy(proxyPort = port)
                            )

                            Toast.makeText(
                                context,
                                "Proxy Port set to $port",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                "Invalid Port (Must be 1024 - 65535)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = CardWhite
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save Port")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = CardWhite),
            border = BorderStroke(1.dp, SurfaceBorder),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SOCKS5 Gateway",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Use these credentials when connecting a device or app to the SOCKS5 gateway.",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = socksPortInput,
                    onValueChange = { socksPortInput = it },
                    label = { Text("SOCKS5 Port") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlack,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = PrimaryBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = socksUsername,
                    onValueChange = { socksUsername = it },
                    label = { Text("Username") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlack,
                        unfocusedBorderColor = SurfaceBorder,
                        focusedLabelColor = PrimaryBlack
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = socksPassword,
                    onValueChange = { socksPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (showSocksPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                showSocksPassword = !showSocksPassword
                            }
                        ) {
                            Icon(
                                imageVector = if (showSocksPassword) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (showSocksPassword) {
                                    "Hide password"
                                } else {
                                    "Show password"
                                }
                            )
                        }
                    },
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
                        val port = socksPortInput.toIntOrNull()

                        when {
                            port == null || port !in 1024..65535 -> {
                                Toast.makeText(
                                    context,
                                    "Invalid SOCKS5 port (1024 - 65535)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            socksUsername.isBlank() -> {
                                Toast.makeText(
                                    context,
                                    "Username cannot be empty",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            socksPassword.isBlank() -> {
                                Toast.makeText(
                                    context,
                                    "Password cannot be empty",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            else -> {
                                onUpdateConfig(
                                    config.copy(
                                        socksPort = port,
                                        socksUsername = socksUsername,
                                        socksPassword = socksPassword
                                    )
                                )

                                Toast.makeText(
                                    context,
                                    "SOCKS5 settings saved",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlack,
                        contentColor = CardWhite
                    ),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save SOCKS5")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                        onUpdateConfig(
                            config.copy(
                                maxConnectedClients = it.toInt()
                            )
                        )
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
                Text(
                    "NetFetch v1.0.0",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )

                Text(
                    "Classic Light Theme Edition - 100% Free",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}
