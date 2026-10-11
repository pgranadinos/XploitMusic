package com.auramusic.app.presentation.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.auramusic.app.R
import com.auramusic.app.domain.model.Song
import com.auramusic.app.presentation.components.AuraAlbumCard
import com.auramusic.app.presentation.components.AuraQuickPickCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSongClick: (Song, List<Song>) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val homeUiState by viewModel.homeUiState.collectAsState()
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
            MaterialTheme.colorScheme.background
        ),
        startY = 0f,
        endY = 900f
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.xploitmusic_brand_icon),
                            contentDescription = "Icono de XploitMusic",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "XploitMusic",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.processHomeAction(HomeAction.RefreshAll) },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Actualizar inicio",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
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
            contentPadding = PaddingValues(bottom = 24.dp)
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
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp)
                )
                Text(
                    text = "Descubre qué está sonando hoy en Perú.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 20.dp)
                )
            }

            item {
                SectionHeader(
                    title = "🇵🇪 Lo más escuchado en Perú",
                    subtitle = "Selección regional actualizada"
                )
            }

            // 1. Regional music grid (2x3)
            when (val state = homeUiState.quickPicks) {
                is SectionState.Loading -> {
                    item { QuickPicksLoading() }
                }
                is SectionState.Success -> {
                    item {
                        val chunked = state.data.take(6).chunked(2)
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            chunked.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { pick ->
                                        AuraQuickPickCard(
                                            imageUrl = pick.imageUrl,
                                            title = pick.title,
                                            onClick = {
                                                pick.song?.let {
                                                    onSongClick(it, state.data.mapNotNull { item -> item.song })
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(68.dp)
                                        )
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                is SectionState.Empty -> {
                    item {
                        HomeSectionMessage(
                            message = "No encontramos música regional en este momento.",
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryQuickPicks) }
                        )
                    }
                }
                is SectionState.Error -> {
                    item {
                        HomeSectionMessage(
                            message = state.message,
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryQuickPicks) }
                        )
                    }
                }
            }

            // 2. Personalized recommendations with playable songs
            item {
                SectionHeader(
                    title = "Creado para ti",
                    subtitle = "Canciones basadas en lo que escuchas"
                )
            }
            when (val state = homeUiState.recommendations) {
                is SectionState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) { items(3) { ShimmerSongCard() } }
                    }
                }
                is SectionState.Success -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.data, key = { it.id }) { song ->
                                SongCard(song = song, onClick = { onSongClick(song, state.data) })
                            }
                        }
                    }
                }
                is SectionState.Empty -> {
                    item {
                        HomeSectionMessage(
                            message = "Escucha algunas canciones para mejorar tus recomendaciones.",
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryRecommendations) }
                        )
                    }
                }
                is SectionState.Error -> {
                    item {
                        HomeSectionMessage(
                            message = state.message,
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryRecommendations) }
                        )
                    }
                }
            }

            // 3. LATAM selection and the original national rankings.
            item {
                SectionHeader(
                    title = "Lo más escuchado en Latinoamérica",
                    subtitle = "Selección de rankings nacionales"
                )
            }
            when (val state = homeUiState.latamCharts) {
                is SectionState.Loading -> {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) { items(3) { ShimmerSongCard() } }
                    }
                }
                is SectionState.Success -> {
                    item {
                        LatamChartsContent(
                            selection = state.data,
                            onSongClick = onSongClick,
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryLatamCharts) }
                        )
                    }
                }
                is SectionState.Empty -> {
                    item {
                        HomeSectionMessage(
                            message = "Los rankings de Latinoamérica no están disponibles en este momento.",
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryLatamCharts) }
                        )
                    }
                }
                is SectionState.Error -> {
                    item {
                        HomeSectionMessage(
                            message = state.message,
                            onRetry = { viewModel.processHomeAction(HomeAction.RetryLatamCharts) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SongCard(song: Song, onClick: () -> Unit) {
    AuraAlbumCard(
        imageUrl = song.thumbnailUrl,
        title = song.title,
        subtitle = song.artist,
        onClick = onClick
    )
}

@Composable
fun ShimmerSongCard() {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = Modifier
            .width(160.dp)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .size(144.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(7.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(12.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(shimmerBrush)
        )
    }
}

@Composable
private fun QuickPicksLoading() {
    val shimmerBrush = rememberShimmerBrush()
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        repeat(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(2) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(68.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeSectionMessage(
    message: String,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRetry) {
                Text("Reintentar")
            }
        }
    }
}

@Composable
private fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "homeShimmer")
    val offset by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1_000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "homeShimmerOffset"
    )
    return Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.surfaceVariant
        ),
        start = Offset(offset - 300f, 0f),
        end = Offset(offset, 300f)
    )
}
