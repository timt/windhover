package io.github.timt.windhover.core.dashboard

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

class RateMeterTest {
    @Test
    fun `rate is the count gained across the window divided by its duration`() {
        val meter =
            (0..6).fold(RateMeter(windowSamples = 5)) { acc, second ->
                acc.sample(second * 1_000_000_000L, mapOf("LXWP0" to second.toLong(), "accel" to second * 400L))
            }

        expectThat(meter.rates).isEqualTo(mapOf("LXWP0" to 1.0, "accel" to 400.0))
    }

    @Test
    fun `a stream that stops reads zero and a stream that just started counts from zero`() {
        val meter =
            RateMeter()
                .sample(0, mapOf("PLXVF" to 50L))
                .sample(2_000_000_000, mapOf("PLXVF" to 50L, "PLXVS" to 4L))

        expectThat(meter.rates).isEqualTo(mapOf("PLXVF" to 0.0, "PLXVS" to 2.0))
    }

    @Test
    fun `no rates until two samples have been taken`() {
        expectThat(RateMeter().sample(0, mapOf("PLXVF" to 1L)).rates).isEmpty()
    }
}
