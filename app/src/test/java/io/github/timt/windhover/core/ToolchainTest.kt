package io.github.timt.windhover.core

import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.map
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ToolchainTest {
    @Test
    fun `junit, strikt and result4k are wired in`() {
        val result: Result4k<Int, String> = Success(20).map { it * 2 }

        expectThat(result).isEqualTo(Success(40))
    }
}
