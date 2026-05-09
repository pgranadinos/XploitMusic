package com.auramusic.app.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auramusic.app.presentation.player.PlayerViewModel
import com.auramusic.app.domain.model.PlaybackState

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
                MaterialTheme.colorScheme.surface
            )
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shadowElevation = 12.dp,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column {
                // ── Progress Bar ──
                LinearProgressIndicator(
                    progress = { if (duration > 0) currentPosition.toFloat() / duration else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // ── Content Row ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .background(bgBrush)
                        .clickable(onClick = onTap)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Album Art
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = "Album art for ${song.title}",
                        modifier = Modifier
                            .size(48.dp)
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
                            style = MaterialTheme.typography.titleMedium,
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
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Play / Pause / Loading button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .clickable { viewModel.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading || playbackState is PlaybackState.Buffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (isPlaying) {
                            // Pause icon (two vertical bars)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Box(
                                    Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
