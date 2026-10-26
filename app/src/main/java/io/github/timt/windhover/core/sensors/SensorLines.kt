package io.github.timt.windhover.core.sensors

fun imuLine(
    stream: String,
    timestampNanos: Long,
    values: FloatArray,
) = buildString {
    append(timestampNanos).append('\t').append(stream)
    values.forEach { append('\t').append(it) }
}

data class LocationFix(
    val elapsedRealtimeNanos: Long,
    val utcMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val mslAltitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val accuracy: Float?,
    val verticalAccuracy: Float?,
    val speedAccuracy: Float?,
    val bearingAccuracy: Float?,
    val mslAltitudeAccuracy: Float?,
)

val LOCATION_HEADER =
    listOf(
        "elapsedRealtimeNanos",
        "utcMillis",
        "latitude",
        "longitude",
        "altitude",
        "mslAltitude",
        "speed",
        "bearing",
        "accuracy",
        "verticalAccuracy",
        "speedAccuracy",
        "bearingAccuracy",
        "mslAltitudeAccuracy",
    ).joinToString("\t")

fun LocationFix.toLine() =
    listOf(
        elapsedRealtimeNanos,
        utcMillis,
        latitude,
        longitude,
        altitude,
        mslAltitude,
        speed,
        bearing,
        accuracy,
        verticalAccuracy,
        speedAccuracy,
        bearingAccuracy,
        mslAltitudeAccuracy,
    ).joinToString("\t") { it?.toString().orEmpty() }

data class GnssClockReading(
    val timeNanos: Long,
    val leapSecond: Int?,
    val timeUncertaintyNanos: Double?,
    val fullBiasNanos: Long?,
    val biasNanos: Double?,
    val biasUncertaintyNanos: Double?,
    val driftNanosPerSecond: Double?,
    val driftUncertaintyNanosPerSecond: Double?,
    val hardwareClockDiscontinuityCount: Int,
    val elapsedRealtimeNanos: Long?,
)

data class GnssMeasurementReading(
    val svid: Int,
    val timeOffsetNanos: Double,
    val state: Int,
    val receivedSvTimeNanos: Long,
    val receivedSvTimeUncertaintyNanos: Long,
    val cn0DbHz: Double,
    val pseudorangeRateMetersPerSecond: Double,
    val pseudorangeRateUncertaintyMetersPerSecond: Double,
    val accumulatedDeltaRangeState: Int,
    val accumulatedDeltaRangeMeters: Double,
    val accumulatedDeltaRangeUncertaintyMeters: Double,
    val carrierFrequencyHz: Float?,
    val multipathIndicator: Int,
    val snrInDb: Double?,
    val constellationType: Int,
    val agcDb: Double?,
    val basebandCn0DbHz: Double?,
    val fullInterSignalBiasNanos: Double?,
    val fullInterSignalBiasUncertaintyNanos: Double?,
    val satelliteInterSignalBiasNanos: Double?,
    val satelliteInterSignalBiasUncertaintyNanos: Double?,
    val codeType: String?,
)

const val GNSS_RAW_HEADER =
    "# Raw,utcTimeMillis,TimeNanos,LeapSecond,TimeUncertaintyNanos,FullBiasNanos,BiasNanos,BiasUncertaintyNanos," +
        "DriftNanosPerSecond,DriftUncertaintyNanosPerSecond,HardwareClockDiscontinuityCount,Svid,TimeOffsetNanos," +
        "State,ReceivedSvTimeNanos,ReceivedSvTimeUncertaintyNanos,Cn0DbHz,PseudorangeRateMetersPerSecond," +
        "PseudorangeRateUncertaintyMetersPerSecond,AccumulatedDeltaRangeState,AccumulatedDeltaRangeMeters," +
        "AccumulatedDeltaRangeUncertaintyMeters,CarrierFrequencyHz,CarrierCycles,CarrierPhase," +
        "CarrierPhaseUncertainty," +
        "MultipathIndicator,SnrInDb,ConstellationType,AgcDb,BasebandCn0DbHz,FullInterSignalBiasNanos," +
        "FullInterSignalBiasUncertaintyNanos,SatelliteInterSignalBiasNanos,SatelliteInterSignalBiasUncertaintyNanos," +
        "CodeType,ChipsetElapsedRealtimeNanos"

fun rawLine(
    utcMillis: Long,
    clock: GnssClockReading,
    measurement: GnssMeasurementReading,
) = with(measurement) {
    listOf(
        "Raw",
        utcMillis,
        clock.timeNanos,
        clock.leapSecond,
        clock.timeUncertaintyNanos,
        clock.fullBiasNanos,
        clock.biasNanos,
        clock.biasUncertaintyNanos,
        clock.driftNanosPerSecond,
        clock.driftUncertaintyNanosPerSecond,
        clock.hardwareClockDiscontinuityCount,
        svid,
        timeOffsetNanos,
        state,
        receivedSvTimeNanos,
        receivedSvTimeUncertaintyNanos,
        cn0DbHz,
        pseudorangeRateMetersPerSecond,
        pseudorangeRateUncertaintyMetersPerSecond,
        accumulatedDeltaRangeState,
        accumulatedDeltaRangeMeters,
        accumulatedDeltaRangeUncertaintyMeters,
        carrierFrequencyHz,
        null,
        null,
        null,
        multipathIndicator,
        snrInDb,
        constellationType,
        agcDb,
        basebandCn0DbHz,
        fullInterSignalBiasNanos,
        fullInterSignalBiasUncertaintyNanos,
        satelliteInterSignalBiasNanos,
        satelliteInterSignalBiasUncertaintyNanos,
        codeType,
        clock.elapsedRealtimeNanos,
    ).joinToString(",") { it?.toString().orEmpty() }
}
