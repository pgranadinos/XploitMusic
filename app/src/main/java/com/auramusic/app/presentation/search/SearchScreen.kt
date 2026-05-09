package com.auramusic.app.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Search Header
        Text(
            text = "Buscar",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground
        )

        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("¿Qué quieres escuchar?") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { keyboardController?.hide() }
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (searchQuery.isEmpty()) {
                item {
                    Text(
                        text = "Explorar todo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                // (Opcional: Agregar cuadrícula de géneros aquí)
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
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                        }
                    }
                    is SearchState.Success -> {
                        renderSearchResults(state.songs, state.channels, state.playlists, onSongClick)
                    }
                    is SearchState.PartialSuccess -> {
                        renderSearchResults(state.songs, state.channels, state.playlists, onSongClick)
                    }
                    is SearchState.Error -> {
                        item {
                            Text(
                                text = "Ocurrió un error: ${state.message}",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    is SearchState.Empty -> {
                        item {
                            Text(
                                text = "Sin resultados para \"$searchQuery\"",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
        val icon = when(suggestion) {
            is SearchSuggestion.AutocompleteItem -> "🔍"
            is SearchSuggestion.HistoryItem -> "🕰️"
        }
        Text(icon, modifier = Modifier.padding(end = 16.dp))
        Text(
            text = when(suggestion) {
                is SearchSuggestion.AutocompleteItem -> suggestion.text
                is SearchSuggestion.HistoryItem -> suggestion.entry.displayQuery
            },
            style = MaterialTheme.typography.bodyLarge
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
            Text(
                "Canciones",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
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
            Text(
                "Artistas",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )
        }
        items(channels) { channel ->
            ChannelResultItem(channel)
        }
    }
}

@Composable
private fun ChannelResultItem(channel: SearchResultItem.ChannelResult) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.thumbnail,
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(25.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = channel.name,
            modifier = Modifier.padding(start = 16.dp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
