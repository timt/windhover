package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.dashboard.Dashboard
import io.github.timt.windhover.core.dashboard.GnssStatus
import io.github.timt.windhover.core.dashboard.PhoneSnapshot
import io.github.timt.windhover.core.dashboard.StreamStatus
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.sensors.StreamStats
import io.github.timt.windhover.core.stream.ReceivedLine
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class LoggingStateTest {
    private val profile = DeviceProfile("s80", "S80", "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP)

    @Test
    fun `starting a session clears previous lines and counts`() {
        val previous = LoggingState(linesLogged = 9, recent = listOf(ReceivedLine("old", 1)))

        expectThat(previous.starting(profile)).isEqualTo(LoggingState(ConnectionState.Connecting(profile)))
    }

    @Test
    fun `session is only named once connected`() {
        expectThat(LoggingState().starting(profile).connected("20261024-094500-S80"))
            .isEqualTo(LoggingState(ConnectionState.Connected(profile), "20261024-094500-S80"))
    }

    @Test
    fun `received lines are counted and only the most recent kept`() {
        val lines = (1..5).map { ReceivedLine("line $it", it.toLong()) }

        val state = LoggingState().received(lines.take(3), keep = 4).received(lines.drop(3), keep = 4)

        expectThat(state.linesLogged).isEqualTo(5)
        expectThat(state.recent).isEqualTo(lines.drop(1))
    }

    @Test
    fun `received lines are tallied by sentence type and decoded into flight values`() {
        val state =
            LoggingState().received(
                listOf(
                    ReceivedLine("\$LXWP0,Y,101.5,447.2,-0.35,,,,,,211,006,11.7*52", 1_000),
                    ReceivedLine("\$GPGGA,121142.353,5052.2832,N*00", 2_000),
                    ReceivedLine("\$LXWP0,Y,101.5,447.2,-0.35,,,,,,211,006,11.7*52", 3_000),
                ),
                keep = 10,
            )

        expectThat(state.sentences).isEqualTo(
            mapOf(
                "LXWP0" to StreamStats(2, 1_000, 3_000),
                "GPGGA" to StreamStats(1, 2_000, 2_000),
            ),
        )
        expectThat(state.values.pressureAltitude?.value).isEqualTo(447.2)
    }

    @Test
    fun `each tick snapshots sentence and phone sensor rates with when each was last seen`() {
        val gnss =
            GnssStatus(lastFixAtNanos = 1_500_000_000, accuracyMetres = 4.0f, satellites = 18, bestCn0DbHz = 41.0)
        val line = ReceivedLine("\$LXWP0,Y,101.5,447.2,-0.35,,,,,,211,006,11.7*52", 1_900_000_000)

        val state =
            LoggingState()
                .tick(0, PhoneSnapshot(mapOf("accel" to StreamStats()), null))
                .received(listOf(line), keep = 10)
                .tick(2_000_000_000, PhoneSnapshot(mapOf("accel" to StreamStats(800, 0, 1_999_000_000)), gnss))

        expectThat(state.dashboard).isEqualTo(
            Dashboard(
                atNanos = 2_000_000_000,
                sentences = listOf(StreamStatus("LXWP0", 0.5, 1_900_000_000)),
                sensors = listOf(StreamStatus("accel", 400.0, 1_999_000_000)),
                gnss = gnss,
            ),
        )
    }

    @Test
    fun `failure keeps the profile and reason`() {
        expectThat(LoggingState().starting(profile).failed("Connection closed by device").connection)
            .isEqualTo(ConnectionState.Failed(profile, "Connection closed by device"))
    }

    @Test
    fun `failure with no session in progress changes nothing`() {
        expectThat(LoggingState().failed("boom")).isEqualTo(LoggingState())
    }
}
