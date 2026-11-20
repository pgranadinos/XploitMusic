package com.xploitmusic.app.presentation.share

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xploitmusic.app.core.theme.*
import com.xploitmusic.app.domain.model.Song

@Composable
fun AutoScalingText(
    text: String,
    initialFontSize: TextUnit,
    minScale: Float,
    maxLinesIfFitsFails: Int = 2,
    fontWeight: FontWeight = FontWeight.Normal,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier
) {
    var fontSize by remember(text, initialFontSize) { mutableStateOf(initialFontSize) }
    var maxLines by remember(text) { mutableStateOf(1) }
    val minFontSize = initialFontSize * minScale

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.hasVisualOverflow && maxLines == 1) {
                val nextSize = fontSize * 0.85f
                if (nextSize >= minFontSize) {
                    fontSize = nextSize
                } else {
                    fontSize = minFontSize
                    maxLines = maxLinesIfFitsFails
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun ShareCard(
    song: Song,
    isArena: Boolean = false,
    coverBitmap: Bitmap? = null,
    lyricSnippet: String? = null,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isArena) ArenaBackground else NocheBackground
    val textPrimary = if (isArena) ArenaTextPrimary else NocheTextPrimary
    val textSecondary = if (isArena) ArenaTextSecondary else NocheTextSecondary
    val primary = if (isArena) ArenaPrimary else NochePrimary
    val placeholderBackground = if (isArena) {
        Brush.linearGradient(listOf(ArenaCard, ArenaPrimaryLight))
    } else {
        Brush.linearGradient(listOf(NocheSurface, NochePrimary))
    }

    val density = LocalDensity.current
    val cardWidthDp = with(density) { 1080.toDp() }
    val cardHeightDp = with(density) { 1920.toDp() }

    Box(
        modifier = modifier
            .size(cardWidthDp, cardHeightDp)
            .background(backgroundColor)
            .padding(64.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background artwork tint or gradient
        if (coverBitmap != null) {
            Box(modifier = Modifier.fillMaxSize().alpha(0.18f)) {
                Image(
                    bitmap = coverBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor.copy(alpha = if (isArena) 0.82f else 0.88f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.12f)
                    .background(placeholderBackground)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Wordmark top
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "X",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "XploitMusic",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            // 2. Central artwork
            val coverShape = RoundedCornerShape(24.dp)
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .shadow(24.dp, coverShape)
                    .clip(coverShape)
                    .then(
                        if (coverBitmap == null) Modifier.background(placeholderBackground)
                        else Modifier.background(if (isArena) ArenaCard else NocheCard)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = "Carátula",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = primary
                    )
                }
            }

            // 3. Song info, 4. Artist, 5. Lyric snippet, 6. Link bottom (NO button)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AutoScalingText(
                    text = song.title,
                    initialFontSize = 24.sp,
                    minScale = 0.40f,
                    maxLinesIfFitsFails = 2,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))

                AutoScalingText(
                    text = song.artist,
                    initialFontSize = 16.sp,
                    minScale = 0.60f,
                    maxLinesIfFitsFails = 2,
                    fontWeight = FontWeight.Normal,
                    color = textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!lyricSnippet.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "“$lyricSnippet”",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = textSecondary.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(18.dp))

                AutoScalingText(
                    text = "https://music.youtube.com/watch?v=${song.id}",
                    initialFontSize = 12.sp,
                    minScale = 0.60f,
                    maxLinesIfFitsFails = 1,
                    color = textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
