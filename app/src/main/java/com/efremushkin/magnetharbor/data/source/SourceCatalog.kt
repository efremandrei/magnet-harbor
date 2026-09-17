package com.efremushkin.magnetharbor.data.source

/**
 * Source names shown in the reference source picker.
 *
 * These are catalog entries only. Connectivity is configured by the user through
 * a Torznab/Jackett endpoint or a Prowlarr server, so the app never embeds
 * changing third-party URLs or credentials.
 */
data class SourceCatalogEntry(
    val name: String,
    val status: String = "Configure via Jackett/Prowlarr",
)

object SourceCatalog {
    val entries: List<SourceCatalogEntry> = listOf(
        "0magnet", "1337x", "AcgRip", "AniLibria", "Anime-Time", "AnimeTosho",
        "AniRena", "Arab-Torrents", "AudioBookBay", "Bangumi", "BitRu", "BitSearch",
        "BlueRoms", "BT4G", "BTDigg", "BTDirectory", "BTSOW", "CloudTorrents",
        "DonTorrent", "EpubLibre", "EZTV", "ExtraTorrent", "FitGirlRepacK",
        "GamesTorrents", "Internet Archive", "ISOHUNT", "Il Corsaro Nero", "KAT",
        "Libgen", "LimeTorrents", "LinuxTracker", "MagnetDL", "MikanAni", "MegaPeer",
        "MoviesDVDR", "NoNameClub", "Nyaa", "OxTorrent", "PC-Torrents", "Pirateiro",
        "TorrentKitty", "TorrentCSV", "Torrentz2", "Torrent9", "TorrentDownload",
        "TorrentGalaxy", "TPB", "Uindex", "Xfsub", "Yihua", "YTS", "Ext.to",
        "GloTorrents", "TorrentMac",
    ).map(::SourceCatalogEntry)
}
