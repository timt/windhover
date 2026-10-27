package io.github.timt.windhover.core.larus

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import kotlin.math.abs
import kotlin.math.sqrt

class AirDataTest {
    @Test
    fun `PLXVF supplies indicated airspeed and pressure altitude`() {
        val lines =
            sequenceOf(
                nmeaLine(100, "PLXVF,123,0.01,0.02,1.01,0.5,25.0,1000.0,0,0"),
                nmeaLine(200, "LXWP0,Y,119.4,1717.6,0.02,,,,,,239,174,10.1"),
            )

        expectThat(lines.airData()).containsExactly(Timed(100, AirData(25.0, 1000.0)))
    }

    @Test
    fun `without PLXVF the LXWP0 airspeed and pressure altitude are used`() {
        val lines =
            sequenceOf(
                nmeaLine(200, "LXWP0,Y,108,1717.6,0.02,,,,,,239,174,10.1"),
                "300\t\$GPRMC,120045.123,A,5054.7504,N,00005.8589,E,69.61,221.69,,,,*10",
                "400\tnot a sentence",
            )

        expectThat(lines.airData()).containsExactly(Timed(200, AirData(30.0, 1717.6)))
    }

    @Test
    fun `pressures follow the standard atmosphere and Larus's own airspeed formula`() {
        val sample = AirData(indicatedAirspeed = 30.0, pressureAltitude = 1000.0)

        expectThat(abs(sample.staticPressure - 89_874.6)).isLessThan(1.0)
        expectThat(abs(sqrt(sample.dynamicPressure * 1.632) - 30.0)).isLessThan(1e-9)
        expectThat(AirData(0.0, 0.0).staticPressure).isEqualTo(101_325.0)
    }
}

fun checksum(body: String) = "%02X".format(body.fold(0) { acc, c -> acc xor c.code })

fun nmeaLine(
    at: Long,
    body: String,
) = "$at\t\$$body*${checksum(body)}"
