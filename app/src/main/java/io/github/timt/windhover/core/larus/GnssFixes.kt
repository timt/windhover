package io.github.timt.windhover.core.larus

import io.github.timt.windhover.core.nmea.Unknown
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

const val NOMINAL_SPEED_ACCURACY = 0.2f

private const val MS_PER_KNOT = 1852.0 / 3600.0
private const val MINUTES_PER_DEGREE = 60.0
private val nmeaDate = DateTimeFormatter.ofPattern("ddMMyy")
private val nmeaTime = DateTimeFormatter.ofPattern("HHmmss[.SSS][.SS][.S]")

private data class Measured(
    val utc: Instant,
    val latitude: Double,
    val longitude: Double,
    val mslAltitude: Double,
    val groundSpeed: Double,
    val track: Double,
    val speedAccuracy: Float,
    val satellites: Int,
    val geoidSeparation: Double,
)

fun Sequence<String>.lxGnss(sessionDate: LocalDate): List<Timed<GnssFix>> =
    mapNotNull { it.lxSentence() }
        .mapNotNull { (at, sentence) ->
            (sentence as? Unknown)?.takeIf { it.type in setOf("GPRMC", "GPGGA") }?.let { Timed(at, it) }
        }.groupBy { it.value.fields.firstOrNull() }
        .values
        .mapNotNull { sentences -> sentences.measured(sessionDate) }
        .sortedBy { it.atNanos }
        .withClimb()

fun Sequence<String>.phoneGnss(): List<Timed<GnssFix>> =
    drop(1)
        .mapNotNull { it.phoneMeasurement() }
        .toList()
        .withClimb()

private fun List<Timed<Unknown>>.measured(sessionDate: LocalDate): Timed<Measured>? {
    val rmc = lastOrNull { it.value.type == "GPRMC" && it.value.fields.getOrNull(1) == "A" } ?: return null
    val gga = lastOrNull { it.value.type == "GPGGA" } ?: return null
    val r = rmc.value.fields
    val g = gga.value.fields
    val date = r.getOrNull(8)?.ifEmpty { null }?.let { LocalDate.parse(it, nmeaDate) } ?: sessionDate
    return Timed(
        maxOf(rmc.atNanos, gga.atNanos),
        Measured(
            utc = LocalTime.parse(r[0], nmeaTime).atDate(date).toInstant(ZoneOffset.UTC),
            latitude = coordinate(r.getOrNull(2), r.getOrNull(3), "S") ?: return null,
            longitude = coordinate(r.getOrNull(4), r.getOrNull(5), "W") ?: return null,
            mslAltitude = g.getOrNull(8)?.toDoubleOrNull() ?: return null,
            groundSpeed = (r.getOrNull(6)?.toDoubleOrNull() ?: 0.0) * MS_PER_KNOT,
            track = r.getOrNull(7)?.toDoubleOrNull() ?: 0.0,
            speedAccuracy = NOMINAL_SPEED_ACCURACY,
            satellites = g.getOrNull(6)?.toIntOrNull() ?: 0,
            geoidSeparation = g.getOrNull(10)?.toDoubleOrNull() ?: 0.0,
        ),
    )
}

private fun coordinate(
    value: String?,
    hemisphere: String?,
    negative: String,
): Double? {
    val number = value?.toDoubleOrNull() ?: return null
    val degrees = (number / 100).toInt()
    val decimal = degrees + (number - degrees * 100) / MINUTES_PER_DEGREE
    return if (hemisphere == negative) -decimal else decimal
}

private fun String.phoneMeasurement(): Timed<Measured>? {
    val fields = split('\t')

    fun double(index: Int) = fields.getOrNull(index)?.toDoubleOrNull()
    val altitude = double(4)
    val mslAltitude = double(5) ?: altitude ?: return null
    return Timed(
        fields[0].toLongOrNull() ?: return null,
        Measured(
            utc = Instant.ofEpochMilli(fields.getOrNull(1)?.toLongOrNull() ?: return null),
            latitude = double(2) ?: return null,
            longitude = double(3) ?: return null,
            mslAltitude = mslAltitude,
            groundSpeed = double(6) ?: 0.0,
            track = double(7) ?: 0.0,
            speedAccuracy = double(10)?.toFloat() ?: NOMINAL_SPEED_ACCURACY,
            satellites = 0,
            geoidSeparation = altitude?.let { it - mslAltitude } ?: 0.0,
        ),
    )
}

private fun List<Timed<Measured>>.withClimb(): List<Timed<GnssFix>> =
    mapIndexed { index, (at, measured) ->
        val sinkRate =
            getOrNull(index - 1)?.value?.let { previous ->
                val seconds = Duration.between(previous.utc, measured.utc).toNanos() / 1e9
                if (seconds > 0) (previous.mslAltitude - measured.mslAltitude) / seconds else 0.0
            } ?: 0.0
        Timed(at, measured.toFix(sinkRate))
    }

private fun Measured.toFix(sinkRate: Double) =
    GnssFix(
        utc = utc,
        latitude = latitude,
        longitude = longitude,
        mslAltitude = mslAltitude.toFloat(),
        velocityNed =
            Vector(
                (groundSpeed * cos(Math.toRadians(track))).toFloat(),
                (groundSpeed * sin(Math.toRadians(track))).toFloat(),
                sinkRate.toFloat(),
            ),
        speedAccuracy = speedAccuracy,
        satellites = satellites,
        geoidSeparation = geoidSeparation.toFloat(),
    )
