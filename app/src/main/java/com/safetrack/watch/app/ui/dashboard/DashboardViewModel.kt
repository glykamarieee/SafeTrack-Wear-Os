package com.safetrack.watch.app.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safetrack.watch.domain.model.MonitoringSnapshot
import com.safetrack.watch.domain.model.SosActivation
import com.safetrack.watch.domain.repository.DeviceRepository
import com.safetrack.watch.domain.repository.MonitoringRepository
import com.safetrack.watch.services.sos.SosController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DashboardViewModel(
    devices: DeviceRepository,
    private val monitoring: MonitoringRepository,
    private val sos: SosController,
) : ViewModel() {

    val snapshot: StateFlow<MonitoringSnapshot> = monitoring.snapshot

    val childName: StateFlow<String> = devices.session
        .map { it?.childName?.substringBefore(' ')?.ifBlank { null } ?: "Child" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "Child")

    val watchId: StateFlow<String> = devices.session
        .map { it?.watchId.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private var ticker: Job? = null

    /** While visible: refresh battery/network and re-evaluate "last sync" every few seconds. */
    fun onVisible() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            monitoring.heartbeat(force = false)
            while (isActive) {
                monitoring.refreshDeviceStatus()
                delay(REFRESH_MS)
            }
        }
    }

    fun onHidden() {
        ticker?.cancel()
        ticker = null
    }

    fun holdCompleted() {
        sos.trigger(SosActivation.TAP_AND_HOLD)
    }

    private companion object {
        const val REFRESH_MS = 15_000L
    }
}
