package com.auramusic.app.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Spotify/Harmony style vertical card for albums, playlists, and mixes.
 */
@Composable
fun AuraAlbumCard(
    imageUrl: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isCircular: Boolean = false // True for Artists
) {
    Column(
        modifier = modifier
            .width(140.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = if (isCircular) Alignment.CenterHorizontally else Alignment.Start
    ) {
        val shape = if (isCircular) RoundedCornerShape(50) else RoundedCornerShape(8.dp)
        
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(124.dp)
                .clip(shape)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
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
