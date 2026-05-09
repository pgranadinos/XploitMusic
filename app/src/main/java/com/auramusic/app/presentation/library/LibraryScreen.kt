package com.auramusic.app.presentation.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auramusic.app.data.local.entity.PlaylistWithSongs
import com.auramusic.app.domain.model.Song
import com.auramusic.app.presentation.components.SongListItem
import org.burnoutcrew.reorderable.*

private enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    FAVORITES("Favoritos"),
    RECENTS("Recientes"),
    DOWNLOADS("Descargas"),
    LOCAL("Local"),
    TOP("Top 🔥")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onNavigateToSpotifyImport: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val allPlaylistsWithSongs by viewModel.allPlaylistsWithSongs.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.background
        )
    )

    if (showDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylist(name) }
        )
    }

    Scaffold(
        topBar = {
            if (selectedPlaylist == null) {
                TopAppBar(
                    title = { 
                        Text(
                            "Biblioteca", 
                            style = MaterialTheme.typography.headlineSmall, 
                            fontWeight = FontWeight.ExtraBold 
                        ) 
                    },
                    actions = {
                        if (selectedTab == LibraryTab.PLAYLISTS) {
                            IconButton(onClick = onNavigateToSpotifyImport) {
                                Icon(Icons.Default.CloudDownload, "Importar de Spotify", tint = Color(0xFF1DB954))
                            }
                            IconButton(onClick = { viewModel.showCreatePlaylistDialog() }) {
                                Icon(Icons.Default.Add, "Create Playlist")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(gradientBrush)
        ) {
            if (selectedPlaylist != null) {
                PlaylistDetailView(
                    pws = selectedPlaylist!!,
                    onBack = { viewModel.clearSelection() },
                    onSongClick = { song, list -> onSongClick(song, list) },
                    onReorder = { from, to -> 
                        viewModel.reorderPlaylist(selectedPlaylist!!.playlist.playlistId, from, to)
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, top = 8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LibraryTab.values()) { tab ->
                            FilterChip(
                                selected = selectedTab == tab,
                                onClick = { selectedTab = tab },
                                label = { Text(tab.label) },
                                leadingIcon = {
                                    val icon = when (tab) {
                                        LibraryTab.PLAYLISTS -> Icons.Default.QueueMusic
                                        LibraryTab.FAVORITES -> Icons.Default.Favorite
                                        LibraryTab.RECENTS   -> Icons.Default.History
                                        LibraryTab.DOWNLOADS -> Icons.Default.CloudDownload
                                        LibraryTab.LOCAL     -> Icons.Default.MusicNote
                                        LibraryTab.TOP       -> Icons.Default.TrendingUp
                                    }
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                },
                                shape = RoundedCornerShape(percent = 50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        when (selectedTab) {
                            LibraryTab.PLAYLISTS -> {
                                if (allPlaylistsWithSongs.isEmpty()) {
                                    item { EmptyState("📋", "Sin playlists", "Crea una nueva para empezar") }
                                } else {
                                    items(allPlaylistsWithSongs) { pws ->
                                        PlaylistItem(pws, { viewModel.selectPlaylist(pws.playlist.playlistId) }, { viewModel.deletePlaylist(pws.playlist.playlistId) })
                                    }
                                }
                            }
                            LibraryTab.FAVORITES -> {
                                if (favorites.isEmpty()) {
                                    item { EmptyState("❤️", "Sin favoritos", "Toca el corazón en el reproductor") }
                                } else {
                                    items(favorites) { song ->
                                        SongListItem(song, { onSongClick(song, favorites) }, Modifier.padding(horizontal = 8.dp))
                                    }
                                }
                            }
                            LibraryTab.RECENTS -> {
                                if (recents.isEmpty()) {
                                    item { EmptyState("🕐", "Sin historial", "Tus canciones aparecerán aquí") }
                                } else {
                                    items(recents) { song ->
                                        SongListItem(song, { onSongClick(song, recents) }, Modifier.padding(horizontal = 8.dp))
                                    }
                                }
                            }
                            LibraryTab.DOWNLOADS -> {
                                item { com.auramusic.app.presentation.downloads.DownloadsScreen() }
                            }
                            LibraryTab.LOCAL -> {
                                item { 
                                    com.auramusic.app.presentation.local.LocalMusicScreen(
                                        onSongClick = { song, list -> onSongClick(song, list) }
                                    ) 
                                }
                            }
                            LibraryTab.TOP -> {
                                if (mostPlayed.isEmpty()) {
                                    item { EmptyState("🔥", "Sin datos", "Escucha música para ver tu top") }
                                } else {
                                    itemsIndexed(mostPlayed) { index, song ->
                                        TopSongItem(index + 1, song, { onSongClick(song, mostPlayed) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailView(
    pws: PlaylistWithSongs,
    onBack: () -> Unit,
    onSongClick: (Song, List<Song>) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val songs = pws.songs.map { entity ->
        Song(entity.id, entity.title, entity.artist, entity.thumbnailUrl, entity.durationSeconds, entity.streamUrl)
    }
    val state = rememberReorderableLazyListState(onMove = { from, to -> onReorder(from.index, to.index) })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(pws.playlist.name, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxSize().reorderable(state),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                ReorderableItem(state, key = song.id) { isDragging ->
                    Box(modifier = Modifier.fillMaxWidth().background(if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)) {
                        SongListItem(song, { onSongClick(song, songs) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(emoji: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, style = MaterialTheme.typography.displayLarge)
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlaylistItem(pws: PlaylistWithSongs, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(8.dp)), Alignment.Center) { Text("🎵") }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(pws.playlist.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("${pws.songs.size} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TopSongItem(rank: Int, song: Song, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", modifier = Modifier.width(32.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Column {
            Text(song.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Playlist") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }) },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("Crear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
