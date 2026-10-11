package com.xploitmusic.app.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Spotify/Harmony style vertical card for albums, playlists, and mixes.
 */
@Composable
fun XploitAlbumCard(
    imageUrl: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false
) {
    Column(
        modifier = modifier
            .width(160.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = if (isCircular) Alignment.CenterHorizontally else Alignment.Start
    ) {
        val shape = if (isCircular) CircleShape else MaterialTheme.shapes.medium

        XploitArtwork(
            imageUrl = imageUrl,
            title = title,
            shape = shape,
            modifier = Modifier.size(144.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Compact horizontal card used by the two-column quick-pick grid. */
@Composable
fun XploitQuickPickCard(
    imageUrl: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = MaterialTheme.shapes.small
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XploitArtwork(
                imageUrl = imageUrl,
                title = title,
                shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 10.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun XploitArtwork(
    imageUrl: String,
    title: String,
    shape: Shape,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isBlank()) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                modifier = Modifier.size(40.dp)
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
