package com.auramusic.app.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.auramusic.app.domain.model.SearchResultItem
import com.auramusic.app.domain.model.Song
import com.auramusic.app.presentation.components.SongListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            MaterialTheme.colorScheme.background
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "XploitMusic",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                actions = {
                    IconButton(onClick = { /* TODO: Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(gradientBrush),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // 0. Greeting
            item {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                val greeting = when {
                    hour in 5..11 -> "Buenos días"
                    hour in 12..18 -> "Buenas tardes"
                    else -> "Buenas noches"
                }
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            item {
                Text(
                    text = "Quick Picks",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            // 1. Quick Picks Grid (2x3)
            when (val state = homeUiState.quickPicks) {
                is SectionState.Loading -> {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            repeat(3) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                    Box(Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                                }
                            }
                        }
                    }
                }
                is SectionState.Success -> {
                    item {
                        val chunked = state.data.take(6).chunked(2)
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            chunked.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { pick ->
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(60.dp)
                                                .clickable { 
                                                    pick.song?.let { onSongClick(it, state.data.mapNotNull { i -> i.song }) } 
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = pick.imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(60.dp)
                                                        .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Text(
                                                    text = pick.title,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(horizontal = 8.dp),
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            // 2. Artist Mixes
            when (val state = homeUiState.artistMixes) {
                is SectionState.Loading -> {
                    item { SectionHeader("🎧 Creado para ti") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) { items(3) { ShimmerSongCard() } }
                    }
                }
                is SectionState.Success -> {
                    item { SectionHeader("🎧 Creado para ti") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data) { mix ->
                                com.auramusic.app.presentation.components.AuraAlbumCard(
                                    imageUrl = mix.imageUrl,
                                    title = mix.title,
                                    subtitle = "Mix",
                                    onClick = { viewModel.onSearchQueryChange(mix.seedArtist) },
                                    isCircular = true
                                )
                            }
                        }
                    }
                }
                else -> {}
            }

            // 3. Local Trends
            when (val state = homeUiState.localTrends) {
                is SectionState.Success -> {
                    item { SectionHeader("📈 Tendencias en tu región") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data) { song ->
                                SongCard(song = song, onClick = { onSongClick(song, state.data) })
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit) {
    com.auramusic.app.presentation.components.AuraAlbumCard(
        imageUrl = song.thumbnailUrl,
        title = song.title,
        subtitle = song.artist,
        onClick = onClick
    )
}

@Composable
fun ShimmerSongCard() {
    Card(
        modifier = Modifier.width(150.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant))
            Column(modifier = Modifier.padding(8.dp)) {
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            }
        }
    }
}
