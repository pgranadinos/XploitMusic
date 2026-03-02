package com.auramusic.app.data.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared event bus for media notification skip actions.
 * The ForwardingPlayer in AuraMediaService emits skip events,
 * and the PlayerViewModel collects them to trigger forceSkipToNext/Previous.
 *
 * This bridges the gap between ExoPlayer (which only knows about queued MediaItems)
 * and our custom autoplay queue (managed by PlayerViewModel).
 */
@Singleton
class SkipHandler @Inject constructor() {

    enum class SkipDirection { NEXT, PREVIOUS }

    private val _skipRequests = MutableSharedFlow<SkipDirection>(extraBufferCapacity = 1)
    val skipRequests: SharedFlow<SkipDirection> = _skipRequests.asSharedFlow()

    fun requestSkipNext() {
        _skipRequests.tryEmit(SkipDirection.NEXT)
    }

    fun requestSkipPrevious() {
        _skipRequests.tryEmit(SkipDirection.PREVIOUS)
    }
}
