package com.xploitmusic.app.presentation.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.xploitmusic.app.data.local.entity.PlaylistWithSongs
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.presentation.components.SongListItem
import org.burnoutcrew.reorderable.*

private enum class LibraryTab(val label: String) {
    PLAYLISTS("Playlists"),
    FAVORITES("Favoritos"),
    RECENTS("Recientes"),
    DOWNLOADS("Descargas"),
    LOCAL("Local"),
    TOP("Más escuchadas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onNavigateToSpotifyImport: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToLocal: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val allPlaylistsWithSongs by viewModel.allPlaylistsWithSongs.collectAsState()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
    val showDialog by viewModel.showCreateDialog.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val mostPlayed by viewModel.mostPlayed.collectAsState()

    var selectedTab by remember { mutableStateOf(LibraryTab.PLAYLISTS) }
    var playlistPendingDeletion by remember { mutableStateOf<PlaylistWithSongs?>(null) }
    var playlistPendingRename by remember { mutableStateOf<PlaylistWithSongs?>(null) }

    BackHandler(enabled = selectedPlaylist != null) {
        viewModel.clearSelection()
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            MaterialTheme.colorScheme.background
        )
    )

    if (showDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.dismissCreatePlaylistDialog() },
            onCreate = { name -> viewModel.createPlaylist(name) }
        )
    }

    playlistPendingRename?.let { playlist ->
        RenamePlaylistDialog(
            playlistId = playlist.playlist.playlistId,
            currentName = playlist.playlist.name,
            onDismiss = { playlistPendingRename = null },
            onSave = { name -> viewModel.renamePlaylist(playlist.playlist.playlistId, name) }
        )
    }

    playlistPendingDeletion?.let { playlist ->
        DeletePlaylistDialog(
            playlistName = playlist.playlist.name,
            onDismiss = { playlistPendingDeletion = null },
            onConfirm = {
                viewModel.deletePlaylist(playlist.playlist.playlistId)
                playlistPendingDeletion = null
            }
        )
    }

    Scaffold(
        topBar = {
            if (selectedPlaylist == null) {
                TopAppBar(
                    title = { 
                        Column {
                            Text(
                                "Biblioteca",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                "Tu música y tus colecciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        if (selectedTab == LibraryTab.PLAYLISTS) {
                            IconButton(
                                onClick = onNavigateToSpotifyImport,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload,
                                    "Importar de Spotify",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                onClick = { viewModel.showCreatePlaylistDialog() },
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    "Crear playlist",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
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
                                onClick = {
                                    when (tab) {
                                        LibraryTab.DOWNLOADS -> onNavigateToDownloads()
                                        LibraryTab.LOCAL -> onNavigateToLocal()
                                        else -> selectedTab = tab
                                    }
                                },
                                label = { Text(tab.label) },
                                leadingIcon = {
                                    val icon = when (tab) {
                                        LibraryTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                                        LibraryTab.FAVORITES -> Icons.Default.Favorite
                                        LibraryTab.RECENTS   -> Icons.Default.History
                                        LibraryTab.DOWNLOADS -> Icons.Default.CloudDownload
                                        LibraryTab.LOCAL     -> Icons.Default.MusicNote
                                        LibraryTab.TOP       -> Icons.AutoMirrored.Filled.TrendingUp
                                    }
                                    Icon(icon, null, modifier = Modifier.size(16.dp))
                                },
                                shape = CircleShape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        when (selectedTab) {
                            LibraryTab.PLAYLISTS -> {
                                if (allPlaylistsWithSongs.isEmpty()) {
                                    item {
                                        EmptyState(
                                            icon = Icons.AutoMirrored.Filled.QueueMusic,
                                            title = "Sin playlists",
                                            subtitle = "Crea una colección para guardar tus canciones",
                                            actionLabel = "Crear playlist",
                                            onAction = { viewModel.showCreatePlaylistDialog() }
                                        )
                                    }
                                } else {
                                    items(allPlaylistsWithSongs, key = { it.playlist.playlistId }) { pws ->
                                        PlaylistItem(
                                            pws = pws,
                                            onClick = { viewModel.selectPlaylist(pws.playlist.playlistId) },
                                            onDelete = { playlistPendingDeletion = pws },
                                            onRename = { playlistPendingRename = pws }
                                        )
                                    }
                                }
                            }
                            LibraryTab.FAVORITES -> {
                                if (favorites.isEmpty()) {
                                    item { EmptyState(Icons.Default.FavoriteBorder, "Sin favoritos", "Toca el corazón en el reproductor") }
                                } else {
                                    items(favorites) { song ->
                                        SongListItem(song, { onSongClick(song, favorites) }, Modifier.padding(horizontal = 8.dp))
                                    }
                                }
                            }
                            LibraryTab.RECENTS -> {
                                if (recents.isEmpty()) {
                                    item { EmptyState(Icons.Default.History, "Sin historial", "Tus canciones aparecerán aquí") }
                                } else {
                                    items(recents) { song ->
                                        SongListItem(song, { onSongClick(song, recents) }, Modifier.padding(horizontal = 8.dp))
                                    }
                                }
                            }
                            LibraryTab.DOWNLOADS, LibraryTab.LOCAL -> Unit
                            LibraryTab.TOP -> {
                                if (mostPlayed.isEmpty()) {
                                    item { EmptyState(Icons.AutoMirrored.Filled.TrendingUp, "Sin datos", "Escucha música para ver tu top") }
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
    val state = rememberReorderableLazyListState(onMove = { from, to ->
        val fromSongIndex = from.index - 1
        val toSongIndex = to.index - 1
        if (fromSongIndex in songs.indices && toSongIndex in songs.indices) {
            onReorder(fromSongIndex, toSongIndex)
        }
    })

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(pws.playlist.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${songs.size} ${if (songs.size == 1) "canción" else "canciones"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
        LazyColumn(
            state = state.listState,
            modifier = Modifier.fillMaxSize().reorderable(state),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (songs.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.MusicNote,
                        title = "Playlist vacía",
                        subtitle = "Añade canciones desde el reproductor"
                    )
                }
            } else {
                item {
                    Text(
                        text = "Mantén pulsada una canción para cambiar su posición",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                items(songs, key = { it.id }) { song ->
                    ReorderableItem(state, key = song.id) { isDragging ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .detectReorderAfterLongPress(state)
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                )
                        ) {
                            SongListItem(song, { onSongClick(song, songs) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun PlaylistItem(pws: PlaylistWithSongs, onClick: () -> Unit, onDelete: () -> Unit, onRename: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val coverUrl = pws.playlist.coverUrl?.takeIf { it.isNotBlank() }
        ?: pws.songs.firstOrNull()?.thumbnailUrl?.takeIf { it.isNotBlank() }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        MaterialTheme.shapes.small
                    ),
                Alignment.Center
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = "Carátula de ${pws.playlist.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(pws.playlist.name, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("${pws.songs.size} canciones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Opciones de ${pws.playlist.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Cambiar nombre") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = { showMenu = false; onRename() })
                    DropdownMenuItem(text = { Text("Eliminar") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        onClick = { showMenu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun TopSongItem(rank: Int, song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(MaterialTheme.shapes.small),
            contentAlignment = Alignment.TopStart
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = "Carátula de ${song.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onCreate(name.trim()) }, enabled = name.isNotBlank()) {
                Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Eliminar playlist") },
        text = {
            Text("¿Quieres eliminar \"$playlistName\"? Las canciones no se borrarán del dispositivo.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
