package com.efremushkin.magnetharbor

import android.app.Application
import com.efremushkin.magnetharbor.data.local.AppDatabase
import com.efremushkin.magnetharbor.data.repository.TorrentRepository
import com.efremushkin.magnetharbor.data.settings.SettingsRepository
import com.efremushkin.magnetharbor.data.source.DemoTorrentSource
import com.efremushkin.magnetharbor.data.source.SearchCoordinator

class MagnetHarborApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database = AppDatabase.create(application)
    val settingsRepository = SettingsRepository(application)
    val sourceIds = setOf("public-archives", "linux-community")

    private val searchCoordinator = SearchCoordinator(
        sources = listOf(
            DemoTorrentSource(
                id = "public-archives",
                displayName = "Public Archives Demo",
                latencyMillis = 350,
            ),
            DemoTorrentSource(
                id = "linux-community",
                displayName = "Linux Community Demo",
                latencyMillis = 650,
            ),
        ),
    )

    val torrentRepository = TorrentRepository(
        searchCoordinator = searchCoordinator,
        favoriteDao = database.favoriteDao(),
        historyDao = database.searchHistoryDao(),
    )
}
