package io.github.timt.windhover.core.larus

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.valueOrNull
import io.github.timt.windhover.core.session.sessionHeaderFromJson
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

fun File.writeLarusLog(out: OutputStream): Result4k<LarusLog, LarusProblem> {
    val header =
        resolve("header.json").takeIf { it.isFile }?.let { sessionHeaderFromJson(it.readText()).valueOrNull() }
            ?: return Failure(LarusProblem.NoHeader)
    val sessionDate = Instant.ofEpochMilli(header.startedAtEpochMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val lx = resolve("lx.log")
    val airData = lx.useLinesOrEmpty { it.airData() }
    val phoneGnss = { resolve("location.tsv").useLinesOrEmpty { it.phoneGnss() } }
    val gnss = lx.useLinesOrEmpty { it.lxGnss(sessionDate) }.ifEmpty { phoneGnss() }
    val imu = resolve("imu.tsv.gz")
    return if (imu.isFile) {
        GZIPInputStream(imu.inputStream()).bufferedReader().useLines { writeLarusLog(it.motion(), airData, gnss, out) }
    } else {
        writeLarusLog(emptySequence(), airData, gnss, out)
    }
}

fun LarusProblem.describe() =
    when (this) {
        LarusProblem.NoHeader -> "header.json is missing or unreadable"
        LarusProblem.NoAirData -> "no airspeed and pressure altitude from the LX unit"
        LarusProblem.NoGnss -> "no GNSS fixes"
        LarusProblem.NoMotion -> "no phone motion sensor data"
    }

private fun <T> File.useLinesOrEmpty(read: (Sequence<String>) -> List<T>) =
    takeIf { it.isFile }?.useLines(block = read).orEmpty()
