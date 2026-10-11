package com.xploitmusic.app.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xploitmusic.app.data.local.entity.PlaylistEntity

/**
 * ModalBottomSheet displayed from the PlayerScreen,
 * allowing users to add the current song to an existing playlist
 * or create a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    playlists: List<PlaylistEntity>,
    onAddToPlaylist: (playlistId: Long, playlistName: String) -> Unit,
    onCreateAndAdd: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNewPlaylistField by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = "Añadir a playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Create new playlist
            if (showNewPlaylistField) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        onClick = {
                            onCreateAndAdd(newPlaylistName)
                        },
                        enabled = newPlaylistName.isNotBlank()
                    ) {
                        Text("Crear")
                    }
                }
            } else {
                FilledTonalButton(
                    onClick = { showNewPlaylistField = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nueva playlist")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            if (playlists.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Existing playlists
            LazyColumn(
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(playlists) { playlist ->
                    ListItem(
                        headlineContent = {
                            Text(
                                text = playlist.name,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .clickable { onAddToPlaylist(playlist.playlistId, playlist.name) }
                            .fillMaxWidth()
                    )
                }

                if (playlists.isEmpty()) {
                    item {
                        Text(
                            text = "No tienes playlists aún",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
