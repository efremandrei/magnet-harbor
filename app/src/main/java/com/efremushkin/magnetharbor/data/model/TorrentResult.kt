package com.efremushkin.magnetharbor.data.model

enum class TorrentCategory(val label: String) {
    ALL("All"),
    AUDIO("Audio"),
    VIDEO("Video"),
    BOOKS("Books"),
    SOFTWARE("Software"),
    OTHER("Other"),
}

enum class SearchSort(val label: String) {
    SEEDERS("Most seeders"),
    SIZE_DESC("Largest"),
    SIZE_ASC("Smallest"),
    TITLE("Title"),
    SOURCE("Source"),
}

data class TorrentResult(
    val title: String,
    val magnetUri: String,
    val source: String,
    val sizeBytes: Long?,
    val seeders: Int?,
    val leechers: Int?,
    val category: TorrentCategory,
    val publishedAtEpochMillis: Long? = null,
) {
    val infoHash: String?
        get() = INFO_HASH.find(magnetUri)?.groupValues?.getOrNull(1)?.lowercase()

    init {
        require(title.isNotBlank()) { "A result title cannot be blank" }
        require(magnetUri.startsWith("magnet:?")) { "A result must contain a magnet URI" }
    }

    companion object {
        private val INFO_HASH = Regex("(?:[?&])xt=urn:btih:([^&]+)", RegexOption.IGNORE_CASE)
    }
}

fun Long?.asReadableSize(): String {
    val bytes = this ?: return "Unknown size"
    if (bytes < 1_024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = -1
    while (value >= 1_024 && unitIndex < units.lastIndex) {
        value /= 1_024
        unitIndex++
    }
    return if (value >= 10) "%.0f %s".format(value, units[unitIndex])
    else "%.1f %s".format(value, units[unitIndex])
}
