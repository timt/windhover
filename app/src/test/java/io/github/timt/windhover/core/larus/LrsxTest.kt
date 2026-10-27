package io.github.timt.windhover.core.larus

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

class LrsxTest {
    private fun ByteArray.words() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()

    private fun ByteArray.le() = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)

    private val sample =
        SensorSample(
            acc = Vector(1f, 2f, 3f),
            gyro = Vector(4f, 5f, 6f),
            mag = Vector(7f, 8f, 9f),
            pitotPressure = 10f,
            staticPressure = 11f,
            temperature = 12f,
            voltage = 13f,
        )

    @Test
    fun `a sensor record carries the header Larus computes and thirteen little-endian floats`() {
        val record = sample.toRecord()

        expectThat(record.size).isEqualTo(4 * 14)
        expectThat(record.words().get(0)).isEqualTo(0xbd450e14.toInt())
        expectThat((1..13).map { record.le().getFloat(4 * it) }).isEqualTo((1..13).map { it.toFloat() })
    }

    @Test
    fun `a GNSS record lays out position, velocity, time and quality as Larus reads them, padded to 56 bytes`() {
        val fix =
            GnssFix(
                utc = Instant.parse("2026-10-26T17:59:05.250Z"),
                latitude = 50.9125,
                longitude = 0.0976,
                mslAltitude = 877.4f,
                velocityNed = Vector(-24.9f, -22.3f, 0.5f),
                speedAccuracy = 0.2f,
                satellites = 12,
                geoidSeparation = 45.6f,
            )

        val record = fix.toRecord().le()

        expectThat(record.limit()).isEqualTo(4 * 15)
        expectThat(record.getInt(0)).isEqualTo(0x1c0f0f1e)
        expectThat(record.getDouble(4)).isEqualTo(50.9125)
        expectThat(record.getDouble(12)).isEqualTo(0.0976)
        expectThat(
            listOf(20, 24, 28, 32, 36).map(record::getFloat),
        ).isEqualTo(listOf(877.4f, -24.9f, -22.3f, 0.5f, 0.2f))
        expectThat((40..47).map { record.get(it).toInt() }).isEqualTo(listOf(26, 10, 26, 17, 59, 5, 12, 1))
        expectThat(record.getInt(48)).isEqualTo(250_000_000)
        expectThat(record.getShort(52)).isEqualTo(456.toShort())
        expectThat(record.getShort(54)).isEqualTo(0.toShort())
        expectThat(record.getInt(56)).isEqualTo(0)
    }

    @Test
    fun `a configuration record is an EEPROM node whose checksum matches Larus`() {
        expectThat(configurationRecord(42, 0f).words().let { listOf(it[0], it[1], it[2]) })
            .isEqualTo(listOf(0x2e7c030c, 0x8528022a.toInt(), 0))
        expectThat(configurationRecord(40, 1f).words()[1]).isEqualTo(0xbef20228.toInt())
        expectThat(configurationRecord(1, 0f).words()[1]).isEqualTo(0x10210201)
    }
}
