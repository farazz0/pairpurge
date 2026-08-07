package com.pairpurge.app.bluetooth

/** What the phone's Bluetooth hardware can currently do. */
enum class BluetoothStatus {
    /** No Bluetooth adapter on this hardware. */
    UNSUPPORTED,

    /** Adapter present, but Bluetooth is turned off. */
    DISABLED,

    /** Adapter present and enabled; bonded devices can be read. */
    READY,
}
