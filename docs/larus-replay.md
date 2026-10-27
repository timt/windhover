# Replaying sessions through the Larus SIL

Windhover converts each logged session into a Larus `.lrsx` log so it can be replayed through the Larus
software-in-the-loop emulator (`larus_emulator`, in [larus-breeze/sw_tools](https://github.com/larus-breeze/sw_tools)).
The converter lives in `core/larus` and runs on the phone when a session is shared, so the zip contains
`<session>.lrsx`, or `larus.txt` explaining why it could not be made.

## What goes into the log

| Larus record | Source in the session | Conversion |
|---|---|---|
| `EEPROM_FILE_RECORD` (12) | none | All 17 Larus configuration parameters with Larus's own defaults. The emulator needs every one. |
| `BASIC_SENSOR_DATA` (20), 100 Hz | `imu.tsv.gz` (`accel`, `gyro`, `mag`) and `lx.log` | Phone samples averaged into 10 ms ticks; empty ticks repeat the last value. |
| `GNSS_DATA` (30) | `$GPRMC` + `$GPGGA` in `lx.log`, otherwise `location.tsv` | NMEA position, speed and track turned into a north/east/down velocity. |

### Sensor record

- **Axes:** phone (x right, y top of screen, z out of screen) → Larus body (front, right, down) as `(y, x, −z)`.
  This assumes the phone lies flat, screen up, with its top towards the nose. Any other mounting needs the
  `SensTilt_*` parameters (or Larus's attitude set-up) before the attitude means anything.
- **Units:** acceleration m/s² and rotation rad/s, unchanged. Gravity reads −9.81 on the down axis, matching Larus.
  Magnetic field stays in µT; Larus's compass calibration scales it.
- **Pitot pressure:** `IAS² / 1.632` Pa, the exact inverse of Larus's `get_IAS_from_dynamic_pressure`.
- **Static pressure:** ISA, `101325 · (1 − 2.25577e−5 · h)^5.25588` Pa from pressure altitude.
- **Air data source:** `$PLXVF` IAS and pressure altitude when the log has any; otherwise `$LXWP0`, whose airspeed
  is TAS in km/h and is used as IAS.
- **Temperature and supply voltage:** fixed at 20 and 12, as Windhover has neither.
- Ticks before the first air data are dropped.

### GNSS record

- 56 bytes: `GNSS_coordinates_t` is padded to 8-byte alignment by its doubles.
- **Time:** UTC from the NMEA sentence. If `$GPRMC` has no date (Condor sends none), the session's start date is used.
- **Vertical velocity:** from the altitude change between successive fixes.
- **Speed accuracy:** NMEA has none, so 0.2 m/s is assumed. Phone fixes use Android's speed accuracy.
- **Satellite count:** from `$GPGGA`, or 0 for phone fixes.
- Fixes before the first sensor record are dropped, because Larus only starts after sensor data.

## Gaps

- **GNSS rate:** Larus expects 10 Hz. NMEA from an LX unit or FLARM, and phone fixes, arrive at about 1 Hz.
  Larus runs, but its wind and attitude lean on GNSS velocity, so expect slower, noisier estimates.
- **Phone mounting:** this is unknown. See the axes assumption above.
- **Condor sessions:** the phone sits still while Condor flies. The motion sensors contradict the GNSS and airspeed,
  so Condor replays only prove the pipeline works.
- **Timing:** LX values reach the phone in Bluetooth bursts, so each sensor tick holds the last value received.
  There is no latency correction.
- **No real Larus log to compare against:** the published test data server did not respond. The format was checked
  against Larus's source: checksums are computed by Larus's own C code in the tests, and struct sizes are taken from
  the compiled emulator.

## Running a replay

Build the emulator once in Docker. It needs Linux because it uses `feenableexcept`. Pin `sw_algorithms_lib` to the
submodule commit `f926612580c003efa59b6ef247f361b617c98bd1`.

```bash
git clone https://github.com/larus-breeze/sw_tools
git clone https://github.com/larus-breeze/sw_algorithms_lib sw_tools/sensor_data_analyzer/lib
git -C sw_tools/sensor_data_analyzer/lib checkout f926612580c003efa59b6ef247f361b617c98bd1
docker run --rm -v $PWD/sw_tools:/src -w /src gcc:14 bash -c \
  "apt-get -qq update && apt-get -qq install -y cmake && \
   cmake -S sensor_data_analyzer -B build -DCMAKE_BUILD_TYPE=Release && cmake --build build -j8"
```

Get a `.lrsx` either from a shared session zip, or by converting a session folder on the desktop:

```bash
WINDHOVER_SESSION=/path/to/20261026-175855-Naviter-BLE \
  ./gradlew testDebugUnitTest --tests '*ConvertSessionTool*' --rerun
```

This writes `20261026-175855-Naviter-BLE.lrsx` beside the folder. Then replay it:

```bash
docker run --rm -v $PWD:/w -w /w gcc:14 sw_tools/build/larus_emulator session.lrsx csv
```

The emulator writes:

- `session.lrsx.csv`: latitude, longitude, altitude, ground speed, track, IAS, vario, roll, pitch and yaw at 100 Hz;
- `session.lrsx.f<N>`: the full binary state;
- `configuration.lrsx`.

Output starts 600 GNSS samples before the first fix above 50 km/h, or at the start if there is none.

### Larus SIL quirk

In `sensor_data_analyzer.cpp`, the local `next_block_identifier` is read before it is set on the first record. A debug
build can then treat the first record as an extended one and stop. Initialising it to 0 avoids this. The release build
happens to be unaffected.

## First result

Condor session `20261026-175855-Naviter-BLE`, 134 s:

- 13,420 sensor records and 134 GNSS fixes in;
- 13,231 records out;
- position, IAS and track follow Condor;
- attitude is meaningless, because the phone was not mounted.
