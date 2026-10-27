# Windhover

Phone-based real-time 3D wind and gust-free vario for gliders flying with LXNAV instruments such as the S80 and LX9000.

The Android app fuses indicated airspeed and pressure altitude from an LXNAV instrument (Bluetooth) with the phone's IMU and GNSS, building on the open-source [Larus](https://github.com/larus-breeze) algorithms.

**Status:** Phase 1 — Bluetooth probe and data logger.

## Building

Requires JDK 21 and the Android SDK (platform 37).

```bash
./gradlew ktlintCheck testDebugUnitTest assembleDebug
./gradlew installDebug
```

## Session files

Each logging session is a folder named `<UTC start>-<profile>` containing:

| File | Contents |
|---|---|
| `header.json` | Profile, phone, sensors and achieved sample rates |
| `lx.log` | Every line from the LX unit, prefixed with its receive time |
| `imu.tsv.gz` | Accelerometer, gyro and magnetometer samples (calibrated and uncalibrated) |
| `location.tsv` | Phone GNSS fixes |
| `gnss-raw.txt.gz` | Raw GNSS measurements in GnssLogger "Raw" format |

All timestamps are `SystemClock.elapsedRealtimeNanos()`, so every file shares one clock.

To share a session, tap **Sessions** on the profiles screen, then tap a session to zip it and open the Android share sheet.
The zip also contains a Larus `.lrsx` log for replay through the Larus emulator — see [docs/larus-replay.md](docs/larus-replay.md).

To copy every session to a computer over USB, enable USB debugging and run:

```bash
adb pull /sdcard/Android/data/io.github.timt.windhover/files/sessions
```

## Licence

GPL-3.0 — see [LICENSE](LICENSE).
