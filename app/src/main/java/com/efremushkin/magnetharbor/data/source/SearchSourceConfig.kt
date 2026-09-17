package com.efremushkin.magnetharbor.data.source

import java.util.UUID

/** A user-owned live search service. Credentials remain on this device. */
enum class SourceKind(val label: String) {
    TORZNAB("Torznab / Jackett"),
    PROWLARR("Prowlarr API"),
    API_BAY("The Pirate Bay public API"),
}

data class SearchSourceConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: SourceKind,
    val endpoint: String,
    val apiKey: String = "",
    val enabled: Boolean = true,
)

data class SourceHealth(
    val sourceId: String,
    val sourceName: String,
    val reachable: Boolean,
    val latencyMillis: Long? = null,
    val message: String,
)
