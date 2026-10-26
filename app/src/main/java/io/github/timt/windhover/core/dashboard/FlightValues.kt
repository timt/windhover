package io.github.timt.windhover.core.dashboard

import dev.forkhandles.result4k.valueOrNull
import io.github.timt.windhover.core.nmea.Lxwp0
import io.github.timt.windhover.core.nmea.Plxvf
import io.github.timt.windhover.core.nmea.parseLxSentence
import io.github.timt.windhover.core.stream.ReceivedLine

private const val PLXVF_PRECEDENCE_NANOS = 2_000_000_000L
private const val KMH_PER_MS = 3.6

data class Reading(
    val value: Double,
    val source: String,
    val atNanos: Long,
)

data class FlightValues(
    val airspeed: Reading? = null,
    val pressureAltitude: Reading? = null,
    val vario: Reading? = null,
) {
    fun update(line: ReceivedLine): FlightValues {
        val at = line.receivedAtNanos
        return when (val sentence = parseLxSentence(line.text).valueOrNull()) {
            is Plxvf ->
                FlightValues(
                    airspeed = sentence.indicatedAirspeed?.let { Reading(it, "PLXVF IAS", at) } ?: airspeed,
                    pressureAltitude = sentence.pressureAltitude?.let { Reading(it, "PLXVF", at) } ?: pressureAltitude,
                    vario = sentence.vario?.let { Reading(it, "PLXVF", at) } ?: vario,
                )
            is Lxwp0 ->
                FlightValues(
                    airspeed =
                        airspeed.unlessPlxvfSince(at)
                            ?: sentence.trueAirspeedKmh?.let { Reading(it / KMH_PER_MS, "LXWP0 TAS", at) }
                            ?: airspeed,
                    pressureAltitude =
                        pressureAltitude.unlessPlxvfSince(at)
                            ?: sentence.pressureAltitude?.let { Reading(it, "LXWP0", at) }
                            ?: pressureAltitude,
                    vario =
                        vario.unlessPlxvfSince(at)
                            ?: sentence.varios.firstOrNull()?.let { Reading(it, "LXWP0", at) }
                            ?: vario,
                )
            else -> this
        }
    }
}

private fun Reading?.unlessPlxvfSince(atNanos: Long) =
    this?.takeIf { it.source.startsWith("PLXVF") && atNanos - it.atNanos < PLXVF_PRECEDENCE_NANOS }
