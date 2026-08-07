# PairPurge

Android app for bulk-managing paired Bluetooth devices.

**Milestone 1 (current): read-only.** Lists every device currently bonded with the
phone, with its name, MAC address, and the total count. No unpairing yet — this
milestone exists to prove enumeration is complete and correct on real hardware.

## Requirements

- Android 14 (API 34) or newer on the device
- Android Studio (its bundled JBR is the only JDK this project needs)

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

`JAVA_HOME` must be set when building from a terminal: `/usr/bin/java` on macOS is a
stub that is not a working JDK. Android Studio sets this itself, so builds started
from the IDE need no extra setup.

## Install on a device

```bash
./gradlew installDebug
```

## Permissions

`BLUETOOTH_CONNECT` only. There is deliberately **no** location permission —
`ACCESS_FINE_LOCATION` is required for Bluetooth *scanning*, which this app does not
do. `minSdk 34` also means the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions are
unnecessary.

## Version pinning

`compileSdk` is 36 (Android 16), per spec. Every AndroidX release from 2026 requires
`compileSdk 37`, so the AndroidX versions in `gradle/libs.versions.toml` are pinned to
the newest ones that still build against 36. Gradle lint reports these as outdated —
that is expected, not drift.

To move to current AndroidX, raise `compileSdk` to 37 in `app/build.gradle.kts` and
un-pin the versions. `compileSdk` only controls which APIs are available at compile
time; `targetSdk` is what changes runtime behaviour, and it can stay at 36.

AGP 9 compiles Kotlin itself, so this project does **not** apply the
`org.jetbrains.kotlin.android` plugin — doing so is now a build error. The Kotlin
version is pinned to the one AGP bundles (2.2.10) so the Compose compiler plugin
matches it.

## Architecture

```
PairedDevicesScreen      Compose: permission launcher, renders state
        │  refresh(hasPermission, permanentlyDenied)
        ▼
PairedDevicesViewModel   StateFlow<PairedDevicesUiState>
        │  status() / bondedDevices()
        ▼
BluetoothDeviceSource    interface
        └── SystemBluetoothDeviceSource   BluetoothManager / BluetoothAdapter
```

All screen-selection logic lives in `derivePairedDevicesState`, a pure function, so
the app's behaviour is unit-tested on the JVM with no device or emulator involved.

## Docs

- Design spec: `docs/superpowers/specs/2026-08-07-paired-bluetooth-devices-design.md`
- Implementation plan: `docs/superpowers/plans/2026-08-07-paired-bluetooth-devices.md`
