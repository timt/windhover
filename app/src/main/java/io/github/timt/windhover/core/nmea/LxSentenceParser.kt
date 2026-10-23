package io.github.timt.windhover.core.nmea

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success
import dev.forkhandles.result4k.flatMap

sealed interface LxSentence

data class Plxvf(
    val millisecondOfSecond: Int?,
    val accelerationX: Double?,
    val accelerationY: Double?,
    val accelerationZ: Double?,
    val vario: Double?,
    val indicatedAirspeed: Double?,
    val pressureAltitude: Double?,
    val mode: Int?,
    val flapPosition: String?,
) : LxSentence

data class Plxvs(
    val outsideAirTemperature: Double?,
    val mode: Int?,
    val voltage: Double?,
    val flightRecorderPressureAltitude: Double?,
    val flapPosition: String?,
) : LxSentence

data class Lxwp0(
    val loggerStored: String?,
    val trueAirspeedKmh: Double?,
    val pressureAltitude: Double?,
    val varios: List<Double>,
    val heading: Double?,
    val windCourse: Double?,
    val windSpeed: Double?,
) : LxSentence

data class Unknown(
    val type: String,
    val fields: List<String>,
) : LxSentence

sealed interface NmeaError {
    data class ChecksumMismatch(
        val expected: Int,
        val actual: Int,
    ) : NmeaError

    data class Malformed(
        val line: String,
    ) : NmeaError

    data class InvalidField(
        val sentence: String,
        val index: Int,
        val value: String,
    ) : NmeaError
}

private val sentenceFormat = Regex("""^\$([^*]*)\*([0-9A-Fa-f]{2})$""")

fun parseLxSentence(line: String): Result4k<LxSentence, NmeaError> = verifiedBody(line).flatMap(::decode)

private fun verifiedBody(line: String): Result4k<String, NmeaError> =
    sentenceFormat.matchEntire(line)?.destructured?.let { (body, checksum) ->
        val expected = checksum.toInt(16)
        val actual = body.fold(0) { acc, c -> acc xor c.code }
        if (expected == actual) Success(body) else Failure(NmeaError.ChecksumMismatch(expected, actual))
    } ?: Failure(NmeaError.Malformed(line))

private fun decode(body: String): Result4k<LxSentence, NmeaError> {
    val fields = Fields(body.split(','))
    val sentence =
        when (fields.type) {
            "PLXVF" -> fields.toPlxvf()
            "PLXVS" -> fields.toPlxvs()
            "LXWP0" -> fields.toLxwp0()
            else -> Unknown(fields.type, fields.rest)
        }
    return fields.firstError?.let(::Failure) ?: Success(sentence)
}

private fun Fields.toPlxvf() =
    Plxvf(
        millisecondOfSecond = int(1),
        accelerationX = double(2),
        accelerationY = double(3),
        accelerationZ = double(4),
        vario = double(5),
        indicatedAirspeed = double(6),
        pressureAltitude = double(7),
        mode = int(8),
        flapPosition = text(9),
    )

private fun Fields.toPlxvs() =
    Plxvs(
        outsideAirTemperature = double(1),
        mode = int(2),
        voltage = double(3),
        flightRecorderPressureAltitude = double(4),
        flapPosition = text(5),
    )

private fun Fields.toLxwp0() =
    Lxwp0(
        loggerStored = text(1),
        trueAirspeedKmh = double(2),
        pressureAltitude = double(3),
        varios = (4..9).mapNotNull(::double),
        heading = double(10),
        windCourse = double(11),
        windSpeed = double(12),
    )

private class Fields(
    private val values: List<String>,
) {
    val type = values.first()

    val rest = values.drop(1)

    var firstError: NmeaError? = null
        private set

    fun text(index: Int) = values.getOrNull(index)?.ifEmpty { null }

    fun double(index: Int) = number(index, String::toDoubleOrNull)

    fun int(index: Int) = number(index, String::toIntOrNull)

    private fun <T> number(
        index: Int,
        parse: (String) -> T?,
    ): T? =
        text(index)?.let { value ->
            parse(value) ?: null.also {
                firstError = firstError ?: NmeaError.InvalidField(type, index, value)
            }
        }
}
