package com.auramusic.app.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auramusic.app.data.local.entity.DownloadedTrackEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val storageUsed by viewModel.storageUsedBytes.collectAsState()
    val maxStorage by viewModel.maxStorageBytes.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Biblioteca", "En proceso", "Ajustes")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargas", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Storage bar across all tabs
            StorageIndicatorBar(
                usedBytes = storageUsed,
                maxBytes = maxStorage,
                formatBytes = viewModel::formatBytes
            )

            // Tab row
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label) }
                    )
                }
            }

            when (selectedTab) {
                0 -> LibraryTab(
                    completed = completedDownloads,
                    failed = failedDownloads,
                    onDelete = viewModel::deleteDownload,
                    onRetry = viewModel::retryFailed,
                    formatBytes = viewModel::formatBytes
                )
                1 -> ActiveTab(
                    active = activeDownloads,
                    onCancel = viewModel::cancelDownload
                )
                2 -> SettingsTab(
                    maxStorageMb = (maxStorage / 1_048_576).toInt(),
                    wifiOnly = wifiOnly,
                    storageUsedFormatted = viewModel.formatBytes(storageUsed),
                    onMaxStorageChange = viewModel::setMaxStorageMb,
                    onWifiOnlyChange = viewModel::setWifiOnly,
                    onClearCache = viewModel::clearCacheNow
                )
            }
        }
    }
}

// ── Storage Bar ──────────────────────────────────────────────────────────────

@Composable
private fun StorageIndicatorBar(usedBytes: Long, maxBytes: Long, formatBytes: (Long) -> String) {
    val fraction = if (maxBytes > 0) (usedBytes.toFloat() / maxBytes).coerceIn(0f, 1f) else 0f
    val color = when {
        fraction > 0.9f -> MaterialTheme.colorScheme.error
        fraction > 0.7f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Storage, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "${formatBytes(usedBytes)} / ${formatBytes(maxBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Library Tab (completed + failed) ─────────────────────────────────────────

@Composable
private fun LibraryTab(
    completed: List<DownloadedTrackEntity>,
    failed: List<DownloadedTrackEntity>,
    onDelete: (String) -> Unit,
    onRetry: (String) -> Unit,
    formatBytes: (Long) -> String
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (failed.isNotEmpty()) {
            item {
                Text(
                    "❌ Error en descarga",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(failed, key = { it.songId }) { track ->
                DownloadTrackRow(
                    track = track,
                    subtitle = track.errorMessage ?: "Descarga fallida",
                    trailing = {
                        Row {
                            IconButton(onClick = { onRetry(track.songId) }) {
                                Icon(Icons.Default.Refresh, "Reintentar", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDelete(track.songId) }) {
                                Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        }

        if (completed.isEmpty()) {
            item {
                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Download, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("No tienes canciones descargadas", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            item {
                Text(
                    "✅ ${completed.size} canción${if (completed.size != 1) "es" else ""} disponibles sin internet",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(completed, key = { it.songId }) { track ->
                DownloadTrackRow(
                    track = track,
                    subtitle = formatBytes(track.fileSizeBytes) + " · ${track.format} · ${track.quality}",
                    trailing = {
                        IconButton(onClick = { onDelete(track.songId) }) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
        }
    }
}

// ── Active Downloads Tab ──────────────────────────────────────────────────────

@Composable
private fun ActiveTab(
    active: List<DownloadedTrackEntity>,
    onCancel: (String) -> Unit
) {
    if (active.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No hay descargas en proceso", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(active, key = { it.songId }) { track ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onCancel(track.songId) }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { track.progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                )
                Text(
                    "${track.progress}% · ${track.status}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

// ── Settings Tab ─────────────────────────────────────────────────────────────

@Composable
private fun SettingsTab(
    maxStorageMb: Int,
    wifiOnly: Boolean,
    storageUsedFormatted: String,
    onMaxStorageChange: (Int) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onClearCache: () -> Unit
) {
    var sliderValue by remember(maxStorageMb) { mutableFloatStateOf(maxStorageMb.toFloat()) }
    var showConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Límite de almacenamiento", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = { onMaxStorageChange(sliderValue.toInt()) },
                        valueRange = 100f..2048f,
                        steps = 18
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("100 MB", style = MaterialTheme.typography.labelSmall)
                        Text("${sliderValue.toInt()} MB seleccionado", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Text("2 GB", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Usando: $storageUsedFormatted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Solo por WiFi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Descargar solo con WiFi activo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { showConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Limpiar caché ahora")
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("⚖️ Aviso de contenido", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Las descargas están disponibles solo para contenido cuya fuente permite uso offline. " +
                        "El contenido de terceros (YouTube/Piped) puede estar sujeto a restricciones de copyright. " +
                        "Úsalo exclusivamente para uso personal y sin redistribución.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("¿Limpiar caché?") },
            text = { Text("Se eliminarán las canciones menos usadas hasta alcanzar el límite configurado.") },
            confirmButton = {
                TextButton(onClick = { onClearCache(); showConfirmDialog = false }) {
                    Text("Limpiar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

// ── Shared Row Composable ─────────────────────────────────────────────────────

@Composable
private fun DownloadTrackRow(
    track: DownloadedTrackEntity,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
        }
        trailing()
    }
}
