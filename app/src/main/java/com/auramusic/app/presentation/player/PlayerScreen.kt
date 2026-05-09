@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.auramusic.app.presentation.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

// Removed PlayerTab para mudar a BottomSheet UX@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsState()
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
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsState()

    // Dynamic palette colors from album art
    var dominantColor by remember { mutableStateOf(Color(0xFF7C4DFF)) }
    var vibrantColor by remember { mutableStateOf(Color(0xFF651FFF)) }

    // Animate color transitions
    val animDominant by animateColorAsState(
        targetValue = dominantColor, animationSpec = tween(800), label = "dominant"
    )
    val animVibrant by animateColorAsState(
        targetValue = vibrantColor, animationSpec = tween(800), label = "vibrant"
    )

    // Extract palette from thumbnail
    val context = LocalContext.current
    LaunchedEffect(currentSong?.thumbnailUrl) {
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
                        dominantColor = Color(palette.getDominantColor(0xFF7C4DFF.toInt()))
                        vibrantColor = Color(palette.getVibrantColor(0xFF651FFF.toInt()))
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No media playing", color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }

    val song = currentSong!!

    val backgroundBrush = Brush.verticalGradient(
        0.0f to animDominant.copy(alpha = 0.8f),
        0.55f to MaterialTheme.colorScheme.background,
        1.0f to MaterialTheme.colorScheme.background
    )

    val lyrics by viewModel.lyrics.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top Bar ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.KeyboardArrowDown, "Collapse", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showQueueSheet = true }) {
                    Icon(
                        Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color.White.copy(alpha = 0.75f)
                    )
                }
                TextButton(onClick = { showLyricsSheet = true }) {
                    Text(
                        "LYR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Content Area ──
            Box(modifier = Modifier.weight(1f)) {
                // ── Cover Always Visible ──
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Album Art
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .aspectRatio(1f)
                            .shadow(32.dp, RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Song Title & Artist
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Loading overlay
                if (isLoadingStream) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = animVibrant, strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Obteniendo audio...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Seek Bar ──
            IsolatedPlaybackSlider(
                viewModel = viewModel,
                duration = duration,
                animVibrant = animVibrant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Playback Controls ──
            // Row 1: Autoplay toggle + Favorite — secondary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Autoplay toggle
                IconButton(onClick = { viewModel.toggleAutoplay() }) {
                    Text(
                        text = "∞",
                        style = MaterialTheme.typography.headlineMedium,
                        color = if (autoplayEnabled) animVibrant else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Add to Playlist button
                IconButton(onClick = { showPlaylistSheet = true }) {
                    Icon(
                        Icons.Default.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Download button
                AnimatedDownloadButton(
                    status = downloadState?.status,
                    progress = downloadState?.progress ?: 0,
                    onClick = { viewModel.toggleDownload() }
                )

                // Favorite button
                AnimatedFavoriteButton(
                    isFavorite = isFavorite,
                    onClick = { 
                        viewModel.toggleFavorite() 
                        val newlyFavorited = !isFavorite
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = if (newlyFavorited) "Añadida a favoritos" else "Eliminada de favoritos",
                                actionLabel = "Deshacer",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.toggleFavorite()
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Shuffle | Previous | Play/Pause | Next | Repeat — main controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) animVibrant else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Skip Previous
                IconButton(
                    onClick = { viewModel.skipPrevious() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play/Pause with AuraPlayButton (scaling already baked in or customized)
                com.auramusic.app.presentation.components.AuraPlayButton(
                    isPlaying = isPlaying,
                    onClick = { viewModel.togglePlayPause() },
                    size = 72.dp,
                    containerColor = animVibrant
                )

                // Skip Next
                IconButton(
                    onClick = { viewModel.skipNext() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Repeat
                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                            animVibrant else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron letras", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
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
        targetValue = if (isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(300),
        label = "favoriteTint"
    )

    IconButton(onClick = {
        animateTrigger = true
        onClick()
    }) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = tint,
            modifier = Modifier.size(32.dp).scale(scale)
        )
    }
}

@Composable
private fun AnimatedDownloadButton(
    status: String?,
    progress: Int,
    onClick: () -> Unit
) {
    val (icon, tint) = when (status) {
        "COMPLETED" -> Icons.Default.OfflinePin to Color(0xFF651FFF) // Vibrant color
        "QUEUED", "DOWNLOADING" -> Icons.Outlined.Download to Color.White.copy(alpha = 0.7f)
        "FAILED" -> Icons.Default.ErrorOutline to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.Download to Color.White.copy(alpha = 0.7f)
    }

    IconButton(onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = "Download",
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
            if (status == "DOWNLOADING") {
                CircularProgressIndicator(
                    progress = { progress / 100f },
                    color = Color(0xFF651FFF),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 2.dp,
                    trackColor = Color.Transparent
                )
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
    animVibrant: Color
) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
            onValueChange = { viewModel.seekTo((it * duration).toLong()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = animVibrant,
                activeTrackColor = animVibrant,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(currentPosition), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
            Text(formatTime(duration), color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun IsolatedLyricsView(
    viewModel: PlayerViewModel,
    lyrics: List<com.auramusic.app.domain.model.LyricLine>
) {
    val currentPosition by viewModel.currentPosition.collectAsState()
    com.auramusic.app.presentation.components.LyricsView(
        lyrics = lyrics,
        currentPosition = currentPosition,
        onLineClick = { viewModel.seekTo(it) },
        modifier = Modifier.fillMaxSize()
    )
}

