package com.xploitmusic.app.presentation.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.FileProvider
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.xploitmusic.app.core.theme.AppTheme
import com.xploitmusic.app.core.theme.XploitMusicTheme
import com.xploitmusic.app.domain.model.Song
import com.xploitmusic.app.domain.repository.ShareMode
import com.xploitmusic.app.domain.repository.ShareSettings
import com.xploitmusic.app.domain.repository.ThemeSettings
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SharePreferencesEntryPoint {
    fun shareSettings(): ShareSettings
    fun themeSettings(): ThemeSettings
}

fun buildShareText(song: Song): String {
    return "${song.title} — ${song.artist}\nhttps://music.youtube.com/watch?v=${song.id}"
}

@Composable
private fun ShareCardContent(song: Song, isArena: Boolean, coverBitmap: Bitmap?, lyricSnippet: String?) {
    XploitMusicTheme(themeMode = if (isArena) AppTheme.ARENA else AppTheme.NOCHE) {
        ShareCard(song = song, isArena = isArena, coverBitmap = coverBitmap, lyricSnippet = lyricSnippet)
    }
}

object ShareHelper {
    private val helperScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    fun shareSong(
        context: Context,
        song: Song,
        lyricSnippet: String? = null,
        onAudioTodo: () -> Unit = {}
    ) {
        helperScope.launch(Dispatchers.Main) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SharePreferencesEntryPoint::class.java
                )
                val shareMode = entryPoint.shareSettings().shareMode.first()
                val isArena = entryPoint.themeSettings().themeMode.first() == AppTheme.ARENA

                if (shareMode == ShareMode.AUDIO) {
                    // TODO: AudioShareManager
                    onAudioTodo()
                }

                var loadedBitmap: Bitmap? = null
                if (!song.thumbnailUrl.isNullOrBlank()) {
                    val request = ImageRequest.Builder(context)
                        .data(song.thumbnailUrl)
                        .allowHardware(false)
                        .build()
                    val result = withTimeoutOrNull(3000L) {
                        context.imageLoader.execute(request)
                    }
                    if (result is SuccessResult) {
                        loadedBitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    }
                }

                val bitmapFile = renderShareCardBitmap(context, song, isArena, loadedBitmap, lyricSnippet)

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    bitmapFile
                )

                val text = buildShareText(song)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, text)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Compartir canción")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Log.e("ShareHelper", "Error sharing song: ${e.message}", e)
            }
        }
    }

    private suspend fun renderShareCardBitmap(
        context: Context,
        song: Song,
        isArena: Boolean,
        coverBitmap: Bitmap?,
        lyricSnippet: String?
    ): File = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "share")
        if (shareDir.exists()) {
            shareDir.listFiles()?.forEach { it.delete() }
        } else {
            shareDir.mkdirs()
        }
        val bitmapFile = File(shareDir, "share_${System.currentTimeMillis()}.png")

        withContext(Dispatchers.Main) {
            val activity = findActivity(context)
            val decorView = activity?.window?.decorView as? ViewGroup
                ?: (context as? Activity)?.window?.decorView as? ViewGroup

            if (decorView == null) {
                Log.e("ShareHelper", "No Activity or decorView available for rendering ComposeView offscreen")
                return@withContext
            }

            val composeView = ComposeView(context).apply {
                translationX = 10000f
                translationY = 10000f
                setContent {
                    ShareCardContent(song = song, isArena = isArena, coverBitmap = coverBitmap, lyricSnippet = lyricSnippet)
                }
            }

            try {
                decorView.addView(composeView, ViewGroup.LayoutParams(1080, 1920))

                withContext(AndroidUiDispatcher.Main) {
                    var frameCount = 0
                    val frameStartMs = System.currentTimeMillis()
                    val timeoutMs = 1000L
                    while ((composeView.childCount == 0 || frameCount < 2) && (System.currentTimeMillis() - frameStartMs) < timeoutMs) {
                        withFrameNanos { }
                        frameCount++
                    }
                }

                val widthPx = 1080
                val heightPx = 1920
                composeView.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                composeView.layout(0, 0, widthPx, heightPx)

                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                composeView.draw(canvas)

                FileOutputStream(bitmapFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bitmap.recycle()
            } finally {
                decorView.removeView(composeView)
            }
        }
        bitmapFile
    }

    private fun findActivity(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
}
