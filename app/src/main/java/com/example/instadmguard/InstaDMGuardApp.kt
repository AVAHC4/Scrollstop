package com.example.instadmguard

import android.app.Application
import android.content.Context
import com.example.instadmguard.data.DebugLogRepository
import com.example.instadmguard.data.SettingsRepository
import com.example.instadmguard.service.ServiceStateStore
import com.example.instadmguard.service.SessionStateManager

class InstaDMGuardApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(applicationContext)
    }
}

class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context)
    val debugLogRepository = DebugLogRepository()
    val serviceStateStore = ServiceStateStore()
    val sessionStateManager = SessionStateManager()
}
