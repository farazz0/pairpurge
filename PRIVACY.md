# PairPurge privacy policy

Effective date: September 19, 2026

PairPurge is an Android app for managing the Bluetooth devices paired with your phone.
It is made by Faraz Aghaakbari, an independent developer. This policy covers what the
app reads, what it keeps on your phone, and what leaves it.

The short version is that PairPurge collects no data. The app has no internet
permission, so it has no way to send anything off your phone. I never see your
devices, your settings, or anything else about you.

## What the app reads

PairPurge reads the following from Android:

- The name and Bluetooth address of each device paired with your phone.
- Whether each paired device is connected right now.
- Android's notices when a Bluetooth device connects, disconnects, pairs or unpairs.
  These arrive even when the app is closed, which keeps the automatic deletion timer
  accurate.

The app uses this to show your paired devices and to unpair them. It unpairs a device
only when you tap Unpair, or when you have turned on automatic deletion and a device
has gone unused for the number of days you chose. PairPurge reads device names fresh
each time it shows the list and never stores them.

## What the app stores on your phone

PairPurge keeps three things in its private app storage, which other apps can't read:

- Your protected devices, stored as Bluetooth addresses.
- Your automatic deletion setting, which is either off or a number of days.
- The time each paired device last connected. The app needs this to tell how long a
  device has been unused. It records these times whether or not automatic deletion
  is on, so the timer is already correct on the day you turn it on.

Because this data never leaves your phone, it is protected by your phone's own
security, such as your screen lock.

## What leaves your phone

PairPurge sends nothing. It has no internet permission and contains no analytics,
advertising, crash reporting or other third-party code that sends data. I don't sell
or share data, because I don't have any.

Android's own backup is the one case where this data can leave your phone, and it is
controlled by you and run by Google. If you have turned on backup in your phone's
settings, Android may include PairPurge's stored data, the three items above, in your
phone's backup to your Google Account. Google holds that backup under Google's privacy
policy, and I have no access to it. If you set up a new phone from that backup, your
protected devices and settings come back with it.

## Permissions

- **Nearby devices** is Android's label for `BLUETOOTH_CONNECT`. PairPurge needs it to
  list your paired devices, check whether they're connected, and unpair them. Android
  asks you for it the first time you open the app.
- **Run at startup** is Android's label for `RECEIVE_BOOT_COMPLETED`. It keeps the
  background check scheduled after your phone restarts. Android grants it at install
  without asking.

PairPurge does not ask for your location and does not scan for nearby devices. It only
works with devices that are already paired.

## How long data is kept, and how to delete it

- PairPurge deletes a device's last-connection time once the device is no longer paired.
- A protected device stays on the list until you remove it. This includes after you
  unpair it, so the device is still protected if you pair it again.
- The automatic deletion setting stays until you change it.

To delete everything at once, go to Settings → Apps → PairPurge → Storage → Clear
storage, or uninstall the app. Either one removes all of PairPurge's data from your
phone. Google manages any copy in your phone's backup.

## Children

PairPurge is not directed at children under 13, and it collects no data from anyone.

## Changes to this policy

If this policy changes, I will update this page and its effective date. Every earlier
version is in the git history of the PairPurge repository.

## Contact

For privacy questions, email the address shown on PairPurge's Google Play listing. If
you don't mind the question being public, you can also open an issue at
https://github.com/farazz0/pairpurge/issues.
