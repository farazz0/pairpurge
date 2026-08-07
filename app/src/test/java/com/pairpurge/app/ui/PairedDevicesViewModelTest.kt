package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private class FakeBluetoothDeviceSource(
    var status: BluetoothStatus = BluetoothStatus.READY,
    var devices: List<PairedDevice> = emptyList(),
    var throwOnRead: Boolean = false,
) : BluetoothDeviceSource {

    var wasRead: Boolean = false
        private set

    override fun status(): BluetoothStatus = status

    override fun bondedDevices(): List<PairedDevice> {
        wasRead = true
        if (throwOnRead) throw SecurityException("denied")
        return devices
    }
}

class PairedDevicesViewModelTest {

    private val headphones = PairedDevice(name = "Headphones", address = "AA:BB:CC:DD:EE:01")

    @Test
    fun `starts in loading`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())

        assertEquals(PairedDevicesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes bonded devices`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Devices(listOf(headphones)), viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes empty when there are no bonded devices`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `refresh reports a disabled adapter`() {
        val source = FakeBluetoothDeviceSource(status = BluetoothStatus.DISABLED)
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.BluetoothDisabled, viewModel.uiState.value)
    }

    @Test
    fun `refresh without permission does not touch the source`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = false, permanentlyDenied = true)

        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = true),
            viewModel.uiState.value,
        )
        assertFalse(source.wasRead)
    }

    @Test
    fun `a source that throws does not crash the refresh`() {
        val source = FakeBluetoothDeviceSource(throwOnRead = true)
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }
}
