package io.github.timt.windhover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.timt.windhover.core.dashboard.Dashboard
import io.github.timt.windhover.core.dashboard.FlightValues
import io.github.timt.windhover.core.dashboard.GnssStatus
import io.github.timt.windhover.core.dashboard.Reading
import io.github.timt.windhover.core.dashboard.StreamStatus
import java.util.Locale

private const val KNOTS_PER_MS = 1.943844
private const val FEET_PER_METRE = 3.28084

@Composable
fun LiveDashboard(
    values: FlightValues,
    dashboard: Dashboard,
) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    FlightValue("Airspeed", values.airspeed, dashboard.atNanos) {
        "${(it * KNOTS_PER_MS).format("%.0f")} kt  (${it.format("%.1f")} m/s)"
    }
    FlightValue("Pressure alt", values.pressureAltitude, dashboard.atNanos) {
        "${(it * FEET_PER_METRE).format("%.0f")} ft  (${it.format("%.0f")} m)"
    }
    FlightValue("Vario", values.vario, dashboard.atNanos) {
        "${(it * KNOTS_PER_MS).format("%+.1f")} kt  (${it.format("%+.2f")} m/s)"
    }
    HorizontalDivider()
    StreamTable("LX sentences", dashboard.sentences, dashboard.atNanos)
    StreamTable("Phone sensors", dashboard.sensors, dashboard.atNanos)
    Text(dashboard.gnss.describe(dashboard.atNanos), style = MaterialTheme.typography.bodySmall)
    HorizontalDivider()
}

@Composable
private fun FlightValue(
    label: String,
    reading: Reading?,
    nowNanos: Long,
    show: (Double) -> String,
) = Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    Text(
        reading?.let { show(it.value) } ?: "—",
        modifier = Modifier.weight(2f),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        reading?.let { "${it.source} · ${age(nowNanos, it.atNanos)}" }.orEmpty(),
        modifier = Modifier.weight(1.5f),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun StreamTable(
    title: String,
    streams: List<StreamStatus>,
    nowNanos: Long,
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (streams.isEmpty()) Text("Nothing yet", style = MaterialTheme.typography.bodySmall)
    streams.forEach { stream ->
        Row(modifier = Modifier.fillMaxWidth()) {
            Cell(stream.name, 2f)
            Cell(stream.rateHz?.let { "${it.format("%.1f")} Hz" } ?: "…", 1f, TextAlign.End)
            Cell(age(nowNanos, stream.lastSeenNanos), 1f, TextAlign.End)
        }
    }
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    align: TextAlign = TextAlign.Start,
) = Text(
    text,
    modifier = Modifier.weight(weight),
    fontFamily = FontFamily.Monospace,
    style = MaterialTheme.typography.bodySmall,
    textAlign = align,
)

private fun GnssStatus?.describe(nowNanos: Long) =
    when {
        this == null -> "GNSS: location not permitted"
        else ->
            listOfNotNull(
                lastFixAtNanos?.let { at ->
                    "GNSS fix ${accuracyMetres?.let { "±${it.toDouble().format("%.0f")} m " }.orEmpty()}" +
                        age(nowNanos, at)
                } ?: "GNSS: no fix",
                "$satellites sats",
                bestCn0DbHz?.let { "best ${it.format("%.0f")} dB-Hz" },
            ).joinToString(" · ")
    }

private fun age(
    nowNanos: Long,
    atNanos: Long,
) = if (atNanos == 0L || nowNanos == 0L) "" else "${((nowNanos - atNanos).coerceAtLeast(0) / 1e9).format("%.1f")} s ago"

private fun Double.format(pattern: String) = String.format(Locale.UK, pattern, this)
