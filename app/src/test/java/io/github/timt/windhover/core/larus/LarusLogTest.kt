package io.github.timt.windhover.core.larus

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant

class LarusLogTest {
    private val ms = 1_000_000L

    private fun motion(at: Long) = Timed(at, Motion(Vector(0f, 0f, -9.8f), Vector(0f, 0f, 0f), Vector(20f, 0f, 40f)))

    private fun fix(at: Long) = Timed(at, GnssFix(Instant.EPOCH, 50.0, 0.0, 100f, Vector(0f, 0f, 0f), 0.2f, 8, 0f))

    private fun ByteArray.recordTypes(): List<Int> {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return generateSequence {
            buffer.takeIf { it.hasRemaining() }?.int?.let { header ->
                buffer.position(buffer.position() + 4 * (((header shr 8) and 0xff) - 1))
                header and 0xff
            }
        }.toList()
    }

    @Test
    fun `configuration first, then sensor ticks from the first air data with GNSS fixes slotted in by time`() {
        val out = ByteArrayOutputStream()

        val result =
            writeLarusLog(
                motion = sequenceOf(motion(0), motion(10 * ms), motion(20 * ms), motion(30 * ms)),
                airData = listOf(Timed(5 * ms, AirData(25.0, 500.0)), Timed(25 * ms, AirData(26.0, 510.0))),
                gnss = listOf(fix(1 * ms), fix(12 * ms), fix(20 * ms), fix(99 * ms)),
                out = out,
            )

        expectThat(result).isEqualTo(Success(LarusLog(sensorRecords = 3, gnssRecords = 2)))
        expectThat(out.toByteArray().recordTypes()).isEqualTo(List(17) { 12 } + listOf(20, 30, 30, 20, 20))
    }

    @Test
    fun `sensor ticks hold the latest air data as pressures`() {
        val out = ByteArrayOutputStream()

        writeLarusLog(sequenceOf(motion(30 * ms)), listOf(Timed(25 * ms, AirData(26.0, 510.0))), listOf(fix(0)), out)

        val buffer = ByteBuffer.wrap(out.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
        val sensor = 17 * 12 + 4
        expectThat(buffer.getFloat(sensor + 36)).isEqualTo(AirData(26.0, 510.0).dynamicPressure.toFloat())
        expectThat(buffer.getFloat(sensor + 40)).isEqualTo(AirData(26.0, 510.0).staticPressure.toFloat())
    }

    @Test
    fun `a log needs air data, GNSS and motion`() {
        val out = ByteArrayOutputStream()

        expectThat(writeLarusLog(sequenceOf(motion(0)), emptyList(), listOf(fix(0)), out))
            .isEqualTo(Failure(LarusProblem.NoAirData))
        expectThat(writeLarusLog(sequenceOf(motion(0)), listOf(Timed(0, AirData(0.0, 0.0))), emptyList(), out))
            .isEqualTo(Failure(LarusProblem.NoGnss))
        expectThat(writeLarusLog(emptySequence(), listOf(Timed(0, AirData(0.0, 0.0))), listOf(fix(0)), out))
            .isEqualTo(Failure(LarusProblem.NoMotion))
    }
}
