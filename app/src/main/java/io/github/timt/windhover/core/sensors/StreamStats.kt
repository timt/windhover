package io.github.timt.windhover.core.sensors

data class StreamStats(
    val count: Long = 0,
    val firstNanos: Long = 0,
    val lastNanos: Long = 0,
) {
    fun record(timestampNanos: Long) =
        StreamStats(count + 1, if (count == 0L) timestampNanos else firstNanos, timestampNanos)

    val rateHz: Double?
        get() = if (count < 2 || lastNanos <= firstNanos) null else (count - 1) * 1e9 / (lastNanos - firstNanos)
}
