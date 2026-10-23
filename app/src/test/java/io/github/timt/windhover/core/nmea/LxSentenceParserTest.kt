package io.github.timt.windhover.core.nmea

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class LxSentenceParserTest {
    @Test
    fun `parses PLXVF`() {
        val result = parseLxSentence("\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5,0,L*25")

        expectThat(result).isEqualTo(
            Success(
                Plxvf(
                    millisecondOfSecond = 250,
                    accelerationX = 0.02,
                    accelerationY = -0.01,
                    accelerationZ = 1.03,
                    vario = 1.25,
                    indicatedAirspeed = 27.80,
                    pressureAltitude = 1234.5,
                    mode = 0,
                    flapPosition = "L",
                ),
            ),
        )
    }

    @Test
    fun `fails when checksum does not match`() {
        val result = parseLxSentence("\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5,0,L*26")

        expectThat(result).isEqualTo(Failure(NmeaError.ChecksumMismatch(expected = 0x26, actual = 0x25)))
    }

    @Test
    fun `treats missing trailing PLXVF fields as absent`() {
        val result = parseLxSentence("\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5*59")

        expectThat(result).isEqualTo(
            Success(
                Plxvf(
                    millisecondOfSecond = 250,
                    accelerationX = 0.02,
                    accelerationY = -0.01,
                    accelerationZ = 1.03,
                    vario = 1.25,
                    indicatedAirspeed = 27.80,
                    pressureAltitude = 1234.5,
                    mode = null,
                    flapPosition = null,
                ),
            ),
        )
    }

    @Test
    fun `parses PLXVS`() {
        val result = parseLxSentence("\$PLXVS,12.5,0,12.8,1234.0,L*06")

        expectThat(result).isEqualTo(
            Success(
                Plxvs(
                    outsideAirTemperature = 12.5,
                    mode = 0,
                    voltage = 12.8,
                    flightRecorderPressureAltitude = 1234.0,
                    flapPosition = "L",
                ),
            ),
        )
    }

    @Test
    fun `parses LXWP0 with empty heading and wind`() {
        val result = parseLxSentence("\$LXWP0,Y,98.5,1234.5,1.2,1.3,1.1,1.0,0.9,0.8,,,*7E")

        expectThat(result).isEqualTo(
            Success(
                Lxwp0(
                    loggerStored = "Y",
                    trueAirspeedKmh = 98.5,
                    pressureAltitude = 1234.5,
                    varios = listOf(1.2, 1.3, 1.1, 1.0, 0.9, 0.8),
                    heading = null,
                    windCourse = null,
                    windSpeed = null,
                ),
            ),
        )
    }

    @Test
    fun `reports unrecognised sentences as unknown`() {
        val result = parseLxSentence("\$GPRMC,123519,A*07")

        expectThat(result).isEqualTo(Success(Unknown(type = "GPRMC", fields = listOf("123519", "A"))))
    }

    @Test
    fun `fails when a numeric field is not a number`() {
        val result = parseLxSentence("\$PLXVF,250,0.02,abc,1.03,1.25,27.80,1234.5,0,L*77")

        expectThat(result).isEqualTo(Failure(NmeaError.InvalidField(sentence = "PLXVF", index = 3, value = "abc")))
    }

    @Test
    fun `fails when checksum is missing`() {
        val result = parseLxSentence("\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5,0,L")

        expectThat(result).isEqualTo(Failure(NmeaError.Malformed("\$PLXVF,250,0.02,-0.01,1.03,1.25,27.80,1234.5,0,L")))
    }
}
