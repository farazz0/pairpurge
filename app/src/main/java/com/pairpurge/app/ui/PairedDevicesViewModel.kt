package com.pairpurge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.SystemBluetoothDeviceSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the paired-devices screen state.
 *
 * Permission handling stays in the Compose layer, which owns the Activity that
 * `shouldShowRequestPermissionRationale` needs, so this class takes `hasPermission`
 * as a parameter and never touches the framework.
 */
class PairedDevicesViewModel(
    private val source: BluetoothDeviceSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairedDevicesUiState>(PairedDevicesUiState.Loading)
    val uiState: StateFlow<PairedDevicesUiState> = _uiState.asStateFlow()

    /** Re-reads Bluetooth state. Cheap, synchronous, local — no coroutine needed. */
    fun refresh(hasPermission: Boolean, permanentlyDenied: Boolean = false) {
        if (!hasPermission) {
            _uiState.value = PairedDevicesUiState.NeedsPermission(permanentlyDenied)
            return
        }

        val status = source.status()
        // Defence in depth: SystemBluetoothDeviceSource already swallows this, but a
        // source that does not must still not take the app down.
        val devices = try {
            source.bondedDevices()
        } catch (_: SecurityException) {
            emptyList()
        }

        _uiState.value = derivePairedDevicesState(
            hasPermission = true,
            permanentlyDenied = false,
            status = status,
            devices = devices,
        )
    }

    /** Ticks or unticks one row. */
    fun toggleSelection(address: String) = updateDevices { it.toggled(address) }

    /** Ticks or unticks every row at once. */
    fun setAllSelected(selected: Boolean) = updateDevices { it.withAllSelected(selected) }

    /**
     * Applies a selection change, but only while a list is on screen.
     *
     * Selection is meaningless in every other state, and a stray tap arriving during a
     * state change must not resurrect a stale list.
     */
    private inline fun updateDevices(
        transform: (PairedDevicesUiState.Devices) -> PairedDevicesUiState.Devices,
    ) {
        val current = _uiState.value
        if (current is PairedDevicesUiState.Devices) _uiState.value = transform(current)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                PairedDevicesViewModel(SystemBluetoothDeviceSource(application))
            }
        }
    }
}
