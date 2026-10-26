package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.Transport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

class SessionCatalogueTest {
    @TempDir
    lateinit var root: File

    private fun header(
        name: String,
        startedAt: Long,
    ) = SessionHeader(
        profile = DeviceProfile(name, name, "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP),
        appVersion = "0.1.0",
        phoneModel = "Pixel",
        androidVersion = "16",
        startedAtEpochMillis = startedAt,
        startedAtElapsedNanos = 0,
    )

    private fun session(
        header: SessionHeader,
        vararg files: Pair<String, String>,
    ) = SessionWriter.open(root, header).use { writer ->
        files.forEach { (name, text) -> writer.directory.resolve(name).writeText(text) }
        writer.directory
    }

    @Test
    fun `sessions are listed newest first with profile, start time and total size`() {
        val older = session(header("S80", 1_793_000_000_000), "lx.log" to "abc")
        val newer = session(header("LX9000", 1_793_000_100_000), "lx.log" to "abcdef", "location.tsv" to "xy")

        expectThat(root.sessions()).containsExactly(
            SessionSummary(newer, "LX9000", 1_793_000_100_000, newer.listFiles()!!.sumOf { it.length() }),
            SessionSummary(older, "S80", 1_793_000_000_000, older.listFiles()!!.sumOf { it.length() }),
        )
    }

    @Test
    fun `a session whose header cannot be read is still listed by its directory name`() {
        val broken = root.resolve("20261025-081514-Naviter-BLE").apply { mkdirs() }
        broken.resolve("header.json").writeText("{")
        broken.resolve("lx.log").writeText("abcd")

        expectThat(root.sessions()).containsExactly(SessionSummary(broken, null, null, 5))
    }

    @Test
    fun `stray files beside the session directories are ignored`() {
        root.resolve("notes.txt").writeText("hello")

        expectThat(root.sessions()).isEqualTo(emptyList())
    }

    @Test
    fun `a missing sessions root lists nothing`() {
        expectThat(root.resolve("absent").sessions()).isEqualTo(emptyList())
    }

    @Test
    fun `a session zips into a folder named after its directory`() {
        val directory = session(header("S80", 1_793_000_000_000), "lx.log" to "abc", "location.tsv" to "xy")
        val zip = ByteArrayOutputStream().also { directory.zipTo(it) }

        val entries =
            ZipInputStream(zip.toByteArray().inputStream()).use { input ->
                generateSequence { input.nextEntry }.associate { it.name to input.readBytes().decodeToString() }
            }

        expectThat(entries).isEqualTo(
            directory.listFiles()!!.associate { "${directory.name}/${it.name}" to it.readText() },
        )
    }
}
