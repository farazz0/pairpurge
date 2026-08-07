package com.pairpurge.app.whitelist

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Whitelist backed by a SharedPreferences string set.
 *
 * Addresses are stable device identity, survive the device renaming itself, and a
 * whitelisted address that is unpaired later stays stored — so re-pairing the same
 * device brings it back already whitelisted.
 */
class SharedPreferencesWhitelistStore(context: Context) : WhitelistStore {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun addresses(): Set<String> =
        preferences.getStringSet(KEY_ADDRESSES, emptySet()).orEmpty()

    override fun add(addresses: Collection<String>) {
        if (addresses.isEmpty()) return
        preferences.edit { putStringSet(KEY_ADDRESSES, addresses() + addresses) }
    }

    override fun remove(address: String) {
        preferences.edit { putStringSet(KEY_ADDRESSES, addresses() - address) }
    }

    private companion object {
        const val PREFERENCES_NAME = "whitelist"
        const val KEY_ADDRESSES = "addresses"
    }
}
