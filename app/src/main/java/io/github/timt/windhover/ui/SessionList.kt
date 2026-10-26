package io.github.timt.windhover.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.timt.windhover.core.session.SessionSummary
import io.github.timt.windhover.fileProviderAuthority
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val startedAt = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.UK)

@Composable
fun SessionList(
    browser: SessionBrowser,
    onExport: (SessionSummary) -> Unit,
    onShared: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(browser.readyToShare) {
        browser.readyToShare?.let {
            context.share(it)
            onShared()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
    browser.problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (browser.sessions.isEmpty()) Text("No sessions logged yet.")
    LazyColumn {
        items(browser.sessions, key = { it.directory.name }) { session ->
            val exporting = browser.exporting == session.directory
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = browser.exporting == null) { onExport(session) }
                        .padding(vertical = 12.dp),
            ) {
                Text(session.title(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    listOfNotNull(session.profileName, session.sizeBytes.asSize(), "zipping…".takeIf { exporting })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
    }
}

private fun SessionSummary.title() =
    startedAtEpochMillis?.let { startedAt.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }
        ?: directory.name

private fun Long.asSize() =
    when {
        this >= 1_000_000 -> String.format(Locale.UK, "%.1f MB", this / 1e6)
        else -> String.format(Locale.UK, "%.0f kB", this / 1e3)
    }

private fun Context.share(zip: File) {
    val uri = FileProvider.getUriForFile(this, fileProviderAuthority, zip)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Windhover session ${zip.nameWithoutExtension}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .apply { clipData = ClipData.newRawUri(zip.name, uri) }
    startActivity(Intent.createChooser(send, "Share session"))
}
