package io.github.timt.windhover.core.larus

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty

class MotionTest {
    private fun line(
        at: Long,
        stream: String,
        x: Float,
        y: Float,
        z: Float,
    ) = "$at\t$stream\t$x\t$y\t$z"

    private val ms = 1_000_000L

    @Test
    fun `samples are averaged into 10 ms ticks and turned from phone axes into front, right, down`() {
        val lines =
            sequenceOf(
                line(1 * ms, "accel", 1f, 2f, 9f),
                line(2 * ms, "accel_uncal", 100f, 100f, 100f),
                line(3 * ms, "gyro", 0.1f, 0.2f, 0.3f),
                line(5 * ms, "mag", 10f, 20f, 30f),
                line(6 * ms, "accel", 3f, 4f, 11f),
                line(12 * ms, "accel", 5f, 6f, 7f),
                line(13 * ms, "gyro", 0.4f, 0.5f, 0.6f),
            )

        expectThat(lines.motion().toList()).containsExactly(
            Timed(0, Motion(Vector(3f, 2f, -10f), Vector(0.2f, 0.1f, -0.3f), Vector(20f, 10f, -30f))),
            Timed(10 * ms, Motion(Vector(6f, 5f, -7f), Vector(0.5f, 0.4f, -0.6f), Vector(20f, 10f, -30f))),
        )
    }

    @Test
    fun `ticks start once every sensor has reported and gaps repeat the last values`() {
        val lines =
            sequenceOf(
                line(1 * ms, "accel", 1f, 0f, 0f),
                line(11 * ms, "gyro", 0f, 1f, 0f),
                line(21 * ms, "mag", 0f, 0f, 1f),
                line(45 * ms, "accel", 2f, 0f, 0f),
            )

        expectThat(lines.motion().map { it.atNanos to it.value.acc.y }.toList()).containsExactly(
            20 * ms to 1f,
            30 * ms to 1f,
            40 * ms to 2f,
        )
    }

    @Test
    fun `samples arriving out of order within half a second are still placed in their tick`() {
        val lines =
            sequenceOf(
                line(1 * ms, "gyro", 0f, 0f, 0f),
                line(2 * ms, "mag", 0f, 0f, 0f),
                line(15 * ms, "accel", 0f, 2f, 0f),
                line(3 * ms, "accel", 0f, 4f, 0f),
            )

        expectThat(lines.motion().map { it.value.acc.x }.toList()).containsExactly(4f, 2f)
    }

    @Test
    fun `no ticks without all three sensors`() {
        expectThat(sequenceOf(line(1, "accel", 0f, 0f, 9.8f)).motion().toList()).isEmpty()
    }
}
