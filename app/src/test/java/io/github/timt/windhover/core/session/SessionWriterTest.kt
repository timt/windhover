package io.github.timt.windhover.core.session

import dev.forkhandles.result4k.Success
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.stream.ReceivedLine
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File

class SessionWriterTest {
    @TempDir
    lateinit var root: File

    private val header =
        SessionHeader(
            profile = DeviceProfile("s80", "S80 at Lasham/29", "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP),
            appVersion = "0.1.0",
            phoneModel = "Google Pixel 8 Pro",
            androidVersion = "16",
            startedAtEpochMillis = 1_793_000_000_000,
            startedAtElapsedNanos = 5_000_000_000,
        )

    @Test
    fun `session directory is named from UTC start time and a filesystem-safe profile name`() {
        expectThat(header.directoryName()).isEqualTo("20261026-073320-S80-at-Lasham-29")
    }

    @Test
    fun `opening a session writes the header`() {
        SessionWriter.open(root, header).use { writer ->
            expectThat(sessionHeaderFromJson(writer.directory.resolve("header.json").readText()))
                .isEqualTo(Success(header))
        }
    }

    @Test
    fun `rewriting the header records sensors and achieved rates`() {
        val finished =
            header.copy(
                sensors = listOf(SensorInfo("gyro", "LSM6DSV Gyroscope", "STMicro", 2_500, 0.0011f, 34.9f)),
                achievedRatesHz = mapOf("gyro" to 400.0, "location" to 1.0),
            )

        SessionWriter.open(root, header).use { writer ->
            writer.writeHeader(finished)

            expectThat(sessionHeaderFromJson(writer.directory.resolve("header.json").readText()))
                .isEqualTo(Success(finished))
        }
    }

    @Test
    fun `lines are logged with their receive timestamp and nothing is lost on close`() {
        val writer = SessionWriter.open(root, header)

        writer.append(
            listOf(ReceivedLine("\$PLXVF,1*00", 6_000_000_000), ReceivedLine("\$PLXVS,2*00", 6_000_000_001)),
            6_000_000_001,
        )
        writer.append(listOf(ReceivedLine("\$LXWP0,3*00", 6_100_000_000)), 6_100_000_000)
        writer.close()

        expectThat(writer.directory.resolve("lx.log").readLines()).isEqualTo(
            listOf("6000000000\t\$PLXVF,1*00", "6000000001\t\$PLXVS,2*00", "6100000000\t\$LXWP0,3*00"),
        )
    }

    @Test
    fun `buffered lines reach disk once the flush interval has passed`() {
        SessionWriter.open(root, header, flushIntervalNanos = 1_000_000_000).use { writer ->
            writer.append(listOf(ReceivedLine("first", 5_500_000_000)), 5_500_000_000)
            writer.append(listOf(ReceivedLine("second", 6_000_000_000)), 6_000_000_000)

            expectThat(writer.directory.resolve("lx.log").readLines())
                .isEqualTo(listOf("5500000000\tfirst", "6000000000\tsecond"))
        }
    }
}
