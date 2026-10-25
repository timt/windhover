package io.github.timt.windhover.core.scan

import io.github.timt.windhover.core.profile.Transport
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class NearbyDevicesTest {
    @Test
    fun `repeat sightings are merged by address keeping the latest known name and pairing`() {
        val devices =
            NearbyDevices()
                .found(NearbyDevice(name = null, address = "00:11:22:33:44:55", paired = false))
                .found(NearbyDevice(name = "S80", address = "00:11:22:33:44:55", paired = false))
                .found(NearbyDevice(name = null, address = "00:11:22:33:44:55", paired = true))

        expectThat(devices.ordered).containsExactly(NearbyDevice("S80", "00:11:22:33:44:55", paired = true))
    }

    @Test
    fun `likely LXNAV units come first, then named devices, then unnamed`() {
        val headphones = NearbyDevice("Pixel Buds", "AA:AA:AA:AA:AA:01", paired = false)
        val unnamed = NearbyDevice(null, "AA:AA:AA:AA:AA:02", paired = false)
        val lx9000 = NearbyDevice("LX9000", "AA:AA:AA:AA:AA:03", paired = false)
        val s80 = NearbyDevice("S80-1234", "AA:AA:AA:AA:AA:04", paired = false)

        val devices = listOf(unnamed, headphones, lx9000, s80).fold(NearbyDevices()) { acc, d -> acc.found(d) }

        expectThat(devices.ordered).containsExactly(lx9000, s80, headphones, unnamed)
    }

    @Test
    fun `LXNAV names are recognised case-insensitively`() {
        expectThat(listOf("LX9000", "lx80", "LXNAV S100", "S80", "s10-2").map { it.looksLikeLxnav() })
            .isEqualTo(List(5) { true })
        expectThat("Samsung TV".looksLikeLxnav()).isFalse()
        expectThat("Galaxy Buds".looksLikeLxnav()).isFalse()
    }

    @Test
    fun `the same address seen over Classic and BLE is listed once per transport`() {
        val classic = NearbyDevice("S80", "00:11:22:33:44:55", paired = false)
        val ble = NearbyDevice("S80", "00:11:22:33:44:55", paired = false, transport = Transport.BLE)

        expectThat(NearbyDevices().found(classic).found(ble).ordered).containsExactly(classic, ble)
    }

    @Test
    fun `a new scan forgets earlier sightings`() {
        val devices = NearbyDevices().found(NearbyDevice("S80", "00:11:22:33:44:55", paired = false)).restarted()

        expectThat(devices).isEqualTo(NearbyDevices(scanning = true))
        expectThat(devices.scanning).isTrue()
    }
}
