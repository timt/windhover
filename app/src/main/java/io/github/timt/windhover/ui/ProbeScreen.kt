package io.github.timt.windhover.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.ProfileDraft
import io.github.timt.windhover.core.profile.Profiles
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.scan.NearbyDevice
import io.github.timt.windhover.core.scan.NearbyDevices
import io.github.timt.windhover.core.session.ConnectionState
import io.github.timt.windhover.core.session.LoggingState
import io.github.timt.windhover.core.stream.ReceivedLine
import java.util.Locale

private val knownModels = listOf("S80", "LX9000")

private val requestedPermissions =
    listOfNotNull(
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
    ).toTypedArray()

private val locationPermissions =
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

@Composable
fun ProbeScreen(
    modifier: Modifier = Modifier,
    viewModel: ProbeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logging by viewModel.logging.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permitted by remember { mutableStateOf(context.granted(Manifest.permission.BLUETOOTH_CONNECT)) }
    val requestScan =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viewModel.scan() }
    val requestPermissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permitted = it[Manifest.permission.BLUETOOTH_CONNECT] == true
        }

    var pendingConnect by remember { mutableStateOf<DeviceProfile?>(null) }
    val requestLocation =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            pendingConnect?.let(viewModel::connect)
            pendingConnect = null
        }

    LaunchedEffect(permitted) { if (permitted) viewModel.refreshDevices() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val editor = state.editor
        when {
            !state.bluetoothAvailable -> Text("This device has no Bluetooth adapter")
            !permitted ->
                Button(onClick = { requestPermissions.launch(requestedPermissions) }) {
                    Text("Allow Bluetooth access")
                }
            editor != null ->
                ProfileForm(
                    editor = editor,
                    devices = state.devices,
                    nearby = state.nearby,
                    onChange = viewModel::updateDraft,
                    onScan = {
                        if (context.granted(Manifest.permission.BLUETOOTH_SCAN)) {
                            viewModel.scan()
                        } else {
                            requestScan.launch(Manifest.permission.BLUETOOTH_SCAN)
                        }
                    },
                    onPickNearby = viewModel::pickNearby,
                    onSave = viewModel::saveProfile,
                    onCancel = viewModel::cancelEdit,
                    onDelete = viewModel::deleteProfile,
                )
            logging.connection == ConnectionState.Idle ->
                ProfileList(
                    profiles = state.profiles,
                    problem = state.profilesProblem,
                    onAdd = viewModel::newProfile,
                    onEdit = viewModel::editProfile,
                    onConnect = { profile ->
                        if (context.granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            viewModel.connect(profile)
                        } else {
                            pendingConnect = profile
                            requestLocation.launch(locationPermissions)
                        }
                    },
                )
            else -> LiveStream(logging, onDisconnect = viewModel::disconnect)
        }
    }
}

@Composable
private fun ProfileList(
    profiles: Profiles,
    problem: String?,
    onAdd: () -> Unit,
    onEdit: (DeviceProfile) -> Unit,
    onConnect: (DeviceProfile) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Profiles", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Button(onClick = onAdd) { Text("Add profile") }
    }
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (profiles.all.isEmpty()) {
        Text("No profiles yet. Pair the LXNAV unit in Android Bluetooth settings, then add a profile for it.")
    }
    val ordered = listOfNotNull(profiles.lastUsed) + profiles.all.filterNot { it == profiles.lastUsed }
    LazyColumn {
        items(ordered, key = { it.id }) { profile ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onConnect(profile) }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        profile.summary(lastUsed = profile == profiles.lastUsed),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { onEdit(profile) }) { Text("Edit") }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ProfileForm(
    editor: ProfileEditor,
    devices: List<PairedDevice>,
    nearby: NearbyDevices,
    onChange: (ProfileDraft) -> Unit,
    onScan: () -> Unit,
    onPickNearby: (NearbyDevice) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val draft = editor.draft
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (editor.id == null) "New profile" else "Edit profile", style = MaterialTheme.typography.titleMedium)
        DraftField("Name", draft.name) { onChange(draft.copy(name = it)) }
        DraftField("Glider", draft.glider) { onChange(draft.copy(glider = it)) }
        DraftField("LX model", draft.lxModel) { onChange(draft.copy(lxModel = it)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            knownModels.forEach { model ->
                FilterChip(draft.lxModel == model, { onChange(draft.copy(lxModel = model)) }, label = { Text(model) })
            }
        }
        Text("Transport", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Transport.entries.forEach { transport ->
                FilterChip(
                    draft.transport == transport,
                    { onChange(draft.copy(transport = transport)) },
                    label = { Text(transport.name) },
                )
            }
        }
        DraftField("Bluetooth address", draft.address) { onChange(draft.copy(address = it)) }
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            editor.id?.let { id -> TextButton(onClick = { onDelete(id) }) { Text("Delete") } }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Nearby devices", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onScan, enabled = !nearby.scanning) {
                Text(if (nearby.scanning) "Scanning…" else "Scan")
            }
        }
        nearby.ordered.forEach { device ->
            DeviceRow(
                title = device.name ?: "Unnamed device",
                subtitle = device.describe(),
                onClick = { onPickNearby(device) },
            )
        }
        Text("Paired devices", style = MaterialTheme.typography.labelLarge)
        if (devices.isEmpty()) Text("No paired devices yet. Scan above to find the LXNAV unit.")
        devices.forEach { device ->
            DeviceRow(device.name, device.address, onClick = { onChange(draft.withDevice(device)) })
        }
    }
}

@Composable
private fun DeviceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) = Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
    Text(title, style = MaterialTheme.typography.bodyLarge)
    Text(subtitle, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) = OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true)

@Composable
private fun LiveStream(
    state: LoggingState,
    onDisconnect: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(state.connection.describe(), modifier = Modifier.weight(1f))
        Button(onClick = onDisconnect) {
            Text(if (state.connection is ConnectionState.Failed) "Back" else "Stop")
        }
    }
    state.sessionName?.let {
        Text(
            "Session $it · ${state.linesLogged} lines",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.connection is ConnectionState.Connected) LiveDashboard(state.values, state.dashboard)
    LazyColumn(reverseLayout = true) {
        items(state.recent.asReversed()) { line -> LineRow(line) }
    }
}

@Composable
private fun LineRow(line: ReceivedLine) =
    Text(
        text = "${line.receivedAtNanos.asSeconds()}  ${line.text}",
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )

private fun NearbyDevice.describe() =
    when {
        transport == Transport.BLE -> "$address · BLE"
        paired -> "$address · paired"
        else -> "$address · tap to pair"
    }

private fun Context.granted(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun DeviceProfile.summary(lastUsed: Boolean) =
    listOf(glider, lxModel, transport.name, address, "last used".takeIf { lastUsed })
        .filterNot { it.isNullOrBlank() }
        .joinToString(" · ")

private fun ProfileDraft.withDevice(device: PairedDevice) =
    copy(address = device.address, name = name.ifBlank { device.name })

private fun ConnectionState.describe() =
    when (this) {
        ConnectionState.Idle -> "Not connected"
        is ConnectionState.Connecting -> "Connecting to ${profile.name}…"
        is ConnectionState.Connected -> "Logging ${profile.name}"
        is ConnectionState.Failed -> "${profile.name}: $reason"
    }

private fun Long.asSeconds() = String.format(Locale.UK, "%.3f", this / 1e9)
