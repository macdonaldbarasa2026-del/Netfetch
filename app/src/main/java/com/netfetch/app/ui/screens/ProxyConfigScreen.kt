package com.netfetch.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.model.TetherMode
import com.netfetch.app.ui.theme.*

/**
 * Proxy Configuration Screen
 *
 * Provides dedicated controls and text input fields for:
 * - HTTP/HTTPS Proxy port number configuration
 * - SOCKS5 Proxy port number and credentials configuration
 * - PAC (Proxy Auto-Configuration) script port number
 * - Port collision detection and validity checks
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxyConfigScreen(
    config: HotspotConfig,
    onNavigateBack: () -> Unit,
    onUpdateConfig: (HotspotConfig) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var httpPortInput by remember(config.proxyPort) {
        mutableStateOf(config.proxyPort.toString())
    }
    var pacPortInput by remember(config.pacPort) {
        mutableStateOf(config.pacPort.toString())
    }
    var socksPortInput by remember(config.socksPort) {
        mutableStateOf(config.socksPort.toString())
    }
    var socksUsernameInput by remember(config.socksUsername) {
        mutableStateOf(config.socksUsername)
    }
    var socksPasswordInput by remember(config.socksPassword) {
        mutableStateOf(config.socksPassword)
    }
    var showPassword by remember {
        mutableStateOf(false)
    }

    val parsedHttpPort = httpPortInput.toIntOrNull()
    val parsedPacPort = pacPortInput.toIntOrNull()
    val parsedSocksPort = socksPortInput.toIntOrNull()

    val isHttpPortValid = parsedHttpPort != null && parsedHttpPort in 1024..65535
    val isPacPortValid = parsedPacPort != null && parsedPacPort in 1024..65535
    val isSocksPortValid = parsedSocksPort != null && parsedSocksPort in 1024..65535

    val hasPortCollision = (parsedHttpPort != null && parsedHttpPort == parsedPacPort) ||
            (parsedHttpPort != null && parsedHttpPort == parsedSocksPort) ||
            (parsedPacPort != null && parsedPacPort == parsedSocksPort)

    val canSave = isHttpPortValid && isPacPortValid && isSocksPortValid &&
            !hasPortCollision && socksUsernameInput.isNotBlank() && socksPasswordInput.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Proxy Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                        Text(
                            text = "Configure custom HTTP & SOCKS5 ports",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PrimaryBlack
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CreamBackground
                )
            )
        },
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // ── Live Host Gateway Banner ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hotspot Gateway IP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted
                        )
                        Text(
                            text = config.hostIp,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlack
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (config.mode == TetherMode.PRO) PrimaryBlack else GreenSuccess.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (config.mode == TetherMode.PRO) "PRO SOCKS5" else "NORMAL HTTP",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (config.mode == TetherMode.PRO) CardWhite else GreenSuccess,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── HTTP/HTTPS Proxy Port Card ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HTTP / HTTPS Proxy Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Connected devices (browsers, TVs, consoles, PCs) route web traffic through this local port.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = httpPortInput,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all { it.isDigit() }) {
                                httpPortInput = input
                            }
                        },
                        label = { Text("HTTP Proxy Port (1024 - 65535)") },
                        placeholder = { Text("8282") },
                        isError = !isHttpPortValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Router, contentDescription = null, tint = PrimaryBlack)
                        },
                        trailingIcon = {
                            if (isHttpPortValid) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess)
                            }
                        },
                        supportingText = {
                            if (!isHttpPortValid) {
                                Text("Enter a valid unprivileged port (1024 - 65535)", color = RedError)
                            } else {
                                Text("Standard: 8282. Clients configure proxy: ${config.hostIp}:$httpPortInput", color = TextMuted)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlack,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = PrimaryBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quick Port Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(8282, 8080, 3128, 8888).forEach { preset ->
                            FilterChip(
                                selected = httpPortInput == preset.toString(),
                                onClick = { httpPortInput = preset.toString() },
                                label = { Text(preset.toString(), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlack,
                                    selectedLabelColor = CardWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SurfaceBorder, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // PAC Auto-Config Port
                    OutlinedTextField(
                        value = pacPortInput,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all { it.isDigit() }) {
                                pacPortInput = input
                            }
                        },
                        label = { Text("PAC Auto-Config Port (1024 - 65535)") },
                        placeholder = { Text("8283") },
                        isError = !isPacPortValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryBlack)
                        },
                        supportingText = {
                            if (!isPacPortValid) {
                                Text("Enter a valid port for PAC script delivery", color = RedError)
                            } else {
                                Text("PAC Script URL: http://${config.hostIp}:$pacPortInput/wpad.dat", color = TextMuted)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlack,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = PrimaryBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── SOCKS5 Proxy Port Card ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardWhite),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SOCKS5 Proxy Settings",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Used by Pro mode, Telegram, games, and VPN applications to route raw TCP streams.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = socksPortInput,
                        onValueChange = { input ->
                            if (input.length <= 5 && input.all { it.isDigit() }) {
                                socksPortInput = input
                            }
                        },
                        label = { Text("SOCKS5 Port (1024 - 65535)") },
                        placeholder = { Text("1080") },
                        isError = !isSocksPortValid,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Security, contentDescription = null, tint = PrimaryBlack)
                        },
                        trailingIcon = {
                            if (isSocksPortValid) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GreenSuccess)
                            }
                        },
                        supportingText = {
                            if (!isSocksPortValid) {
                                Text("Enter a valid unprivileged port (1024 - 65535)", color = RedError)
                            } else {
                                Text("Standard: 1080. SOCKS5 target: ${config.hostIp}:$socksPortInput", color = TextMuted)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlack,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = PrimaryBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Quick Port Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1080, 1081, 9050, 10808).forEach { preset ->
                            FilterChip(
                                selected = socksPortInput == preset.toString(),
                                onClick = { socksPortInput = preset.toString() },
                                label = { Text(preset.toString(), fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryBlack,
                                    selectedLabelColor = CardWhite
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SOCKS5 Username
                    OutlinedTextField(
                        value = socksUsernameInput,
                        onValueChange = { socksUsernameInput = it.trim() },
                        label = { Text("SOCKS5 Username") },
                        placeholder = { Text("netfetch") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlack)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlack,
                            unfocusedBorderColor = SurfaceBorder,
                            focusedLabelColor = PrimaryBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SOCKS5 Password
                    OutlinedTextField(
                        value = socksPasswordInput,
                        onValueChange = { socksPasswordInput = it.trim() },
                        label = { Text("SOCKS5 Password") },
                        placeholder = { Text("netfetch1080") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryBlack)
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password",
                                    tint = PrimaryBlack
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
                }
            }

            // ── Port Collision Warning (if any) ──
            if (hasPortCollision) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RedError.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, RedError.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Port collision detected! HTTP, PAC, and SOCKS5 must use distinct port numbers.",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RedError
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Action Buttons ──
            Button(
                onClick = {
                    val httpPort = httpPortInput.toIntOrNull()
                    val pacPort = pacPortInput.toIntOrNull()
                    val socksPort = socksPortInput.toIntOrNull()

                    when {
                        httpPort == null || httpPort !in 1024..65535 -> {
                            Toast.makeText(context, "Invalid HTTP proxy port (1024 - 65535)", Toast.LENGTH_SHORT).show()
                        }
                        pacPort == null || pacPort !in 1024..65535 -> {
                            Toast.makeText(context, "Invalid PAC port (1024 - 65535)", Toast.LENGTH_SHORT).show()
                        }
                        socksPort == null || socksPort !in 1024..65535 -> {
                            Toast.makeText(context, "Invalid SOCKS5 port (1024 - 65535)", Toast.LENGTH_SHORT).show()
                        }
                        hasPortCollision -> {
                            Toast.makeText(context, "HTTP, PAC, and SOCKS5 cannot use the same port", Toast.LENGTH_SHORT).show()
                        }
                        socksUsernameInput.isBlank() -> {
                            Toast.makeText(context, "SOCKS5 username cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                        socksPasswordInput.isBlank() -> {
                            Toast.makeText(context, "SOCKS5 password cannot be empty", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            val updatedConfig = config.copy(
                                proxyPort = httpPort,
                                pacPort = pacPort,
                                socksPort = socksPort,
                                socksUsername = socksUsernameInput,
                                socksPassword = socksPasswordInput
                            )
                            onUpdateConfig(updatedConfig)
                            Toast.makeText(
                                context,
                                "Proxy ports updated: HTTP $httpPort | PAC $pacPort | SOCKS5 $socksPort",
                                Toast.LENGTH_LONG
                            ).show()
                            onNavigateBack()
                        }
                    }
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlack,
                    contentColor = CardWhite
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Proxy Settings", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    httpPortInput = "8282"
                    pacPortInput = "8283"
                    socksPortInput = "1080"
                    socksUsernameInput = "netfetch"
                    socksPasswordInput = "netfetch1080"

                    val resetConfig = config.copy(
                        proxyPort = 8282,
                        pacPort = 8283,
                        socksPort = 1080,
                        socksUsername = "netfetch",
                        socksPassword = "netfetch1080"
                    )
                    onUpdateConfig(resetConfig)
                    Toast.makeText(context, "Reset to default proxy ports", Toast.LENGTH_SHORT).show()
                },
                border = BorderStroke(1.dp, PrimaryBlack.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, tint = PrimaryBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reset to Defaults", color = PrimaryBlack, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
