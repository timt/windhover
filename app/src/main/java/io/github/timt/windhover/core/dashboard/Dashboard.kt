package io.github.timt.windhover.core.dashboard

import io.github.timt.windhover.core.sensors.StreamStats

data class StreamStatus(
    val name: String,
    val rateHz: Double?,
    val lastSeenNanos: Long,
)

data class GnssStatus(
    val lastFixAtNanos: Long? = null,
    val accuracyMetres: Float? = null,
    val satellites: Int = 0,
    val bestCn0DbHz: Double? = null,
)

data class PhoneSnapshot(
    val streams: Map<String, StreamStats>,
    val gnss: GnssStatus?,
)

data class Dashboard(
    val atNanos: Long = 0,
    val sentences: List<StreamStatus> = emptyList(),
    val sensors: List<StreamStatus> = emptyList(),
    val gnss: GnssStatus? = null,
)

fun Map<String, StreamStats>.statuses(rates: Map<String, Double>) =
    map { (name, stats) -> StreamStatus(name, rates[name], stats.lastNanos) }.sortedBy { it.name }
