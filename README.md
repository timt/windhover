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

## Licence

GPL-3.0 — see [LICENSE](LICENSE).
