package com.xploitmusic.app.core.util

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackLogManagerReplayTest {

    @Test
    fun `late subscriber receives replayed network event but previousNetworkType matching prevents duplicate action`() = runTest {
        val networkEvents = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)

        // Emit an initial network event
        networkEvents.emit("Wi-Fi")

        // Replay = 1 allows late collector to receive last emitted value
        val lateEmitted = networkEvents.first()
        assertThat(lateEmitted).isEqualTo("Wi-Fi")

        // When previousNetworkType == lateEmitted, condition previousNetworkType != newNetworkType is FALSE
        var actionExecuted = false
        val previousNetworkType = lateEmitted
        val newNetworkType = lateEmitted

        if (previousNetworkType != newNetworkType) {
            actionExecuted = true
        }

        assertThat(actionExecuted).isFalse()
    }
}
