package com.example.instadmguard.service

import com.example.instadmguard.model.ServiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ServiceStateStore {

    private val _status = MutableStateFlow(ServiceStatus())
    val status: StateFlow<ServiceStatus> = _status.asStateFlow()

    fun update(transform: (ServiceStatus) -> ServiceStatus) {
        _status.update(transform)
    }

    fun reset() {
        _status.value = ServiceStatus()
    }
}
