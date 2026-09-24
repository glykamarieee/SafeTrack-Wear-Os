package com.safetrack.watch.app.ui.connect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safetrack.watch.domain.model.Outcome
import com.safetrack.watch.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val watchId: String = "",
    val verifying: Boolean = false,
    val error: String? = null,
) {
    companion object {
        const val CODE_LENGTH = 6
    }
}

class ConnectViewModel(private val devices: DeviceRepository) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    /**
     * The typed code. Compose state, not a StateFlow: text-field input must be
     * applied synchronously, or keystrokes arriving before the next frame are
     * lost and the watch keyboard falls out of sync with the field.
     */
    var code by mutableStateOf("")
        private set

    init {
        viewModelScope.launch {
            _state.update { it.copy(watchId = devices.watchId()) }
        }
        // Explains why the watch is back on this screen after SafeTrack unlinked it.
        viewModelScope.launch {
            devices.unlinkNotice.filterNotNull().collect { notice -> _state.update { it.copy(error = notice) } }
        }
    }

    /** Input from the watch keyboard: digits only, at most six. */
    fun onCodeChange(input: String) {
        if (_state.value.verifying) return
        val digits = input.filter(Char::isDigit).take(ConnectUiState.CODE_LENGTH)
        if (digits != code) {
            code = digits
            _state.update { it.copy(error = null) }
        }
    }

    fun connect() {
        if (_state.value.verifying) return
        if (code.length != ConnectUiState.CODE_LENGTH) {
            _state.update { it.copy(error = "Enter all 6 digits of the connection code.") }
            return
        }
        val entered = code
        _state.update { it.copy(verifying = true, error = null) }
        devices.clearUnlinkNotice()

        viewModelScope.launch {
            val result = devices.pair(entered)
            code = ""
            when (result) {
                // The root screen switches to "Connected" when the session appears.
                is Outcome.Ok -> _state.update { it.copy(verifying = false) }
                is Outcome.Failed -> _state.update { it.copy(verifying = false, error = result.error.message) }
            }
        }
    }
}
