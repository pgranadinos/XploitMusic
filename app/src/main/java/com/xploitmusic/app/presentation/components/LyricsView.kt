package com.xploitmusic.app.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xploitmusic.app.domain.model.LyricLine

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onLineClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Find active line index
    val activeIndex = lyrics.indexOfLast { it.startTimeMs <= currentPosition }

    // Auto-scroll to active line
    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem(activeIndex, scrollOffset = -150)
        }
    }

    // Edge fade gradient
    val edgeFade = Modifier
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadeHeight = 48.dp.toPx()
            // Top fade
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black),
                    startY = 0f,
                    endY = fadeHeight
                ),
                blendMode = BlendMode.DstIn
            )
            // Bottom fade
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black, Color.Transparent),
                    startY = size.height - fadeHeight,
                    endY = size.height
                ),
                blendMode = BlendMode.DstIn
            )
        }

    Box(modifier = modifier.then(edgeFade)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 48.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex

                val textColor by animateColorAsState(
                    targetValue = if (isActive)
                        MaterialTheme.colorScheme.onBackground
                    else
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    label = "textColor"
                )

                val scale = if (isActive) 1.1f else 1.0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLineClick(line.startTimeMs) }
                        .padding(vertical = 8.dp, horizontal = 16.dp)
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = (18 * scale).sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
