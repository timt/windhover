package io.github.timt.windhover.core.larus

import dev.forkhandles.result4k.valueOrNull
import io.github.timt.windhover.core.nmea.LxSentence
import io.github.timt.windhover.core.nmea.Lxwp0
import io.github.timt.windhover.core.nmea.Plxvf
import io.github.timt.windhover.core.nmea.parseLxSentence
import kotlin.math.pow

private const val SEA_LEVEL_PRESSURE = 101_325.0
private const val ISA_LAPSE_FACTOR = 2.25577e-5
private const val ISA_EXPONENT = 5.25588
private const val LARUS_RECIP_STD_DENSITY_TIMES_2 = 1.632
private const val KMH_PER_MS = 3.6

data class Timed<T>(
    val atNanos: Long,
    val value: T,
)

data class AirData(
    val indicatedAirspeed: Double,
    val pressureAltitude: Double,
) {
    val staticPressure get() = SEA_LEVEL_PRESSURE * (1 - ISA_LAPSE_FACTOR * pressureAltitude).pow(ISA_EXPONENT)

    val dynamicPressure get() = indicatedAirspeed * indicatedAirspeed / LARUS_RECIP_STD_DENSITY_TIMES_2
}

fun Sequence<String>.airData(): List<Timed<AirData>> {
    val sentences = mapNotNull { it.lxSentence() }.filter { it.value is Plxvf || it.value is Lxwp0 }.toList()
    val fromPlxvf = sentences.mapNotNull { (at, sentence) -> (sentence as? Plxvf)?.airData()?.let { Timed(at, it) } }
    return fromPlxvf.ifEmpty {
        sentences.mapNotNull { (at, sentence) -> (sentence as? Lxwp0)?.airData()?.let { Timed(at, it) } }
    }
}

fun String.lxSentence(): Timed<LxSentence>? {
    val (at, text) = split('\t', limit = 2).takeIf { it.size == 2 } ?: return null
    val nanos = at.toLongOrNull() ?: return null
    return parseLxSentence(text).valueOrNull()?.let { Timed(nanos, it) }
}

private fun Plxvf.airData() = indicatedAirspeed?.let { ias -> pressureAltitude?.let { AirData(ias, it) } }

private fun Lxwp0.airData() = trueAirspeedKmh?.let { tas -> pressureAltitude?.let { AirData(tas / KMH_PER_MS, it) } }
