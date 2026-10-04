package com.netfetch.app.model

/**
 * Authoritative downstream-client registry.
 *
 * A client may be observed through different subsystems:
 * - Wi-Fi Direct: MAC + device name, sometimes no IP yet
 * - HTTP proxy: IP, usually no MAC
 * - SOCKS5: IP, usually no MAC
 *
 * MAC and IP are therefore treated as secondary identities that can
 * converge onto the same internal client record.
 */
class NetfetchClientRegistry {

    private val lock = Any()

    private val clients =
        LinkedHashMap<String, ClientDevice>()

    private val macIndex =
        HashMap<String, String>()

    private val ipIndex =
        HashMap<String, String>()

    fun publishP2pClients(
        devices: List<ClientDevice>
    ): List<ClientDevice> =
        publish(devices)

    fun publishTrafficClients(
        devices: List<ClientDevice>
    ): List<ClientDevice> =
        publish(devices)

    fun upsert(
        incoming: ClientDevice
    ): ClientDevice =
        synchronized(lock) {
            val mac = normalizeMac(incoming.macAddress)
            val ip = normalizeIp(incoming.ipAddress)

            val macId =
                mac?.let { macIndex[it] }

            val ipId =
                ip?.let { ipIndex[it] }

            val existingId =
                when {
                    macId != null && ipId != null && macId != ipId -> {
                        mergeRecords(
                            primaryId = macId,
                            secondaryId = ipId,
                            incoming = incoming
                        )
                    }

                    macId != null -> {
                        mergeInto(
                            id = macId,
                            incoming = incoming
                        )
                        macId
                    }

                    ipId != null -> {
                        mergeInto(
                            id = ipId,
                            incoming = incoming
                        )
                        ipId
                    }

                    else -> {
                        val newId = createId()
                        clients[newId] = incoming
                        newId
                    }
                }

            refreshIndexes(
                id = existingId
            )

            clients[existingId] ?: incoming
        }

    fun clear() {
        synchronized(lock) {
            clients.clear()
            macIndex.clear()
            ipIndex.clear()
        }
    }

    fun snapshot(): List<ClientDevice> =
        synchronized(lock) {
            clients.values
                .sortedBy { it.connectedTimestamp }
                .toList()
        }

    fun count(): Int =
        synchronized(lock) {
            clients.size
        }

    private fun publish(
        devices: List<ClientDevice>
    ): List<ClientDevice> {
        devices.forEach { upsert(it) }
        return snapshot()
    }

    /**
     * Merge two records that were previously believed to be different
     * clients but are now proven to share MAC/IP identity.
     */
    private fun mergeRecords(
        primaryId: String,
        secondaryId: String,
        incoming: ClientDevice
    ): String {
        val primary =
            clients[primaryId]
                ?: return secondaryId.also {
                    mergeInto(it, incoming)
                }

        val secondary =
            clients[secondaryId]

        val combined =
            if (secondary != null) {
                merge(
                    existing = primary,
                    incoming = secondary
                )
            } else {
                primary
            }

        clients[primaryId] =
            merge(
                existing = combined,
                incoming = incoming
            )

        if (secondaryId != primaryId) {
            clients.remove(secondaryId)

            macIndex.entries.removeIf {
                it.value == secondaryId
            }

            ipIndex.entries.removeIf {
                it.value == secondaryId
            }
        }

        return primaryId
    }

    private fun mergeInto(
        id: String,
        incoming: ClientDevice
    ) {
        val existing = clients[id]

        clients[id] =
            if (existing == null) {
                incoming
            } else {
                merge(
                    existing = existing,
                    incoming = incoming
                )
            }
    }

    private fun merge(
        existing: ClientDevice,
        incoming: ClientDevice
    ): ClientDevice {
        val incomingIp =
            normalizeIp(incoming.ipAddress)

        val existingIp =
            normalizeIp(existing.ipAddress)

        val resolvedIp =
            when {
                incomingIp != null -> incoming.ipAddress
                existingIp != null -> existing.ipAddress
                else -> "Awaiting IP"
            }

        val incomingMac =
            normalizeMac(incoming.macAddress)

        val existingMac =
            normalizeMac(existing.macAddress)

        val resolvedMac =
            when {
                incomingMac != null -> incoming.macAddress
                existingMac != null -> existing.macAddress
                else -> "Unknown MAC"
            }

        val resolvedName =
            when {
                isUsefulName(incoming.deviceName) ->
                    incoming.deviceName

                isUsefulName(existing.deviceName) ->
                    existing.deviceName

                else ->
                    "Connected Client"
            }

        return existing.copy(
            ipAddress = resolvedIp,
            macAddress = resolvedMac,
            deviceName = resolvedName,
            connectedTimestamp =
                minOf(
                    existing.connectedTimestamp,
                    incoming.connectedTimestamp
                ),
            bytesUploaded =
                maxOf(
                    existing.bytesUploaded,
                    incoming.bytesUploaded
                ),
            bytesDownloaded =
                maxOf(
                    existing.bytesDownloaded,
                    incoming.bytesDownloaded
                ),
            isBlocked =
                existing.isBlocked || incoming.isBlocked
        )
    }

    private fun refreshIndexes(
        id: String
    ) {
        val client = clients[id] ?: return

        normalizeMac(client.macAddress)?.let {
            macIndex[it] = id
        }

        normalizeIp(client.ipAddress)?.let {
            ipIndex[it] = id
        }
    }

    private fun normalizeMac(
        value: String
    ): String? {
        val normalized =
            value
                .trim()
                .lowercase()

        return if (
            normalized.isBlank() ||
            normalized == "unknown mac"
        ) {
            null
        } else {
            normalized
        }
    }

    private fun normalizeIp(
        value: String
    ): String? {
        val normalized = value.trim()

        return if (
            normalized.isBlank() ||
            normalized == "Awaiting IP"
        ) {
            null
        } else {
            normalized
        }
    }

    private fun isUsefulName(
        value: String
    ): Boolean =
        value.isNotBlank() &&
            value != "Connected Client" &&
            value != "Connected Device"

    private fun createId(): String =
        "client-${nextId++}"

    private var nextId = 1L
}
