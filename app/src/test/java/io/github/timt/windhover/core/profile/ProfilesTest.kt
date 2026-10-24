package io.github.timt.windhover.core.profile

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

class ProfilesTest {
    private val s80 =
        DeviceProfile(
            id = "s80",
            name = "S80 at Lasham",
            glider = "ASG29E",
            lxModel = "S80",
            address = "00:11:22:33:44:55",
            transport = Transport.SPP,
        )
    private val lx9000 =
        DeviceProfile(
            id = "lx9000",
            name = "LX9000 at Dunstable",
            glider = "LS8",
            lxModel = "LX9000",
            address = "66:77:88:99:AA:BB",
            transport = Transport.SPP,
        )

    @Test
    fun `valid draft becomes a profile with trimmed fields and upper-case address`() {
        val draft = ProfileDraft(" S80 at Lasham ", "ASG29E ", "S80", "00:11:22:33:44:55".lowercase(), Transport.SPP)

        expectThat(draft.toProfile("s80")).isEqualTo(Success(s80))
    }

    @Test
    fun `blank name is rejected`() {
        val draft = ProfileDraft(" ", "ASG29E", "S80", "00:11:22:33:44:55", Transport.SPP)

        expectThat(draft.toProfile("s80")).isEqualTo(Failure(ProfileError.BlankName))
    }

    @Test
    fun `malformed Bluetooth address is rejected`() {
        val draft = ProfileDraft("S80", "ASG29E", "S80", "00:11:22:33:44", Transport.SPP)

        expectThat(draft.toProfile("s80")).isEqualTo(Failure(ProfileError.InvalidAddress("00:11:22:33:44")))
    }

    @Test
    fun `saving adds a new profile and replaces one with the same id`() {
        val renamed = s80.copy(name = "S80 in the 29")

        val profiles = Profiles().save(s80).save(lx9000).save(renamed)

        expectThat(profiles.all).containsExactly(renamed, lx9000)
    }

    @Test
    fun `last used profile is remembered and forgotten when removed`() {
        val profiles = Profiles().save(s80).save(lx9000).markUsed("lx9000")

        expectThat(profiles.lastUsed).isEqualTo(lx9000)
        expectThat(profiles.remove("lx9000").lastUsed).isNull()
        expectThat(profiles.remove("lx9000").all).containsExactly(s80)
    }

    @Test
    fun `marking an unknown profile as used changes nothing`() {
        val profiles = Profiles().save(s80)

        expectThat(profiles.markUsed("missing")).isEqualTo(profiles)
    }

    @Test
    fun `profiles survive a round trip through JSON`() {
        val profiles = Profiles().save(s80).save(lx9000).markUsed("s80")

        expectThat(profilesFromJson(profiles.toJson())).isEqualTo(Success(profiles))
    }

    @Test
    fun `unreadable JSON is reported as corrupt`() {
        expectThat(profilesFromJson("{not json")).isA<Failure<ProfileError.Corrupt>>()
    }
}
