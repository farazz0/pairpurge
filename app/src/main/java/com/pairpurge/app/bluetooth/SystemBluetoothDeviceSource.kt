package com.pairpurge.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context

/** Reads real Bluetooth state from the platform. */
class SystemBluetoothDeviceSource(context: Context) : BluetoothDeviceSource {

    private val appContext = context.applicationContext

    private val adapter
        get() = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    override fun status(): BluetoothStatus {
        val adapter = adapter ?: return BluetoothStatus.UNSUPPORTED
        return if (adapter.isEnabled) BluetoothStatus.READY else BluetoothStatus.DISABLED
    }

    // The permission is checked at the call site before every refresh. Lint cannot see
    // that, and the platform can still throw, so the SecurityException catch below is
    // the real guard — without it an unlucky race crashes the app.
    @SuppressLint("MissingPermission")
    override fun bondedDevices(): List<PairedDevice> = try {
        adapter?.bondedDevices.orEmpty().map { device ->
            PairedDevice(name = device.name, address = device.address)
        }
    } catch (_: SecurityException) {
        emptyList()
    }

    /** Null means connection state could not be verified, so automatic cleanup must skip it. */
    @SuppressLint("MissingPermission", "PrivateApi")
    fun isConnected(address: String): Boolean? = try {
        val device = adapter?.getRemoteDevice(address)
        device?.javaClass?.getMethod("isConnected")?.invoke(device) as? Boolean
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    /**
     * Android does not expose bond removal in its public SDK. The hidden method is
     * used deliberately here so PairPurge can perform its core action without sending
     * the user to Settings. Failure is expected on devices that restrict this API.
     */
    @SuppressLint("MissingPermission", "PrivateApi")
    override fun unpair(address: String): Boolean = try {
        val device = adapter?.getRemoteDevice(address) ?: return false
        val removeBond = device.javaClass.getMethod("removeBond")
        removeBond.invoke(device) == true
    } catch (_: ReflectiveOperationException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
