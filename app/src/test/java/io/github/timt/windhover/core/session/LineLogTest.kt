package io.github.timt.windhover.core.session

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File
import java.util.zip.GZIPInputStream

class LineLogTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `compressed logs read back intact after close`() {
        val file = root.resolve("imu.tsv.gz")
        val lines = (1..5_000).map { "$it\tgyro\t0.1\t0.2\t0.3" }

        LineLog.open(file, compressed = true, startNanos = 0).use { log -> lines.forEach { log.write(it, 0) } }

        expectThat(GZIPInputStream(file.inputStream()).bufferedReader().readLines()).isEqualTo(lines)
    }
}
