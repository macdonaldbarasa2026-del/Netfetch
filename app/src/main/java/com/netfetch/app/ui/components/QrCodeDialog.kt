package com.netfetch.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.netfetch.app.model.HotspotConfig
import com.netfetch.app.ui.theme.*

/**
 * High-resolution QR Code generator using ZXing core.
 */
fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        val blackColor = 0xFF111827.toInt()
        val whiteColor = 0xFFFFFFFF.toInt()

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) blackColor else whiteColor
            }
        }

        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) {
        null
    }
}

enum class QrType {
    WIFI_CONNECT,
    WEB_PORTAL,
    PAC_URL
}

@Composable
fun QrCodeDialog(
    config: HotspotConfig,
    initialType: QrType = QrType.WIFI_CONNECT,
    onDismissRequest: () -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    val context = LocalContext.current

    // Standard Wi-Fi connection QR payload: WIFI:T:WPA;S:<SSID>;P:<Password>;;
    val wifiQrPayload = remember(config.ssid, config.passphrase) {
        "WIFI:T:WPA;S:${config.ssid};P:${config.passphrase};;"
    }

    val portalQrPayload = remember(config.hostIp, config.proxyPort) {
        "http://${config.hostIp}:${config.proxyPort}/"
    }

    val pacQrPayload = remember(config.hostIp, config.pacPort) {
        "http://${config.hostIp}:${config.pacPort}/wpad.dat"
    }

    val currentPayload = when (selectedType) {
        QrType.WIFI_CONNECT -> wifiQrPayload
        QrType.WEB_PORTAL -> portalQrPayload
        QrType.PAC_URL -> pacQrPayload
    }

    val qrBitmap = remember(currentPayload) {
        generateQrBitmap(currentPayload, 512)
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardWhite,
            border = BorderStroke(1.dp, SurfaceBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect via QR Code",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Selector Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedType == QrType.WIFI_CONNECT,
                        onClick = { selectedType = QrType.WIFI_CONNECT },
                        label = { Text("Wi-Fi Join", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlack,
                            selectedLabelColor = CardWhite,
                            selectedLeadingIconColor = CardWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    FilterChip(
                        selected = selectedType == QrType.WEB_PORTAL,
                        onClick = { selectedType = QrType.WEB_PORTAL },
                        label = { Text("Web Portal", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(14.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlack,
                            selectedLabelColor = CardWhite,
                            selectedLeadingIconColor = CardWhite
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // QR Code Display Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.5.dp, PrimaryBlack),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(230.dp)
                        .padding(4.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "NetFetch QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(
                                color = PrimaryBlack,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Informational Description
                Text(
                    text = when (selectedType) {
                        QrType.WIFI_CONNECT ->
                            "Scan with any Android or iOS camera to join '${config.ssid}' with 1 tap."
                        QrType.WEB_PORTAL ->
                            "Scan to open the NetFetch 1-Click Windows .bat & Proxy setup dashboard."
                        QrType.PAC_URL ->
                            "Scan to import the automatic proxy PAC configuration script."
                    },
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Details Box
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (selectedType) {
                                    QrType.WIFI_CONNECT -> "SSID: ${config.ssid}"
                                    QrType.WEB_PORTAL -> "URL: http://${config.hostIp}:${config.proxyPort}/"
                                    QrType.PAC_URL -> "PAC: http://${config.hostIp}:${config.pacPort}/wpad.dat"
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlack
                            )
                            if (selectedType == QrType.WIFI_CONNECT) {
                                Text(
                                    text = "Password: ${config.passphrase}",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clipText = when (selectedType) {
                                    QrType.WIFI_CONNECT -> config.passphrase
                                    QrType.WEB_PORTAL -> "http://${config.hostIp}:${config.proxyPort}/"
                                    QrType.PAC_URL -> "http://${config.hostIp}:${config.pacPort}/wpad.dat"
                                }
                                clipboard.setPrimaryClip(ClipData.newPlainText("NetFetch", clipText))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy",
                                tint = PrimaryBlack,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlack),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Done", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
