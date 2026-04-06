package com.example.instadmguard.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appSettingsDataStore by preferencesDataStore(name = "insta_dm_guard_settings")
