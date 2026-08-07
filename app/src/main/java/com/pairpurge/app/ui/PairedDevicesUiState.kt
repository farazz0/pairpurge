package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice

/** Everything the paired-devices screen can show. */
sealed interface PairedDevicesUiState {

    /** Before the first read. Never returned by [derivePairedDevicesState]. */
    data object Loading : PairedDevicesUiState

    data class NeedsPermission(val permanentlyDenied: Boolean) : PairedDevicesUiState

    data object BluetoothUnsupported : PairedDevicesUiState

    data object BluetoothDisabled : PairedDevicesUiState

    data object Empty : PairedDevicesUiState

    /**
     * The device list, plus which rows the user has ticked.
     *
     * Selection is held as addresses rather than [PairedDevice] values because the
     * address is the stable identity — a device that renames itself between refreshes
     * is still the same device.
     */
    data class Devices(
        val devices: List<PairedDevice>,
        val selectedAddresses: Set<String> = emptySet(),
    ) : PairedDevicesUiState {

        /** False for an empty list: "all of nothing" would tick the select-all box. */
        val allSelected: Boolean
            get() = devices.isNotEmpty() && selectedAddresses.size == devices.size

        /** Ticks or unticks one row. Unknown addresses are ignored, never added. */
        fun toggled(address: String): Devices = when {
            devices.none { it.address == address } -> this
            address in selectedAddresses -> copy(selectedAddresses = selectedAddresses - address)
            else -> copy(selectedAddresses = selectedAddresses + address)
        }

        fun withAllSelected(selected: Boolean): Devices = copy(
            selectedAddresses = if (selected) devices.mapTo(mutableSetOf()) { it.address } else emptySet(),
        )
    }
}

/**
 * Decides what the screen shows. Pure, so every state is unit-testable without a device.
 *
 * Permission is checked before adapter status because the adapter cannot be read
 * reliably without it — any other answer would be reporting on an untrustworthy read.
 */
fun derivePairedDevicesState(
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    status: BluetoothStatus,
    devices: List<PairedDevice>,
): PairedDevicesUiState = when {
    !hasPermission -> PairedDevicesUiState.NeedsPermission(permanentlyDenied)
    status == BluetoothStatus.UNSUPPORTED -> PairedDevicesUiState.BluetoothUnsupported
    status == BluetoothStatus.DISABLED -> PairedDevicesUiState.BluetoothDisabled
    devices.isEmpty() -> PairedDevicesUiState.Empty
    else -> PairedDevicesUiState.Devices(devices.sortedForDisplay())
}

/**
 * Named devices first (case-insensitive), unnamed last, ties broken by address.
 *
 * `bondedDevices` returns an unordered Set, so without this the list can reshuffle
 * between refreshes — which would make comparing it against system settings guesswork.
 */
internal fun List<PairedDevice>.sortedForDisplay(): List<PairedDevice> =
    sortedWith(
        compareBy<PairedDevice> { !it.hasName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy { it.address },
    )
