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

    // Selection

    private val speaker = PairedDevice(name = "Speaker", address = "AA:BB:CC:DD:EE:02")

    private fun listing(vararg devices: PairedDevice): PairedDevicesViewModel =
        PairedDevicesViewModel(FakeBluetoothDeviceSource(devices = devices.toList()))
            .apply { refresh(hasPermission = true) }

    private val PairedDevicesViewModel.selection: Set<String>
        get() = (uiState.value as PairedDevicesUiState.Devices).selectedAddresses

    @Test
    fun `toggling selection publishes a new state`() {
        val viewModel = listing(headphones, speaker)

        viewModel.toggleSelection(headphones.address)

        assertEquals(setOf(headphones.address), viewModel.selection)
    }

    @Test
    fun `selecting all publishes every address`() {
        val viewModel = listing(headphones, speaker)

        viewModel.setAllSelected(true)

        assertEquals(setOf(headphones.address, speaker.address), viewModel.selection)
    }

    @Test
    fun `refresh clears the selection`() {
        val viewModel = listing(headphones, speaker)
        viewModel.setAllSelected(true)

        viewModel.refresh(hasPermission = true)

        assertEquals(emptySet<String>(), viewModel.selection)
    }

    @Test
    fun `toggling selection before a list is shown is ignored`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())

        viewModel.toggleSelection(headphones.address)

        assertEquals(PairedDevicesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `selecting all with no list shown is ignored`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())
        viewModel.refresh(hasPermission = false)

        viewModel.setAllSelected(true)

        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            viewModel.uiState.value,
        )
    }
}
