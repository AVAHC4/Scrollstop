package com.example.instadmguard.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.instadmguard.InstaDMGuardApp
import com.example.instadmguard.data.AppSettings
import com.example.instadmguard.model.BlockMode
import com.example.instadmguard.model.DebugLogEntry
import com.example.instadmguard.model.ServiceStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as InstaDMGuardApp).container

    val uiState: StateFlow<MainUiState> =
        combine(
            container.settingsRepository.settings,
            container.serviceStateStore.status,
            container.debugLogRepository.entries,
        ) { settings, serviceStatus, debugLogs ->
            MainUiState(
                settings = settings,
                serviceStatus = serviceStatus,
                debugLogs = debugLogs,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = MainUiState(),
        )

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setMasterEnabled(enabled)
        }
    }

    fun setAllowDmOpenedReelsOnly(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setAllowDmOpenedReelsOnly(enabled)
        }
    }

    fun setBlockMode(blockMode: BlockMode) {
        viewModelScope.launch {
            container.settingsRepository.setBlockMode(blockMode)
        }
    }

    fun setGraceDurationSeconds(seconds: Int) {
        viewModelScope.launch {
            container.settingsRepository.setGraceDurationSeconds(seconds)
        }
    }

    fun pauseForMinutes(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.pauseForMinutes(minutes)
        }
    }

    fun resumeProtection() {
        viewModelScope.launch {
            container.settingsRepository.clearPause()
        }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setDebugMode(enabled)
        }
    }

    fun setOverlayDismissible(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setOverlayDismissible(enabled)
        }
    }

    fun setDailyLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setDailyLimitEnabled(enabled)
        }
    }

    fun setDailyLimitMinutes(minutes: Int) {
        viewModelScope.launch {
            container.settingsRepository.setDailyLimitMinutes(minutes)
        }
    }

    fun clearDebugLogs() {
        container.debugLogRepository.clear()
    }
}

data class MainUiState(
    val settings: AppSettings = AppSettings(),
    val serviceStatus: ServiceStatus = ServiceStatus(),
    val debugLogs: List<DebugLogEntry> = emptyList(),
)
