package io.github.timt.windhover.core.dashboard

data class RateMeter(
    private val windowSamples: Int = 5,
    private val samples: List<Pair<Long, Map<String, Long>>> = emptyList(),
) {
    fun sample(
        nowNanos: Long,
        counts: Map<String, Long>,
    ) = copy(samples = (samples + (nowNanos to counts)).takeLast(windowSamples + 1))

    val rates: Map<String, Double>
        get() {
            if (samples.size < 2) return emptyMap()
            val (startNanos, start) = samples.first()
            val (endNanos, end) = samples.last()
            return end.mapValues { (stream, count) -> (count - (start[stream] ?: 0)) * 1e9 / (endNanos - startNanos) }
        }
}
