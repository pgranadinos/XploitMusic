package com.xploitmusic.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.domain.repository.ShareMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class DataStoreShareSettingsTest {

    @get:Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

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
    fun `default shareMode emits COVER when key does not exist`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_share_1.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val settings = DataStoreShareSettings(dataStore)
        runCurrent()
        assertThat(settings.shareMode.first()).isEqualTo(ShareMode.COVER)
    }

    @Test
    fun `emits AUDIO when AUDIO is saved in preferences`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_share_2.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val settings = DataStoreShareSettings(dataStore)
        runCurrent()

        settings.setShareMode(ShareMode.AUDIO)
        runCurrent()

        assertThat(settings.shareMode.first()).isEqualTo(ShareMode.AUDIO)
    }

    @Test
    fun `unknown preference value falls back to COVER without failing`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_share_3.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val keyShare = stringPreferencesKey("share_mode")
        dataStore.edit { prefs ->
            prefs[keyShare] = "UNKNOWN_MODE_INVALID"
        }
        runCurrent()

        val settings = DataStoreShareSettings(dataStore)
        runCurrent()

        assertThat(settings.shareMode.first()).isEqualTo(ShareMode.COVER)
    }
}
