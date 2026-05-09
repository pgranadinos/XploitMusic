package com.auramusic.app.presentation.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.auramusic.app.data.remote.SpotifyPlaylistDto
import com.auramusic.app.data.repository.SpotifyMatchingEngine.MatchType
import com.auramusic.app.domain.usecase.ImportProgress
import com.auramusic.app.domain.usecase.TrackImportStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ImportPlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current

    val oauthCode by SpotifyAuthManager.codeResult.collectAsState()
    LaunchedEffect(oauthCode) {
        oauthCode?.let { code ->
            val verifier = SpotifyAuthManager.verifierToConsume
            if (verifier != null) {
                viewModel.exchangeToken(code, verifier)
                SpotifyAuthManager.verifierToConsume = null
            }
            SpotifyAuthManager.codeResult.value = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Importar desde Spotify", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                    titleContentColor = Color(0xFF1DB954) // Spotify Green
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
                        onLoginClick = { launchSpotifyAuth(context, viewModel) }
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
                        Button(onClick = { viewModel.loadPlaylists() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is SpotifyImportState.PlaylistsLoaded -> {
                    PlaylistPickerView(
                        playlists = state.playlists,
                        onPlaylistClick = { viewModel.startImport(it) }
                    )
                }
                is SpotifyImportState.Importing -> {
                    ImportProgressView(
                        progress = state.progress,
                        onDone = onNavigateBack
                    )
                }
            }
        }
    }
}

// ─── Constants ───
private val SpotifyGreen = Color(0xFF1DB954)

@Composable
private fun UnauthenticatedView(onLoginClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Dummy Spotify Icon presentation (Aesthetic purposes)
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(SpotifyGreen.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.LibraryMusic,
                contentDescription = null,
                tint = SpotifyGreen,
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
            "Importa tus playlists a AuraMusic. AuraMusic buscará las equivalencias en YouTube y las guardará localmente.\n\nNo se violan términos de Spotify (no hay streaming de audio).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onLoginClick,
            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
        ) {
            Text("Conectar con Spotify", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun PlaylistPickerView(
    playlists: List<SpotifyPlaylistDto>,
    onPlaylistClick: (SpotifyPlaylistDto) -> Unit
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
                        "${playlist.tracks.total} canciones",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressView(progress: ImportProgress, onDone: () -> Unit) {
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
                color = SpotifyGreen,
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
                        MatchType.EXACT -> Triple(Icons.Default.CheckCircle, SpotifyGreen, "Match Exácto: ${status.song.title}")
                        MatchType.HIGH_CONFIDENCE -> Triple(Icons.Default.CheckCircle, Color(0xFF8BC34A), "Match Encontrado: ${status.song.title}")
                        MatchType.LOW_CONFIDENCE -> Triple(Icons.Default.Warning, Color(0xFFFFC107), "Baja Confianza: ${status.song.title}")
                        MatchType.NO_MATCH -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "No match para: ${status.spotifyTrack.name}")
                    }
                }
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
            Button(
                onClick = onDone,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Text("Ir a Librería")
            }
        }
    }
}

// ─── External Actions ───

// Guardamos el verifier globalmente de forma temporal para el callback.
// En una app más robusta se persiste en DataStore.
object SpotifyAuthManager {
    var verifierToConsume: String? = null
    val codeResult = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

private fun launchSpotifyAuth(context: Context, viewModel: ImportPlaylistViewModel) {
    val (url, verifier) = viewModel.getOAuthUrl()
    SpotifyAuthManager.verifierToConsume = verifier

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}
