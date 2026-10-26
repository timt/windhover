package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.dashboard.Dashboard
import io.github.timt.windhover.core.dashboard.FlightValues
import io.github.timt.windhover.core.dashboard.PhoneSnapshot
import io.github.timt.windhover.core.dashboard.RateMeter
import io.github.timt.windhover.core.dashboard.statuses
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.sensors.StreamStats
import io.github.timt.windhover.core.stream.ReceivedLine

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class Connecting(
        val profile: DeviceProfile,
    ) : ConnectionState

    data class Connected(
        val profile: DeviceProfile,
    ) : ConnectionState

    data class Failed(
        val profile: DeviceProfile,
        val reason: String,
    ) : ConnectionState
}

private val ConnectionState.profile
    get() =
        when (this) {
            ConnectionState.Idle -> null
            is ConnectionState.Connecting -> profile
            is ConnectionState.Connected -> profile
            is ConnectionState.Failed -> profile
        }

data class LoggingState(
    val connection: ConnectionState = ConnectionState.Idle,
    val sessionName: String? = null,
    val linesLogged: Long = 0,
    val recent: List<ReceivedLine> = emptyList(),
    val sentences: Map<String, StreamStats> = emptyMap(),
    val values: FlightValues = FlightValues(),
    val dashboard: Dashboard = Dashboard(),
    private val sentenceRates: RateMeter = RateMeter(),
    private val sensorRates: RateMeter = RateMeter(),
) {
    fun starting(profile: DeviceProfile) = LoggingState(ConnectionState.Connecting(profile))

    fun connected(sessionName: String) =
        connection.profile?.let { copy(connection = ConnectionState.Connected(it), sessionName = sessionName) } ?: this

    fun received(
        lines: List<ReceivedLine>,
        keep: Int,
    ) = copy(
        linesLogged = linesLogged + lines.size,
        recent = (recent + lines).takeLast(keep),
        sentences =
            lines.fold(sentences) { acc, line ->
                val type = line.sentenceType()
                acc + (type to (acc[type] ?: StreamStats()).record(line.receivedAtNanos))
            },
        values = lines.fold(values, FlightValues::update),
    )

    fun tick(
        nowNanos: Long,
        phone: PhoneSnapshot?,
    ): LoggingState {
        val sentenceMeter = sentenceRates.sample(nowNanos, sentences.mapValues { it.value.count })
        val sensorMeter = sensorRates.sample(nowNanos, phone?.streams.orEmpty().mapValues { it.value.count })
        return copy(
            sentenceRates = sentenceMeter,
            sensorRates = sensorMeter,
            dashboard =
                Dashboard(
                    atNanos = nowNanos,
                    sentences = sentences.statuses(sentenceMeter.rates),
                    sensors = phone?.streams.orEmpty().statuses(sensorMeter.rates),
                    gnss = phone?.gnss,
                ),
        )
    }

    fun failed(reason: String) =
        connection.profile?.let { copy(connection = ConnectionState.Failed(it, reason)) } ?: this
}

private fun ReceivedLine.sentenceType() = text.removePrefix("$").substringBefore(',').substringBefore('*')
