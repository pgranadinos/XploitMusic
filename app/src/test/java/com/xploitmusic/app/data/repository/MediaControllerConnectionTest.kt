package com.xploitmusic.app.data.repository

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.SettableFuture
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class MediaControllerConnectionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `disconnected controller is replaced and healthy connection reused`() = runTest(dispatcher) {
        val first = mockk<MediaController>(relaxed = true)
        val second = mockk<MediaController>(relaxed = true)
        every { first.isConnected } returns true
        every { second.isConnected } returns true
        var builds = 0
        val connection = MediaControllerConnection { Futures.immediateFuture(if (++builds == 1) first else second) }
        assertThat(connection.get()).isSameInstanceAs(first)
        assertThat(connection.get()).isSameInstanceAs(first)
        every { first.isConnected } returns false
        assertThat(connection.get()).isSameInstanceAs(second)
        assertThat(builds).isEqualTo(2)
        connection.release()
    }

    @Test fun `failed connection can be retried`() = runTest(dispatcher) {
        val controller = mockk<MediaController>(relaxed = true)
        every { controller.isConnected } returns true
        var builds = 0
        val connection = MediaControllerConnection {
            if (++builds == 1) Futures.immediateFailedFuture(IllegalStateException("disconnected"))
            else Futures.immediateFuture(controller)
        }
        assertThat(connection.get()).isNull()
        assertThat(connection.get()).isSameInstanceAs(controller)
        connection.release()
    }

    @Test fun `connection wait is bounded`() = runTest(dispatcher) {
        val connection = MediaControllerConnection { SettableFuture.create<MediaController>() }
        assertThat(connection.get()).isNull()
        assertThat(testScheduler.currentTime).isEqualTo(10_000L)
        connection.release()
    }
}
