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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBackClick: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val isLoadingStream by viewModel.isLoadingStream.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

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
        colors = listOf(
            animDominant.copy(alpha = 0.7f),
            animVibrant.copy(alpha = 0.3f),
            MaterialTheme.colorScheme.background
        )
    )

    var showLyrics by remember { mutableStateOf(false) }
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
                    if (showLyrics) "Lyrics" else "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { showLyrics = !showLyrics }) {
                    Text(
                        "LYR",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (showLyrics) animVibrant else Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Content Area ──
            Box(modifier = Modifier.weight(1f)) {
                if (showLyrics) {
                    if (lyrics.isNotEmpty()) {
                        com.auramusic.app.presentation.components.LyricsView(
                            lyrics = lyrics,
                            currentPosition = currentPosition,
                            onLineClick = { viewModel.seekTo(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No lyrics available", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Album Art
                        AsyncImage(
                            model = song.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .shadow(24.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Song Title & Artist
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
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
                        color = if (autoplayEnabled) animVibrant else Color.White.copy(alpha = 0.3f)
                    )
                }

                // Favorite button
                AnimatedFavoriteButton(
                    isFavorite = isFavorite,
                    onClick = { viewModel.toggleFavorite() }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Previous | Play/Pause | Next — main controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                // Play/Pause with bounce animation
                val playScale by animateFloatAsState(
                    targetValue = if (isPlaying) 1f else 0.95f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "playScale"
                )

                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier.size(72.dp).scale(playScale),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = animVibrant,
                        contentColor = Color.White
                    )
                ) {
                    if (isPlaying) {
                        Text("❚❚", style = MaterialTheme.typography.headlineMedium)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(36.dp))
                    }
                }

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
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Snackbar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    var animateTrigger by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animateTrigger) 1.4f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        finishedListener = { animateTrigger = false },
        label = "favoriteScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.6f),
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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
