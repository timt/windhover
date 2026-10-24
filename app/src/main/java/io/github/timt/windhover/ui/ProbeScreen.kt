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
import io.github.timt.windhover.core.stream.ReceivedLine
import java.util.Locale

private val knownModels = listOf("S80", "LX9000")

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
        val editor = state.editor
        when {
            !state.bluetoothAvailable -> Text("This device has no Bluetooth adapter")
            !permitted ->
                Button(onClick = { requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                    Text("Allow Bluetooth access")
                }
            editor != null ->
                ProfileForm(
                    editor = editor,
                    devices = state.devices,
                    onChange = viewModel::updateDraft,
                    onSave = viewModel::saveProfile,
                    onCancel = viewModel::cancelEdit,
                    onDelete = viewModel::deleteProfile,
                )
            state.connection == ConnectionState.Idle ->
                ProfileList(
                    profiles = state.profiles,
                    problem = state.profilesProblem,
                    onAdd = viewModel::newProfile,
                    onEdit = viewModel::editProfile,
                    onConnect = viewModel::connect,
                )
            else -> LiveStream(state, onDisconnect = viewModel::disconnect)
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
    onChange: (ProfileDraft) -> Unit,
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
        OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, label = { Text("Name") }, singleLine = true)
        OutlinedTextField(
            draft.glider,
            { onChange(draft.copy(glider = it)) },
            label = { Text("Glider") },
            singleLine = true,
        )
        OutlinedTextField(draft.lxModel, {
            onChange(draft.copy(lxModel = it))
        }, label = { Text("LX model") }, singleLine = true)
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
        Text("Paired devices", style = MaterialTheme.typography.labelLarge)
        if (devices.isEmpty()) Text("No paired devices. Pair the LXNAV unit in Android Bluetooth settings first.")
        devices.forEach { device ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChange(draft.withDevice(device)) }
                        .padding(vertical = 8.dp),
            ) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
        }
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSave) { Text("Save") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            editor.id?.let { id -> TextButton(onClick = { onDelete(id) }) { Text("Delete") } }
        }
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) = OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true)

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
        is ConnectionState.Connected -> "Connected to ${profile.name}"
        is ConnectionState.Failed -> "${profile.name}: $reason"
    }

private fun Long.asSeconds() = String.format(Locale.UK, "%.3f", this / 1e9)
