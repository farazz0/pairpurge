package com.pairpurge.app.whitelist

/**
 * Persists the addresses the user has whitelisted.
 *
 * Exists so the ViewModel can be tested on the JVM against a fake, without an
 * Android framework dependency — the same split as [com.pairpurge.app.bluetooth.BluetoothDeviceSource].
 */
interface WhitelistStore {

    fun addresses(): Set<String>

    fun add(addresses: Collection<String>)

    fun remove(address: String)
}
