# PairPurge — Milestone 1: Enumerate Paired Bluetooth Devices

**Date:** 2026-08-07
**Status:** Approved

## Purpose

Verify that an Android app can reliably enumerate every Bluetooth device currently
bonded to a real phone, and display them clearly. This milestone is a capability
probe, not a feature. Everything that follows in PairPurge (bulk unpairing) depends
on this enumeration being complete and correct, so the only success criterion is:
the list the app shows matches the list in system Bluetooth settings, exactly.

## Scope

### In scope

1. Request the `BLUETOOTH_CONNECT` runtime permission.
2. Read all bonded devices from the system Bluetooth adapter.
3. Display them in a Jetpack Compose + Material 3 list.
4. Show device name, Bluetooth MAC address, and the total count.
5. Display `Unknown device` for devices with no usable name.
6. Show an empty state when there are no paired devices.
7. Show distinct states when Bluetooth is unsupported or disabled.

### Explicitly out of scope

Unpairing or removing devices; scanning or discovery; `CompanionDeviceManager`;
hidden or reflection-based Android APIs; Shizuku; root or ADB assistance;
persistence or databases; any backend or networking; dependency injection
frameworks; navigation libraries; multi-module or layered architecture.

## Platform targets

| Setting | Value |
| --- | --- |
| minSdk | 34 (Android 14) |
| compileSdk | 36 (Android 16) |
| targetSdk | 36 (Android 16) |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| applicationId / namespace | `com.pairpurge.app` |

`minSdk 34` removes work that older projects need: no legacy `BLUETOOTH` /
`BLUETOOTH_ADMIN` permissions with `maxSdkVersion="30"`, and no location
permission — `ACCESS_FINE_LOCATION` is only required for *scanning*, which is out
of scope. `BLUETOOTH_CONNECT` alone is sufficient to read bonded devices and their
names on every supported API level here.

## Project structure

Single-module Gradle project, Kotlin DSL, version catalog.

```
pairpurge/
├── settings.gradle.kts              # + foojay toolchain resolver
├── build.gradle.kts                 # plugin versions only
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── gradlew
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/pairpurge/app/
        │   │   ├── MainActivity.kt
        │   │   ├── bluetooth/
        │   │   │   ├── PairedDevice.kt
        │   │   │   ├── BluetoothStatus.kt
        │   │   │   ├── BluetoothDeviceSource.kt
        │   │   │   └── SystemBluetoothDeviceSource.kt
        │   │   └── ui/
        │   │       ├── PairedDevicesUiState.kt
        │   │       ├── PairedDevicesViewModel.kt
        │   │       ├── PairedDevicesScreen.kt
        │   │       └── theme/{Color,Theme,Type}.kt
        │   └── res/
        └── test/java/com/pairpurge/app/
            ├── PairedDeviceTest.kt
            ├── PairedDevicesUiStateTest.kt
            └── PairedDevicesViewModelTest.kt
```

### Dependency versions

AGP, Kotlin, and the Compose BOM are **resolved against Google's Maven metadata at
implementation time**, not guessed. The constraint that forces this: the only JDK on
the build machine is Android Studio's bundled JBR 25, and `compileSdk 36` requires a
recent AGP. The Gradle / AGP / JDK triple has to be mutually compatible, and a wrong
guess produces an opaque sync failure. The Java and Kotlin toolchains are pinned to
**JDK 21** via the foojay resolver plugin, which auto-provisions that JDK, so
compilation does not depend on JBR 25 behaviour even though Gradle itself runs on it.

## Architecture

Deliberately flat: one screen, one ViewModel, one interface behind the Android
Bluetooth APIs. The design goal is that all decision logic lives in a pure function
that can be tested on the JVM without a device or emulator.

```
PairedDevicesScreen  (Compose: permission launcher, renders state)
        │  refresh(hasPermission, permanentlyDenied)
        ▼
PairedDevicesViewModel  (StateFlow<PairedDevicesUiState>)
        │  status() / bondedDevices()
        ▼
BluetoothDeviceSource  (interface)
        └── SystemBluetoothDeviceSource  (BluetoothManager / BluetoothAdapter)
```

Permission state is owned by the Compose layer, because
`shouldShowRequestPermissionRationale` requires an `Activity`. The ViewModel never
touches permissions; it receives `hasPermission` as a parameter. This keeps the
ViewModel free of Android framework context and trivially testable.

### Components

**`PairedDevice`** — `data class PairedDevice(val name: String?, val address: String)`.
Exposes `displayName: String`, which returns `"Unknown device"` when `name` is null
**or blank/whitespace-only**. Blank matters: some peripherals report an empty-string
name rather than null, and a naive null check renders an invisible row.

**`BluetoothStatus`** — `enum { UNSUPPORTED, DISABLED, READY }`. `UNSUPPORTED` when
the system has no Bluetooth adapter; `DISABLED` when the adapter exists but is off.

**`BluetoothDeviceSource`** — interface with `status(): BluetoothStatus` and
`bondedDevices(): List<PairedDevice>`. Existence of this interface is what makes the
ViewModel testable; it is not speculative abstraction.

**`SystemBluetoothDeviceSource`** — reads
`context.getSystemService(BluetoothManager::class.java)?.adapter`. Maps
`adapter.bondedDevices` to `PairedDevice`, treating a null set as empty. Wraps the
read in `try/catch (SecurityException)` and returns an empty list on catch: the
platform can throw even when a prior permission check passed, and an uncaught
`SecurityException` here would crash the app.

**`PairedDevicesUiState`** — sealed interface with six states:

| State | Meaning |
| --- | --- |
| `NeedsPermission(permanentlyDenied: Boolean)` | `BLUETOOTH_CONNECT` not granted |
| `BluetoothUnsupported` | No Bluetooth adapter on this hardware |
| `BluetoothDisabled` | Adapter present but turned off |
| `Empty` | Permission granted, Bluetooth on, zero bonded devices |
| `Devices(devices: List<PairedDevice>)` | One or more bonded devices; count is `devices.size` |
| `Loading` | Initial state before the first read |

Alongside it, the pure function:

```kotlin
fun derivePairedDevicesState(
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    status: BluetoothStatus,
    devices: List<PairedDevice>,
): PairedDevicesUiState
```

Precedence is fixed and total: permission → unsupported → disabled → empty →
devices. Permission is checked first because the Bluetooth adapter's state cannot be
read reliably without it, so any other answer would be reporting on an unreliable
read. This function never returns `Loading`; `Loading` is only the ViewModel's
initial value before the first read.

**`PairedDevicesViewModel`** — holds `StateFlow<PairedDevicesUiState>`, starting at
`Loading`. `refresh(hasPermission: Boolean, permanentlyDenied: Boolean)` calls the
source and pushes the derived state. Constructed with the source injected; a
`companion object` `viewModelFactory` supplies the real implementation in the app.

### Sorting

Named devices first, sorted case-insensitively by name; unnamed devices last;
ties broken by MAC address. Rationale: `bondedDevices` returns a `Set` with no order
guarantee, so an unsorted list can reshuffle between refreshes. Since the entire
point of this milestone is comparing the app's list against system settings, the
order must be stable across refreshes.

## Refresh model

**Manual only.** A refresh icon in the top app bar re-reads permission state,
adapter status, and bonded devices. There is no `onResume` refresh, no
`BluetoothAdapter.ACTION_STATE_CHANGED` receiver, and no bond-state receiver.

Consequence, accepted deliberately: after the user enables Bluetooth or pairs a
device in system settings and returns to PairPurge, the screen shows stale content
until they tap Refresh. To keep that from reading as a bug, the `BluetoothDisabled`
and `Empty` body copy ends with an explicit instruction to tap Refresh, and the
refresh action is visible in every state.

## UI specification

Single screen. `Scaffold` with a `TopAppBar` titled "PairPurge" and one action: a
refresh `IconButton` with content description "Refresh". Material 3 theme with
dynamic color, edge-to-edge.

### Device list

Rendered for the `Devices` state only. A header line above the list reads
`"12 paired devices"`, correctly singular at one (`"1 paired device"`). The zero
case never reaches this composable — it is the `Empty` state, whose headline
carries the count instead. Below the header, a `LazyColumn` keyed by MAC address.
Each row:

- Device name — `bodyLarge`, `onSurface`
- MAC address — `bodyMedium`, `FontFamily.Monospace`, `onSurfaceVariant`

Monospace on the address keeps the colon-separated octets aligned down the column,
which is what makes visual comparison against system settings practical.

### Message states

`BluetoothUnsupported`, `BluetoothDisabled`, `Empty`, and `NeedsPermission` all
render through one shared centered composable: icon, headline, body, optional button.

| State | Headline | Body | Button |
| --- | --- | --- | --- |
| `NeedsPermission(false)` | Bluetooth permission needed | PairPurge needs Bluetooth access to read the devices paired with this phone. | **Grant permission** → permission launcher |
| `NeedsPermission(true)` | Bluetooth permission needed | Permission was denied. Enable it in app settings to see your paired devices. | **Open app settings** → `ACTION_APPLICATION_DETAILS_SETTINGS` |
| `BluetoothUnsupported` | Bluetooth unavailable | This device doesn't have Bluetooth, so there are no paired devices to show. | none |
| `BluetoothDisabled` | Bluetooth is off | Turn Bluetooth on to see your paired devices, then tap Refresh. | **Open Bluetooth settings** → `ACTION_BLUETOOTH_SETTINGS` |
| `Empty` | No paired devices | Devices you pair in Bluetooth settings will appear here. Tap Refresh after pairing. | **Open Bluetooth settings** |

## Permission flow

Manifest declares:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
```

`required="false"` on the feature is what makes `BluetoothUnsupported` reachable —
with the default `true`, such devices could not install the app at all.

Runtime flow:

1. On first composition, check `ContextCompat.checkSelfPermission`.
2. If not granted, state is `NeedsPermission(permanentlyDenied = false)`.
3. The Grant button launches `rememberLauncherForActivityResult(RequestPermission())`.
4. In the launcher callback: if granted, refresh. If denied, set
   `permanentlyDenied = !shouldShowRequestPermissionRationale(BLUETOOTH_CONNECT)`.

`shouldShowRequestPermissionRationale` is evaluated **only inside the denial
callback**, never on first launch. On a fresh install it returns `false` before the
user has been asked, so checking it early would misreport a never-asked permission
as permanently denied.

## Error handling

| Failure | Handling |
| --- | --- |
| No Bluetooth adapter | `BluetoothUnsupported` state |
| Adapter off | `BluetoothDisabled` state |
| `SecurityException` on `bondedDevices` | Caught; empty list returned |
| `bondedDevices` returns null | Treated as empty list |
| Device name null or blank | Rendered as `Unknown device` |
| Permission permanently denied | App-settings deep link |

There is no generic error state, because after the above there is no remaining
failure mode this screen can encounter — these calls are local system service reads
with no I/O, no parsing, and no network.

## Testing

JVM unit tests only (`app/src/test`), run with `testDebugUnitTest`. No
instrumentation tests: everything device-dependent is precisely what gets verified
by hand on the real phone, and an emulator has no real bonded devices to enumerate.

**`PairedDeviceTest`** — `displayName` for a normal name, `null`, `""`, and
whitespace-only.

**`PairedDevicesUiStateTest`** — `derivePairedDevicesState` for: no permission;
permanently denied; unsupported; disabled; granted + ready + empty; granted + ready
+ devices. Plus precedence (missing permission wins over a disabled adapter), sort
order (named before unnamed, case-insensitive, address tie-break), and count.

**`PairedDevicesViewModelTest`** — against a fake `BluetoothDeviceSource`: initial
`Loading`; refresh produces `Devices`; refresh with `hasPermission = false` produces
`NeedsPermission` without querying the source; a source that throws
`SecurityException` does not propagate.

## Verification

Automated, on the build machine:

1. `./gradlew assembleDebug` — compiles.
2. `./gradlew testDebugUnitTest` — unit tests pass.

Manual, on a real phone (the milestone's actual acceptance test):

3. Install and launch; grant the permission prompt.
4. Compare the list against Settings → Connected devices, one device at a time.
   **Every bonded device must appear, with a matching name and MAC.**
5. Confirm a nameless device (if any) renders as `Unknown device`.
6. Toggle Bluetooth off, tap Refresh, confirm the `BluetoothDisabled` state.
7. Deny the permission, relaunch, confirm the rationale state; deny permanently,
   confirm the app-settings state.

## Known risks

**SDK platform 36 is not installed locally.** The machine has `android-37.0` and
build-tools 36.0.0. AGP should auto-download platform 36 since the SDK license is
already accepted. If it does not, fall back to fetching `cmdline-tools` and
installing the platform with `sdkmanager`.

**No standalone Gradle and no wrapper cache.** The wrapper JAR must be generated by
running a downloaded Gradle distribution once, before `./gradlew` works.

**JDK 25 only.** Gradle must be a version that supports running on JDK 25; the
project toolchain pins compilation to JDK 21 via foojay auto-provisioning.
