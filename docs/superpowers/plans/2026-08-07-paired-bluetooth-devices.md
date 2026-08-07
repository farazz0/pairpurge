# PairPurge Milestone 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a minimal Android app that requests `BLUETOOTH_CONNECT`, enumerates every bonded Bluetooth device, and lists each one's name, MAC address, and total count in Jetpack Compose.

**Architecture:** One screen, one ViewModel, one interface (`BluetoothDeviceSource`) wrapping the Android Bluetooth APIs. All decision logic lives in a pure function, `derivePairedDevicesState`, so the app's behaviour is unit-testable on the JVM without a device or emulator. The Compose layer owns permission state, because `shouldShowRequestPermissionRationale` needs an `Activity`; the ViewModel receives `hasPermission` as a plain parameter and never touches the framework.

**Tech Stack:** Kotlin 2.4.10, Jetpack Compose (BOM 2026.06.01), Material 3, AGP 9.3.1, Gradle 9.7.0, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-07-paired-bluetooth-devices-design.md`

## Global Constraints

- `minSdk = 34`, `compileSdk = 36`, `targetSdk = 36`.
- `namespace` and `applicationId` are both `com.pairpurge.app`.
- Gradle `9.7.0`, AGP `9.3.1`, Kotlin `2.2.10`, Compose BOM `2025.10.01`, JUnit `4.13.2`.
- **AGP 9 compiles Kotlin itself.** Applying `org.jetbrains.kotlin.android` is a hard error. Kotlin is pinned to `2.2.10` because that is the KGP version AGP 9.3.1 bundles; the Compose compiler plugin must match it.
- **AndroidX versions are held back deliberately.** Every 2026 AndroidX release requires `compileSdk 37`. The versions pinned here are the newest that still compile against Android 16 (API 36), which the spec requires. Raising `compileSdk` to 37 would allow newer libraries without changing runtime behaviour (`targetSdk` controls that), but it is a deviation from the spec and needs the user's say-so.
- Java/Kotlin toolchain pinned to **JDK 21**, auto-provisioned by the foojay resolver.
- `JAVA_HOME` must be exported for every Gradle invocation. `/usr/bin/java` on this machine is a non-functional stub; the only real JDK is Android Studio's JBR:
  `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Only one permission is declared: `android.permission.BLUETOOTH_CONNECT`. **Never** add `ACCESS_FINE_LOCATION` or the legacy `BLUETOOTH` / `BLUETOOTH_ADMIN` permissions — location is only needed for scanning, which is out of scope, and the legacy permissions are `maxSdkVersion="30"` shims that `minSdk 34` makes dead code.
- Out of scope, do not implement: unpairing, scanning/discovery, `CompanionDeviceManager`, hidden or reflection-based APIs, Shizuku, root/ADB, persistence, networking, dependency-injection frameworks, navigation libraries.
- Refresh is **manual only**. Do not add an `onResume` refresh, a `BluetoothAdapter.ACTION_STATE_CHANGED` receiver, or a bond-state receiver.
- All user-visible copy lives in `res/values/strings.xml`, verbatim as written in the tasks below. The one exception is `"Unknown device"`, which is a constant on `PairedDevice` so the fallback stays unit-testable without Android resources.
- Do not add `androidx.compose.material:material-icons-extended`. It is frozen at an old version and is not in the BOM. Every icon used here (`Refresh`, `Info`, `Warning`, `Settings`) ships in `material-icons-core`, which Material 3 already pulls in.
- Commit after each task.

---

### Task 1: Buildable project skeleton

Produces a Gradle project that compiles and launches an empty themed screen. Everything version-related and toolchain-related is settled here, so later tasks only write Kotlin.

**Files:**
- Create: `local.properties`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` (generated)
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/pairpurge/app/MainActivity.kt`
- Create: `app/src/main/java/com/pairpurge/app/ui/theme/{Color,Theme,Type}.kt`
- Create: `app/src/main/res/values/{strings,themes,colors}.xml`, `app/src/main/res/values-night/themes.xml`
- Create: `app/src/main/res/drawable/ic_launcher_foreground.xml`, `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- Modify: `.gitignore` (already exists; verify it ignores `local.properties` and `.gradle/`)

**Interfaces:**
- Consumes: nothing.
- Produces: a working `./gradlew`; the `com.pairpurge.app` namespace; `PairPurgeTheme { }` composable wrapper; `R.string.app_name`.

- [ ] **Step 1: Download a Gradle distribution**

There is no Gradle on this machine and no wrapper JAR, so one distribution has to be fetched by hand to generate the wrapper later.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
SCRATCH="$TMPDIR/pairpurge-bootstrap"
mkdir -p "$SCRATCH"
curl -sSL -o "$SCRATCH/gradle.zip" https://services.gradle.org/distributions/gradle-9.7.0-bin.zip
unzip -q -o "$SCRATCH/gradle.zip" -d "$SCRATCH"
"$SCRATCH/gradle-9.7.0/bin/gradle" --version
```

Expected: prints `Gradle 9.7.0` and `JVM: 25.0.2`.

The `wrapper` task cannot run yet — Gradle 9 refuses to run any task in a directory with no settings file, so the wrapper is generated in Step 10 after the build files exist.

- [ ] **Step 2: Point Gradle at the Android SDK**

`local.properties` is machine-specific and gitignored.

```properties
sdk.dir=/Users/faraz/Library/Android/sdk
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PairPurge"
include(":app")
```

The foojay plugin is what lets `jvmToolchain(21)` work when no JDK 21 is installed — it downloads one on first build.

- [ ] **Step 4: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "9.3.1"
# Must match the Kotlin Gradle plugin that AGP 9.3.1 bundles for its built-in Kotlin
# support, otherwise the Compose compiler plugin and the compiler disagree on version.
kotlin = "2.2.10"
# AndroidX releases from 2026 require compileSdk 37. These are the newest versions
# that still compile against Android 16 (API 36), which this project targets.
coreKtx = "1.17.0"
activityCompose = "1.11.0"
lifecycle = "2.9.4"
composeBom = "2025.10.01"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-material3 = { group = "androidx.compose.material3", name = "material3" }
junit = { group = "junit", name = "junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 5: Write `gradle.properties` and root `build.gradle.kts`**

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 6: Write `app/build.gradle.kts`**

```kotlin
// AGP 9 compiles Kotlin itself; applying org.jetbrains.kotlin.android is an error now.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pairpurge.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pairpurge.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.ui.tooling.preview)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
```

Create an empty `app/proguard-rules.pro` (a comment line is fine).

- [ ] **Step 7: Write the manifest**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

    <uses-feature
        android:name="android.hardware.bluetooth"
        android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.PairPurge">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.PairPurge">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

`android:required="false"` on the feature is load-bearing: with the default `true`, phones without Bluetooth could not install the app, making the `BluetoothUnsupported` state unreachable.

- [ ] **Step 8: Write resources**

`app/src/main/res/values/strings.xml` — the complete final copy, used by later tasks:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">PairPurge</string>

    <string name="action_refresh">Refresh</string>
    <string name="action_grant">Grant permission</string>
    <string name="action_open_app_settings">Open app settings</string>
    <string name="action_open_bluetooth_settings">Open Bluetooth settings</string>

    <string name="permission_headline">Bluetooth permission needed</string>
    <string name="permission_body">PairPurge needs Bluetooth access to read the devices paired with this phone.</string>
    <string name="permission_body_denied">Permission was denied. Enable it in app settings to see your paired devices.</string>

    <string name="unsupported_headline">Bluetooth unavailable</string>
    <string name="unsupported_body">This device doesn\'t have Bluetooth, so there are no paired devices to show.</string>

    <string name="disabled_headline">Bluetooth is off</string>
    <string name="disabled_body">Turn Bluetooth on to see your paired devices, then tap Refresh.</string>

    <string name="empty_headline">No paired devices</string>
    <string name="empty_body">Devices you pair in Bluetooth settings will appear here. Tap Refresh after pairing.</string>

    <plurals name="paired_device_count">
        <item quantity="one">%d paired device</item>
        <item quantity="other">%d paired devices</item>
    </plurals>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PairPurge" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

`app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.PairPurge" parent="android:Theme.Material.NoActionBar" />
</resources>
```

Parenting off the platform `android:Theme.Material` avoids pulling in the Material Components View library, which this app has no use for — all UI is Compose.

`app/src/main/res/values/colors.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1B5EAA</color>
</resources>
```

`app/src/main/res/drawable/ic_launcher_foreground.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FFFFFF"
        android:pathData="M54,30 L54,54 L45,45 M54,54 L45,63 M54,54 L63,45 L54,36 M54,54 L63,63 L54,72 Z"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
```

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

- [ ] **Step 9: Write the Compose theme**

`app/src/main/java/com/pairpurge/app/ui/theme/Color.kt`:

```kotlin
package com.pairpurge.app.ui.theme

import androidx.compose.ui.graphics.Color

val Blue80 = Color(0xFFA8C8FF)
val BlueGrey80 = Color(0xFFBFC6DC)
val Cyan80 = Color(0xFFA0CFD8)

val Blue40 = Color(0xFF1B5EAA)
val BlueGrey40 = Color(0xFF565E71)
val Cyan40 = Color(0xFF3A656E)
```

`app/src/main/java/com/pairpurge/app/ui/theme/Type.kt`:

```kotlin
package com.pairpurge.app.ui.theme

import androidx.compose.material3.Typography

val Typography = Typography()
```

`app/src/main/java/com/pairpurge/app/ui/theme/Theme.kt`:

```kotlin
package com.pairpurge.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
)

private val LightColors = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Cyan40,
)

@Composable
fun PairPurgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 10: Write a placeholder `MainActivity`**

Task 6 replaces the body with the real screen.

```kotlin
package com.pairpurge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.pairpurge.app.ui.theme.PairPurgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PairPurgeTheme {
                Surface {
                    Text("PairPurge")
                }
            }
        }
    }
}
```

- [ ] **Step 11: Generate the Gradle wrapper**

Now that a settings file exists, the `wrapper` task can run.

```bash
cd /Users/faraz/projects/pairpurge
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
"$TMPDIR/pairpurge-bootstrap/gradle-9.7.0/bin/gradle" wrapper --gradle-version 9.7.0
./gradlew --version
```

Expected: `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`, and `gradlew` now exist.

- [ ] **Step 12: Build**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. First run is slow — it downloads Gradle deps, a JDK 21 toolchain, and SDK platform 36.

**If SDK platform 36 fails to auto-download**, install it manually and re-run:

```bash
cd "$TMPDIR/pairpurge-bootstrap"
curl -sSL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip
unzip -q -o cmdline-tools.zip
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
yes | ./cmdline-tools/bin/sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platforms;android-36"
```

**If AGP 9.3.1 rejects any DSL above**, read the error and fix the DSL — do not downgrade AGP. Gradle 8.x is not an option here because it does not support running on JDK 25, and JBR 25 is the only JDK available.

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "build: scaffold Compose project targeting Android 16, minSdk 34"
```

---

### Task 2: Bluetooth data layer

**Files:**
- Create: `app/src/main/java/com/pairpurge/app/bluetooth/PairedDevice.kt`
- Create: `app/src/main/java/com/pairpurge/app/bluetooth/BluetoothStatus.kt`
- Create: `app/src/main/java/com/pairpurge/app/bluetooth/BluetoothDeviceSource.kt`
- Create: `app/src/main/java/com/pairpurge/app/bluetooth/SystemBluetoothDeviceSource.kt`
- Test: `app/src/test/java/com/pairpurge/app/bluetooth/PairedDeviceTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `data class PairedDevice(val name: String?, val address: String)` with `val displayName: String`, `val hasName: Boolean`, and `PairedDevice.UNKNOWN_DEVICE_NAME: String`
  - `enum class BluetoothStatus { UNSUPPORTED, DISABLED, READY }`
  - `interface BluetoothDeviceSource { fun status(): BluetoothStatus; fun bondedDevices(): List<PairedDevice> }`
  - `class SystemBluetoothDeviceSource(context: Context) : BluetoothDeviceSource`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/pairpurge/app/bluetooth/PairedDeviceTest.kt`:

```kotlin
package com.pairpurge.app.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedDeviceTest {

    @Test
    fun `displayName uses the reported name`() {
        val device = PairedDevice(name = "Pixel Buds Pro", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Pixel Buds Pro", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is null`() {
        val device = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is empty`() {
        val device = PairedDevice(name = "", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `displayName falls back when the name is only whitespace`() {
        val device = PairedDevice(name = "   ", address = "AA:BB:CC:DD:EE:FF")

        assertEquals("Unknown device", device.displayName)
    }

    @Test
    fun `hasName is true only for a usable name`() {
        assertTrue(PairedDevice(name = "Car", address = "AA:BB:CC:DD:EE:01").hasName)
        assertFalse(PairedDevice(name = null, address = "AA:BB:CC:DD:EE:02").hasName)
        assertFalse(PairedDevice(name = "  ", address = "AA:BB:CC:DD:EE:03").hasName)
    }
}
```

The blank cases are not padding. Some peripherals report `""` rather than `null`, and a null-only check renders a row with no visible text.

- [ ] **Step 2: Run the test to verify it fails**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest --tests "*PairedDeviceTest*"
```

Expected: FAIL — compilation error, `Unresolved reference: PairedDevice`.

- [ ] **Step 3: Write `PairedDevice`**

```kotlin
package com.pairpurge.app.bluetooth

/** A Bluetooth device currently bonded (paired) with this phone. */
data class PairedDevice(
    val name: String?,
    val address: String,
) {
    /** True when the device reported a usable name. Drives sorting: unnamed devices go last. */
    val hasName: Boolean
        get() = !name.isNullOrBlank()

    /** The name to show, falling back when the device reports no usable name. */
    val displayName: String
        get() = if (hasName) name!! else UNKNOWN_DEVICE_NAME

    companion object {
        const val UNKNOWN_DEVICE_NAME: String = "Unknown device"
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew testDebugUnitTest --tests "*PairedDeviceTest*"
```

Expected: `BUILD SUCCESSFUL`, 5 tests passing.

- [ ] **Step 5: Write `BluetoothStatus` and `BluetoothDeviceSource`**

`BluetoothStatus.kt`:

```kotlin
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
```

`BluetoothDeviceSource.kt`:

```kotlin
package com.pairpurge.app.bluetooth

/**
 * Reads Bluetooth state and bonded devices.
 *
 * Exists so the ViewModel can be tested on the JVM against a fake, without an
 * Android framework dependency.
 */
interface BluetoothDeviceSource {
    fun status(): BluetoothStatus

    /**
     * Bonded devices, or an empty list if they cannot be read. Callers must hold
     * [android.Manifest.permission.BLUETOOTH_CONNECT]; implementations must not throw.
     */
    fun bondedDevices(): List<PairedDevice>
}
```

- [ ] **Step 6: Write `SystemBluetoothDeviceSource`**

```kotlin
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
}
```

- [ ] **Step 7: Verify the whole module still builds**

```bash
./gradlew assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: add Bluetooth data layer for reading bonded devices"
```

---

### Task 3: UI state and the pure derivation function

This is the behavioural core of the app. Every requirement about *which* screen shows *when* is decided here, and tested here.

**Files:**
- Create: `app/src/main/java/com/pairpurge/app/ui/PairedDevicesUiState.kt`
- Test: `app/src/test/java/com/pairpurge/app/ui/PairedDevicesUiStateTest.kt`

**Interfaces:**
- Consumes: `PairedDevice`, `BluetoothStatus` from Task 2.
- Produces:
  - `sealed interface PairedDevicesUiState` with `Loading`, `NeedsPermission(permanentlyDenied: Boolean)`, `BluetoothUnsupported`, `BluetoothDisabled`, `Empty`, `Devices(devices: List<PairedDevice>)`
  - `fun derivePairedDevicesState(hasPermission: Boolean, permanentlyDenied: Boolean, status: BluetoothStatus, devices: List<PairedDevice>): PairedDevicesUiState`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/pairpurge/app/ui/PairedDevicesUiStateTest.kt`:

```kotlin
package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class PairedDevicesUiStateTest {

    private val headphones = PairedDevice(name = "Headphones", address = "AA:BB:CC:DD:EE:01")
    private val car = PairedDevice(name = "car stereo", address = "AA:BB:CC:DD:EE:02")
    private val unnamed = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:03")

    private fun derive(
        hasPermission: Boolean = true,
        permanentlyDenied: Boolean = false,
        status: BluetoothStatus = BluetoothStatus.READY,
        devices: List<PairedDevice> = emptyList(),
    ) = derivePairedDevicesState(hasPermission, permanentlyDenied, status, devices)

    @Test
    fun `missing permission asks for permission`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            derive(hasPermission = false),
        )
    }

    @Test
    fun `permanent denial is carried into the state`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = true),
            derive(hasPermission = false, permanentlyDenied = true),
        )
    }

    @Test
    fun `missing permission wins over a disabled adapter`() {
        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = false),
            derive(hasPermission = false, status = BluetoothStatus.DISABLED),
        )
    }

    @Test
    fun `no adapter reports unsupported`() {
        assertEquals(
            PairedDevicesUiState.BluetoothUnsupported,
            derive(status = BluetoothStatus.UNSUPPORTED),
        )
    }

    @Test
    fun `disabled adapter reports disabled`() {
        assertEquals(
            PairedDevicesUiState.BluetoothDisabled,
            derive(status = BluetoothStatus.DISABLED),
        )
    }

    @Test
    fun `no bonded devices reports empty`() {
        assertEquals(PairedDevicesUiState.Empty, derive(devices = emptyList()))
    }

    @Test
    fun `bonded devices are listed`() {
        val state = derive(devices = listOf(headphones))

        assertEquals(PairedDevicesUiState.Devices(listOf(headphones)), state)
    }

    @Test
    fun `device count matches the list size`() {
        val state = derive(devices = listOf(headphones, car, unnamed)) as PairedDevicesUiState.Devices

        assertEquals(3, state.devices.size)
    }

    @Test
    fun `devices sort case-insensitively by name with unnamed devices last`() {
        val state = derive(devices = listOf(unnamed, headphones, car)) as PairedDevicesUiState.Devices

        assertEquals(listOf(car, headphones, unnamed), state.devices)
    }

    @Test
    fun `unnamed devices are ordered by address`() {
        val second = PairedDevice(name = null, address = "AA:BB:CC:DD:EE:09")
        val first = PairedDevice(name = "", address = "AA:BB:CC:DD:EE:04")

        val state = derive(devices = listOf(second, first)) as PairedDevicesUiState.Devices

        assertEquals(listOf(first, second), state.devices)
    }

    @Test
    fun `devices with the same name are ordered by address`() {
        val second = PairedDevice(name = "Speaker", address = "AA:BB:CC:DD:EE:22")
        val first = PairedDevice(name = "speaker", address = "AA:BB:CC:DD:EE:11")

        val state = derive(devices = listOf(second, first)) as PairedDevicesUiState.Devices

        assertEquals(listOf(first, second), state.devices)
    }
}
```

Sorting is tested because `bondedDevices` returns an unordered `Set`. If the order can shuffle between refreshes, comparing the app's list against system settings — the entire point of this milestone — becomes guesswork.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*PairedDevicesUiStateTest*"
```

Expected: FAIL — `Unresolved reference: derivePairedDevicesState`.

- [ ] **Step 3: Write `PairedDevicesUiState`**

```kotlin
package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice

/** Everything the paired-devices screen can show. */
sealed interface PairedDevicesUiState {

    /** Before the first read. Never returned by [derivePairedDevicesState]. */
    data object Loading : PairedDevicesUiState

    data class NeedsPermission(val permanentlyDenied: Boolean) : PairedDevicesUiState

    data object BluetoothUnsupported : PairedDevicesUiState

    data object BluetoothDisabled : PairedDevicesUiState

    data object Empty : PairedDevicesUiState

    data class Devices(val devices: List<PairedDevice>) : PairedDevicesUiState
}

/**
 * Decides what the screen shows. Pure, so every state is unit-testable without a device.
 *
 * Permission is checked before adapter status because the adapter cannot be read
 * reliably without it — any other answer would be reporting on an untrustworthy read.
 */
fun derivePairedDevicesState(
    hasPermission: Boolean,
    permanentlyDenied: Boolean,
    status: BluetoothStatus,
    devices: List<PairedDevice>,
): PairedDevicesUiState = when {
    !hasPermission -> PairedDevicesUiState.NeedsPermission(permanentlyDenied)
    status == BluetoothStatus.UNSUPPORTED -> PairedDevicesUiState.BluetoothUnsupported
    status == BluetoothStatus.DISABLED -> PairedDevicesUiState.BluetoothDisabled
    devices.isEmpty() -> PairedDevicesUiState.Empty
    else -> PairedDevicesUiState.Devices(devices.sortedForDisplay())
}

/** Named devices first (case-insensitive), unnamed last, ties broken by address. */
internal fun List<PairedDevice>.sortedForDisplay(): List<PairedDevice> =
    sortedWith(
        compareBy<PairedDevice> { !it.hasName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy { it.address },
    )
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew testDebugUnitTest --tests "*PairedDevicesUiStateTest*"
```

Expected: `BUILD SUCCESSFUL`, 11 tests passing.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add paired-devices UI state and pure state derivation"
```

---

### Task 4: ViewModel

**Files:**
- Create: `app/src/main/java/com/pairpurge/app/ui/PairedDevicesViewModel.kt`
- Test: `app/src/test/java/com/pairpurge/app/ui/PairedDevicesViewModelTest.kt`

**Interfaces:**
- Consumes: `BluetoothDeviceSource`, `SystemBluetoothDeviceSource`, `BluetoothStatus`, `PairedDevice` (Task 2); `PairedDevicesUiState`, `derivePairedDevicesState` (Task 3).
- Produces:
  - `class PairedDevicesViewModel(source: BluetoothDeviceSource) : ViewModel()`
  - `val uiState: StateFlow<PairedDevicesUiState>`
  - `fun refresh(hasPermission: Boolean, permanentlyDenied: Boolean = false)`
  - `PairedDevicesViewModel.Factory: ViewModelProvider.Factory`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/pairpurge/app/ui/PairedDevicesViewModelTest.kt`:

```kotlin
package com.pairpurge.app.ui

import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.BluetoothStatus
import com.pairpurge.app.bluetooth.PairedDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

private class FakeBluetoothDeviceSource(
    var status: BluetoothStatus = BluetoothStatus.READY,
    var devices: List<PairedDevice> = emptyList(),
    var throwOnRead: Boolean = false,
) : BluetoothDeviceSource {

    var wasRead: Boolean = false
        private set

    override fun status(): BluetoothStatus = status

    override fun bondedDevices(): List<PairedDevice> {
        wasRead = true
        if (throwOnRead) throw SecurityException("denied")
        return devices
    }
}

class PairedDevicesViewModelTest {

    private val headphones = PairedDevice(name = "Headphones", address = "AA:BB:CC:DD:EE:01")

    @Test
    fun `starts in loading`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())

        assertEquals(PairedDevicesUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes bonded devices`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Devices(listOf(headphones)), viewModel.uiState.value)
    }

    @Test
    fun `refresh publishes empty when there are no bonded devices`() {
        val viewModel = PairedDevicesViewModel(FakeBluetoothDeviceSource())

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `refresh reports a disabled adapter`() {
        val source = FakeBluetoothDeviceSource(status = BluetoothStatus.DISABLED)
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.BluetoothDisabled, viewModel.uiState.value)
    }

    @Test
    fun `refresh without permission does not touch the source`() {
        val source = FakeBluetoothDeviceSource(devices = listOf(headphones))
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = false, permanentlyDenied = true)

        assertEquals(
            PairedDevicesUiState.NeedsPermission(permanentlyDenied = true),
            viewModel.uiState.value,
        )
        assertFalse(source.wasRead)
    }

    @Test
    fun `a source that throws does not crash the refresh`() {
        val source = FakeBluetoothDeviceSource(throwOnRead = true)
        val viewModel = PairedDevicesViewModel(source)

        viewModel.refresh(hasPermission = true)

        assertEquals(PairedDevicesUiState.Empty, viewModel.uiState.value)
    }
}
```

The last test documents defence in depth: `SystemBluetoothDeviceSource` already swallows `SecurityException`, and the ViewModel must survive a source that does not.

- [ ] **Step 2: Run the test to verify it fails**

```bash
./gradlew testDebugUnitTest --tests "*PairedDevicesViewModelTest*"
```

Expected: FAIL — `Unresolved reference: PairedDevicesViewModel`.

- [ ] **Step 3: Write the ViewModel**

```kotlin
package com.pairpurge.app.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pairpurge.app.bluetooth.BluetoothDeviceSource
import com.pairpurge.app.bluetooth.SystemBluetoothDeviceSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the paired-devices screen state.
 *
 * Permission handling stays in the Compose layer, which owns the Activity that
 * `shouldShowRequestPermissionRationale` needs, so this class takes [hasPermission]
 * as a parameter and never touches the framework.
 */
class PairedDevicesViewModel(
    private val source: BluetoothDeviceSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PairedDevicesUiState>(PairedDevicesUiState.Loading)
    val uiState: StateFlow<PairedDevicesUiState> = _uiState.asStateFlow()

    /** Re-reads Bluetooth state. Cheap, synchronous, local — no coroutine needed. */
    fun refresh(hasPermission: Boolean, permanentlyDenied: Boolean = false) {
        if (!hasPermission) {
            _uiState.value = PairedDevicesUiState.NeedsPermission(permanentlyDenied)
            return
        }

        val status = source.status()
        val devices = try {
            source.bondedDevices()
        } catch (_: SecurityException) {
            emptyList()
        }

        _uiState.value = derivePairedDevicesState(
            hasPermission = true,
            permanentlyDenied = false,
            status = status,
            devices = devices,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                PairedDevicesViewModel(SystemBluetoothDeviceSource(application))
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew testDebugUnitTest --tests "*PairedDevicesViewModelTest*"
```

Expected: `BUILD SUCCESSFUL`, 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: add PairedDevicesViewModel with manual refresh"
```

---

### Task 5: Compose screen

**Files:**
- Create: `app/src/main/java/com/pairpurge/app/ui/PairedDevicesScreen.kt`
- Modify: `app/src/main/java/com/pairpurge/app/MainActivity.kt`

**Interfaces:**
- Consumes: `PairedDevice` (Task 2); `PairedDevicesUiState` (Task 3); `PairedDevicesViewModel`, `PairedDevicesViewModel.Factory` (Task 4); `PairPurgeTheme`, all `R.string`/`R.plurals` (Task 1).
- Produces: `@Composable fun PairedDevicesScreen(viewModel: PairedDevicesViewModel = viewModel(factory = PairedDevicesViewModel.Factory))`.

There is no unit test for this task — Compose UI tests would need an instrumented device, and the screen contains no logic beyond mapping already-tested states to already-specified copy. It is verified by build plus the manual device checklist in Task 6.

- [ ] **Step 1: Write the screen**

```kotlin
package com.pairpurge.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pairpurge.app.R
import com.pairpurge.app.bluetooth.PairedDevice

private const val BLUETOOTH_PERMISSION = Manifest.permission.BLUETOOTH_CONNECT

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairedDevicesScreen(
    viewModel: PairedDevicesViewModel = viewModel(factory = PairedDevicesViewModel.Factory),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // Only meaningful after a denial: on a fresh install shouldShowRationale is
        // false because the user has never been asked, so checking it earlier would
        // misreport a never-asked permission as permanently denied.
        permanentlyDenied = !granted && !context.shouldShowBluetoothRationale()
        viewModel.refresh(hasPermission = granted, permanentlyDenied = permanentlyDenied)
    }

    LaunchedEffect(Unit) {
        viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.refresh(context.hasBluetoothPermission(), permanentlyDenied)
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)

        when (val state = uiState) {
            PairedDevicesUiState.Loading -> Unit

            is PairedDevicesUiState.NeedsPermission -> MessageState(
                icon = Icons.Default.Info,
                headline = stringResource(R.string.permission_headline),
                body = stringResource(
                    if (state.permanentlyDenied) {
                        R.string.permission_body_denied
                    } else {
                        R.string.permission_body
                    },
                ),
                buttonLabel = stringResource(
                    if (state.permanentlyDenied) {
                        R.string.action_open_app_settings
                    } else {
                        R.string.action_grant
                    },
                ),
                onButtonClick = {
                    if (state.permanentlyDenied) {
                        context.openAppSettings()
                    } else {
                        permissionLauncher.launch(BLUETOOTH_PERMISSION)
                    }
                },
                modifier = contentModifier,
            )

            PairedDevicesUiState.BluetoothUnsupported -> MessageState(
                icon = Icons.Default.Warning,
                headline = stringResource(R.string.unsupported_headline),
                body = stringResource(R.string.unsupported_body),
                buttonLabel = null,
                onButtonClick = {},
                modifier = contentModifier,
            )

            PairedDevicesUiState.BluetoothDisabled -> MessageState(
                icon = Icons.Default.Settings,
                headline = stringResource(R.string.disabled_headline),
                body = stringResource(R.string.disabled_body),
                buttonLabel = stringResource(R.string.action_open_bluetooth_settings),
                onButtonClick = { context.openBluetoothSettings() },
                modifier = contentModifier,
            )

            PairedDevicesUiState.Empty -> MessageState(
                icon = Icons.Default.Info,
                headline = stringResource(R.string.empty_headline),
                body = stringResource(R.string.empty_body),
                buttonLabel = stringResource(R.string.action_open_bluetooth_settings),
                onButtonClick = { context.openBluetoothSettings() },
                modifier = contentModifier,
            )

            is PairedDevicesUiState.Devices -> DeviceList(
                devices = state.devices,
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun DeviceList(devices: List<PairedDevice>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier) {
        item {
            Text(
                text = pluralStringResource(
                    R.plurals.paired_device_count,
                    devices.size,
                    devices.size,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        items(devices, key = { it.address }) { device ->
            ListItem(
                headlineContent = { Text(device.displayName) },
                supportingContent = {
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        // Monospace keeps the octets aligned down the column, which is
                        // what makes comparing against system settings practical.
                        fontFamily = FontFamily.Monospace,
                    )
                },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun MessageState(
    icon: ImageVector,
    headline: String,
    body: String,
    buttonLabel: String?,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = headline,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (buttonLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onButtonClick) { Text(buttonLabel) }
        }
    }
}

private fun Context.hasBluetoothPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, BLUETOOTH_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.shouldShowBluetoothRationale(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, BLUETOOTH_PERMISSION)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun Context.openBluetoothSettings() {
    startActivity(
        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
```

- [ ] **Step 2: Wire it into `MainActivity`**

Replace the whole file:

```kotlin
package com.pairpurge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pairpurge.app.ui.PairedDevicesScreen
import com.pairpurge.app.ui.theme.PairPurgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PairPurgeTheme {
                PairedDevicesScreen()
            }
        }
    }
}
```

- [ ] **Step 3: Build and run all tests**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`, 22 tests passing.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "feat: add paired devices screen with permission and empty states"
```

---

### Task 6: Verification

**Files:**
- Create: `README.md`

**Interfaces:**
- Consumes: everything.
- Produces: a verified debug APK and a written manual-test record.

- [ ] **Step 1: Run the full check**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew clean assembleDebug testDebugUnitTest lint
```

Expected: `BUILD SUCCESSFUL`. Read the lint report at `app/build/reports/lint-results-debug.html`; fix any error-severity findings. Warnings about unused resources are acceptable.

- [ ] **Step 2: Write `README.md`**

```markdown
# PairPurge

Android app for bulk-managing paired Bluetooth devices.

**Milestone 1 (current):** read-only. Lists every device currently bonded with the
phone, with its name, MAC address, and the total count. No unpairing yet.

## Requirements

- Android 14 (API 34) or newer
- Android Studio with a JDK; this project's toolchain is JDK 21, auto-provisioned by Gradle

## Build

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Install on a device

```bash
./gradlew installDebug
```

## Permissions

`BLUETOOTH_CONNECT` only. No location permission — that is required for Bluetooth
*scanning*, which this app does not do.
```

- [ ] **Step 3: Install on the phone and verify by hand**

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
~/Library/Android/sdk/platform-tools/adb devices   # confirm the phone is listed
./gradlew installDebug
```

Then walk the checklist. This is the milestone's real acceptance test — the unit tests prove the logic, only the phone proves enumeration:

1. Launch. The permission rationale appears; tap **Grant permission**, then **Allow**.
2. The list appears with a count header.
3. **Open Settings → Connected devices → See all, and compare one device at a time. Every bonded device must appear in PairPurge, with a matching name and MAC.**
4. Any device with no name shows as `Unknown device`.
5. Turn Bluetooth off, return to PairPurge, tap **Refresh** → "Bluetooth is off" with an **Open Bluetooth settings** button.
6. Turn Bluetooth on, tap **Refresh** → the list returns.
7. Reinstall, deny the permission prompt → rationale state with **Grant permission**.
8. Deny twice (permanently) → state changes to **Open app settings**; the button opens PairPurge's system settings page.

Record the result: how many devices system settings shows, how many PairPurge shows, and whether the names and addresses matched.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "docs: add README with build and manual verification steps"
```

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
| --- | --- |
| Request `BLUETOOTH_CONNECT` | 1 (manifest), 5 (runtime flow) |
| Read bonded devices | 2 |
| Compose + Material 3 list | 1 (theme), 5 (list) |
| Name, MAC, total count | 5 (`DeviceList`, `paired_device_count` plural) |
| `Unknown device` fallback | 2 (`displayName`, incl. blank names) |
| Empty state | 3 (`Empty`), 5 (rendering) |
| Bluetooth unavailable / disabled states | 2 (`BluetoothStatus`), 3, 5 |
| Permanently-denied handling | 3 (flag), 5 (rationale timing, app-settings link) |
| `SecurityException` safety | 2 (source), 4 (ViewModel) |
| Null `bondedDevices` | 2 (`.orEmpty()`) |
| Stable sort order | 3 (`sortedForDisplay`) |
| Manual-refresh-only model | 4, 5 (no receivers, no `onResume`) |
| minSdk 34 / compile+target 36 / `com.pairpurge.app` | 1 |
| Unit tests, no instrumentation | 2, 3, 4 |
| Build + device verification | 6 |
| SDK-platform-36 download risk | 1 (Step 11 fallback) |

**Placeholder scan:** none — every code step contains complete, runnable content.

**Type consistency:** `refresh(hasPermission, permanentlyDenied)` matches across Tasks 4 and 5. `derivePairedDevicesState`'s four-parameter signature matches between Tasks 3 and 4. `PairedDevice.hasName` is defined in Task 2 and consumed by `sortedForDisplay` in Task 3. `PairedDevicesViewModel.Factory` is defined in Task 4 and consumed in Task 5. All `R.string` / `R.plurals` keys used in Task 5 are defined in Task 1.
