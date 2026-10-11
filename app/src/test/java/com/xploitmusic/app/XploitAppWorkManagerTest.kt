package com.xploitmusic.app

import androidx.work.WorkerFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class XploitAppWorkManagerTest {

    @Test
    fun `work manager configuration uses injected worker factory`() {
        val factory = mockk<WorkerFactory>()

        val configuration = createWorkManagerConfiguration(factory)

        assertThat(configuration.workerFactory).isSameInstanceAs(factory)
    }
}
