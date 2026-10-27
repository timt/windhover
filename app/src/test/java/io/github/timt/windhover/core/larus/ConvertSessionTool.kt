package io.github.timt.windhover.core.larus

import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import strikt.api.expectThat
import strikt.assertions.isA
import java.io.File

@EnabledIfEnvironmentVariable(named = "WINDHOVER_SESSION", matches = ".+")
class ConvertSessionTool {
    @Test
    fun `convert the session in WINDHOVER_SESSION to a Larus log beside it`() {
        val session = File(System.getenv("WINDHOVER_SESSION"))

        val result = session.resolveSibling("${session.name}.lrsx").outputStream().use { session.writeLarusLog(it) }

        println("${session.name}: $result")
        expectThat(result).isA<Success<LarusLog>>()
    }
}
