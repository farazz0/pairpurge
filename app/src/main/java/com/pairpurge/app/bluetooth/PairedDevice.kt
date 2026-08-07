package com.pairpurge.app.bluetooth

/** A Bluetooth device currently bonded (paired) with this phone. */
data class PairedDevice(
    val name: String?,
    val address: String,
) {
    /** True when the device reported a usable name. Drives sorting: unnamed devices go last. */
    val hasName: Boolean
        get() = !name.isNullOrBlank()

    /**
     * The name to show, falling back when the device reports no usable name.
     *
     * Blank counts as no name: some peripherals report an empty string rather than
     * null, and a null-only check renders a row with no visible text.
     */
    val displayName: String
        get() = if (hasName) name!! else UNKNOWN_DEVICE_NAME

    companion object {
        const val UNKNOWN_DEVICE_NAME: String = "Unknown device"
    }
}
