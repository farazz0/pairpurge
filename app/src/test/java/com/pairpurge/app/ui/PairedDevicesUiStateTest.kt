package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import org.junit.Assert.assertEquals
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
    ) = derivePairedDevicesState(hasPermission, permanentlyDenied, status, devices)

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
}
