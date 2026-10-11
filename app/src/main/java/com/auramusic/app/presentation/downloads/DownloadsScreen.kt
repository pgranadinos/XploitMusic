package com.auramusic.app.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auramusic.app.data.local.entity.DownloadedTrackEntity
import com.auramusic.app.domain.model.Song

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onSongClick: (Song, List<Song>) -> Unit = { _, _ -> },
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val completedDownloads by viewModel.completedDownloads.collectAsState()
    val activeDownloads by viewModel.activeDownloads.collectAsState()
    val failedDownloads by viewModel.failedDownloads.collectAsState()
    val storageUsed by viewModel.storageUsedBytes.collectAsState()
    val maxStorage by viewModel.maxStorageBytes.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var trackPendingDeletion by remember { mutableStateOf<DownloadedTrackEntity?>(null) }
    val tabs = listOf("Biblioteca", "En proceso", "Ajustes")
    val offlineSongs = completedDownloads.map { track ->
        Song(
            id = track.songId,
            title = track.title,
            artist = track.artist,
            thumbnailUrl = track.thumbnailUrl,
            durationSeconds = track.durationSeconds,
            streamUrl = track.filePath.orEmpty()
        )
    }
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Descargas", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Tu música disponible sin conexión",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(backgroundBrush)
        ) {

            // Storage bar across all tabs
            StorageIndicatorBar(
                usedBytes = storageUsed,
                maxBytes = maxStorage,
                formatBytes = viewModel::formatBytes
            )

            // Tab row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                tabs.forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, fontWeight = FontWeight.SemiBold) },
                        icon = {
                            val count = when (idx) {
                                0 -> completedDownloads.size
                                1 -> activeDownloads.size
                                else -> 0
                            }
                            if (idx != 2) {
                                Badge(containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                                    Text(count.toString())
                                }
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (selectedTab) {
                0 -> LibraryTab(
                    completed = completedDownloads,
                    failed = failedDownloads,
                    onPlay = { track ->
                        offlineSongs.firstOrNull { it.id == track.songId }?.let { song ->
                            onSongClick(song, offlineSongs)
                        }
                    },
                    onDelete = { trackPendingDeletion = it },
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

    trackPendingDeletion?.let { track ->
        DeleteDownloadDialog(
            trackTitle = track.title,
            onDismiss = { trackPendingDeletion = null },
            onConfirm = {
                viewModel.deleteDownload(track.songId)
                trackPendingDeletion = null
            }
        )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Almacenamiento offline",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${formatBytes(usedBytes)} / ${formatBytes(maxBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                color = color,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        }
    }
}

// ── Library Tab (completed + failed) ─────────────────────────────────────────

@Composable
private fun LibraryTab(
    completed: List<DownloadedTrackEntity>,
    failed: List<DownloadedTrackEntity>,
    onPlay: (DownloadedTrackEntity) -> Unit,
    onDelete: (DownloadedTrackEntity) -> Unit,
    onRetry: (String) -> Unit,
    formatBytes: (Long) -> String
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (failed.isNotEmpty()) {
            item {
                DownloadSectionHeader(
                    icon = Icons.Default.ErrorOutline,
                    title = "Descargas con errores",
                    tint = MaterialTheme.colorScheme.error
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
                            IconButton(onClick = { onDelete(track) }) {
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
                    DownloadEmptyState(
                        icon = Icons.Default.Download,
                        title = "Sin canciones descargadas",
                        subtitle = "Guarda una canción desde el reproductor para escucharla sin conexión"
                    )
                }
            }
        } else {
            item {
                DownloadSectionHeader(
                    icon = Icons.Default.DownloadDone,
                    title = "${completed.size} canción${if (completed.size != 1) "es" else ""} sin conexión",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            items(completed, key = { it.songId }) { track ->
                DownloadTrackRow(
                    track = track,
                    subtitle = "Disponible sin conexión · ${formatBytes(track.fileSizeBytes)} · ${track.format}",
                    onClick = { onPlay(track) },
                    trailing = {
                        IconButton(onClick = { onDelete(track) }) {
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
            DownloadEmptyState(
                icon = Icons.Default.Download,
                title = "Sin descargas en proceso",
                subtitle = "Aquí verás el progreso de las canciones que guardes"
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(active, key = { it.songId }) { track ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
            ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onCancel(track.songId) }) {
                        Text("Cancelar", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { track.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall)
                )
                Text(
                    when (track.status) {
                        "QUEUED" -> "En espera de descarga"
                        "DOWNLOADING" -> "Descargando · ${track.progress.coerceIn(0, 100)}%"
                        "PAUSED" -> "En pausa · ${track.progress.coerceIn(0, 100)}%"
                        else -> "Preparando descarga"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
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
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f))
            ) {
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
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.86f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Solo por Wi‑Fi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Descargar únicamente con una red Wi‑Fi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Optimizar almacenamiento")
            }
        }

        item {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Uso responsable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    }
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
            title = { Text("¿Optimizar almacenamiento?") },
            text = { Text("Se eliminarán las descargas menos usadas solo si superas el límite configurado.") },
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
private fun DownloadSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DownloadEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun DownloadTrackRow(
    track: DownloadedTrackEntity,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = "Carátula de ${track.title}",
                modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.labelSmall,
                    color = if (track.status == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

@Composable
private fun DeleteDownloadDialog(
    trackTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Eliminar descarga") },
        text = { Text("Se eliminará el archivo guardado de \"$trackTitle\".") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Eliminar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
