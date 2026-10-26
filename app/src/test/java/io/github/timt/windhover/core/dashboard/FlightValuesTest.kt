package io.github.timt.windhover.core.dashboard

import io.github.timt.windhover.core.stream.ReceivedLine
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class FlightValuesTest {
    private val plxvf = "\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5,0,L*25"
    private val lxwp0 = "\$LXWP0,Y,101.5,447.2,-0.35,,,,,,211,006,11.7*52"

    @Test
    fun `PLXVF supplies indicated airspeed, pressure altitude and vario`() {
        val values = FlightValues().update(ReceivedLine(plxvf, 1_000))

        expectThat(values).isEqualTo(
            FlightValues(
                airspeed = Reading(27.80, "PLXVF IAS", 1_000),
                pressureAltitude = Reading(1234.5, "PLXVF", 1_000),
                vario = Reading(1.25, "PLXVF", 1_000),
            ),
        )
    }

    @Test
    fun `LXWP0 fills in with true airspeed converted to metres per second when there is no PLXVF`() {
        val values = FlightValues().update(ReceivedLine(lxwp0, 1_000))

        expectThat(values.airspeed).isEqualTo(Reading(101.5 / 3.6, "LXWP0 TAS", 1_000))
        expectThat(values.pressureAltitude).isEqualTo(Reading(447.2, "LXWP0", 1_000))
        expectThat(values.vario).isEqualTo(Reading(-0.35, "LXWP0", 1_000))
    }

    @Test
    fun `LXWP0 does not replace PLXVF values received in the last two seconds`() {
        val fromPlxvf = FlightValues().update(ReceivedLine(plxvf, 1_000_000_000))

        expectThat(fromPlxvf.update(ReceivedLine(lxwp0, 2_900_000_000))).isEqualTo(fromPlxvf)
        expectThat(fromPlxvf.update(ReceivedLine(lxwp0, 3_100_000_000)).airspeed?.source).isEqualTo("LXWP0 TAS")
    }

    @Test
    fun `lines that are not valid LX sentences change nothing`() {
        val values = FlightValues().update(ReceivedLine(plxvf, 1_000))

        expectThat(values.update(ReceivedLine("\$GPGGA,121142.353,5052.2832,N*00", 2_000))).isEqualTo(values)
        expectThat(values.update(ReceivedLine("\$PLXVF,250,0.02*00", 2_000))).isEqualTo(values)
    }
}
