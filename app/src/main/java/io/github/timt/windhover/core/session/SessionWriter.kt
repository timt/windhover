package io.github.timt.windhover.core.session

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.stream.ReceivedLine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Serializable
data class SessionHeader(
    val profile: DeviceProfile,
    val appVersion: String,
    val phoneModel: String,
    val androidVersion: String,
    val startedAtEpochMillis: Long,
    val startedAtElapsedNanos: Long,
    val sensors: List<SensorInfo> = emptyList(),
    val achievedRatesHz: Map<String, Double> = emptyMap(),
)

@Serializable
data class SensorInfo(
    val stream: String,
    val name: String,
    val vendor: String,
    val minDelayMicros: Int,
    val resolution: Float,
    val maximumRange: Float,
)

private val json = Json { prettyPrint = true }

private val directoryTimestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC)

private val unsafeCharacters = Regex("[^A-Za-z0-9]+")

fun SessionHeader.directoryName() =
    "${directoryTimestamp.format(Instant.ofEpochMilli(startedAtEpochMillis))}-" +
        profile.name.replace(unsafeCharacters, "-").trim('-')

fun sessionHeaderFromJson(text: String): Result4k<SessionHeader, String> =
    try {
        Success(json.decodeFromString(SessionHeader.serializer(), text))
    } catch (e: IllegalArgumentException) {
        Failure(e.message ?: "unreadable")
    }

class SessionWriter private constructor(
    val directory: File,
    private val log: LineLog,
) : Closeable {
    fun append(
        lines: List<ReceivedLine>,
        nowNanos: Long,
    ) = lines.forEach { log.write("${it.receivedAtNanos}\t${it.text}", nowNanos) }

    fun writeHeader(header: SessionHeader) = directory.writeHeader(header)

    override fun close() = log.close()

    companion object {
        fun open(
            root: File,
            header: SessionHeader,
            flushIntervalNanos: Long = 2_000_000_000,
        ): SessionWriter {
            val directory = root.resolve(header.directoryName()).apply { mkdirs() }
            directory.writeHeader(header)
            return SessionWriter(
                directory = directory,
                log =
                    LineLog.open(
                        directory.resolve("lx.log"),
                        startNanos = header.startedAtElapsedNanos,
                        flushIntervalNanos = flushIntervalNanos,
                    ),
            )
        }
    }
}

private fun File.writeHeader(header: SessionHeader) =
    resolve("header.json").writeText(json.encodeToString(SessionHeader.serializer(), header))
