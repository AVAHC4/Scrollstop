package com.example.instadmguard.data

import com.example.instadmguard.model.DebugLogEntry
import com.example.instadmguard.model.InstagramScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DebugLogRepository {

    private val _entries = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val entries: StateFlow<List<DebugLogEntry>> = _entries.asStateFlow()

    fun add(screen: InstagramScreen, message: String) {
        val entry = DebugLogEntry(
            timestampMillis = System.currentTimeMillis(),
            screen = screen,
            message = message,
        )
        _entries.update { existing ->
            (listOf(entry) + existing).take(MAX_ENTRIES)
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    private companion object {
        const val MAX_ENTRIES = 20
    }
}
