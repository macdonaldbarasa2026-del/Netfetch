package com.netfetch.app.model

data class ClientDevice(
    val ipAddress: String,
    val macAddress: String = "Unknown MAC",
    val deviceName: String = "Connected Client",
    val connectedTimestamp: Long = System.currentTimeMillis(),
    val bytesUploaded: Long = 0L,
    val bytesDownloaded: Long = 0L,
    val isBlocked: Boolean = false
) {
    val totalUsageFormatted: String
        get() {
            val total = bytesUploaded + bytesDownloaded
            return when {
                total < 1024 -> "$total B"
                total < 1024 * 1024 -> String.format("%.1f KB", total / 1024.0)
                total < 1024 * 1024 * 1024 -> String.format("%.2f MB", total / (1024.0 * 1024.0))
                else -> String.format("%.2f GB", total / (1024.0 * 1024.0 * 1024.0))
            }
        }
}
