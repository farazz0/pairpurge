package com.pairpurge.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedDeviceTest {

    @Test
    fun `displayName uses the reported name`() {
        val device = PairedDevice(name = "Pixel Buds Pro", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Pixel Buds Pro", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is null`() {
        val device = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is empty`() {
        val device = PairedDevice(name = "", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is only whitespace`() {
        val device = PairedDevice(name = "   ", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `hasName is true only for a usable name`() {
        assertTrue(PairedDevice(name = "Car", address = "AA:BB:CC:DD:EE:01").hasName)
        assertFalse(PairedDevice(name = null, address = "AA:BB:CC:DD:EE:02").hasName)
        assertFalse(PairedDevice(name = "  ", address = "AA:BB:CC:DD:EE:03").hasName)
    }
}
