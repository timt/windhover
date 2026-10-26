package io.github.timt.windhover.core.sensors

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class StreamStatsTest {
    @Test
    fun `achieved rate is the number of intervals over the time spanned`() {
        val stats = (0..400).fold(StreamStats()) { acc, i -> acc.record(5_000_000_000 + i * 2_500_000L) }

        expectThat(stats.count).isEqualTo(401L)
        expectThat(stats.rateHz).isEqualTo(400.0)
    }

    @Test
    fun `rate is unknown until two samples have arrived`() {
        expectThat(StreamStats().rateHz).isNull()
        expectThat(StreamStats().record(1).rateHz).isNull()
    }
}
