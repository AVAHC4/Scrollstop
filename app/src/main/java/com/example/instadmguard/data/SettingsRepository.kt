package com.example.instadmguard.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.instadmguard.model.BlockMode
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settings: StateFlow<AppSettings> =
        context.appSettingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }
            .map(::preferencesToSettings)
            .stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.MASTER_ENABLED] = enabled
        }
    }

    suspend fun setAllowDmOpenedReelsOnly(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.ALLOW_DM_ONLY] = enabled
        }
    }

    suspend fun setBlockMode(blockMode: BlockMode) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.BLOCK_MODE] = blockMode.name
        }
    }

    suspend fun setGraceDurationSeconds(seconds: Int) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.GRACE_DURATION_SECONDS] = seconds.coerceIn(5, 120)
        }
    }

    suspend fun pauseForMinutes(minutes: Int) {
        val pauseUntil = System.currentTimeMillis() + (minutes * 60_000L)
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.PAUSE_UNTIL_MILLIS] = pauseUntil
        }
    }

    suspend fun clearPause() {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.PAUSE_UNTIL_MILLIS] = 0L
        }
    }

    suspend fun setDebugMode(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.DEBUG_MODE] = enabled
        }
    }

    suspend fun setOverlayDismissible(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_DISMISSIBLE] = enabled
        }
    }

    suspend fun setDailyLimitEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.DAILY_LIMIT_ENABLED] = enabled
        }
    }

    suspend fun setDailyLimitMinutes(minutes: Int) {
        context.appSettingsDataStore.edit { preferences ->
            preferences[Keys.DAILY_LIMIT_MINUTES] = minutes.coerceIn(1, 60)
        }
    }

    private fun preferencesToSettings(preferences: Preferences): AppSettings {
        val blockMode =
            runCatching {
                BlockMode.valueOf(
                    preferences[Keys.BLOCK_MODE] ?: BlockMode.OVERLAY_AND_BACK.name,
                )
            }.getOrDefault(BlockMode.OVERLAY_AND_BACK)

        return AppSettings(
            masterEnabled = preferences[Keys.MASTER_ENABLED] ?: true,
            allowDmOpenedReelsOnly = preferences[Keys.ALLOW_DM_ONLY] ?: true,
            blockMode = blockMode,
            graceDurationSeconds = (preferences[Keys.GRACE_DURATION_SECONDS] ?: 30).coerceIn(5, 120),
            pauseUntilMillis = preferences[Keys.PAUSE_UNTIL_MILLIS] ?: 0L,
            debugMode = preferences[Keys.DEBUG_MODE] ?: false,
            overlayDismissible = preferences[Keys.OVERLAY_DISMISSIBLE] ?: false,
            dailyLimitEnabled = preferences[Keys.DAILY_LIMIT_ENABLED] ?: false,
            dailyLimitMinutes = (preferences[Keys.DAILY_LIMIT_MINUTES] ?: 10).coerceIn(1, 60),
        )
    }

    private object Keys {
        val MASTER_ENABLED = booleanPreferencesKey("master_enabled")
        val ALLOW_DM_ONLY = booleanPreferencesKey("allow_dm_only")
        val BLOCK_MODE = stringPreferencesKey("block_mode")
        val GRACE_DURATION_SECONDS = intPreferencesKey("grace_duration_seconds")
        val PAUSE_UNTIL_MILLIS = longPreferencesKey("pause_until_millis")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
        val OVERLAY_DISMISSIBLE = booleanPreferencesKey("overlay_dismissible")
        val DAILY_LIMIT_ENABLED = booleanPreferencesKey("daily_limit_enabled")
        val DAILY_LIMIT_MINUTES = intPreferencesKey("daily_limit_minutes")
    }
}
