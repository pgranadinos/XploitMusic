package com.xploitmusic.app.presentation.local

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import com.xploitmusic.app.domain.model.LocalAudioFolder
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.presentation.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    val localSongs by viewModel.localSongs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val permissionGranted by viewModel.permissionGranted.collectAsState()
    val selectedFolders by viewModel.selectedFolders.collectAsState()
    val context = LocalContext.current

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some document providers grant access without supporting persistence.
        }
        viewModel.addSelectedFolder(uri.toString())
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.onPermissionResult(true)
        } else {
            permissionLauncher.launch(permission)
        }
    }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Música local", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Audio guardado en este dispositivo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    if (permissionGranted) {
                        IconButton(onClick = { folderLauncher.launch(null) }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Añadir carpeta")
                        }
                    }
                    if (permissionGranted && selectedFolders.isNotEmpty() && !isLoading) {
                        IconButton(onClick = viewModel::loadLocalSongs) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar música")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(gradientBrush),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!permissionGranted) {
                item {
                    LocalEmptyState(
                        icon = Icons.Default.Lock,
                        title = "Permiso de música requerido",
                        description = "XploitMusic solo leerá los archivos de audio para mostrarlos y reproducirlos en tu dispositivo.",
                        actionLabel = "Permitir acceso",
                        onAction = { permissionLauncher.launch(permission) }
                    )
                }
            } else if (selectedFolders.isEmpty()) {
                item {
                    LocalEmptyState(
                        icon = Icons.Default.FolderOff,
                        title = "Elige tus carpetas de música",
                        description = "Solo se mostrarán canciones de las carpetas que selecciones. Puedes añadir más de una.",
                        actionLabel = "Elegir carpeta",
                        onAction = { folderLauncher.launch(null) }
                    )
                }
            } else {
                item {
                    Text(
                        "Carpetas incluidas (${selectedFolders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Incluye sus subcarpetas. Quitar una carpeta de esta lista no borra sus archivos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(selectedFolders, key = { "folder_${it.uri}" }) { folder ->
                    SelectedFolderRow(
                        folder = folder,
                        onRemove = { viewModel.removeSelectedFolder(folder.uri) }
                    )
                }

                item {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.CreateNewFolder, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir carpeta")
                    }
                }

                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (localSongs.isEmpty()) {
                    item {
                        LocalEmptyState(
                            icon = Icons.Default.FolderOff,
                            title = "Sin música en estas carpetas",
                            description = "No encontramos música de al menos 30 segundos. Puedes añadir otra carpeta.",
                            actionLabel = "Añadir carpeta",
                            onAction = { folderLauncher.launch(null) }
                        )
                    }
                } else {
                    item {
                        LocalCollectionHeader(
                            songCount = localSongs.size,
                            onPlayAll = { onSongClick(localSongs.first(), localSongs) }
                        )
                    }

                    items(localSongs, key = { it.id }) { song ->
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.84f)
                        ) {
                            SongListItem(
                                song = song,
                                onClick = { onSongClick(song, localSongs) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedFolderRow(
    folder: LocalAudioFolder,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (folder.path.isNotBlank()) Text(folder.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Quitar carpeta ${folder.name}")
            }
        }
    }
}

@Composable
private fun LocalCollectionHeader(songCount: Int, onPlayAll: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AudioFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("En este dispositivo", fontWeight = FontWeight.Bold)
                Text(
                    "$songCount ${if (songCount == 1) "canción" else "canciones"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(onClick = onPlayAll) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir todo")
            }
        }
    }
}

@Composable
private fun LocalEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAction, shape = MaterialTheme.shapes.medium) {
            Text(actionLabel)
        }
    }
}
