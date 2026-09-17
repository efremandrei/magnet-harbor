package com.efremushkin.magnetharbor.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efremushkin.magnetharbor.data.local.SearchHistoryEntity
import com.efremushkin.magnetharbor.data.model.SearchSort
import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.model.asReadableSize
import com.efremushkin.magnetharbor.data.settings.AppSettings
import com.efremushkin.magnetharbor.data.source.SearchSourceConfig
import com.efremushkin.magnetharbor.data.source.SourceHealth
import com.efremushkin.magnetharbor.data.source.SourceKind
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen(val label: String, val symbol: String) {
    SEARCH("Search", "⌕"), SOURCES("Sources", "◉"), FAVORITES("Favorites", "★"),
    HISTORY("History", "↶"), SETTINGS("Settings", "⚙"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetHarborApp(viewModel: MagnetHarborViewModel, onOpenMagnet: (String) -> Unit, onCopyMagnet: (String) -> Unit, onShareMagnet: (String) -> Unit) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()
    val favoriteMagnets = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.magnetUri } }
    var screen by remember { mutableStateOf(AppScreen.SEARCH) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(viewModel::search)
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(screen.label) }) },
        bottomBar = { NavigationBar { AppScreen.entries.forEach { item -> NavigationBarItem(screen == item, { screen = item }, { Text(item.symbol) }, label = { Text(item.label) }) } } },
    ) { padding ->
        when (screen) {
            AppScreen.SEARCH -> SearchScreen(Modifier.padding(padding), searchState, favoriteMagnets, sources.count { it.enabled }, viewModel::updateQuery, viewModel::search, {
                voiceLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()) })
            }, viewModel::setSort, viewModel::setCategory, viewModel::setFilters, viewModel::toggleFavorite, onOpenMagnet, onCopyMagnet, onShareMagnet)
            AppScreen.SOURCES -> SourcesScreen(Modifier.padding(padding), sources, sourceHealth, viewModel::saveSource, viewModel::setSourceEnabled, viewModel::testSource, viewModel::deleteSource)
            AppScreen.FAVORITES -> ResultsList(Modifier.padding(padding), favorites, favoriteMagnets, "No favorites yet.", viewModel::toggleFavorite, onOpenMagnet, onCopyMagnet, onShareMagnet)
            AppScreen.HISTORY -> HistoryScreen(Modifier.padding(padding), history, { screen = AppScreen.SEARCH; viewModel.search(it) }, viewModel::clearHistory)
            AppScreen.SETTINGS -> SettingsScreen(Modifier.padding(padding), settings, viewModel::setHideZeroSeeders, viewModel::setDarkTheme)
        }
    }
}

@Composable
private fun SearchScreen(modifier: Modifier, state: SearchUiState, favorites: Set<String>, enabledSources: Int, onQueryChanged: (String) -> Unit, onSearch: () -> Unit, onVoiceSearch: () -> Unit, onSortChanged: (SearchSort) -> Unit, onCategoryChanged: (TorrentCategory) -> Unit, onFiltersChanged: (String, String, String) -> Unit, onFavorite: (TorrentResult) -> Unit, onOpen: (String) -> Unit, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    var showFilters by remember { mutableStateOf(false) }
    var minSeeders by remember(state.minSeeders) { mutableStateOf(state.minSeeders?.toString().orEmpty()) }
    var maxSize by remember(state.maxSizeGiB) { mutableStateOf(state.maxSizeGiB?.toString().orEmpty()) }
    var maxAge by remember(state.maxAgeDays) { mutableStateOf(state.maxAgeDays?.toString().orEmpty()) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.query, onValueChange = onQueryChanged, modifier = Modifier.weight(1f), label = { Text("Search enabled sources") }, singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }))
            Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onVoiceSearch) { Text("Mic") }; Spacer(Modifier.width(8.dp)); Button(onClick = onSearch, enabled = !state.isLoading && enabledSources > 0) { Text("Go") }
        }
        Text("$enabledSources live source${if (enabledSources == 1) "" else "s"} enabled", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(TorrentCategory.entries) { category -> FilterChip(state.category == category, { onCategoryChanged(category) }, { Text(category.label) }) } }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { showFilters = !showFilters }) { Text(if (showFilters) "Hide filters" else "Filters") }; SortSelector(Modifier.weight(1f), state.sort, onSortChanged) }
        if (showFilters) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterField(Modifier.weight(1f), "Min seeds", minSeeders) { minSeeders = it; onFiltersChanged(minSeeders, maxSize, maxAge) }
            FilterField(Modifier.weight(1f), "Max GiB", maxSize) { maxSize = it; onFiltersChanged(minSeeders, maxSize, maxAge) }
            FilterField(Modifier.weight(1f), "Max days", maxAge) { maxAge = it; onFiltersChanged(minSeeders, maxSize, maxAge) }
        }
        if (state.isLoading) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        state.failures.forEach { Text("${it.sourceName}: ${it.message}", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
        ResultsList(Modifier.weight(1f), state.results, favorites, if (enabledSources == 0) "Add a source in the Sources tab to start live searches." else if (state.query.isBlank()) "Search across your enabled sources." else "No matching results.", onFavorite, onOpen, onCopy, onShare)
    }
}

@Composable
private fun FilterField(modifier: Modifier, label: String, value: String, onValue: (String) -> Unit) = OutlinedTextField(value = value, onValueChange = onValue, modifier = modifier, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSelector(modifier: Modifier, selected: SearchSort, onSelected: (SearchSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) { OutlinedTextField(value = selected.label, onValueChange = {}, modifier = Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Sort") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }); ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { SearchSort.entries.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { onSelected(option); expanded = false }) } } }
}

@Composable
private fun SourcesScreen(modifier: Modifier, sources: List<SearchSourceConfig>, health: Map<String, SourceHealth>, onSave: (SearchSourceConfig) -> Unit, onEnabled: (String, Boolean) -> Unit, onTest: (SearchSourceConfig) -> Unit, onDelete: (String) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Live sources", style = MaterialTheme.typography.titleLarge); Text("Add your own Torznab/Jackett endpoint or Prowlarr server. Keys stay on this device and are only sent to that source.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)); Button(onClick = { adding = true }) { Text("Add source") }
        if (adding) SourceEditor({ onSave(it); adding = false }, { adding = false })
        if (sources.isEmpty()) Text("No sources configured.", Modifier.padding(vertical = 24.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(sources, key = { it.id }) { source -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(source.name, style = MaterialTheme.typography.titleMedium); Text(source.kind.label, style = MaterialTheme.typography.bodySmall) }; Switch(source.enabled, { onEnabled(source.id, it) }) }
            Text(source.endpoint, style = MaterialTheme.typography.bodySmall)
            health[source.id]?.let { item -> Text("${if (item.reachable) "Connected" else "Unavailable"}${item.latencyMillis?.let { " · ${it} ms" }.orEmpty()} · ${item.message}", color = if (item.reachable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TextButton({ onTest(source) }) { Text("Test") }; TextButton({ onDelete(source.id) }) { Text("Remove") } }
        } } } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceEditor(onSave: (SearchSourceConfig) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }; var endpoint by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }; var kind by remember { mutableStateOf(SourceKind.TORZNAB) }; var kindOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(vertical = 12.dp)) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("New source", style = MaterialTheme.typography.titleMedium); OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Display name") }, singleLine = true)
        ExposedDropdownMenuBox(expanded = kindOpen, onExpandedChange = { kindOpen = it }) { OutlinedTextField(value = kind.label, onValueChange = {}, modifier = Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Source type") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(kindOpen) }); ExposedDropdownMenu(expanded = kindOpen, onDismissRequest = { kindOpen = false }) { SourceKind.entries.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.label) }, onClick = { kind = candidate; kindOpen = false }) } } }
        OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (kind == SourceKind.PROWLARR) "Server URL, e.g. https://host:9696" else "Torznab endpoint or server URL") }, singleLine = true)
        OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key") }, singleLine = true)
        Text(if (kind == SourceKind.PROWLARR) "Uses /api/v1/search with X-Api-Key." else "A server URL gets /api appended; a full endpoint is used as-is.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(enabled = name.isNotBlank() && endpoint.isNotBlank(), onClick = { onSave(SearchSourceConfig(name = name.trim(), kind = kind, endpoint = endpoint.trim(), apiKey = key.trim())) }) { Text("Save") }; OutlinedButton(onClick = onCancel) { Text("Cancel") } }
    } }
}

@Composable
private fun ResultsList(modifier: Modifier, results: List<TorrentResult>, favorites: Set<String>, emptyMessage: String, onFavorite: (TorrentResult) -> Unit, onOpen: (String) -> Unit, onCopy: (String) -> Unit, onShare: (String) -> Unit) {
    if (results.isEmpty()) { Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(emptyMessage, style = MaterialTheme.typography.bodyLarge) }; return }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { items(results, key = { it.magnetUri }) { result -> ResultCard(result, result.magnetUri in favorites, { onFavorite(result) }, { onOpen(result.magnetUri) }, { onCopy(result.magnetUri) }, { onShare(result.magnetUri) }) } }
}

@Composable
private fun ResultCard(result: TorrentResult, favorite: Boolean, onFavorite: () -> Unit, onOpen: () -> Unit, onCopy: () -> Unit, onShare: () -> Unit) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
    Text(result.title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text("${result.category.label} · ${result.sizeBytes.asReadableSize()}", style = MaterialTheme.typography.bodySmall); Text("Seeders ${result.seeders ?: "?"} · Leechers ${result.leechers ?: "?"}", style = MaterialTheme.typography.bodySmall); Text("From ${result.sourceNames.joinToString()}", style = MaterialTheme.typography.bodySmall); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(onClick = onOpen) { Text("Open") }; TextButton(onClick = onCopy) { Text("Copy") }; TextButton(onClick = onShare) { Text("Share") }; TextButton(onClick = onFavorite) { Text(if (favorite) "Unfavorite" else "Favorite") } }
} }

@Composable
private fun HistoryScreen(modifier: Modifier, history: List<SearchHistoryEntity>, onRepeat: (String) -> Unit, onClear: () -> Unit) { Column(modifier.fillMaxSize()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("Clear history") } }; if (history.isEmpty()) Text("No searches yet.", Modifier.padding(24.dp)) else LazyColumn(Modifier.fillMaxSize()) { items(history, key = { it.id }) { item -> TextButton(onClick = { onRepeat(item.query) }, modifier = Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(item.query, style = MaterialTheme.typography.titleMedium); Text(DateFormat.getDateTimeInstance().format(Date(item.searchedAtEpochMillis)), style = MaterialTheme.typography.bodySmall) } }; HorizontalDivider() } } }

@Composable
private fun SettingsScreen(modifier: Modifier, settings: AppSettings, onHideZeroChanged: (Boolean) -> Unit, onDarkThemeChanged: (Boolean) -> Unit) { Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { SettingSwitch("Hide results with zero seeders", settings.hideZeroSeeders, onHideZeroChanged); SettingSwitch("Dark theme", settings.darkTheme, onDarkThemeChanged); HorizontalDivider(); Text("Privacy", style = MaterialTheme.typography.titleMedium); Text("Magnet Harbor only connects to sources you configure. It does not download or stream torrent content.", style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, onCheckedChange) } }
