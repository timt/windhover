package io.github.timt.windhover.core.stream

data class ReceivedLine(
    val text: String,
    val receivedAtNanos: Long,
)

private val boundary = Regex("[\r\n]|(?=\\$)")

data class LineFramer(
    private val maxLineLength: Int = 512,
    private val pending: String = "",
) {
    fun accept(
        chunk: ByteArray,
        receivedAtNanos: Long,
    ): Pair<LineFramer, List<ReceivedLine>> {
        val parts = (pending + chunk.toString(Charsets.ISO_8859_1)).replace("\u0000", "").split(boundary)
        val lines =
            parts
                .dropLast(1)
                .filter { it.isNotEmpty() }
                .map { ReceivedLine(it, receivedAtNanos) }
        val remainder = parts.last().takeIf { it.length <= maxLineLength } ?: ""
        return copy(pending = remainder) to lines
    }
}
