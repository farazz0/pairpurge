package com.pairpurge.app.settings

import android.content.Context
import androidx.core.content.edit

class DeletionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("automatic_deletion", Context.MODE_PRIVATE)

    fun policy() = DeletionPolicy(preferences.getInt("days", 0).takeIf { it in 1..3650 })

    fun save(policy: DeletionPolicy) {
        preferences.edit { putInt("days", policy.days ?: 0) }
    }

    fun recordConnection(address: String, now: Long) {
        preferences.edit { putLong("seen_$address", now) }
    }

    fun forget(address: String) {
        preferences.edit { remove("seen_$address") }
    }

    /** Keep timestamps until Android actually removes the bond, including failed requests. */
    fun observe(addresses: Set<String>, now: Long): Map<String, Long> {
        val observed = addresses.associateWith { preferences.getLong("seen_$it", now) }
        preferences.edit {
            preferences.all.keys.filter { it.startsWith("seen_") && it.removePrefix("seen_") !in addresses }
                .forEach { remove(it) }
            observed.forEach { (address, time) -> putLong("seen_$address", time) }
        }
        return observed
    }
}
