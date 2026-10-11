package com.auramusic.app.data.service

import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class SkipHandlerTest {

    @Test
    fun `uses delegate while UI exists and background after detach`() {
        val navigator = mockk<BackgroundPlaybackNavigator>(relaxed = true)
        val delegate = mockk<SkipHandler.Delegate>(relaxed = true)
        val handler = SkipHandler(navigator)

        handler.attach(delegate)
        handler.requestSkipNext()

        verify(exactly = 1) { delegate.onSkipNext() }
        verify(exactly = 0) { navigator.requestSkipNext() }

        handler.detach(delegate)
        handler.requestSkipNext()

        verify(exactly = 1) { navigator.requestSkipNext() }
    }

    @Test
    fun `detaching an old delegate does not remove the current one`() {
        val navigator = mockk<BackgroundPlaybackNavigator>(relaxed = true)
        val oldDelegate = mockk<SkipHandler.Delegate>(relaxed = true)
        val currentDelegate = mockk<SkipHandler.Delegate>(relaxed = true)
        val handler = SkipHandler(navigator)

        handler.attach(oldDelegate)
        handler.attach(currentDelegate)
        handler.detach(oldDelegate)
        handler.requestSkipPrevious()

        verify(exactly = 1) { currentDelegate.onSkipPrevious() }
        verify(exactly = 0) { navigator.requestSkipPrevious() }
    }

    @Test
    fun `automatic next always uses background navigator even while UI is attached`() {
        val navigator = mockk<BackgroundPlaybackNavigator>(relaxed = true)
        val delegate = mockk<SkipHandler.Delegate>(relaxed = true)
        val handler = SkipHandler(navigator)

        handler.attach(delegate)
        handler.requestAutomaticNext()
        verify(exactly = 0) { delegate.onAutomaticNext() }
        verify(exactly = 1) { navigator.requestAutomaticNext() }

        handler.detach(delegate)
        handler.requestAutomaticNext()
        verify(exactly = 2) { navigator.requestAutomaticNext() }
    }
}
