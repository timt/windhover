package io.github.timt.windhover.logging

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementRequest
import android.location.GnssMeasurementsEvent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import io.github.timt.windhover.core.dashboard.GnssStatus
import io.github.timt.windhover.core.dashboard.PhoneSnapshot
import io.github.timt.windhover.core.sensors.GNSS_RAW_HEADER
import io.github.timt.windhover.core.sensors.GnssClockReading
import io.github.timt.windhover.core.sensors.GnssMeasurementReading
import io.github.timt.windhover.core.sensors.LOCATION_HEADER
import io.github.timt.windhover.core.sensors.LocationFix
import io.github.timt.windhover.core.sensors.StreamStats
import io.github.timt.windhover.core.sensors.imuLine
import io.github.timt.windhover.core.sensors.rawLine
import io.github.timt.windhover.core.sensors.toLine
import io.github.timt.windhover.core.session.LineLog
import io.github.timt.windhover.core.session.SensorInfo
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val SHUTDOWN_TIMEOUT_SECONDS = 5L

private val imuStreams =
    listOf(
        Sensor.TYPE_ACCELEROMETER to "accel",
        Sensor.TYPE_ACCELEROMETER_UNCALIBRATED to "accel_uncal",
        Sensor.TYPE_GYROSCOPE to "gyro",
        Sensor.TYPE_GYROSCOPE_UNCALIBRATED to "gyro_uncal",
        Sensor.TYPE_MAGNETIC_FIELD to "mag",
        Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED to "mag_uncal",
    )

class PhoneSensors(
    private val context: Context,
) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val streams =
        imuStreams.mapNotNull { (type, stream) -> sensorManager.getDefaultSensor(type)?.let { stream to it } }

    val info =
        streams.map { (stream, sensor) ->
            SensorInfo(stream, sensor.name, sensor.vendor, sensor.minDelay, sensor.resolution, sensor.maximumRange)
        }

    fun record(directory: File): SensorRecording =
        SensorRecording(
            imu = ImuRecorder(sensorManager, streams, directory),
            gnss =
                GnssRecorder(context.getSystemService(LocationManager::class.java), directory).takeIf {
                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                        PackageManager.PERMISSION_GRANTED
                },
        )
}

class SensorRecording(
    private val imu: ImuRecorder,
    private val gnss: GnssRecorder?,
) {
    fun stop(): Map<String, Double> = imu.stop() + gnss?.stop().orEmpty()

    fun snapshot() = PhoneSnapshot(imu.stats + gnss?.stats.orEmpty(), gnss?.status)
}

class ImuRecorder(
    private val sensorManager: SensorManager,
    streams: List<Pair<String, Sensor>>,
    directory: File,
) : SensorEventListener {
    private val streamOf = streams.associate { (stream, sensor) -> sensor to stream }
    private val thread = HandlerThread("imu").apply { start() }
    private val log = LineLog.open(directory.resolve("imu.tsv.gz"), compressed = true, startNanos = now())

    @Volatile
    var stats = emptyMap<String, StreamStats>()
        private set

    init {
        val handler = Handler(thread.looper)
        streams.forEach { (_, sensor) ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, 0, handler)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val stream = streamOf[event.sensor] ?: return
        log.write(imuLine(stream, event.timestamp, event.values), now())
        stats = stats + (stream to (stats[stream] ?: StreamStats()).record(event.timestamp))
    }

    override fun onAccuracyChanged(
        sensor: Sensor,
        accuracy: Int,
    ) = Unit

    fun stop(): Map<String, Double> {
        sensorManager.unregisterListener(this)
        thread.quitSafely()
        thread.join()
        log.close()
        return stats.rates()
    }
}

@SuppressLint("MissingPermission")
class GnssRecorder(
    private val locationManager: LocationManager,
    directory: File,
) {
    private val executor = Executors.newSingleThreadExecutor()
    private val locations =
        LineLog.open(directory.resolve("location.tsv"), startNanos = now()).apply { write(LOCATION_HEADER, now()) }
    private val raw =
        LineLog.open(directory.resolve("gnss-raw.txt.gz"), compressed = true, startNanos = now()).apply {
            write(GNSS_RAW_HEADER, now())
        }

    @Volatile
    var stats = emptyMap<String, StreamStats>()
        private set

    @Volatile
    var status = GnssStatus()
        private set

    private val locationListener = LocationListener(::recordLocation)

    private val measurementsCallback =
        object : GnssMeasurementsEvent.Callback() {
            override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
                val clock = event.clock.toReading()
                val utcMillis = System.currentTimeMillis()
                event.measurements.forEach { raw.write(rawLine(utcMillis, clock, it.toReading()), now()) }
                clock.elapsedRealtimeNanos?.let { record("gnss_raw", it) }
                status =
                    status.copy(
                        satellites = event.measurements.distinctBy { it.constellationType to it.svid }.size,
                        bestCn0DbHz = event.measurements.maxOfOrNull { it.cn0DbHz },
                    )
            }
        }

    init {
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            LocationRequest.Builder(0).setQuality(LocationRequest.QUALITY_HIGH_ACCURACY).build(),
            executor,
            locationListener,
        )
        locationManager.registerGnssMeasurementsCallback(
            GnssMeasurementRequest.Builder().setFullTracking(true).build(),
            executor,
            measurementsCallback,
        )
    }

    private fun recordLocation(location: Location) {
        locations.write(location.toFix().toLine(), now())
        record("location", location.elapsedRealtimeNanos)
        status =
            status.copy(
                lastFixAtNanos = location.elapsedRealtimeNanos,
                accuracyMetres = location.accuracy.takeIf { location.hasAccuracy() },
            )
    }

    private fun record(
        stream: String,
        timestampNanos: Long,
    ) {
        stats = stats + (stream to (stats[stream] ?: StreamStats()).record(timestampNanos))
    }

    fun stop(): Map<String, Double> {
        locationManager.removeUpdates(locationListener)
        locationManager.unregisterGnssMeasurementsCallback(measurementsCallback)
        executor.shutdown()
        executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        locations.close()
        raw.close()
        return stats.rates()
    }
}

private fun now() = SystemClock.elapsedRealtimeNanos()

private fun Map<String, StreamStats>.rates() =
    mapNotNull { (stream, stats) -> stats.rateHz?.let { stream to it } }.toMap()

private fun Location.toFix() =
    LocationFix(
        elapsedRealtimeNanos = elapsedRealtimeNanos,
        utcMillis = time,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude.takeIf { hasAltitude() },
        mslAltitude =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                hasMslAltitude()
            ) {
                mslAltitudeMeters
            } else {
                null
            },
        speed = speed.takeIf { hasSpeed() },
        bearing = bearing.takeIf { hasBearing() },
        accuracy = accuracy.takeIf { hasAccuracy() },
        verticalAccuracy = verticalAccuracyMeters.takeIf { hasVerticalAccuracy() },
        speedAccuracy = speedAccuracyMetersPerSecond.takeIf { hasSpeedAccuracy() },
        bearingAccuracy = bearingAccuracyDegrees.takeIf { hasBearingAccuracy() },
        mslAltitudeAccuracy =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && hasMslAltitudeAccuracy()) {
                mslAltitudeAccuracyMeters
            } else {
                null
            },
    )

private fun GnssClock.toReading() =
    GnssClockReading(
        timeNanos = timeNanos,
        leapSecond = leapSecond.takeIf { hasLeapSecond() },
        timeUncertaintyNanos = timeUncertaintyNanos.takeIf { hasTimeUncertaintyNanos() },
        fullBiasNanos = fullBiasNanos.takeIf { hasFullBiasNanos() },
        biasNanos = biasNanos.takeIf { hasBiasNanos() },
        biasUncertaintyNanos = biasUncertaintyNanos.takeIf { hasBiasUncertaintyNanos() },
        driftNanosPerSecond = driftNanosPerSecond.takeIf { hasDriftNanosPerSecond() },
        driftUncertaintyNanosPerSecond = driftUncertaintyNanosPerSecond.takeIf { hasDriftUncertaintyNanosPerSecond() },
        hardwareClockDiscontinuityCount = hardwareClockDiscontinuityCount,
        elapsedRealtimeNanos = elapsedRealtimeNanos.takeIf { hasElapsedRealtimeNanos() },
    )

@Suppress("DEPRECATION")
private fun GnssMeasurement.toReading() =
    GnssMeasurementReading(
        svid = svid,
        timeOffsetNanos = timeOffsetNanos,
        state = state,
        receivedSvTimeNanos = receivedSvTimeNanos,
        receivedSvTimeUncertaintyNanos = receivedSvTimeUncertaintyNanos,
        cn0DbHz = cn0DbHz,
        pseudorangeRateMetersPerSecond = pseudorangeRateMetersPerSecond,
        pseudorangeRateUncertaintyMetersPerSecond = pseudorangeRateUncertaintyMetersPerSecond,
        accumulatedDeltaRangeState = accumulatedDeltaRangeState,
        accumulatedDeltaRangeMeters = accumulatedDeltaRangeMeters,
        accumulatedDeltaRangeUncertaintyMeters = accumulatedDeltaRangeUncertaintyMeters,
        carrierFrequencyHz = carrierFrequencyHz.takeIf { hasCarrierFrequencyHz() },
        multipathIndicator = multipathIndicator,
        snrInDb = snrInDb.takeIf { hasSnrInDb() },
        constellationType = constellationType,
        agcDb = automaticGainControlLevelDb.takeIf { hasAutomaticGainControlLevelDb() },
        basebandCn0DbHz = basebandCn0DbHz.takeIf { hasBasebandCn0DbHz() },
        fullInterSignalBiasNanos = fullInterSignalBiasNanos.takeIf { hasFullInterSignalBiasNanos() },
        fullInterSignalBiasUncertaintyNanos =
            fullInterSignalBiasUncertaintyNanos.takeIf { hasFullInterSignalBiasUncertaintyNanos() },
        satelliteInterSignalBiasNanos = satelliteInterSignalBiasNanos.takeIf { hasSatelliteInterSignalBiasNanos() },
        satelliteInterSignalBiasUncertaintyNanos =
            satelliteInterSignalBiasUncertaintyNanos.takeIf { hasSatelliteInterSignalBiasUncertaintyNanos() },
        codeType = codeType.takeIf { hasCodeType() },
    )
