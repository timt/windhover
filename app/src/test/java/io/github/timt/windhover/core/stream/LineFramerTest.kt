package io.github.timt.windhover.core.stream

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty

class LineFramerTest {
    @Test
    fun `emits a complete line stamped with its receive time`() {
        val (_, lines) = LineFramer().accept("\$PLXVF,1*00\r\n".toByteArray(), receivedAtNanos = 1_000)

        expectThat(lines).containsExactly(ReceivedLine("\$PLXVF,1*00", receivedAtNanos = 1_000))
    }

    @Test
    fun `holds a partial line until the chunk that completes it`() {
        val (framer, first) = LineFramer().accept("\$PLXVF,".toByteArray(), receivedAtNanos = 1_000)
        val (_, second) = framer.accept("1*00\r\n".toByteArray(), receivedAtNanos = 2_000)

        expectThat(first).isEmpty()
        expectThat(second).containsExactly(ReceivedLine("\$PLXVF,1*00", receivedAtNanos = 2_000))
    }

    @Test
    fun `emits every line in a chunk and skips blank ones`() {
        val (_, lines) = LineFramer().accept("\$A*00\r\n\r\n\$B*00\r\n".toByteArray(), receivedAtNanos = 1_000)

        expectThat(lines.map { it.text }).containsExactly("\$A*00", "\$B*00")
    }

    @Test
    fun `accepts carriage return alone as a terminator and drops NUL padding`() {
        val (_, lines) = LineFramer().accept("\$A*00\r\u0000\$B*00\r\u0000".toByteArray(), receivedAtNanos = 1_000)

        expectThat(lines.map { it.text }).containsExactly("\$A*00", "\$B*00")
    }

    @Test
    fun `a new sentence ends an unterminated one`() {
        val (framer, first) = LineFramer().accept("\$BTINF,1".toByteArray(), receivedAtNanos = 1_000)
        val (_, second) = framer.accept("\$GPGGA,2\r".toByteArray(), receivedAtNanos = 2_000)

        expectThat(first).isEmpty()
        expectThat(second.map { it.text }).containsExactly("\$BTINF,1", "\$GPGGA,2")
    }

    @Test
    fun `discards an unterminated run longer than the maximum line length`() {
        val (framer, _) = LineFramer(maxLineLength = 8).accept("garbage-garbage".toByteArray(), receivedAtNanos = 1_000)
        val (_, lines) = framer.accept("\$A*00\r\n".toByteArray(), receivedAtNanos = 2_000)

        expectThat(lines).containsExactly(ReceivedLine("\$A*00", receivedAtNanos = 2_000))
    }
}
