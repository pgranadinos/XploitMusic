package com.xploitmusic.app.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import com.xploitmusic.app.core.theme.AppTheme
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
class DataStoreThemeSettingsTest {

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
    fun `default themeMode emits NOCHE when key does not exist`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_theme_1.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val settings = DataStoreThemeSettings(dataStore)
        runCurrent()
        assertThat(settings.themeMode.first()).isEqualTo(AppTheme.NOCHE)
    }

    @Test
    fun `emits ARENA when ARENA is saved in preferences`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_theme_2.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val settings = DataStoreThemeSettings(dataStore)
        runCurrent()

        settings.setThemeMode(AppTheme.ARENA)
        runCurrent()

        assertThat(settings.themeMode.first()).isEqualTo(AppTheme.ARENA)
    }

    @Test
    fun `unknown preference value falls back to NOCHE without failing`() = runTest(testDispatcher) {
        val testFile = temporaryFolder.newFile("test_theme_3.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { testFile }
        )
        val keyTheme = stringPreferencesKey("theme_mode")
        dataStore.edit { prefs ->
            prefs[keyTheme] = "UNKNOWN_THEME_INVALID"
        }
        runCurrent()

        val settings = DataStoreThemeSettings(dataStore)
        runCurrent()

        assertThat(settings.themeMode.first()).isEqualTo(AppTheme.NOCHE)
    }
}
