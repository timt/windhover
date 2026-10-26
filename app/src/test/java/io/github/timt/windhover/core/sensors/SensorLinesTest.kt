package io.github.timt.windhover.core.sensors

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class SensorLinesTest {
    @Test
    fun `motion samples are written with their event timestamp and every value exactly`() {
        val values = floatArrayOf(0.1f, -2.5e-7f, 9.80665f)

        val line = imuLine("accel", 6_000_000_123, values)

        expectThat(line).isEqualTo("6000000123\taccel\t0.1\t-2.5E-7\t9.80665")
        expectThat(line.split('\t').drop(2).map { it.toFloat() }).isEqualTo(values.toList())
    }

    @Test
    fun `location fixes leave missing optional values blank`() {
        val fix =
            LocationFix(
                elapsedRealtimeNanos = 7_000_000_000,
                utcMillis = 1_793_000_000_000,
                latitude = 51.18765432,
                longitude = -1.03456789,
                altitude = 182.4,
                mslAltitude = null,
                speed = 27.5f,
                bearing = null,
                accuracy = 3.2f,
                verticalAccuracy = 4.5f,
                speedAccuracy = 0.3f,
                bearingAccuracy = null,
                mslAltitudeAccuracy = null,
            )

        expectThat(fix.toLine()).isEqualTo(
            "7000000000\t1793000000000\t51.18765432\t-1.03456789\t182.4\t\t27.5\t\t3.2\t4.5\t0.3\t\t",
        )
        expectThat(LOCATION_HEADER.split('\t').size).isEqualTo(fix.toLine().split('\t').size)
    }

    @Test
    fun `raw GNSS measurements follow the GnssLogger Raw column order`() {
        val clock =
            GnssClockReading(
                timeNanos = 1_000,
                leapSecond = 18,
                timeUncertaintyNanos = null,
                fullBiasNanos = -1_400_000_000_000_000_000,
                biasNanos = 0.25,
                biasUncertaintyNanos = 10.0,
                driftNanosPerSecond = 1.5,
                driftUncertaintyNanosPerSecond = 0.5,
                hardwareClockDiscontinuityCount = 3,
                elapsedRealtimeNanos = 8_000_000_000,
            )
        val measurement =
            GnssMeasurementReading(
                svid = 12,
                timeOffsetNanos = 0.0,
                state = 16431,
                receivedSvTimeNanos = 345_000_000_000,
                receivedSvTimeUncertaintyNanos = 9,
                cn0DbHz = 38.5,
                pseudorangeRateMetersPerSecond = -512.25,
                pseudorangeRateUncertaintyMetersPerSecond = 0.05,
                accumulatedDeltaRangeState = 0,
                accumulatedDeltaRangeMeters = 0.0,
                accumulatedDeltaRangeUncertaintyMeters = 0.0,
                carrierFrequencyHz = 1.57542e9f,
                multipathIndicator = 0,
                snrInDb = null,
                constellationType = 1,
                agcDb = null,
                basebandCn0DbHz = 34.0,
                fullInterSignalBiasNanos = null,
                fullInterSignalBiasUncertaintyNanos = null,
                satelliteInterSignalBiasNanos = null,
                satelliteInterSignalBiasUncertaintyNanos = null,
                codeType = "C",
            )

        val columns = GNSS_RAW_HEADER.removePrefix("# ").split(',')
        val fields = rawLine(1_793_000_000_000, clock, measurement).split(',')

        val row = columns.zip(fields).toMap()
        val expected =
            mapOf(
                "Raw" to "Raw",
                "utcTimeMillis" to "1793000000000",
                "TimeUncertaintyNanos" to "",
                "FullBiasNanos" to "-1400000000000000000",
                "Svid" to "12",
                "Cn0DbHz" to "38.5",
                "CarrierFrequencyHz" to "1.57542E9",
                "CarrierCycles" to "",
                "ConstellationType" to "1",
                "CodeType" to "C",
                "ChipsetElapsedRealtimeNanos" to "8000000000",
            )

        expectThat(fields.size).isEqualTo(columns.size)
        expectThat(expected.keys.associateWith { row[it] }).isEqualTo(expected)
    }
}
