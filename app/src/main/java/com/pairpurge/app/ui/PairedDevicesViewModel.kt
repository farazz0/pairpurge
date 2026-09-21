package com.pairpurge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.SystemBluetoothDeviceSource
import com.pairpurge.app.whitelist.SharedPreferencesWhitelistStore
import com.pairpurge.app.whitelist.WhitelistStore
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
    private val whitelist: WhitelistStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairedDevicesUiState>(PairedDevicesUiState.Loading)
    val uiState: StateFlow<PairedDevicesUiState> = _uiState.asStateFlow()

    // Held here rather than only in the state, because refresh() rebuilds that state
    // from scratch and would otherwise throw the user's choice away on every reload.
    private var sortOrder = DeviceSortOrder.Name

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
            whitelistedAddresses = whitelist.addresses(),
            sortOrder = sortOrder,
        )
    }

    /** Ticks or unticks one row. */
    fun toggleSelection(address: String) = updateDevices { it.toggled(address) }

    /** Ticks or unticks every row at once. */
    fun setAllSelected(selected: Boolean) = updateDevices { it.withAllSelected(selected) }

    /** Drops the whole selection, which is what the header's Clear action does. */
    fun clearSelection() = updateDevices { it.copy(selectedAddresses = emptySet()) }

    /** Flips the sort pill between the two orders the list can be read in. */
    fun toggleSortOrder() {
        sortOrder = when (sortOrder) {
            DeviceSortOrder.Name -> DeviceSortOrder.Address
            DeviceSortOrder.Address -> DeviceSortOrder.Name
        }
        updateDevices { it.copy(sortOrder = sortOrder) }
    }

    /**
     * Requests an unpair and removes the device from the visible list when Android
     * accepts it. A manual refresh restores the row if the asynchronous removal later
     * fails at the platform level.
     */
    fun unpair(address: String): Boolean {
        val current = _uiState.value as? PairedDevicesUiState.Devices ?: return false
        if (current.devices.none { it.address == address }) return false

        val accepted = try {
            source.unpair(address)
        } catch (_: SecurityException) {
            false
        }
        if (!accepted) return false

        val remainingDevices = current.devices.filterNot { it.address == address }
        _uiState.value = if (remainingDevices.isEmpty()) {
            PairedDevicesUiState.Empty
        } else {
            current.copy(
                devices = remainingDevices,
                selectedAddresses = current.selectedAddresses - address,
            )
        }
        return true
    }

    /**
     * Unpairs every selected device and returns how many requests Android rejected.
     * Rejected devices stay listed and stay selected so the user can retry them.
     */
    fun unpairSelected(): Int {
        val current = _uiState.value as? PairedDevicesUiState.Devices ?: return 0
        if (current.selectedAddresses.isEmpty()) return 0

        val failed = current.selectedAddresses.filterNotTo(mutableSetOf()) { address ->
            try {
                source.unpair(address)
            } catch (_: SecurityException) {
                false
            }
        }

        val remainingDevices = current.devices.filter {
            it.address !in current.selectedAddresses || it.address in failed
        }
        _uiState.value = if (remainingDevices.isEmpty()) {
            PairedDevicesUiState.Empty
        } else {
            current.copy(devices = remainingDevices, selectedAddresses = failed)
        }
        return failed.size
    }

    /** Moves the selected devices to the whitelist and persists them. */
    fun whitelistSelected() {
        val current = _uiState.value as? PairedDevicesUiState.Devices ?: return
        if (current.selectedAddresses.isEmpty()) return

        whitelist.add(current.selectedAddresses)
        _uiState.value = current.copy(
            whitelistedAddresses = current.whitelistedAddresses + current.selectedAddresses,
            selectedAddresses = emptySet(),
        )
    }

    /** Returns one device from the whitelist to the main list and persists the removal. */
    fun removeFromWhitelist(address: String) {
        whitelist.remove(address)
        updateDevices { it.copy(whitelistedAddresses = it.whitelistedAddresses - address) }
    }

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
                PairedDevicesViewModel(
                    source = SystemBluetoothDeviceSource(application),
                    whitelist = SharedPreferencesWhitelistStore(application),
                )
            }
        }
    }
}
