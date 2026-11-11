package com.xploitmusic.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DataStoreAutoplaySettingsTest {

    @get:Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        temporaryFolder.create()
    }

    @After
    fun tearDown() {
        temporaryFolder.delete()
        Dispatchers.resetMain()
    }

    @Test
    fun `default value is true when key does not exist`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_settings_1.preferences_pb")
        val scope = CoroutineScope(testDispatcher + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile }
        )
        val settings = DataStoreAutoplaySettings(dataStore)
        runCurrent()
        assertThat(settings.enabled.first()).isTrue()
        scope.cancel()
    }

    @Test
    fun `setEnabled false persists when recreating instance over same file`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_settings_2.preferences_pb")
        val scope1 = CoroutineScope(testDispatcher + Job())
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = scope1,
            produceFile = { testFile }
        )
        val settings1 = DataStoreAutoplaySettings(dataStore1)
        runCurrent()
        settings1.setEnabled(false)
        runCurrent()

        scope1.cancel()

        val scope2 = CoroutineScope(testDispatcher + Job())
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = scope2,
            produceFile = { testFile }
        )
        val settings2 = DataStoreAutoplaySettings(dataStore2)
        runCurrent()

        assertThat(settings2.enabled.first()).isFalse()
        scope2.cancel()
    }

    @Test
    fun `toggle inverts the persisted value`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_settings_3.preferences_pb")
        val scope = CoroutineScope(testDispatcher + Job())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { testFile }
        )
        val settings = DataStoreAutoplaySettings(dataStore)
        runCurrent()
        assertThat(settings.enabled.first()).isTrue()

        val initialValue = settings.enabled.first()
        settings.setEnabled(!initialValue)
        runCurrent()

        assertThat(settings.enabled.first()).isFalse()
        scope.cancel()
    }

    @Test
    fun `toggle persists each change in order`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_settings_4.preferences_pb")

        val scope1 = CoroutineScope(testDispatcher + Job())
        val dataStore1 = PreferenceDataStoreFactory.create(
            scope = scope1,
            produceFile = { testFile }
        )
        val settings1 = DataStoreAutoplaySettings(dataStore1)
        runCurrent()
        settings1.toggle()
        runCurrent()
        scope1.cancel()

        val scope2 = CoroutineScope(testDispatcher + Job())
        val dataStore2 = PreferenceDataStoreFactory.create(
            scope = scope2,
            produceFile = { testFile }
        )
        val settings2 = DataStoreAutoplaySettings(dataStore2)
        runCurrent()
        assertThat(settings2.enabled.first()).isFalse()

        settings2.toggle()
        runCurrent()
        scope2.cancel()

        val scope3 = CoroutineScope(testDispatcher + Job())
        val dataStore3 = PreferenceDataStoreFactory.create(
            scope = scope3,
            produceFile = { testFile }
        )
        val settings3 = DataStoreAutoplaySettings(dataStore3)
        runCurrent()
        assertThat(settings3.enabled.first()).isTrue()
        scope3.cancel()
    }
}
