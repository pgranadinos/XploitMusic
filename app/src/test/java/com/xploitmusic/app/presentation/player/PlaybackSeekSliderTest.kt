package com.xploitmusic.app.presentation.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSeekSliderTest {
    @get:Rule val compose = createComposeRule()

    private fun dragAcrossUpdates(start: Float, end: Float) {
        val position = mutableStateOf((start * 100_000).toLong())
        val seeks = mutableListOf<Long>()
        compose.setContent {
            MaterialTheme {
                PlaybackSeekSlider(position.value, 100_000, "track", Color.Blue,
                    { seeks.add(it) }, Modifier.testTag("seek"))
            }
        }
        val slider = compose.onNodeWithTag("seek")
        slider.performTouchInput { down(Offset(width * start, centerY)) }
        compose.waitForIdle()
        slider.performTouchInput { moveTo(Offset(width * 0.5f, centerY), 200) }
        compose.runOnIdle {
            assertThat(seeks).isEmpty()
            position.value += 500L
        }
        slider.performTouchInput { moveTo(Offset(width * end, centerY), 200) }
        compose.waitForIdle()
        slider.performTouchInput { up() }
        compose.runOnIdle {
            assertThat(seeks).hasSize(1)
            assertThat(seeks.single()).isWithin(5_000L).of((end * 100_000).toLong())
        }
    }

    @Test fun `forward drag survives recomposition and seeks once on release`() = dragAcrossUpdates(0.2f, 0.8f)
    @Test fun `backward drag survives recomposition and seeks once on release`() = dragAcrossUpdates(0.8f, 0.2f)

    @Test fun `tap still seeks`() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            MaterialTheme {
                PlaybackSeekSlider(0, 100_000, "track", Color.Blue,
                    { seeks.add(it) }, Modifier.testTag("seek"))
            }
        }
        compose.onNodeWithTag("seek").performTouchInput { click(center) }
        compose.runOnIdle {
            assertThat(seeks).hasSize(1)
            assertThat(seeks.single()).isWithin(2_000L).of(50_000L)
        }
    }
}
