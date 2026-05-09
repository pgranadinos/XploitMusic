package com.auramusic.app.presentation.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.Song
import org.burnoutcrew.reorderable.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueView(
    upNext: List<Song>,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (upNext.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tu cola está vacía", color = Color.White.copy(alpha = 0.7f))
        }
        return
    }

    val state = rememberReorderableLazyListState(onMove = { from, to ->
        onMove(from.index, to.index)
    })

    LazyColumn(
        state = state.listState,
        modifier = modifier
            .fillMaxSize()
            .reorderable(state),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        itemsIndexed(items = upNext, key = { _, item -> item.id }) { index, song ->
            ReorderableItem(state, key = song.id) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                val alpha = if (isDragging) 0.8f else 1f

                SwipeToDismissBox(
                    state = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                onRemove(index)
                                true
                            } else false
                        }
                    ),
                    backgroundContent = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Red.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White, modifier = Modifier.padding(end = 16.dp))
                        }
                    },
                    content = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .detectReorderAfterLongPress(state)
                                .shadow(elevation, RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clip(RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            AsyncImage(
                                model = song.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = alpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = alpha * 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(Icons.Default.DragHandle, contentDescription = "Reordenar", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.padding(12.dp))
                        }
                    }
                )
            }
        }
    }
}
