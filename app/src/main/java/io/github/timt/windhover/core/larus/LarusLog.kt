package io.github.timt.windhover.core.larus

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import java.io.OutputStream

private const val SENS_TILT_ROLL = 1
private const val SENS_TILT_PITCH = 2
private const val SENS_TILT_YAW = 3
private const val PITOT_OFFSET = 4
private const val PITOT_SPAN = 5
private const val QNH_OFFSET = 6
private const val MAG_AUTO_CALIB = 17
private const val VARIO_TC = 30
private const val VARIO_INT_TC = 31
private const val WIND_TC = 32
private const val MEAN_WIND_TC = 33
private const val HORIZON = 34
private const val VARIO_P_TC = 35
private const val GNSS_CONFIGURATION = 40
private const val ANT_BASELENGTH = 41
private const val ANT_SLAVE_DOWN = 42
private const val ANT_SLAVE_RIGHT = 43
private const val ASSUMED_TEMPERATURE = 20f
private const val ASSUMED_VOLTAGE = 12f

data class LarusLog(
    val sensorRecords: Int,
    val gnssRecords: Int,
)

sealed interface LarusProblem {
    data object NoHeader : LarusProblem

    data object NoAirData : LarusProblem

    data object NoGnss : LarusProblem

    data object NoMotion : LarusProblem
}

private val larusDefaultConfiguration =
    listOf(
        SENS_TILT_ROLL to 0f,
        SENS_TILT_PITCH to 0f,
        SENS_TILT_YAW to 0f,
        PITOT_OFFSET to 0f,
        PITOT_SPAN to 1f,
        QNH_OFFSET to 0f,
        MAG_AUTO_CALIB to 0f,
        VARIO_TC to 2f,
        VARIO_INT_TC to 30f,
        WIND_TC to 5f,
        MEAN_WIND_TC to 30f,
        HORIZON to 3.12969516e-34f,
        VARIO_P_TC to 3f,
        GNSS_CONFIGURATION to 1f,
        ANT_BASELENGTH to 1f,
        ANT_SLAVE_DOWN to 0f,
        ANT_SLAVE_RIGHT to 0f,
    )

fun writeLarusLog(
    motion: Sequence<Timed<Motion>>,
    airData: List<Timed<AirData>>,
    gnss: List<Timed<GnssFix>>,
    out: OutputStream,
): Result4k<LarusLog, LarusProblem> {
    if (airData.isEmpty()) return Failure(LarusProblem.NoAirData)
    if (gnss.isEmpty()) return Failure(LarusProblem.NoGnss)
    larusDefaultConfiguration.forEach { (id, value) -> out.write(configurationRecord(id, value)) }
    var nextAir = 0
    var nextFix = 0
    var sensorRecords = 0
    var gnssRecords = 0
    motion.forEach { (at, sample) ->
        while (nextAir < airData.size && airData[nextAir].atNanos <= at) nextAir++
        if (nextAir == 0) return@forEach
        while (nextFix < gnss.size && gnss[nextFix].atNanos <= at) {
            if (sensorRecords > 0) {
                out.write(gnss[nextFix].value.toRecord())
                gnssRecords++
            }
            nextFix++
        }
        out.write(sample.toSensorSample(airData[nextAir - 1].value).toRecord())
        sensorRecords++
    }
    return if (sensorRecords == 0) Failure(LarusProblem.NoMotion) else Success(LarusLog(sensorRecords, gnssRecords))
}

private fun Motion.toSensorSample(air: AirData) =
    SensorSample(
        acc = acc,
        gyro = gyro,
        mag = mag,
        pitotPressure = air.dynamicPressure.toFloat(),
        staticPressure = air.staticPressure.toFloat(),
        temperature = ASSUMED_TEMPERATURE,
        voltage = ASSUMED_VOLTAGE,
    )
