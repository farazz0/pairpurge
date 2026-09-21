package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import com.pairpurge.app.whitelist.WhitelistStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeBluetoothDeviceSource(
    var status: BluetoothStatus = BluetoothStatus.READY,
    var devices: List<PairedDevice> = emptyList(),
    var throwOnRead: Boolean = false,
    var unpairResult: Boolean = true,
    var failUnpairFor: Set<String> = emptySet(),
) : BluetoothDeviceSource {

    var wasRead: Boolean = false
        private set

    val unpairAttempts = mutableListOf<String>()

    override fun status(): BluetoothStatus = status

    override fun bondedDevices(): List<PairedDevice> {
        wasRead = true
        if (throwOnRead) throw SecurityException("denied")
        return devices
    }

    override fun unpair(address: String): Boolean {
        unpairAttempts += address
        return unpairResult && address !in failUnpairFor
    }
}

private class InMemoryWhitelistStore(
    initial: Set<String> = emptySet(),
) : WhitelistStore {

    private val stored = initial.toMutableSet()

    override fun addresses(): Set<String> = stored.toSet()

    override fun add(addresses: Collection<String>) {
        stored += addresses
    }

    override fun remove(address: String) {
        stored -= address
    }
}

private fun viewModel(
    source: BluetoothDeviceSource = FakeBluetoothDeviceSource(),
    whitelist: WhitelistStore = InMemoryWhitelistStore(),
) = PairedDevicesViewModel(source, whitelist)

class PairedDevicesViewModelTest {

    private val headphones = PairedDevice(name = "Headphones", address = "AA:BB:CC:DD:EE:01")

    @Test
    fun `starts in loading`() {
        val viewModel = viewModel()

        assertEquals(PairedDevicesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes bonded devices`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = viewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Devices(listOf(headphones)), viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes empty when there are no bonded devices`() {
        val viewModel = viewModel()

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `refresh reports a disabled adapter`() {
        val source = FakeBluetoothDeviceSource(status = BluetoothStatus.DISABLED)
        val viewModel = viewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.BluetoothDisabled, viewModel.uiState.value)
    }

    @Test
    fun `refresh without permission does not touch the source`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = viewModel(source)

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
        val viewModel = viewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    // Selection

    private val speaker = PairedDevice(name = "Speaker", address = "AA:BB:CC:DD:EE:02")

    private fun listing(vararg devices: PairedDevice): PairedDevicesViewModel =
        viewModel(FakeBluetoothDeviceSource(devices = devices.toList()))
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
        val viewModel = viewModel()

        viewModel.toggleSelection(headphones.address)

        assertEquals(PairedDevicesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `selecting all with no list shown is ignored`() {
        val viewModel = viewModel()
        viewModel.refresh(hasPermission = false)

        viewModel.setAllSelected(true)

        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            viewModel.uiState.value,
        )
    }

    // Unpairing

    @Test
    fun `accepted unpair removes the device and its selection`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones, speaker))
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)
        viewModel.toggleSelection(headphones.address)

        assertTrue(viewModel.unpair(headphones.address))

        assertEquals(listOf(headphones.address), source.unpairAttempts)
        assertEquals(
            PairedDevicesUiState.Devices(devices = listOf(speaker)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `accepted unpair of the last device publishes empty`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)

        assertTrue(viewModel.unpair(headphones.address))

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `rejected unpair leaves the list unchanged`() {
        val source = FakeBluetoothDeviceSource(
            devices = listOf(headphones),
            unpairResult = false,
        )
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)

        assertFalse(viewModel.unpair(headphones.address))

        assertEquals(
            PairedDevicesUiState.Devices(devices = listOf(headphones)),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `unpairing an unknown device does not call the source`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)

        assertFalse(viewModel.unpair(speaker.address))

        assertEquals(emptyList<String>(), source.unpairAttempts)
    }

    // Whitelist

    private val PairedDevicesViewModel.listState: PairedDevicesUiState.Devices
        get() = uiState.value as PairedDevicesUiState.Devices

    @Test
    fun `refresh loads the persisted whitelist`() {
        val store = InMemoryWhitelistStore(initial = setOf(speaker.address))
        val viewModel = viewModel(
            FakeBluetoothDeviceSource(devices = listOf(headphones, speaker)),
            store,
        )

        viewModel.refresh(hasPermission = true)

        assertEquals(listOf(headphones), viewModel.listState.mainDevices)
        assertEquals(listOf(speaker), viewModel.listState.whitelistedDevices)
    }

    @Test
    fun `whitelisting the selection moves the devices and persists them`() {
        val store = InMemoryWhitelistStore()
        val viewModel = viewModel(
            FakeBluetoothDeviceSource(devices = listOf(headphones, speaker)),
            store,
        )
        viewModel.refresh(hasPermission = true)
        viewModel.toggleSelection(headphones.address)

        viewModel.whitelistSelected()

        assertEquals(listOf(speaker), viewModel.listState.mainDevices)
        assertEquals(listOf(headphones), viewModel.listState.whitelistedDevices)
        assertEquals(setOf(headphones.address), store.addresses())
    }

    @Test
    fun `whitelisting the selection clears the selection`() {
        val viewModel = listing(headphones, speaker)
        viewModel.setAllSelected(true)

        viewModel.whitelistSelected()

        assertEquals(emptySet<String>(), viewModel.selection)
    }

    @Test
    fun `whitelisting with nothing selected changes nothing`() {
        val store = InMemoryWhitelistStore()
        val viewModel = viewModel(
            FakeBluetoothDeviceSource(devices = listOf(headphones)),
            store,
        )
        viewModel.refresh(hasPermission = true)

        viewModel.whitelistSelected()

        assertEquals(listOf(headphones), viewModel.listState.mainDevices)
        assertEquals(emptySet<String>(), store.addresses())
    }

    @Test
    fun `removing from the whitelist returns the device and persists the removal`() {
        val store = InMemoryWhitelistStore(initial = setOf(headphones.address))
        val viewModel = viewModel(
            FakeBluetoothDeviceSource(devices = listOf(headphones)),
            store,
        )
        viewModel.refresh(hasPermission = true)

        viewModel.removeFromWhitelist(headphones.address)

        assertEquals(listOf(headphones), viewModel.listState.mainDevices)
        assertEquals(emptyList<PairedDevice>(), viewModel.listState.whitelistedDevices)
        assertEquals(emptySet<String>(), store.addresses())
    }

    // Bulk unpairing

    @Test
    fun `unpair selected unpairs every selected device`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones, speaker))
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)
        viewModel.setAllSelected(true)

        val failures = viewModel.unpairSelected()

        assertEquals(0, failures)
        assertEquals(
            setOf(headphones.address, speaker.address),
            source.unpairAttempts.toSet(),
        )
        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `unpair selected keeps devices whose unpair was rejected`() {
        val source = FakeBluetoothDeviceSource(
            devices = listOf(headphones, speaker),
            failUnpairFor = setOf(speaker.address),
        )
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)
        viewModel.setAllSelected(true)

        val failures = viewModel.unpairSelected()

        assertEquals(1, failures)
        assertEquals(listOf(speaker), viewModel.listState.mainDevices)
        assertEquals(setOf(speaker.address), viewModel.selection)
    }

    @Test
    fun `unpair selected with nothing selected does not call the source`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = viewModel(source)
        viewModel.refresh(hasPermission = true)

        val failures = viewModel.unpairSelected()

        assertEquals(0, failures)
        assertEquals(emptyList<String>(), source.unpairAttempts)
    }

    @Test
    fun `unpairing every main device keeps whitelisted devices listed`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones, speaker))
        val viewModel = viewModel(source, InMemoryWhitelistStore(initial = setOf(speaker.address)))
        viewModel.refresh(hasPermission = true)
        viewModel.setAllSelected(true)

        viewModel.unpairSelected()

        assertEquals(emptyList<PairedDevice>(), viewModel.listState.mainDevices)
        assertEquals(listOf(speaker), viewModel.listState.whitelistedDevices)
    }

    @Test
    fun `clearing the selection drops every ticked row`() {
        val viewModel = viewModel(FakeBluetoothDeviceSource(devices = listOf(headphones, speaker)))
        viewModel.refresh(hasPermission = true)
        viewModel.setAllSelected(true)

        viewModel.clearSelection()

        assertEquals(emptySet<String>(), viewModel.listState.selectedAddresses)
    }

    @Test
    fun `the sort pill flips between the two orders`() {
        val viewModel = viewModel(FakeBluetoothDeviceSource(devices = listOf(headphones)))
        viewModel.refresh(hasPermission = true)

        viewModel.toggleSortOrder()
        assertEquals(DeviceSortOrder.Address, viewModel.listState.sortOrder)

        viewModel.toggleSortOrder()
        assertEquals(DeviceSortOrder.Name, viewModel.listState.sortOrder)
    }

    @Test
    fun `the chosen sort order survives a refresh`() {
        val viewModel = viewModel(FakeBluetoothDeviceSource(devices = listOf(headphones)))
        viewModel.refresh(hasPermission = true)
        viewModel.toggleSortOrder()

        viewModel.refresh(hasPermission = true)

        assertEquals(DeviceSortOrder.Address, viewModel.listState.sortOrder)
    }
}
