package io.github.timt.windhover.core.profile

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.io.File

class ProfileFileTest {
    @TempDir
    lateinit var dir: File

    private val profile = DeviceProfile("s80", "S80", "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP)

    @Test
    fun `missing file loads as no profiles`() {
        expectThat(ProfileFile(dir.resolve("profiles.json")).load()).isEqualTo(Success(Profiles()))
    }

    @Test
    fun `written profiles load back`() {
        val file = ProfileFile(dir.resolve("profiles.json"))
        val profiles = Profiles().save(profile).markUsed("s80")

        file.write(profiles)

        expectThat(file.load()).isEqualTo(Success(profiles))
    }

    @Test
    fun `corrupt file is reported rather than silently emptied`() {
        val target = dir.resolve("profiles.json").apply { writeText("garbage") }

        expectThat(ProfileFile(target).load()).isA<Failure<ProfileError.Corrupt>>()
        expectThat(target.readText()).isEqualTo("garbage")
    }
}
