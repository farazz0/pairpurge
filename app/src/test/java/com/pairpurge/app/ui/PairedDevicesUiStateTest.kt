package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedDevicesUiStateTest {

    private val headphones = PairedDevice(name = "Headphones", address = "AA:BB:CC:DD:EE:01")
    private val car = PairedDevice(name = "car stereo", address = "AA:BB:CC:DD:EE:02")
    private val unnamed = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:03")

    private fun derive(
        hasPermission: Boolean = true,
        permanentlyDenied: Boolean = false,
        status: BluetoothStatus = BluetoothStatus.READY,
        devices: List<PairedDevice> = emptyList(),
        whitelistedAddresses: Set<String> = emptySet(),
        sortOrder: DeviceSortOrder = DeviceSortOrder.Name,
    ) = derivePairedDevicesState(
        hasPermission,
        permanentlyDenied,
        status,
        devices,
        whitelistedAddresses,
        sortOrder,
    )

    @Test
    fun `missing permission asks for permission`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            derive(hasPermission = false),
        )
    }

    @Test
    fun `permanent denial is carried into the state`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = true),
            derive(hasPermission = false, permanentlyDenied = true),
        )
    }

    @Test
    fun `missing permission wins over a disabled adapter`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            derive(hasPermission = false, status = BluetoothStatus.DISABLED),
        )
    }

    @Test
    fun `no adapter reports unsupported`() {
        assertEquals(
            PairedDevicesUiState.BluetoothUnsupported,
            derive(status = BluetoothStatus.UNSUPPORTED),
        )
    }

    @Test
    fun `disabled adapter reports disabled`() {
        assertEquals(
            PairedDevicesUiState.BluetoothDisabled,
            derive(status = BluetoothStatus.DISABLED),
        )
    }

    @Test
    fun `no bonded devices reports empty`() {
        assertEquals(PairedDevicesUiState.Empty, derive(devices = emptyList()))
    }

    @Test
    fun `bonded devices are listed`() {
        val state = derive(devices = listOf(headphones))

        assertEquals(PairedDevicesUiState.Devices(listOf(headphones)), state)
    }

    @Test
    fun `device count matches the list size`() {
        val state = derive(devices = listOf(headphones, car, unnamed)) as PairedDevicesUiState.Devices

        assertEquals(3, state.devices.size)
    }

    @Test
    fun `devices sort case-insensitively by name with unnamed devices last`() {
        val state = derive(devices = listOf(unnamed, headphones, car)) as PairedDevicesUiState.Devices

        assertEquals(listOf(car, headphones, unnamed), state.devices)
    }

    @Test
    fun `unnamed devices are ordered by address`() {
        val second = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:09")
        val first = PairedDevice(name = "", address = "AA:BB:CC:DD:EE:04")

        val state = derive(devices = listOf(second, first)) as PairedDevicesUiState.Devices

        assertEquals(listOf(first, second), state.devices)
    }

    @Test
    fun `devices with the same name are ordered by address`() {
        val second = PairedDevice(name = "Speaker", address = "AA:BB:CC:DD:EE:22")
        val first = PairedDevice(name = "speaker", address = "AA:BB:CC:DD:EE:11")

        val state = derive(devices = listOf(second, first)) as PairedDevicesUiState.Devices

        assertEquals(listOf(first, second), state.devices)
    }

    // Selection

    private fun listed(vararg devices: PairedDevice) =
        derive(devices = devices.toList()) as PairedDevicesUiState.Devices

    @Test
    fun `a freshly derived list has nothing selected`() {
        val state = listed(headphones, car)

        assertEquals(emptySet<String>(), state.selectedAddresses)
        assertFalse(state.allSelected)
    }

    @Test
    fun `toggling a device selects it`() {
        val state = listed(headphones, car).toggled(headphones.address)

        assertEquals(setOf(headphones.address), state.selectedAddresses)
    }

    @Test
    fun `toggling a selected device deselects it`() {
        val state = listed(headphones, car)
            .toggled(headphones.address)
            .toggled(headphones.address)

        assertEquals(emptySet<String>(), state.selectedAddresses)
    }

    @Test
    fun `toggling leaves other selections alone`() {
        val state = listed(headphones, car, unnamed)
            .toggled(headphones.address)
            .toggled(car.address)
            .toggled(headphones.address)

        assertEquals(setOf(car.address), state.selectedAddresses)
    }

    @Test
    fun `toggling an address that is not listed changes nothing`() {
        val state = listed(headphones).toggled("AA:BB:CC:DD:EE:FF")

        assertEquals(emptySet<String>(), state.selectedAddresses)
    }

    @Test
    fun `selecting all selects every listed device`() {
        val state = listed(headphones, car, unnamed).withAllSelected(true)

        assertEquals(
            setOf(headphones.address, car.address, unnamed.address),
            state.selectedAddresses,
        )
        assertTrue(state.allSelected)
    }

    @Test
    fun `deselecting all clears the selection`() {
        val state = listed(headphones, car).withAllSelected(true).withAllSelected(false)

        assertEquals(emptySet<String>(), state.selectedAddresses)
        assertFalse(state.allSelected)
    }

    @Test
    fun `all selected is false while only some are selected`() {
        val state = listed(headphones, car).toggled(headphones.address)

        assertFalse(state.allSelected)
    }

    @Test
    fun `toggling the last unselected device makes all selected true`() {
        val state = listed(headphones, car)
            .toggled(headphones.address)
            .toggled(car.address)

        assertTrue(state.allSelected)
    }

    // Whitelist

    @Test
    fun `whitelisted devices are split out of the main list`() {
        val state = derive(
            devices = listOf(headphones, car),
            whitelistedAddresses = setOf(car.address),
        ) as PairedDevicesUiState.Devices

        assertEquals(listOf(headphones), state.mainDevices)
        assertEquals(listOf(car), state.whitelistedDevices)
    }

    @Test
    fun `whitelisting every device keeps the list state`() {
        val state = derive(
            devices = listOf(headphones),
            whitelistedAddresses = setOf(headphones.address),
        ) as PairedDevicesUiState.Devices

        assertEquals(emptyList<PairedDevice>(), state.mainDevices)
        assertEquals(listOf(headphones), state.whitelistedDevices)
    }

    @Test
    fun `whitelist entries for devices that are no longer bonded are not listed`() {
        val state = derive(
            devices = listOf(headphones),
            whitelistedAddresses = setOf("AA:BB:CC:DD:EE:FF"),
        ) as PairedDevicesUiState.Devices

        assertEquals(listOf(headphones), state.mainDevices)
        assertEquals(emptyList<PairedDevice>(), state.whitelistedDevices)
    }

    @Test
    fun `selecting all selects only main-list devices`() {
        val state = derive(
            devices = listOf(headphones, car),
            whitelistedAddresses = setOf(car.address),
        ) as PairedDevicesUiState.Devices

        assertEquals(setOf(headphones.address), state.withAllSelected(true).selectedAddresses)
    }

    @Test
    fun `toggling a whitelisted device changes nothing`() {
        val state = derive(
            devices = listOf(headphones, car),
            whitelistedAddresses = setOf(car.address),
        ) as PairedDevicesUiState.Devices

        assertEquals(emptySet<String>(), state.toggled(car.address).selectedAddresses)
    }

    @Test
    fun `all selected ignores whitelisted devices`() {
        val state = derive(
            devices = listOf(headphones, car),
            whitelistedAddresses = setOf(car.address),
        ) as PairedDevicesUiState.Devices

        assertTrue(state.toggled(headphones.address).allSelected)
    }

    @Test
    fun `all selected is false when every device is whitelisted`() {
        val state = derive(
            devices = listOf(headphones),
            whitelistedAddresses = setOf(headphones.address),
        ) as PairedDevicesUiState.Devices

        assertFalse(state.allSelected)
    }

    @Test
    fun `sorting by name puts named devices first and unnamed last`() {
        val state = derive(devices = listOf(unnamed, headphones, car)) as PairedDevicesUiState.Devices

        assertEquals(listOf(car, headphones, unnamed), state.mainDevices)
    }

    @Test
    fun `sorting by address ignores the name order entirely`() {
        val state = derive(
            devices = listOf(unnamed, headphones, car),
            sortOrder = DeviceSortOrder.Address,
        ) as PairedDevicesUiState.Devices

        assertEquals(listOf(headphones, car, unnamed), state.mainDevices)
    }

    @Test
    fun `the protected list stays in name order whatever the sort pill says`() {
        val everything = setOf(headphones.address, car.address, unnamed.address)
        val state = derive(
            devices = listOf(unnamed, headphones, car),
            whitelistedAddresses = everything,
            sortOrder = DeviceSortOrder.Address,
        ) as PairedDevicesUiState.Devices

        assertEquals(listOf(car, headphones, unnamed), state.whitelistedDevices)
    }
}
