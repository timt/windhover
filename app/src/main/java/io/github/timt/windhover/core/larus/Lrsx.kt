package io.github.timt.windhover.core.larus

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

private const val EEPROM_FILE_RECORD = 12
private const val BASIC_SENSOR_DATA = 20
private const val GNSS_DATA = 30
private const val CRC_SEED = 0xfff1
private const val SAT_FIX = 1
private const val FIRST_GNSS_YEAR = 2000

data class Vector(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class SensorSample(
    val acc: Vector,
    val gyro: Vector,
    val mag: Vector,
    val pitotPressure: Float,
    val staticPressure: Float,
    val temperature: Float,
    val voltage: Float,
)

data class GnssFix(
    val utc: Instant,
    val latitude: Double,
    val longitude: Double,
    val mslAltitude: Float,
    val velocityNed: Vector,
    val speedAccuracy: Float,
    val satellites: Int,
    val geoidSeparation: Float,
)

fun SensorSample.toRecord() =
    record(BASIC_SENSOR_DATA, 13) {
        putVector(acc)
        putVector(gyro)
        putVector(mag)
        putFloat(pitotPressure)
        putFloat(staticPressure)
        putFloat(temperature)
        putFloat(voltage)
    }

fun GnssFix.toRecord() =
    record(GNSS_DATA, 14) {
        val time = utc.atOffset(ZoneOffset.UTC)
        putDouble(latitude)
        putDouble(longitude)
        putFloat(mslAltitude)
        putVector(velocityNed)
        putFloat(speedAccuracy)
        put((time.year - FIRST_GNSS_YEAR).toByte())
        put(time.monthValue.toByte())
        put(time.dayOfMonth.toByte())
        put(time.hour.toByte())
        put(time.minute.toByte())
        put(time.second.toByte())
        put(satellites.toByte())
        put(SAT_FIX.toByte())
        putInt(time.nano)
        putShort((geoidSeparation * 10).roundToInt().toShort())
        putShort(0)
        putInt(0)
    }

fun configurationRecord(
    id: Int,
    value: Float,
) = record(EEPROM_FILE_RECORD, 2) {
    val bits = value.toRawBits()
    val dataCrc = crc16(bits shr 16, crc16(bits, 0))
    val info = id or (2 shl 8)
    putInt(info or (crc16(info, dataCrc) shl 16))
    putFloat(value)
}

private fun record(
    type: Int,
    words: Int,
    payload: ByteBuffer.() -> Unit,
): ByteArray {
    val info = type or ((words + 1) shl 8)
    return ByteBuffer
        .allocate(4 * (words + 1))
        .order(ByteOrder.LITTLE_ENDIAN)
        .putInt(info or (crc16(info, CRC_SEED) shl 16))
        .apply(payload)
        .also { check(!it.hasRemaining()) }
        .array()
}

private fun ByteBuffer.putVector(vector: Vector) = putFloat(vector.x).putFloat(vector.y).putFloat(vector.z)

private val crcTable =
    IntArray(256) { index ->
        (0 until 8).fold(index shl 8) { crc, _ -> if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 } and
            0xffff
    }

private fun crc16(
    input: Int,
    crc: Int,
) = ((crc shl 8) xor crcTable[((crc shr 8) xor input) and 0xff]) and 0xffff
