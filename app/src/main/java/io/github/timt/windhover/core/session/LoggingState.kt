package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.profile.DeviceProfile
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
) {
    fun starting(profile: DeviceProfile) = LoggingState(ConnectionState.Connecting(profile))

    fun connected(sessionName: String) =
        connection.profile?.let { copy(connection = ConnectionState.Connected(it), sessionName = sessionName) } ?: this

    fun received(
        lines: List<ReceivedLine>,
        keep: Int,
    ) = copy(linesLogged = linesLogged + lines.size, recent = (recent + lines).takeLast(keep))

    fun failed(reason: String) =
        connection.profile?.let { copy(connection = ConnectionState.Failed(it, reason)) } ?: this
}
