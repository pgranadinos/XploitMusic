package com.xploitmusic.app.data.repository

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** All MediaController access and replacement is confined to its main looper. */
internal class MediaControllerConnection(private val create: () -> ListenableFuture<MediaController>) {
    private val mutex = Mutex()
    private var future: ListenableFuture<MediaController>? = null

    suspend fun get(): MediaController? = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            future?.let { old ->
                val controller = if (old.isDone) runCatching { old.get() }.getOrNull() else null
                if (controller?.isConnected == true) return@withLock controller
                MediaController.releaseFuture(old)
                future = null
            }
            val pending = create().also { future = it }
            val controller = withTimeoutOrNull(10_000L) {
                suspendCancellableCoroutine<MediaController?> { continuation ->
                    pending.addListener({
                        val connected = runCatching { pending.get() }.getOrNull()
                        if (continuation.isActive) continuation.resume(connected)
                    }, MoreExecutors.directExecutor())
                }
            }
            if (controller?.isConnected == true) controller else {
                MediaController.releaseFuture(pending)
                future = null
                null
            }
        }
    }

    suspend fun release() = withContext(Dispatchers.Main.immediate) {
        mutex.withLock {
            future?.let(MediaController::releaseFuture)
            future = null
        }
    }
}
