package com.efremushkin.magnetharbor.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.efremushkin.magnetharbor.data.local.SearchHistoryEntity
import com.efremushkin.magnetharbor.data.model.SearchSort
import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.repository.TorrentRepository
import com.efremushkin.magnetharbor.data.settings.AppSettings
import com.efremushkin.magnetharbor.data.settings.SettingsRepository
import com.efremushkin.magnetharbor.data.source.SourceFailure
import com.efremushkin.magnetharbor.data.source.SearchSourceConfig
import com.efremushkin.magnetharbor.data.source.SourceHealth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<TorrentResult> = emptyList(),
    val isLoading: Boolean = false,
    val sort: SearchSort = SearchSort.SEEDERS,
    val category: TorrentCategory = TorrentCategory.ALL,
    val failures: List<SourceFailure> = emptyList(),
    val minSeeders: Int? = null,
    val maxSizeGiB: Int? = null,
    val maxAgeDays: Int? = null,
    val message: String? = null,
)

class MagnetHarborViewModel(
    private val repository: TorrentRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    val favorites = repository.favorites.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val history: StateFlow<List<SearchHistoryEntity>> = repository.history.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )
    val sources: StateFlow<List<SearchSourceConfig>> = settingsRepository.sources.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    private val _sourceHealth = MutableStateFlow<Map<String, SourceHealth>>(emptyMap())
    val sourceHealth: StateFlow<Map<String, SourceHealth>> = _sourceHealth.asStateFlow()

    private var rawResults: List<TorrentResult> = emptyList()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { refreshVisibleResults(it) }
        }
    }

    fun updateQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query, message = null)
    }

    fun search(queryOverride: String? = null) {
        val query = queryOverride?.also(::updateQuery)?.trim() ?: _searchState.value.query.trim()
        if (query.isBlank()) {
            _searchState.value = _searchState.value.copy(message = "Enter something to search for.")
            return
        }

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isLoading = true, failures = emptyList(), message = null)
            runCatching { repository.search(query, sources.value) }
                .onSuccess { batch ->
                    rawResults = batch.results
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        failures = batch.failures,
                    )
                    refreshVisibleResults(settings.value)
                }
                .onFailure { error ->
                    _searchState.value = _searchState.value.copy(
                        isLoading = false,
                        message = error.message ?: "Search failed.",
                    )
                }
        }
    }

    fun setSort(sort: SearchSort) {
        _searchState.value = _searchState.value.copy(sort = sort)
        refreshVisibleResults(settings.value)
    }

    fun setCategory(category: TorrentCategory) {
        _searchState.value = _searchState.value.copy(category = category)
        refreshVisibleResults(settings.value)
    }

    fun setFilters(minSeeders: String, maxSizeGiB: String, maxAgeDays: String) {
        _searchState.value = _searchState.value.copy(
            minSeeders = minSeeders.toIntOrNull()?.coerceAtLeast(0),
            maxSizeGiB = maxSizeGiB.toIntOrNull()?.coerceAtLeast(1),
            maxAgeDays = maxAgeDays.toIntOrNull()?.coerceAtLeast(1),
        )
        refreshVisibleResults(settings.value)
    }

    fun saveSource(config: SearchSourceConfig) {
        viewModelScope.launch { settingsRepository.saveSource(config) }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSourceEnabled(id, enabled) }
    }

    fun deleteSource(id: String) {
        viewModelScope.launch { settingsRepository.deleteSource(id) }
    }

    fun testSource(config: SearchSourceConfig) {
        viewModelScope.launch {
            _sourceHealth.value = _sourceHealth.value + (config.id to SourceHealth(config.id, config.name, false, null, "Testing…"))
            _sourceHealth.value = _sourceHealth.value + (config.id to repository.testSource(config))
        }
    }

    fun toggleFavorite(result: TorrentResult) {
        viewModelScope.launch { repository.toggleFavorite(result) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun setHideZeroSeeders(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setHideZeroSeeders(enabled) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkTheme(enabled) }
    }

    private fun refreshVisibleResults(currentSettings: AppSettings) {
        val current = _searchState.value
        val filtered = rawResults
            .asSequence()
            .filter { current.category == TorrentCategory.ALL || it.category == current.category }
            .filter { !currentSettings.hideZeroSeeders || (it.seeders ?: 0) > 0 }
            .filter { current.minSeeders == null || (it.seeders ?: 0) >= current.minSeeders }
            .filter { current.maxSizeGiB == null || (it.sizeBytes ?: Long.MAX_VALUE) <= current.maxSizeGiB * 1024L * 1024L * 1024L }
            .filter {
                current.maxAgeDays == null || it.publishedAtEpochMillis == null ||
                    it.publishedAtEpochMillis >= System.currentTimeMillis() - current.maxAgeDays * 86_400_000L
            }
            .toList()

        val sorted = when (current.sort) {
            SearchSort.SEEDERS -> filtered.sortedByDescending { it.seeders ?: -1 }
            SearchSort.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes ?: -1 }
            SearchSort.SIZE_ASC -> filtered.sortedBy { it.sizeBytes ?: Long.MAX_VALUE }
            SearchSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            SearchSort.SOURCE -> filtered.sortedBy { it.source.lowercase() }
        }
        _searchState.value = current.copy(results = sorted)
    }

    class Factory(
        private val repository: TorrentRepository,
        private val settingsRepository: SettingsRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MagnetHarborViewModel::class.java))
            return MagnetHarborViewModel(repository, settingsRepository) as T
        }
    }
}
