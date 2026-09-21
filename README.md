# PairPurge

Android app for bulk-managing paired Bluetooth devices.

Lists every device currently bonded with the phone, with its name, MAC address, and
the total count. Each row has an **Unpair** action, and a pill above the list switches
between name and address order.

Rows can be selected with checkboxes (or all at once from the count row). While a
selection exists, a bottom action bar takes the place of the navigation and offers
**Protect** and **Unpair** for the whole selection; unpairing asks for confirmation
first. Protected devices are hidden from the main list and get their own tab, each
with a **Remove** action that returns it. The protected list is persisted (keyed by
MAC address), so it survives restarts and even re-pairing the same device.

The **Settings** tab offers **Delete connections after N days**, off by default.
Choose 1–3650 days and save. Each Bluetooth connection resets the timer, and time
spent connected does not count as inactivity. Devices with no recorded connection
start their timer when PairPurge first observes them. Protected devices are excluded.
Cleanup runs when opening the app and roughly every six hours in the background;
Android may delay it. Bluetooth must be on and its permission granted. Failed unpair
requests are retried on later checks. If the phone blocks the connection-state check,
automatic cleanup skips that device. Force-stopping the app prevents tracking until
it is opened again. Shortening the period can make existing devices eligible.

Connection tracking uses Android's [Bluetooth connection broadcasts](https://developer.android.com/reference/android/bluetooth/BluetoothDevice#ACTION_ACL_CONNECTED),
which are [allowed in manifest receivers](https://developer.android.com/develop/background-work/background-tasks/broadcasts/broadcast-exceptions).

Android does not provide bond removal in its public SDK, so direct unpairing uses the
hidden `BluetoothDevice.removeBond()` method. PairPurge handles devices that block
that method without crashing, but support can vary by Android release and phone maker.

## Requirements

- Android 12 (API 31) or newer on the device
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

## Run on a physical device

No special build configuration is needed — the debug APK is signed with the local
debug keystore and installs on any Android 12+ phone. Only the connection has to be
set up.

```bash
./scripts/run-on-device.sh
```

Builds, installs, and launches the app, and refuses clearly if no device is attached
or the phone is older than API 31. Useful flags:

| Flag | Effect |
| --- | --- |
| `--logs` | Tail logcat filtered to this app's process |
| `--reset` | Wipe app data first, so the permission prompt reappears |
| `--devices` | List attached devices |
| `--pair` | Wireless debugging: instructions, or `--pair IP:PORT CODE` to pair |
| `--connect` | `--connect IP:PORT` to attach over Wi-Fi |

`adb` does not need to be on your `PATH` — the script finds it inside the SDK. To
use it directly anyway, add this to `~/.zshrc`:

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
```

Enabling debugging on the phone, over USB:

1. Settings → About phone → tap **Build number** seven times.
2. Settings → System → Developer options → enable **USB debugging**.
3. Plug in the cable, then pick **File transfer** on the phone's USB notification.
   Charging-only mode hides the device from `adb`.
4. Tap **Allow** on the USB debugging prompt.

Confirm with `./scripts/run-on-device.sh --devices` — the phone should be listed as
`device`, not `unauthorized`.

Or plain Gradle, if the phone is already connected:

```bash
./gradlew installDebug
```

## Release build

Release signing reads `keystore.properties` from the repository root. That file and
the keystore are gitignored; without them the release build still runs but comes out
unsigned, so a fresh clone needs no setup to build debug or run tests.

Generate an upload key once:

```bash
keytool -genkeypair -v -keystore upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias upload
```

Then copy `keystore.properties.example` to `keystore.properties` and fill in the
passwords. Build the artifact Play expects:

```bash
./gradlew bundleRelease
```

The `.aab` lands in `app/build/outputs/bundle/release/`. **Back up the keystore.** It
is the only key that can publish updates to this app — losing it means the listing
can never be updated again.

## Permissions

`BLUETOOTH_CONNECT` for Bluetooth access and `RECEIVE_BOOT_COMPLETED` to persist
the cleanup schedule across restarts. There is deliberately **no** location permission —
`ACCESS_FINE_LOCATION` is required for Bluetooth *scanning*, which this app does not
do. `minSdk 31` also means the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions are
unnecessary — they are `maxSdkVersion="30"` shims, so no supported device reads them.

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
PairedDevicesScreen      Compose: permission launcher, bottom nav, renders state
        │  refresh(hasPermission, permanentlyDenied)
        ▼
PairedDevicesViewModel   StateFlow<PairedDevicesUiState>
        │  status() / bondedDevices()          │  addresses() / add() / remove()
        ▼                                      ▼
BluetoothDeviceSource    interface          WhitelistStore    interface
        └── SystemBluetoothDeviceSource             └── SharedPreferencesWhitelistStore
```

All screen-selection logic lives in `derivePairedDevicesState`, a pure function, so
the app's behaviour is unit-tested on the JVM with no device or emulator involved.

## Design

The UI follows the Organic system from the *PairPurge Screens* design doc: cream
ground, terracotta primary, sage for the protected role, Space Grotesk on headings
and buttons, Figtree everywhere else, and pills in place of Material's rectangles.
Material 3 still supplies the touch targets, list rhythm and tri-state select-all.

Colour is load-bearing — terracotta reads as destructive, sage as protected, so the
star and the trash never look alike. **Dynamic colour is therefore off**: recolouring
from the wallpaper would collapse that distinction.

`ui/theme/` holds the whole system. `Color.kt` carries roles Material has no slot for
(the tint a selected row takes, the protected colour); `PairPurgeIcons.kt` transcribes
the design's stroked icons rather than pulling in `material-icons-extended` for glyphs
in the wrong style. Every screen has an `@Preview` at the bottom of
`ui/PairedDevicesScreen.kt`, including states that are awkward to reach on a device.

Both fonts are bundled variable fonts under `app/src/main/res/font/`, used under the
SIL Open Font License — see `licenses/`.

One naming note: the UI says **Protected devices**, but the code underneath still
says `whitelist` (`WhitelistStore`, `whitelistedAddresses`, and the persisted
SharedPreferences key). Renaming those would mean migrating stored data for no
behavioural gain, so the copy change stops at the surface.

## Docs

- Design spec: `docs/superpowers/specs/2026-08-07-paired-bluetooth-devices-design.md`
- Implementation plan: `docs/superpowers/plans/2026-08-07-paired-bluetooth-devices.md`
- Screen designs: `PairPurge Screens.dc.html` in the *PairPurge design brief* project
  on [claude.ai/design](https://claude.ai/design/p/2af5abd7-9eee-4222-9ac0-eeedeb9b2950)
