package com.xploitmusic.app.presentation.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.xploitmusic.app.data.remote.SpotifyPlaylistDto
import com.xploitmusic.app.data.repository.SpotifyMatchingEngine.MatchType
import com.xploitmusic.app.domain.usecase.ImportProgress
import com.xploitmusic.app.domain.usecase.TrackImportStatus
import com.xploitmusic.app.data.local.SpotifyConfigurationStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportPlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val spotifyClientId by viewModel.spotifyClientId.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.authUrlEvents.collect { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar desde Spotify", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (userProfile != null) {
                        TextButton(onClick = { viewModel.logout() }) {
                            Text("Cerrar Sesión")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is SpotifyImportState.CheckingAuth -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is SpotifyImportState.Unauthenticated -> {
                    UnauthenticatedView(
                        clientId = spotifyClientId,
                        onLoginClick = viewModel::startOAuth
                    )
                }
                is SpotifyImportState.LoadingPlaylists -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando tus playlists...", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                is SpotifyImportState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is SpotifyImportState.PlaylistsLoaded -> {
                    PlaylistPickerView(
                        playlists = state.playlists,
                        onPlaylistClick = { viewModel.startImport(it) },
                        onOpenSpotify = { playlist ->
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://open.spotify.com/playlist/${playlist.id}")
                                )
                            )
                        }
                    )
                }
                is SpotifyImportState.Importing -> {
                    ImportProgressView(
                        progress = state.progress,
                        onDone = onNavigateBack,
                        onCancel = { viewModel.cancelImport() }
                    )
                }
            }
        }
    }
}

@Composable
private fun UnauthenticatedView(
    clientId: String,
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "Conecta tu cuenta Spotify",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Importa tus playlists a XploitMusic. Se usarán sus metadatos para buscar equivalencias y guardarlas localmente en tu biblioteca.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (clientId.isBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⚠️ Client ID no configurado",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Configura 'SPOTIFY_CLIENT_ID' en tu archivo 'local.properties' para habilitar la autenticación.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else {
            Button(
                onClick = onLoginClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(56.dp)
            ) {
                Text("Conectar con Spotify", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun PlaylistPickerView(
    playlists: List<SpotifyPlaylistDto>,
    onPlaylistClick: (SpotifyPlaylistDto) -> Unit,
    onOpenSpotify: (SpotifyPlaylistDto) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        if (playlists.isEmpty()) {
            item {
                Text(
                    "No tienes playlists en Spotify",
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
        items(playlists) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = playlist.images?.firstOrNull()?.url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${playlist.totalItems} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onOpenSpotify(playlist) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Abrir en Spotify",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressView(
    progress: ImportProgress,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    val completedRatio = if (progress.totalTracks > 0) progress.processedTracks.toFloat() / progress.totalTracks else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Importando '${progress.playlist.name}'",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Circular Progress
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            CircularProgressIndicator(
                progress = { completedRatio },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 12.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${(completedRatio * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${progress.processedTracks} / ${progress.totalTracks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Latest Status
        val status = progress.latestStatus
        if (status != null) {
            val (icon, tint, message) = when (status) {
                is TrackImportStatus.Matched -> {
                    when (status.matchType) {
                        MatchType.EXACT -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Coincidencia exacta: ${status.song.title}")
                        MatchType.HIGH_CONFIDENCE -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Coincidencia encontrada: ${status.song.title}")
                        MatchType.LOW_CONFIDENCE -> Triple(Icons.Default.Warning, Color(0xFFFFC107), "Baja Confianza: ${status.song.title}")
                        MatchType.NO_MATCH -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "No match para: ${status.spotifyTrack.name}")
                    }
                }
                is TrackImportStatus.LowConfidence -> Triple(
                    Icons.Default.Warning,
                    Color(0xFFFFC107),
                    "Omitida por baja confianza: ${status.spotifyTrack.name}"
                )
                is TrackImportStatus.NoMatch -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "No encontrado: ${status.spotifyTrack.name}")
                is TrackImportStatus.Failed -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Error: ${status.spotifyTrack.name}")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (progress.isComplete) {
            Text(
                "${progress.matchedTracks} importadas · ${progress.skippedTracks} omitidas · ${progress.failedTracks} con error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Ir a Librería")
            }
        } else {
            TextButton(onClick = onCancel) {
                Text("Cancelar importación")
            }
        }
    }
}
