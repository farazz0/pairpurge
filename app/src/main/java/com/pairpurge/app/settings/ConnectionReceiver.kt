package com.pairpurge.app.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bluetooth broadcasts let the timer reset even when the screen is closed. */
class ConnectionReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED &&
            action != BluetoothDevice.ACTION_ACL_DISCONNECTED &&
            action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        val store = DeletionSettingsStore(context)
        try {
            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    store.recordConnection(device.address, System.currentTimeMillis())
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> when (
                    intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                ) {
                    BluetoothDevice.BOND_NONE -> store.forget(device.address)
                    BluetoothDevice.BOND_BONDED -> store.recordConnection(device.address, System.currentTimeMillis())
                }
            }
        } catch (_: SecurityException) {
            // The Bluetooth permission may have been revoked.
        }
    }
}
