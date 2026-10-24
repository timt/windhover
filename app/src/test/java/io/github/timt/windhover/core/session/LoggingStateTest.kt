package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.stream.ReceivedLine
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class LoggingStateTest {
    private val profile = DeviceProfile("s80", "S80", "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP)

    @Test
    fun `starting a session clears previous lines and counts`() {
        val previous = LoggingState(linesLogged = 9, recent = listOf(ReceivedLine("old", 1)))

        expectThat(previous.starting(profile, "20261024-094500-S80"))
            .isEqualTo(LoggingState(ConnectionState.Connecting(profile), "20261024-094500-S80"))
    }

    @Test
    fun `connecting session becomes connected`() {
        expectThat(LoggingState().starting(profile, "dir").connected().connection)
            .isEqualTo(ConnectionState.Connected(profile))
    }

    @Test
    fun `received lines are counted and only the most recent kept`() {
        val lines = (1..5).map { ReceivedLine("line $it", it.toLong()) }

        val state = LoggingState().received(lines.take(3), keep = 4).received(lines.drop(3), keep = 4)

        expectThat(state.linesLogged).isEqualTo(5)
        expectThat(state.recent).isEqualTo(lines.drop(1))
    }

    @Test
    fun `failure keeps the profile and reason`() {
        expectThat(LoggingState().starting(profile, "dir").failed("Connection closed by device").connection)
            .isEqualTo(ConnectionState.Failed(profile, "Connection closed by device"))
    }

    @Test
    fun `failure with no session in progress changes nothing`() {
        expectThat(LoggingState().failed("boom")).isEqualTo(LoggingState())
    }
}
