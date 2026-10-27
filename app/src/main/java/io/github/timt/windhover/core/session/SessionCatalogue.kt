package io.github.timt.windhover.core.session

import dev.forkhandles.result4k.map
import dev.forkhandles.result4k.recover
import dev.forkhandles.result4k.valueOrNull
import io.github.timt.windhover.core.larus.describe
import io.github.timt.windhover.core.larus.writeLarusLog
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class SessionSummary(
    val directory: File,
    val profileName: String?,
    val startedAtEpochMillis: Long?,
    val sizeBytes: Long,
)

fun File.sessions(): List<SessionSummary> =
    listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .sortedByDescending { it.name }
        .map { it.summary() }

fun File.zipTo(
    out: OutputStream,
    extras: List<File> = emptyList(),
) = ZipOutputStream(out.buffered()).use { zip ->
    (listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name } + extras).forEach { file ->
        zip.putNextEntry(ZipEntry("$name/${file.name}"))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}

fun File.exportInto(exports: File): File {
    exports.deleteRecursively()
    exports.mkdirs()
    val larus = exports.resolve("$name.lrsx")
    val extra =
        larus.outputStream().use { writeLarusLog(it) }.map { larus }.recover { problem ->
            larus.delete()
            exports.resolve("larus.txt").apply { writeText("No Larus log: ${problem.describe()}\n") }
        }
    return exports.resolve("$name.zip").also { zip -> zip.outputStream().use { zipTo(it, listOf(extra)) } }
}

private fun File.summary(): SessionSummary {
    val header =
        resolve("header.json").takeIf { it.isFile }?.let { sessionHeaderFromJson(it.readText()).valueOrNull() }
    return SessionSummary(
        directory = this,
        profileName = header?.profile?.name,
        startedAtEpochMillis = header?.startedAtEpochMillis,
        sizeBytes = walk().filter { it.isFile }.sumOf { it.length() },
    )
}
