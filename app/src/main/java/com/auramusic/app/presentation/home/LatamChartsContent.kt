package com.auramusic.app.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auramusic.app.domain.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun LatamChartsContent(
    selection: LatamCharts,
    onSongClick: (Song, List<Song>) -> Unit,
    onRetry: () -> Unit,
) {
    var selectedCode by rememberSaveable { mutableStateOf("LATAM") }
    var showSources by remember { mutableStateOf(false) }
    val country = LatamCountry.values().firstOrNull { it.name == selectedCode }
    val chart = selection.charts.firstOrNull { it.country == country }
    val entries = remember(selection, country) {
        if (country == null) selection.entries else {
            selection.charts.firstOrNull { it.country == country }?.entries.orEmpty().map {
                LatamTrack(it.song, listOf(CountryPosition(country, it.rank)))
            }
        }
    }
    val songs = remember(entries) { entries.map { it.song } }
    Column {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(selected = country == null, onClick = { selectedCode = "LATAM" }, label = { Text("LATAM") })
            }
            items(LatamCountry.values().toList(), key = { it.name }) { option ->
                FilterChip(
                    selected = country == option,
                    onClick = { selectedCode = option.name },
                    enabled = option !in selection.unavailableCountries,
                    label = { Text(option.label) }
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = if (country == null) {
                    "Vídeos musicales · Tops diarios de ${selection.charts.size} ${if (selection.charts.size == 1) "país" else "países"}"
                } else chart?.sourceTitle ?: "Ranking de ${country.label} no disponible",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (country == null) {
                Text(
                    "Selección de XploitMusic: primero más países, después mejor posición media. Cada país tiene el mismo peso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selection.unavailableCountries.isNotEmpty()) {
                Text(
                    "No disponibles: ${selection.unavailableCountries.joinToString { it.label }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onRetry) { Text("Reintentar países pendientes") }
            }
            TextButton(onClick = { showSources = true }) { Text("Ver fuentes y fechas de consulta") }
        }
        // Reset the horizontal position when changing the country.
        key(selectedCode) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(entries, key = { it.song.id }) { entry ->
                    Column(Modifier.width(160.dp)) {
                        Text(
                            text = if (country == null) "En ${entry.positions.size} ${if (entry.positions.size == 1) "país" else "países"}" else "#${entry.positions.single().rank} ${country.label}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
                        )
                        SongCard(entry.song, onClick = { onSongClick(entry.song, songs) })
                        if (country == null) {
                            Text(
                                entry.positions.take(3).joinToString(" · ") { "#${it.rank} ${it.country.label}" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    if (showSources) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { showSources = false },
            title = { Text("Rankings nacionales") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Text("LATAM combina las listas disponibles; no es un ranking oficial regional de YouTube. Las fechas indican cuándo se consultó cada lista.")
                    }
                    items(selection.charts, key = { it.country.name }) { source ->
                        Column {
                            Text(source.country.label, fontWeight = FontWeight.Bold)
                            Text(source.sourceTitle, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Consultado: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE")).format(Date(source.fetchedAtMs))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = {
                                uriHandler.openUri("https://music.youtube.com/playlist?list=${source.playlistId}")
                            }) { Text("Ver en YouTube Music") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSources = false }) { Text("Cerrar") } }
        )
    }
}
