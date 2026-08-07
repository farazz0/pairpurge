#!/usr/bin/env bash
#
# Build, install, and launch PairPurge on a connected physical device.
#
#   ./scripts/run-on-device.sh                       build, install, launch
#   ./scripts/run-on-device.sh --logs                ...then tail this app's logcat
#   ./scripts/run-on-device.sh --reset               wipe app data first, so the
#                                                    permission prompt reappears
#   ./scripts/run-on-device.sh --devices             list attached devices
#   ./scripts/run-on-device.sh --pair                how to connect over Wi-Fi
#   ./scripts/run-on-device.sh --pair IP:PORT CODE   pair over Wi-Fi
#   ./scripts/run-on-device.sh --connect IP:PORT     connect over Wi-Fi
#
# adb does not need to be on PATH; this script finds it inside the SDK.
#
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
APP_ID="com.pairpurge.app"
ACTIVITY="$APP_ID/.MainActivity"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELF="./scripts/run-on-device.sh"

if [[ ! -x "$ADB" ]]; then
    echo "adb not found at: $ADB" >&2
    echo "Set ANDROID_HOME to your Android SDK location, or install Platform-Tools" >&2
    echo "via Android Studio -> Settings -> Languages & Frameworks -> Android SDK." >&2
    exit 1
fi

# /usr/bin/java on macOS is a stub, not a JDK. Android Studio's bundled JBR is
# the real one, so builds from a terminal need this pointed at it.
if [[ -z "${JAVA_HOME:-}" ]]; then
    export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

show_help() {
    # Print the header comment block, stopping at the first line of code so the
    # help text cannot drift out of sync with a hardcoded line range.
    awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "${BASH_SOURCE[0]}"
}

RESET=false
LOGS=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --reset) RESET=true; shift ;;
        --logs) LOGS=true; shift ;;
        --devices)
            "$ADB" devices -l
            exit 0
            ;;
        --connect)
            [[ -n "${2:-}" ]] || { echo "usage: $SELF --connect IP:PORT" >&2; exit 2; }
            exec "$ADB" connect "$2"
            ;;
        --pair)
            if [[ -n "${2:-}" && -n "${3:-}" ]]; then
                exec "$ADB" pair "$2" "$3"
            fi
            cat <<EOF
Wireless debugging pairing
--------------------------
On the phone: Settings -> System -> Developer options -> Wireless debugging
  1. Turn Wireless debugging on.
  2. Tap "Pair device with pairing code".
     It shows an IP:PORT and a 6-digit code. Both are single-use.
  3. Note the *other* IP:PORT on the main Wireless debugging screen — that is
     the connect address, and it is a different port from the pairing one.
     Mixing the two up is the usual reason pairing appears to fail.

Then, using the values from the phone:

  $SELF --pair    <pair-ip>:<pair-port> <6-digit-code>
  $SELF --connect <connect-ip>:<connect-port>
  $SELF --devices

EOF
            exit 0
            ;;
        -h|--help) show_help; exit 0 ;;
        *) echo "unknown option: $1" >&2; echo "try: $SELF --help" >&2; exit 2 ;;
    esac
done

devices=$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')
count=$(printf '%s' "$devices" | grep -c . || true)

if [[ "$count" -eq 0 ]]; then
    unauthorized=$("$ADB" devices | awk 'NR>1 && $2=="unauthorized" {print $1}')
    if [[ -n "$unauthorized" ]]; then
        echo "Device connected but not authorized."
        echo "Unlock the phone, tap \"Allow\" on the USB debugging prompt, then rerun."
        exit 1
    fi
    cat <<EOF
No device found.

Over USB:
  1. Phone: Settings -> About phone -> tap "Build number" 7 times.
  2. Settings -> System -> Developer options -> enable "USB debugging".
  3. Connect the cable, then on the phone's USB notification choose
     "File transfer". Charging-only mode hides the device from adb.
  4. Tap "Allow" on the USB debugging prompt.

Over Wi-Fi (no cable):
  $SELF --pair

Then check with:
  $SELF --devices
EOF
    exit 1
fi

if [[ "$count" -gt 1 && -z "${ANDROID_SERIAL:-}" ]]; then
    echo "More than one device attached:"
    printf '  %s\n' $devices
    echo "Pick one with:  ANDROID_SERIAL=<serial> $SELF"
    exit 1
fi

serial="${ANDROID_SERIAL:-$(printf '%s' "$devices" | head -1)}"
export ANDROID_SERIAL="$serial"

model=$("$ADB" -s "$serial" shell getprop ro.product.model | tr -d '\r')
release=$("$ADB" -s "$serial" shell getprop ro.build.version.release | tr -d '\r')
sdk=$("$ADB" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
echo "Device: $model — Android $release (API $sdk) [$serial]"

if [[ "$sdk" -lt 34 ]]; then
    echo "This app needs Android 14 (API 34) or newer; this device is API $sdk." >&2
    exit 1
fi

if [[ "$RESET" == true ]]; then
    echo "Clearing app data (permission prompt will reappear)..."
    "$ADB" -s "$serial" shell pm clear "$APP_ID" >/dev/null 2>&1 || true
fi

echo "Building and installing..."
(cd "$ROOT" && ./gradlew installDebug)

echo "Launching..."
"$ADB" -s "$serial" shell am start -n "$ACTIVITY" >/dev/null

if [[ "$LOGS" == true ]]; then
    pid=""
    for _ in $(seq 1 20); do
        pid=$("$ADB" -s "$serial" shell pidof "$APP_ID" | tr -d '\r')
        [[ -n "$pid" ]] && break
        sleep 0.3
    done
    if [[ -z "$pid" ]]; then
        echo "App did not start; showing unfiltered log instead. Ctrl-C to stop."
        exec "$ADB" -s "$serial" logcat
    fi
    echo "Tailing logcat for pid $pid. Ctrl-C to stop."
    exec "$ADB" -s "$serial" logcat --pid="$pid"
fi

echo "Running on $model."
