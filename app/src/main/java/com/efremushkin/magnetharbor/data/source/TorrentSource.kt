package com.efremushkin.magnetharbor.data.source

import com.efremushkin.magnetharbor.data.model.TorrentResult

interface TorrentSource {
    val id: String
    val displayName: String

    suspend fun search(query: String, page: Int = 1): List<TorrentResult>
}
