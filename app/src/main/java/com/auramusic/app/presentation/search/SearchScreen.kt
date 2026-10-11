package com.auramusic.app.presentation.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.Song
import com.auramusic.app.presentation.home.*
import com.auramusic.app.presentation.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onSongClick: (Song) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchState by viewModel.searchUiState.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val submitSearch = {
        val normalizedQuery = searchQuery.trim()
        if (normalizedQuery.isNotEmpty()) {
            viewModel.onSuggestionSelected(normalizedQuery)
        }
        keyboardController?.hide()
    }
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.background
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        Text(
            text = "Buscar",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Canciones, artistas y playlists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp)
        )

        TextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("¿Qué quieres escuchar?") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MaterialTheme.colorScheme.primary
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { submitSearch() }
            )
        )

        val recentStrings by viewModel.recentSearchStrings.collectAsState()
        val recentQueries = recentStrings
            .distinctBy { it.trim().lowercase() }
            .take(10)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (searchQuery.isEmpty()) {
                // ── Recent Searches Chips ──
                if (recentQueries.isNotEmpty()) {
                    item {
                        SearchSectionHeader(
                            title = "Búsquedas recientes",
                            subtitle = "Continúa donde lo dejaste",
                            actionLabel = "Borrar",
                            onAction = {
                                viewModel.processAction(SearchAction.ClearHistory)
                            }
                        )
                    }
                    item {
                        RecentSearchChips(
                            queries = recentQueries,
                            onChipClick = { query ->
                                viewModel.onRecentSearchClick(query)
                                keyboardController?.hide()
                            }
                        )
                    }
                }

                item {
                    SearchSectionHeader(
                        title = "Explorar",
                        subtitle = "Empieza por un género"
                    )
                }
                item {
                    ExploreGenres(
                        onGenreClick = { genre ->
                            viewModel.onSuggestionSelected(genre)
                            keyboardController?.hide()
                        }
                    )
                }
            } else if (suggestions.isNotEmpty()) {
                items(suggestions) { suggestion ->
                    SearchSuggestionItem(suggestion) {
                        viewModel.onSuggestionSelected(it)
                        keyboardController?.hide()
                    }
                }
            } else {
                when (val state = searchState) {
                    is SearchState.Loading -> {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                            )
                        }
                    }
                    is SearchState.Success -> {
                        renderSearchResults(state.songs, state.channels, state.playlists, onSongClick)
                    }
                    is SearchState.PartialSuccess -> {
                        item {
                            SearchMessageCard(
                                message = "Mostramos los resultados disponibles. Algunas categorías no pudieron cargarse.",
                                icon = Icons.Default.WarningAmber
                            )
                        }
                        renderSearchResults(state.songs, state.channels, state.playlists, onSongClick)
                    }
                    is SearchState.Error -> {
                        item {
                            SearchMessageCard(
                                message = state.message,
                                icon = Icons.Default.ErrorOutline,
                                isError = true,
                                actionLabel = "Reintentar",
                                onAction = { viewModel.processAction(SearchAction.Retry) }
                            )
                        }
                    }
                    is SearchState.Empty -> {
                        item {
                            SearchMessageCard(
                                message = "Sin resultados para \"$searchQuery\"",
                                icon = Icons.Default.SearchOff
                            )
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}
@Composable
private fun SearchSuggestionItem(
    suggestion: SearchSuggestion,
    onClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { 
                when(suggestion) {
                    is SearchSuggestion.AutocompleteItem -> onClick(suggestion.text)
                    is SearchSuggestion.HistoryItem -> onClick(suggestion.entry.displayQuery)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (suggestion) {
                is SearchSuggestion.AutocompleteItem -> Icons.Default.Search
                is SearchSuggestion.HistoryItem -> Icons.Default.History
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 16.dp)
                .size(20.dp)
        )
        Text(
            text = when(suggestion) {
                is SearchSuggestion.AutocompleteItem -> suggestion.text
                is SearchSuggestion.HistoryItem -> suggestion.entry.displayQuery
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun LazyListScope.renderSearchResults(
    songs: List<Song>,
    channels: List<SearchResultItem.ChannelResult>,
    playlists: List<SearchResultItem.PlaylistResult>,
    onSongClick: (Song) -> Unit
) {
    if (songs.isNotEmpty()) {
        item {
            SearchSectionHeader(
                title = "Canciones",
                subtitle = "${songs.size} resultados"
            )
        }
        items(songs) { song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
    
    if (channels.isNotEmpty()) {
        item {
            SearchSectionHeader(
                title = "Artistas",
                subtitle = "Perfiles relacionados"
            )
        }
        items(channels) { channel ->
            ChannelResultItem(channel)
        }
    }

    if (playlists.isNotEmpty()) {
        item {
            SearchSectionHeader(
                title = "Playlists",
                subtitle = "Colecciones relacionadas"
            )
        }
        items(playlists) { playlist ->
            PlaylistResultItem(playlist)
        }
    }
}

@Composable
private fun ChannelResultItem(channel: SearchResultItem.ChannelResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = channel.thumbnail,
                contentDescription = channel.name,
                modifier = Modifier.size(50.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = channel.name,
                modifier = Modifier.padding(start = 14.dp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlaylistResultItem(playlist: SearchResultItem.PlaylistResult) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.thumbnail,
                contentDescription = playlist.name,
                modifier = Modifier.size(54.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.videoCount} canciones",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ExploreGenres(onGenreClick: (String) -> Unit) {
    val genres = listOf("Pop latino", "Rock", "Reggaetón", "Salsa", "Indie", "Electrónica")
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        genres.chunked(2).forEachIndexed { rowIndex, rowGenres ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowGenres.forEachIndexed { columnIndex, genre ->
                    val accentAlpha = 0.16f + ((rowIndex + columnIndex) % 3) * 0.05f
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(72.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { onGenreClick(genre) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = accentAlpha),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                        )
                    ) {
                        Box(
                            modifier = Modifier.padding(14.dp),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = genre,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessageCard(
    message: String,
    icon: ImageVector = Icons.Default.Info,
    isError: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val accentColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(22.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

/**
 * Horizontal scrollable row of recent search chips.
 * Uses SuggestionChip for a modern, dark-theme-friendly look.
 */
@Composable
private fun RecentSearchChips(
    queries: List<String>,
    onChipClick: (String) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(queries) { query ->
            SuggestionChip(
                onClick = { onChipClick(query) },
                label = {
                    Text(
                        text = query,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                icon = {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                shape = MaterialTheme.shapes.large,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = SuggestionChipDefaults.suggestionChipBorder(
                    enabled = true,
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
        }
    }
}
