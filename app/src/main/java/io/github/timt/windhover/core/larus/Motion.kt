package io.github.timt.windhover.core.larus

import java.util.TreeMap

const val TICK_NANOS = 10_000_000L

private const val SETTLE_TICKS = 50L
private val streams = listOf("accel", "gyro", "mag")

data class Motion(
    val acc: Vector,
    val gyro: Vector,
    val mag: Vector,
)

fun Sequence<String>.motion(): Sequence<Timed<Motion>> =
    sequence {
        val pending = TreeMap<Long, Bucket>()
        val resampler = Resampler()
        forEach { line ->
            val sample = line.imuSample() ?: return@forEach
            val tick = Math.floorDiv(sample.atNanos, TICK_NANOS)
            if (resampler.released(tick)) return@forEach
            pending.getOrPut(tick) { Bucket() }.add(sample.value)
            while (pending.firstKey() < tick - SETTLE_TICKS) yieldAll(resampler.release(pending.pollFirstEntry()))
        }
        while (pending.isNotEmpty()) yieldAll(resampler.release(pending.pollFirstEntry()))
    }

private data class StreamSample(
    val stream: Int,
    val values: Vector,
)

private fun String.imuSample(): Timed<StreamSample>? {
    val fields = split('\t')
    val stream = streams.indexOf(fields.getOrNull(1)).takeIf { it >= 0 } ?: return null
    val at = fields[0].toLongOrNull() ?: return null
    val (x, y, z) = (2..4).map { fields.getOrNull(it)?.toFloatOrNull() ?: return null }
    return Timed(at, StreamSample(stream, Vector(x, y, z)))
}

private class Bucket {
    private val sums = Array(streams.size) { DoubleArray(3) }
    private val counts = IntArray(streams.size)

    fun add(sample: StreamSample) {
        val sum = sums[sample.stream]
        sum[0] += sample.values.x
        sum[1] += sample.values.y
        sum[2] += sample.values.z
        counts[sample.stream]++
    }

    fun mean(stream: Int) =
        counts[stream].takeIf { it > 0 }?.let { count ->
            sums[stream].let { Vector((it[0] / count).toFloat(), (it[1] / count).toFloat(), (it[2] / count).toFloat()) }
        }
}

private class Resampler {
    private val held = arrayOfNulls<Vector>(streams.size)
    private var lastTick: Long? = null

    fun released(tick: Long) = lastTick?.let { tick <= it } ?: false

    fun release(entry: Map.Entry<Long, Bucket>): List<Timed<Motion>> {
        val (tick, bucket) = entry
        val gap = lastTick?.let { (it + 1 until tick).mapNotNull(::current) }.orEmpty()
        streams.indices.forEach { stream -> bucket.mean(stream)?.let { held[stream] = it } }
        lastTick = tick
        return gap + listOfNotNull(current(tick))
    }

    private fun current(tick: Long): Timed<Motion>? {
        val (acc, gyro, mag) = held.map { it ?: return null }
        return Timed(tick * TICK_NANOS, Motion(acc.toBody(), gyro.toBody(), mag.toBody()))
    }
}

private fun Vector.toBody() = Vector(y, x, -z)
