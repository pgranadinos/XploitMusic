package com.xploitmusic.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.xploitmusic.app.presentation.player.PlayerViewModel
import com.xploitmusic.app.domain.model.PlaybackState

/**
 * Persistent mini-player bar shown at the bottom of all screens while a song is active.
 *
 * Rendered inside the main Scaffold's [bottomBar] so it survives navigation between tabs
 * (Home / Library / Local) without losing its state. The [PlayerViewModel] is scoped to
 * the Activity (not re-created on navigation), so `currentSong`, `isPlaying`, etc. remain
 * stable across the entire session.
 *
 * @param viewModel Activity-scoped [PlayerViewModel].
 * @param onTap     Called when the user taps anywhere on the bar — typically navigates to the
 *                  full PlayerScreen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    viewModel: PlayerViewModel,
    onTap: () -> Unit
) {
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val isLoading by viewModel.isLoadingStream.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()

    // Only show when there is an active song
    AnimatedVisibility(
        visible = currentSong != null,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(tween(300)),
        exit = slideOutVertically(animationSpec = tween(250)) { it } + fadeOut(tween(250))
    ) {
        val song = currentSong ?: return@AnimatedVisibility

        val bgBrush = Brush.horizontalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
        val progress = if (duration > 0L) {
            (currentPosition.toFloat() / duration).coerceIn(0f, 1f)
        } else {
            0f
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 5.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 10.dp,
            tonalElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
            shape = MaterialTheme.shapes.medium
        ) {
            Column {
                // ── Content Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(bgBrush)
                        .clickable(onClick = onTap)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Album Art
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = "Carátula de ${song.title}",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    // Song info with marquee for long titles
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.basicMarquee(
                                iterations = Int.MAX_VALUE,
                                delayMillis = 3000,
                                initialDelayMillis = 2000
                            )
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = viewModel::toggleFavorite,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isFavorite) "Quitar de favoritos" else "Añadir a favoritos",
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Play / Pause / Loading button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || playbackState is PlaybackState.Buffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else if (isPlaying) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = "Pausar",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Reproducir",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // Progress sits at the lower edge, closer to Spotify's compact player.
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface
                )
            }
        }
    }
}
