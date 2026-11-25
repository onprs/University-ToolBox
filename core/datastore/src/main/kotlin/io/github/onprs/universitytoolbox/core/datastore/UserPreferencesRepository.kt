package io.github.onprs.universitytoolbox.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * 应用外观主题模式配置。
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * 应用内界面语言配置。
 */
enum class AppLanguage(val languageTag: String?) {
    SYSTEM(null),
    ZH("zh"),
    EN("en"),
}

/**
 * 用户轻量偏好设置聚合模型。
 */
data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val lastUpdateCheckEpochSec: Long? = null,
)

/**
 * 应用偏好设置仓储契约。
 */
interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setAppLanguage(language: AppLanguage)

    suspend fun setLastUpdateCheckEpochSec(epochSec: Long)

    suspend fun clearPreferences()
}

/**
 * 基于 Preferences DataStore 的 [UserPreferencesRepository] 默认实现。
 */
@Singleton
class DataStoreUserPreferencesRepository @Inject constructor(
    @param:Named(PREFERENCES_DATASTORE_NAME)
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserPreferences(
                themeMode = prefs[KEY_THEME_MODE].toEnumOrDefault(ThemeMode.SYSTEM),
                useDynamicColor = prefs[KEY_USE_DYNAMIC_COLOR] ?: true,
                appLanguage = prefs[KEY_APP_LANGUAGE].toEnumOrDefault(AppLanguage.SYSTEM),
                lastUpdateCheckEpochSec = prefs[KEY_LAST_UPDATE_CHECK_EPOCH_SEC],
            )
        }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode.name
        }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_USE_DYNAMIC_COLOR] = enabled
        }
    }

    override suspend fun setAppLanguage(language: AppLanguage) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_LANGUAGE] = language.name
        }
    }

    override suspend fun setLastUpdateCheckEpochSec(epochSec: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_CHECK_EPOCH_SEC] = epochSec
        }
    }

    override suspend fun clearPreferences() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(defaultValue: T): T {
        if (this.isNullOrBlank()) {
            return defaultValue
        }
        return runCatching { enumValueOf<T>(this) }.getOrDefault(defaultValue)
    }

    companion object {
        const val PREFERENCES_DATASTORE_NAME = "user_preferences"

        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_LAST_UPDATE_CHECK_EPOCH_SEC = longPreferencesKey("last_update_check_epoch_sec")
    }
}
