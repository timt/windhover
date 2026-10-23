package io.github.timt.windhover.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.timt.windhover.core.stream.ReceivedLine
import java.util.Locale

@Composable
fun ProbeScreen(
    modifier: Modifier = Modifier,
    viewModel: ProbeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }

    LaunchedEffect(permitted) { if (permitted) viewModel.refreshDevices() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            !state.bluetoothAvailable -> Text("This device has no Bluetooth adapter")
            !permitted ->
                Button(onClick = { requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                    Text("Allow Bluetooth access")
                }
            state.connection == ConnectionState.Idle ->
                DeviceList(state.devices, onRefresh = viewModel::refreshDevices, onSelect = viewModel::connect)
            else -> LiveStream(state, onDisconnect = viewModel::disconnect)
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<PairedDevice>,
    onRefresh: () -> Unit,
    onSelect: (PairedDevice) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Paired devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Button(onClick = onRefresh) { Text("Refresh") }
    }
    if (devices.isEmpty()) {
        Text("No paired devices. Pair the LXNAV unit in Android Bluetooth settings first.")
    }
    LazyColumn {
        items(devices, key = { it.address }) { device ->
            Column(modifier = Modifier.fillMaxWidth().clickable { onSelect(device) }.padding(vertical = 12.dp)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun LiveStream(
    state: ProbeState,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.connection.describe(), modifier = Modifier.weight(1f))
        Button(onClick = onDisconnect) {
            Text(if (state.connection is ConnectionState.Failed) "Back" else "Disconnect")
        }
    }
    LazyColumn(reverseLayout = true) {
        items(state.lines.asReversed()) { line -> LineRow(line) }
    }
}

@Composable
private fun LineRow(line: ReceivedLine) =
    Text(
        text = "${line.receivedAtNanos.asSeconds()}  ${line.text}",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )

private fun ConnectionState.describe() =
    when (this) {
        ConnectionState.Idle -> "Not connected"
        is ConnectionState.Connecting -> "Connecting to ${device.name}…"
        is ConnectionState.Connected -> "Connected to ${device.name}"
        is ConnectionState.Failed -> "${device.name}: $reason"
    }

private fun Long.asSeconds() = String.format(Locale.UK, "%.3f", this / 1e9)
