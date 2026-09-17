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
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.efremushkin.magnetharbor.data.local.SearchHistoryEntity
import com.efremushkin.magnetharbor.data.model.SearchSort
import com.efremushkin.magnetharbor.data.model.TorrentCategory
import com.efremushkin.magnetharbor.data.model.TorrentResult
import com.efremushkin.magnetharbor.data.model.asReadableSize
import com.efremushkin.magnetharbor.data.settings.AppSettings
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class AppScreen(val label: String, val symbol: String) {
    SEARCH("Search", "⌕"),
    FAVORITES("Favorites", "★"),
    HISTORY("History", "↶"),
    SETTINGS("Settings", "⚙"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagnetHarborApp(
    viewModel: MagnetHarborViewModel,
    onOpenMagnet: (String) -> Unit,
    onCopyMagnet: (String) -> Unit,
    onShareMagnet: (String) -> Unit,
) {
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val favoriteMagnets = remember(favorites) { favorites.mapTo(mutableSetOf()) { it.magnetUri } }
    var currentScreen by remember { mutableStateOf(AppScreen.SEARCH) }

    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.search(spoken)
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(currentScreen.label) }) },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { Text(screen.symbol) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.SEARCH -> SearchScreen(
                modifier = Modifier.padding(innerPadding),
                state = searchState,
                favoriteMagnets = favoriteMagnets,
                onQueryChanged = viewModel::updateQuery,
                onSearch = { viewModel.search() },
                onVoiceSearch = {
                    voiceLauncher.launch(
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "What do you want to search for?")
                        },
                    )
                },
                onSortChanged = viewModel::setSort,
                onCategoryChanged = viewModel::setCategory,
                onFavorite = viewModel::toggleFavorite,
                onOpen = onOpenMagnet,
                onCopy = onCopyMagnet,
                onShare = onShareMagnet,
            )

            AppScreen.FAVORITES -> ResultsList(
                modifier = Modifier.padding(innerPadding),
                results = favorites,
                favoriteMagnets = favoriteMagnets,
                emptyMessage = "No favorites yet.",
                onFavorite = viewModel::toggleFavorite,
                onOpen = onOpenMagnet,
                onCopy = onCopyMagnet,
                onShare = onShareMagnet,
            )

            AppScreen.HISTORY -> HistoryScreen(
                modifier = Modifier.padding(innerPadding),
                history = history,
                onRepeat = {
                    currentScreen = AppScreen.SEARCH
                    viewModel.search(it)
                },
                onClear = viewModel::clearHistory,
            )

            AppScreen.SETTINGS -> SettingsScreen(
                modifier = Modifier.padding(innerPadding),
                settings = settings,
                onHideZeroChanged = viewModel::setHideZeroSeeders,
                onDarkThemeChanged = viewModel::setDarkTheme,
            )
        }
    }
}

@Composable
private fun SearchScreen(
    modifier: Modifier,
    state: SearchUiState,
    favoriteMagnets: Set<String>,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onVoiceSearch: () -> Unit,
    onSortChanged: (SearchSort) -> Unit,
    onCategoryChanged: (TorrentCategory) -> Unit,
    onFavorite: (TorrentResult) -> Unit,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.query,
                onValueChange = onQueryChanged,
                label = { Text("Search public torrents") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onVoiceSearch) { Text("Mic") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onSearch, enabled = !state.isLoading) { Text("Go") }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(TorrentCategory.entries) { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { onCategoryChanged(category) },
                    label = { Text(category.label) },
                )
            }
        }

        SortSelector(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            selected = state.sort,
            onSelected = onSortChanged,
        )

        if (state.isLoading) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (state.failures.isNotEmpty()) {
            Text(
                text = state.failures.joinToString("\n") { "${it.sourceName}: ${it.message}" },
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        ResultsList(
            modifier = Modifier.weight(1f),
            results = state.results,
            favoriteMagnets = favoriteMagnets,
            emptyMessage = if (state.query.isBlank()) "Search across your enabled sources." else "No matching results.",
            onFavorite = onFavorite,
            onOpen = onOpen,
            onCopy = onCopy,
            onShare = onShare,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSelector(
    modifier: Modifier,
    selected: SearchSort,
    onSelected: (SearchSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sort results") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SearchSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        onSelected(sort)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultsList(
    modifier: Modifier,
    results: List<TorrentResult>,
    favoriteMagnets: Set<String>,
    emptyMessage: String,
    onFavorite: (TorrentResult) -> Unit,
    onOpen: (String) -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    if (results.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(emptyMessage, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(results, key = { it.magnetUri }) { result ->
            ResultCard(
                result = result,
                isFavorite = result.magnetUri in favoriteMagnets,
                onFavorite = { onFavorite(result) },
                onOpen = { onOpen(result.magnetUri) },
                onCopy = { onCopy(result.magnetUri) },
                onShare = { onShare(result.magnetUri) },
            )
        }
    }
}

@Composable
private fun ResultCard(
    result: TorrentResult,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "${result.source} · ${result.category.label} · ${result.sizeBytes.asReadableSize()}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Seeders ${result.seeders ?: "?"} · Leechers ${result.leechers ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onShare) { Text("Share") }
                TextButton(onClick = onFavorite) { Text(if (isFavorite) "Unfavorite" else "Favorite") }
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    modifier: Modifier,
    history: List<SearchHistoryEntity>,
    onRepeat: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) { Text("Clear history") }
        }
        if (history.isEmpty()) {
            Text("No searches yet.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(history, key = { it.id }) { item ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRepeat(item.query) },
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(item.query, style = MaterialTheme.typography.titleMedium)
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(item.searchedAtEpochMillis)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier,
    settings: AppSettings,
    onHideZeroChanged: (Boolean) -> Unit,
    onDarkThemeChanged: (Boolean) -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingSwitch(
            title = "Hide results with zero seeders",
            checked = settings.hideZeroSeeders,
            onCheckedChange = onHideZeroChanged,
        )
        SettingSwitch(
            title = "Dark theme",
            checked = settings.darkTheme,
            onCheckedChange = onDarkThemeChanged,
        )
        HorizontalDivider()
        Text("Enabled sources", style = MaterialTheme.typography.titleMedium)
        Text("Public Archives Demo\nLinux Community Demo")
        Text(
            "This development build uses deterministic demonstration data. Add documented API or Torznab adapters before production use.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
