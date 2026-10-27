package io.github.timt.windhover.core.larus

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isLessThan
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs

class GnssFixesTest {
    private val sessionDate = LocalDate.parse("2026-10-26")

    @Test
    fun `matching GPRMC and GPGGA sentences become one fix timed by the later of the two`() {
        val lines =
            sequenceOf(
                nmeaLine(100, "GPGGA,120045.250,5054.7500,N,00005.8500,W,1,12,1.0,877.4,M,45.6,M,,"),
                nmeaLine(200, "GPRMC,120045.250,A,5054.7500,N,00005.8500,W,10.0,90.0,,,,"),
            )

        val fixes = lines.lxGnss(sessionDate)

        expectThat(fixes).hasSize(1)
        val (at, fix) = fixes.single()
        expectThat(at).isEqualTo(200)
        expectThat(fix.utc).isEqualTo(Instant.parse("2026-10-26T12:00:45.250Z"))
        expectThat(abs(fix.latitude - 50.9125)).isLessThan(1e-9)
        expectThat(abs(fix.longitude + 0.0975)).isLessThan(1e-9)
        expectThat(fix.mslAltitude).isEqualTo(877.4f)
        expectThat(abs(fix.velocityNed.x)).isLessThan(1e-5f)
        expectThat(fix.velocityNed.y).isEqualTo(5.1444445f)
        expectThat(fix.velocityNed.z).isEqualTo(0f)
        expectThat(fix.satellites).isEqualTo(12)
        expectThat(fix.geoidSeparation).isEqualTo(45.6f)
    }

    @Test
    fun `a date in GPRMC wins over the session date and climb comes from successive altitudes`() {
        val lines =
            sequenceOf(
                nmeaLine(100, "GPGGA,235959.000,5054.7500,N,00005.8500,E,1,8,1.0,100.0,M,,M,,"),
                nmeaLine(110, "GPRMC,235959.000,A,5054.7500,N,00005.8500,E,0.0,0.0,251026,,,"),
                nmeaLine(1100, "GPRMC,000001.000,A,5054.7500,N,00005.8500,E,0.0,0.0,261026,,,"),
                nmeaLine(1110, "GPGGA,000001.000,5054.7500,N,00005.8500,E,1,8,1.0,104.0,M,,M,,"),
            )

        expectThat(lines.lxGnss(sessionDate).map { it.value.utc to it.value.velocityNed.z }).containsExactly(
            Instant.parse("2026-10-25T23:59:59Z") to 0f,
            Instant.parse("2026-10-26T00:00:01Z") to -2f,
        )
    }

    @Test
    fun `phone fixes come from location tsv with velocity from speed and bearing`() {
        val lines =
            sequenceOf(
                "elapsedRealtimeNanos\tutcMillis\tlatitude\tlongitude\taltitude\tmslAltitude\tspeed\tbearing\taccuracy",
                "1000\t1793037560573\t50.8\t-0.15\t97.4\t52.2\t20.0\t180.0\t5.0\t3.0\t0.4\t2.0\t3.0",
                "2000000000\t1793037561573\t50.8\t-0.15\t95.4\t50.2\t0.0\t\t5.0\t3.0\t\t\t",
                "3000000000\t1793037562573\t50.8\t-0.15\t97.4\t\t0.0\t\t5.0\t3.0\t\t\t",
            )

        val fixes = lines.phoneGnss()

        expectThat(fixes.map { it.atNanos }).containsExactly(1000L, 2_000_000_000L, 3_000_000_000L)
        val first = fixes.first().value
        expectThat(first.utc).isEqualTo(Instant.ofEpochMilli(1793037560573))
        expectThat(first.mslAltitude).isEqualTo(52.2f)
        expectThat(first.velocityNed.x).isEqualTo(-20f)
        expectThat(abs(first.velocityNed.y)).isLessThan(1e-5f)
        expectThat(first.speedAccuracy).isEqualTo(0.4f)
        expectThat(abs(first.geoidSeparation - 45.2f)).isLessThan(1e-4f)
        expectThat(fixes[1].value.velocityNed).isEqualTo(Vector(0f, 0f, 2f))
        expectThat(fixes[1].value.speedAccuracy).isEqualTo(NOMINAL_SPEED_ACCURACY)
        expectThat(fixes.last().value.mslAltitude).isEqualTo(97.4f)
    }
}
