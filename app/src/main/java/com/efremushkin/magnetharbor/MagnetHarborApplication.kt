package com.efremushkin.magnetharbor

import android.app.Application
import com.efremushkin.magnetharbor.data.local.AppDatabase
import com.efremushkin.magnetharbor.data.repository.TorrentRepository
import com.efremushkin.magnetharbor.data.settings.SettingsRepository

class MagnetHarborApplication : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(application: Application) {
    private val database = AppDatabase.create(application)
    val settingsRepository = SettingsRepository(application)
    val torrentRepository = TorrentRepository(
        favoriteDao = database.favoriteDao(),
        historyDao = database.searchHistoryDao(),
    )
}
