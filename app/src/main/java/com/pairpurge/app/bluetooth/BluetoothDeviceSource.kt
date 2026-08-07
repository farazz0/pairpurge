package com.pairpurge.app.bluetooth

/**
 * Reads Bluetooth state and bonded devices.
 *
 * Exists so the ViewModel can be tested on the JVM against a fake, without an
 * Android framework dependency.
 */
interface BluetoothDeviceSource {

    fun status(): BluetoothStatus

    /**
     * Bonded devices, or an empty list if they cannot be read. Callers must hold
     * [android.Manifest.permission.BLUETOOTH_CONNECT]; implementations must not throw.
     */
    fun bondedDevices(): List<PairedDevice>
}
