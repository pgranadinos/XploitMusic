@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.xploitmusic.app.presentation.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.xploitmusic.app.core.theme.AppTheme
import com.xploitmusic.app.presentation.share.ShareHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.xploitmusic.app.domain.model.Song
import kotlin.math.abs

// Removed PlayerTab para mudar a BottomSheet UX@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    onEmptySession: () -> Unit = onBackClick,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val sessionChecked by viewModel.sessionChecked.collectAsState()
    val downloadState by viewModel.downloadState.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    // Performance: currentPosition has been isolated to sub-components
    val duration by viewModel.duration.collectAsState()
    val isLoadingStream by viewModel.isLoadingStream.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    val songOriginTags by viewModel.songOriginTags.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Permiso de notificaciones necesario para ver el progreso de descarga.")
            }
        }
    }

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()

    // Dynamic palette colors from album art
    val steelBlue = MaterialTheme.colorScheme.primary
    var dominantColor by remember { mutableStateOf(steelBlue) }

    // Animate color transitions
    val animDominant by animateColorAsState(
        targetValue = dominantColor, animationSpec = tween(800), label = "dominant"
    )
    // Extract palette from thumbnail
    val context = LocalContext.current
    val defaultPrimaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    LaunchedEffect(currentSong?.thumbnailUrl, steelBlue, defaultPrimaryArgb) {
        dominantColor = steelBlue
        currentSong?.thumbnailUrl?.let { url ->
            // Performance: Debounce fetching palette on quick skips
            kotlinx.coroutines.delay(300)
            try {
                val request = ImageRequest.Builder(context)
                    .data(url).allowHardware(false).size(200).build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let {
                        val palette = Palette.from(it).generate()
                        val artworkColor = Color(
                            palette.getDominantColor(defaultPrimaryArgb)
                        )
                        dominantColor = lerp(steelBlue, artworkColor, 0.32f)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Show snackbar on error
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
            viewModel.clearError()
        }
    }

    if (currentSong == null) {
        LaunchedEffect(sessionChecked, isLoadingStream) {
            if (sessionChecked && !isLoadingStream) onEmptySession()
        }
        Box(modifier = Modifier.fillMaxSize()) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimizar reproductor"
                )
            }
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        return
    }

    val song = currentSong!!

    val backgroundBrush = Brush.verticalGradient(
        0.0f to animDominant.copy(alpha = 0.62f),
        0.48f to MaterialTheme.colorScheme.background,
        1.0f to MaterialTheme.colorScheme.background
    )

    val lyrics by viewModel.lyrics.collectAsState()
    val isLyricsLoading by viewModel.isLyricsLoading.collectAsState()
    val currentPos by viewModel.currentPosition.collectAsState()
    val activeLyricText = remember(lyrics, currentPos) {
        if (lyrics.isEmpty()) null
        else {
            val currentIndex = lyrics.indexOfLast { it.startTimeMs <= currentPos }
            if (currentIndex >= 0) lyrics[currentIndex].text else null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            val isLandscape = maxWidth > maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
                PlayerTopBar(
                    onBackClick = onBackClick,
                    onQueueClick = { showQueueSheet = true }
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerArtwork(
                            song = song,
                            isLoading = isLoadingStream,
                            viewModel = viewModel,
                            durationMs = duration,
                            modifier = Modifier.weight(0.9f).fillMaxHeight()
                        )
                        Spacer(modifier = Modifier.width(24.dp))
                        PlayerDetails(
                            modifier = Modifier.weight(1.1f),
                            song = song,
                            viewModel = viewModel,
                            duration = duration,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            autoplayEnabled = autoplayEnabled,
                            isShuffleEnabled = isShuffleEnabled,
                            repeatMode = repeatMode,
                            downloadStatus = if (song.id.startsWith("local_")) "LOCAL" else downloadState?.status,
                            downloadProgress = downloadState?.progress ?: 0,
                            accent = steelBlue,
                            currentOrigin = songOriginTags[song.id],
                            onLyricsClick = {
                                viewModel.requestLyrics()
                                showLyricsSheet = true
                            },
                            onPlaylistClick = { showPlaylistSheet = true },
                            onFavoriteClick = {
                                handleFavoriteClick(viewModel, isFavorite, coroutineScope, snackbarHostState)
                            },
                            onDownloadClick = {
                                handleDownloadClick(
                                    context,
                                    viewModel,
                                    downloadState?.status,
                                    coroutineScope,
                                    snackbarHostState
                                ) {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onShareClick = {
                                ShareHelper.shareSong(
                                    context = context,
                                    song = song,
                                    lyricSnippet = activeLyricText
                                )
                            }
                        )
                    }
                } else {
                    PlayerArtwork(
                        song = song,
                        isLoading = isLoadingStream,
                        viewModel = viewModel,
                        durationMs = duration,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    PlayerDetails(
                        song = song,
                        viewModel = viewModel,
                        duration = duration,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        autoplayEnabled = autoplayEnabled,
                        isShuffleEnabled = isShuffleEnabled,
                        repeatMode = repeatMode,
                        downloadStatus = if (song.id.startsWith("local_")) "LOCAL" else downloadState?.status,
                        downloadProgress = downloadState?.progress ?: 0,
                        accent = steelBlue,
                        currentOrigin = songOriginTags[song.id],
                        onLyricsClick = {
                            viewModel.requestLyrics()
                            showLyricsSheet = true
                        },
                        onPlaylistClick = { showPlaylistSheet = true },
                        onFavoriteClick = {
                            handleFavoriteClick(viewModel, isFavorite, coroutineScope, snackbarHostState)
                        },
                        onDownloadClick = {
                            handleDownloadClick(
                                context,
                                viewModel,
                                downloadState?.status,
                                coroutineScope,
                                snackbarHostState
                            ) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onShareClick = {
                            ShareHelper.shareSong(
                                context = context,
                                song = song,
                                lyricSnippet = activeLyricText
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // Add to Playlist BottomSheet
    if (showPlaylistSheet) {
        AddToPlaylistSheet(
            playlists = playlists,
            onAddToPlaylist = { playlistId, playlistName ->
                viewModel.addCurrentSongToPlaylist(playlistId)
                showPlaylistSheet = false
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Añadida a \"$playlistName\"",
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onCreateAndAdd = { name ->
                viewModel.createPlaylistAndAddSong(name)
                showPlaylistSheet = false
                coroutineScope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Creada \"$name\" y canción añadida",
                        duration = SnackbarDuration.Short
                    )
                }
            },
            onDismiss = { showPlaylistSheet = false }
        )
    }

    // Modal Queue
    if (showQueueSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            QueueView(
                upNext = upNext,
                onMove = { from, to -> viewModel.moveSongInQueue(from, to) },
                onRemove = { idx -> viewModel.removeSongFromQueue(idx) },
                onItemClick = { idx -> viewModel.playQueueItem(idx) },
                originTags = songOriginTags,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Modal Lyrics
    if (showLyricsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLyricsSheet = false },
            containerColor = animDominant, // Use palette dominant for lyrics BG
            modifier = Modifier.fillMaxHeight(0.9f)
        ) {
            if (lyrics.isNotEmpty()) {
                IsolatedLyricsView(
                    viewModel = viewModel,
                    lyrics = lyrics
                )
            } else if (isLyricsLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Text("Buscando letra…", color = Color.White.copy(alpha = 0.8f))
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron letras", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun PlayerTopBar(
    onBackClick: () -> Unit,
    onQueueClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp)) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimizar reproductor",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Text(
            "REPRODUCIENDO",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.84f),
            modifier = Modifier.align(Alignment.Center)
        )
        IconButton(
            onClick = onQueueClick,
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "Ver cola de reproducción",
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f)
            )
        }
    }
}

fun calculateProgress(currentPositionMs: Long, durationMs: Long, durationSeconds: Long): Float {
    val effectiveDuration = if (durationMs > 0L) {
        durationMs
    } else {
        durationSeconds.coerceIn(0L, Long.MAX_VALUE / 1_000L) * 1_000L
    }
    return if (effectiveDuration > 0L) {
        (currentPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)
    } else {
        0f
    }
}

@Composable
private fun PlayerArtwork(
    song: Song,
    isLoading: Boolean,
    viewModel: PlayerViewModel,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val currentPositionMs by viewModel.currentPosition.collectAsState()
    val progress = calculateProgress(currentPositionMs, durationMs, song.durationSeconds)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxDim = minOf(maxWidth, maxHeight)
        val ringSize = minOf(maxDim, 300.dp)
        val artworkSize = minOf(maxDim * 0.8f, 240.dp)

        Box(
            modifier = Modifier.size(ringSize),
            contentAlignment = Alignment.Center
        ) {
            // Anillo circular de progreso alrededor de la carátula (~300 dp en primary)
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.5.dp,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            )

            // Carátula cuadrada de 240 dp con esquinas de 24 dp
            val coverShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .shadow(16.dp, coverShape)
                    .clip(coverShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = "Carátula de ${song.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Preparando audio…",
                                style = MaterialTheme.typography.bodySmall,
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
private fun PlayerDetails(
    song: Song,
    viewModel: PlayerViewModel,
    duration: Long,
    isPlaying: Boolean,
    isFavorite: Boolean,
    autoplayEnabled: Boolean,
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    downloadStatus: String?,
    downloadProgress: Int,
    accent: Color,
    onLyricsClick: () -> Unit,
    onPlaylistClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onShareClick: () -> Unit,
    currentOrigin: com.xploitmusic.app.domain.model.SongOrigin? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                currentOrigin?.let { origin ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = origin.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
            AnimatedFavoriteButton(isFavorite = isFavorite, onClick = onFavoriteClick)
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onShareClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Compartir canción",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        val effectiveDuration = if (duration > 0L) {
            duration
        } else {
            song.durationSeconds.coerceIn(0L, Long.MAX_VALUE / 1_000L) * 1_000L
        }
        IsolatedPlaybackSlider(viewModel = viewModel, duration = effectiveDuration, accent = accent)
        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::toggleShuffle, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Default.Shuffle,
                    contentDescription = if (isShuffleEnabled) "Desactivar aleatorio" else "Activar aleatorio",
                    tint = if (isShuffleEnabled) accent else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.size(25.dp)
                )
            }
            IconButton(onClick = viewModel::skipPrevious, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Canción anterior",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(34.dp)
                )
            }
            com.xploitmusic.app.presentation.components.XploitPlayButton(
                isPlaying = isPlaying,
                onClick = viewModel::togglePlayPause,
                size = 68.dp,
                containerColor = accent
            )
            IconButton(onClick = viewModel::skipNext, modifier = Modifier.size(52.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Siguiente canción",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(34.dp)
                )
            }
            IconButton(onClick = viewModel::toggleRepeat, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) {
                        Icons.Default.RepeatOne
                    } else {
                        Icons.Default.Repeat
                    },
                    contentDescription = when (repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repetir canción"
                        androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repetir cola"
                        else -> "Activar repetición"
                    },
                    tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) {
                        accent
                    } else {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.74f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerUtilityButton(
                    icon = Icons.Default.AllInclusive,
                    label = "Autoplay",
                    selected = autoplayEnabled,
                    onClick = viewModel::toggleAutoplay
                )
                PlayerUtilityButton(
                    icon = Icons.Default.Lyrics,
                    label = "Letra",
                    onClick = onLyricsClick
                )
                PlayerUtilityButton(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    label = "Añadir",
                    onClick = onPlaylistClick
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedDownloadButton(
                        status = downloadStatus,
                        progress = downloadProgress,
                        onClick = onDownloadClick
                    )
                    Text(
                        text = downloadActionLabel(downloadStatus, downloadProgress),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (downloadStatus == "FAILED") {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlayerUtilityButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        Color.Transparent
                    },
                    shape = androidx.compose.foundation.shape.CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(25.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

private fun downloadActionLabel(status: String?, progress: Int): String = when (status) {
    "LOCAL" -> "En el teléfono"
    "COMPLETED" -> "Descargada"
    "QUEUED" -> "En espera"
    "DOWNLOADING" -> if (progress > 0) "$progress%" else "Descargando"
    "FAILED" -> "Reintentar"
    else -> "Descargar"
}

private fun handleDownloadClick(
    context: android.content.Context,
    viewModel: PlayerViewModel,
    currentStatus: String?,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onRequestNotificationPermission: () -> Unit
) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
        androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        onRequestNotificationPermission()
    }

    viewModel.toggleDownload()
    coroutineScope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(
            message = when (currentStatus) {
                "COMPLETED" -> "Descarga eliminada"
                "FAILED" -> "Reintentando descarga…"
                else -> "Descarga iniciada"
            },
            duration = SnackbarDuration.Short
        )
    }
}

private fun handleFavoriteClick(
    viewModel: PlayerViewModel,
    wasFavorite: Boolean,
    coroutineScope: CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    viewModel.toggleFavorite()
    coroutineScope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        val result = snackbarHostState.showSnackbar(
            message = if (wasFavorite) "Eliminada de favoritos" else "Añadida a favoritos",
            actionLabel = "Deshacer",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.toggleFavorite()
    }
}

@Composable
private fun AnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    var animateTrigger by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animateTrigger) 1.6f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = { animateTrigger = false },
        label = "favoriteScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "favoriteTint"
    )

    IconButton(
        onClick = {
            animateTrigger = true
            onClick()
        },
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = tint,
            modifier = Modifier.size(28.dp).scale(scale)
        )
    }
}

@Composable
private fun AnimatedDownloadButton(
    status: String?,
    progress: Int,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    var actionPending by remember(status) { mutableStateOf(false) }
    val transferActive = status == "QUEUED" || status == "DOWNLOADING"
    val showProgress = transferActive || actionPending
    val (icon, tint) = when (status) {
        "COMPLETED", "LOCAL" -> Icons.Default.OfflinePin to accent
        "QUEUED", "DOWNLOADING" -> Icons.Outlined.Download to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        "FAILED" -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.Download to MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
    }

    IconButton(
        onClick = {
            actionPending = true
            onClick()
        },
        enabled = status != "LOCAL" && !transferActive && !actionPending
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = when (status) {
                    "COMPLETED", "LOCAL" -> "Disponible sin conexión"
                    "DOWNLOADING" -> "Descargando para uso sin conexión"
                    "FAILED" -> "Reintentar descarga sin conexión"
                    else -> "Guardar para uso sin conexión"
                },
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
            if (showProgress) {
                if (status == "DOWNLOADING" && progress > 0) {
                    CircularProgressIndicator(
                        progress = { progress / 100f },
                        color = accent,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent
                    )
                } else {
                    CircularProgressIndicator(
                        color = accent,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent
                    )
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun IsolatedPlaybackSlider(
    viewModel: PlayerViewModel,
    duration: Long,
    accent: Color
) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    val song by viewModel.currentSong.collectAsState()
    PlaybackSeekSlider(currentPosition, duration, song?.id, accent, viewModel::seekTo)
}

@Composable
internal fun PlaybackSeekSlider(
    currentPosition: Long,
    duration: Long,
    mediaId: String?,
    accent: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember(mediaId) { mutableStateOf(false) }
    var dragFraction by remember(mediaId) { mutableFloatStateOf(0f) }
    var pendingSeekPosition by remember(mediaId) { mutableStateOf<Long?>(null) }
    val latestDuration = rememberUpdatedState(duration)
    val latestOnSeek = rememberUpdatedState(onSeek)
    // Material3 1.2 keys SliderState by this callback. Changing its identity
    // during position updates cancels an in-progress drag.
    val finishSeek: () -> Unit = remember(mediaId) {
        {
            if (isDragging && latestDuration.value > 0L) {
                val target = (dragFraction.coerceIn(0f, 1f) * latestDuration.value).toLong()
                pendingSeekPosition = target
                isDragging = false
                latestOnSeek.value(target)
            }
        }
    }

    // Keep the thumb at the selected point until Media3 reports the completed seek.
    // Without this optimistic state it briefly snaps back to the previous position.
    LaunchedEffect(currentPosition, pendingSeekPosition) {
        val target = pendingSeekPosition ?: return@LaunchedEffect
        if (abs(currentPosition - target) <= 2_000L) pendingSeekPosition = null
    }

    val displayPosition = when {
        isDragging && duration > 0L -> (dragFraction.coerceIn(0f, 1f) * duration).toLong()
        pendingSeekPosition != null -> pendingSeekPosition!!
        else -> currentPosition
    }
    val sliderFraction = if (isDragging) {
        dragFraction
    } else if (duration > 0L) {
        (displayPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = modifier) {
        Slider(
            value = sliderFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragFraction = fraction
            },
            onValueChangeFinished = finishSeek,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(displayPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun IsolatedLyricsView(
    viewModel: PlayerViewModel,
    lyrics: List<com.xploitmusic.app.domain.model.LyricLine>
) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    com.xploitmusic.app.presentation.components.LyricsView(
        lyrics = lyrics,
        currentPosition = currentPosition,
        onLineClick = viewModel::seekTo
    )
}
