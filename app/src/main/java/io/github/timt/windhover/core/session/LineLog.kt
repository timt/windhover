package io.github.timt.windhover.core.session

import java.io.Closeable
import java.io.File
import java.io.Writer
import java.util.zip.GZIPOutputStream

private const val BUFFER_BYTES = 64 * 1024

class LineLog private constructor(
    private val out: Writer,
    private val flushIntervalNanos: Long,
    private var lastFlushNanos: Long,
) : Closeable {
    fun write(
        line: String,
        nowNanos: Long,
    ) {
        out.write(line)
        out.write("\n")
        if (nowNanos - lastFlushNanos >= flushIntervalNanos) {
            out.flush()
            lastFlushNanos = nowNanos
        }
    }

    override fun close() = out.close()

    companion object {
        fun open(
            file: File,
            compressed: Boolean = false,
            startNanos: Long,
            flushIntervalNanos: Long = 2_000_000_000,
        ) = LineLog(
            out =
                file
                    .outputStream()
                    .let { if (compressed) GZIPOutputStream(it, BUFFER_BYTES, true) else it }
                    .writer()
                    .buffered(BUFFER_BYTES),
            flushIntervalNanos = flushIntervalNanos,
            lastFlushNanos = startNanos,
        )
    }
}
