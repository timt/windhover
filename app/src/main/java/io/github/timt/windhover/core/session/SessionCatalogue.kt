package io.github.timt.windhover.core.session

import dev.forkhandles.result4k.valueOrNull
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

fun File.zipTo(out: OutputStream) =
    ZipOutputStream(out.buffered()).use { zip ->
        listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.forEach { file ->
            zip.putNextEntry(ZipEntry("$name/${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
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
