package io.github.onprs.universitytoolbox.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreUserPreferencesRepositoryTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `user preferences emit defaults and persist updates`() = runTest {
        val prefsFile = tempFolder.newFile("user_prefs.preferences_pb").apply { delete() }
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { prefsFile },
        )
        val repository = DataStoreUserPreferencesRepository(dataStore)

        repository.userPreferences.test {
            val initial = awaitItem()
            assertEquals(ThemeMode.SYSTEM, initial.themeMode)
            assertEquals(true, initial.useDynamicColor)
            assertEquals(AppLanguage.SYSTEM, initial.appLanguage)
            assertNull(initial.lastUpdateCheckEpochSec)

            repository.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)

            repository.setUseDynamicColor(false)
            assertEquals(false, awaitItem().useDynamicColor)

            repository.setAppLanguage(AppLanguage.EN)
            assertEquals(AppLanguage.EN, awaitItem().appLanguage)

            repository.setLastUpdateCheckEpochSec(1_760_000_500L)
            assertEquals(1_760_000_500L, awaitItem().lastUpdateCheckEpochSec)

            repository.clearPreferences()
            val reset = awaitItem()
            assertEquals(ThemeMode.SYSTEM, reset.themeMode)
            assertEquals(true, reset.useDynamicColor)
            assertEquals(AppLanguage.SYSTEM, reset.appLanguage)
            assertNull(reset.lastUpdateCheckEpochSec)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
