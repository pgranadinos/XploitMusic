package com.xploitmusic.app.data.service

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes manual notification navigation to the active UI when available.
 * Natural track completion always uses the lifecycle-independent player path.
 */
@Singleton
class SkipHandler @Inject constructor(
    private val backgroundNavigator: BackgroundPlaybackNavigator
) {

    interface Delegate {
        fun onSkipNext()
        fun onSkipPrevious()
        fun onAutomaticNext()
    }

    private val delegate = AtomicReference<Delegate?>(null)

    fun hasActiveDelegate(): Boolean = delegate.get() != null

    fun attach(delegate: Delegate) {
        backgroundNavigator.cancelPendingNavigation()
        this.delegate.set(delegate)
    }

    fun cancelPendingNavigation() = backgroundNavigator.cancelPendingNavigation()

    fun detach(delegate: Delegate) {
        this.delegate.compareAndSet(delegate, null)
    }

    fun requestSkipNext() {
        delegate.get()?.onSkipNext() ?: backgroundNavigator.requestSkipNext()
    }

    fun requestSkipPrevious() {
        delegate.get()?.onSkipPrevious() ?: backgroundNavigator.requestSkipPrevious()
    }

    fun requestAutomaticNext() {
        // Track completion can happen with the UI in the background while its
        // ViewModel is still attached. Keep automatic playback owned by the
        // service path so it cannot depend on the screen's lifecycle.
        backgroundNavigator.requestAutomaticNext()
    }
}
