package io.github.timt.windhover.core.session

import io.github.timt.windhover.core.larus.nmeaLine
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.Transport
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
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

    private fun zipEntries(zip: File) =
        ZipInputStream(zip.inputStream()).use { input ->
            generateSequence { input.nextEntry }.associate { it.name to input.readBytes() }
        }

    @Test
    fun `an export adds a Larus log when the session has air data, GNSS and motion`() {
        val directory =
            session(
                header("Condor", 1_793_000_000_000),
                "lx.log" to
                    listOf(
                        nmeaLine(0, "LXWP0,Y,108,1717.6,0.02,,,,,,239,174,10.1"),
                        nmeaLine(5_000_000, "GPGGA,120045.123,5054.7504,N,00005.8589,E,1,12,10,877.4,M,,,,,0000"),
                        nmeaLine(6_000_000, "GPRMC,120045.123,A,5054.7504,N,00005.8589,E,69.61,221.69,,,,"),
                    ).joinToString("\n"),
            )
        GZIPOutputStream(directory.resolve("imu.tsv.gz").outputStream()).bufferedWriter().use { out ->
            (0L..30_000_000L step 2_500_000).forEach { at ->
                listOf("accel", "gyro", "mag").forEach { out.write("$at\t$it\t0.0\t0.0\t9.8\n") }
            }
        }

        val entries = zipEntries(directory.exportInto(root.resolve("exports")))

        expectThat(entries.keys).contains("${directory.name}/${directory.name}.lrsx", "${directory.name}/imu.tsv.gz")
        expectThat(entries.getValue("${directory.name}/${directory.name}.lrsx").size).isEqualTo(
            17 * 12 + 4 * 15 + 4 * 14 * 4,
        )
    }

    @Test
    fun `an export explains why it has no Larus log`() {
        val directory = session(header("S80", 1_793_000_000_000), "lx.log" to "abc")

        val zip = directory.exportInto(root.resolve("exports"))

        expectThat(zip.name).isEqualTo("${directory.name}.zip")
        expectThat(zipEntries(zip).mapValues { it.value.decodeToString() }).isEqualTo(
            mapOf(
                "${directory.name}/header.json" to directory.resolve("header.json").readText(),
                "${directory.name}/lx.log" to "abc",
                "${directory.name}/larus.txt" to "No Larus log: no airspeed and pressure altitude from the LX unit\n",
            ),
        )
    }
}
