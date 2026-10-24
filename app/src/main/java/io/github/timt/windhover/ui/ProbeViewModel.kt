package io.github.timt.windhover.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import io.github.timt.windhover.bluetooth.LinkEvent
import io.github.timt.windhover.bluetooth.sppLines
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.ProfileDraft
import io.github.timt.windhover.core.profile.ProfileError
import io.github.timt.windhover.core.profile.ProfileFile
import io.github.timt.windhover.core.profile.Profiles
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.profile.toProfile
import io.github.timt.windhover.core.stream.ReceivedLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class PairedDevice(
    val name: String,
    val address: String,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class Connecting(
        val profile: DeviceProfile,
    ) : ConnectionState

    data class Connected(
        val profile: DeviceProfile,
    ) : ConnectionState

    data class Failed(
        val profile: DeviceProfile,
        val reason: String,
    ) : ConnectionState
}

data class ProfileEditor(
    val id: String?,
    val draft: ProfileDraft,
    val error: String? = null,
)

data class ProbeState(
    val bluetoothAvailable: Boolean = true,
    val devices: List<PairedDevice> = emptyList(),
    val profiles: Profiles = Profiles(),
    val profilesProblem: String? = null,
    val editor: ProfileEditor? = null,
    val connection: ConnectionState = ConnectionState.Idle,
    val lines: List<ReceivedLine> = emptyList(),
)

private const val RECENT_LINE_LIMIT = 200

private val emptyDraft = ProfileDraft(name = "", glider = "", lxModel = "", address = "", transport = Transport.SPP)

class ProbeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val profileFile = ProfileFile(File(application.filesDir, "profiles.json"))
    private val mutableState = MutableStateFlow(ProbeState(bluetoothAvailable = adapter != null).withStoredProfiles())
    private var link: Job? = null

    val state: StateFlow<ProbeState> = mutableState.asStateFlow()

    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        val devices =
            adapter
                ?.bondedDevices
                .orEmpty()
                .map { PairedDevice(it.name ?: it.address, it.address) }
                .sortedBy { it.name }
        mutableState.update { it.copy(devices = devices) }
    }

    fun newProfile() = openEditor(ProfileEditor(id = null, draft = emptyDraft))

    fun editProfile(profile: DeviceProfile) = openEditor(ProfileEditor(id = profile.id, draft = profile.toDraft()))

    fun updateDraft(draft: ProfileDraft) = mutableState.update { it.copy(editor = it.editor?.copy(draft = draft)) }

    fun cancelEdit() = mutableState.update { it.copy(editor = null) }

    fun saveProfile() {
        val editor = mutableState.value.editor ?: return
        when (val result = editor.draft.toProfile(editor.id ?: UUID.randomUUID().toString())) {
            is Success -> storeProfiles { it.save(result.value) }
            is Failure -> mutableState.update { it.copy(editor = editor.copy(error = result.reason.describe())) }
        }
    }

    fun deleteProfile(id: String) = storeProfiles { it.remove(id) }

    fun connect(profile: DeviceProfile) {
        storeProfiles { it.markUsed(profile.id) }
        if (profile.transport == Transport.BLE) {
            fail(profile, "BLE is not supported yet")
            return
        }
        val remote = adapter?.getRemoteDevice(profile.address) ?: return
        link?.cancel()
        mutableState.update { it.copy(connection = ConnectionState.Connecting(profile), lines = emptyList()) }
        link =
            viewModelScope.launch {
                remote
                    .sppLines()
                    .onCompletion { cause -> if (cause == null) fail(profile, "Connection closed by device") }
                    .catch { error -> fail(profile, error.message ?: error.javaClass.simpleName) }
                    .collect { event -> mutableState.update { it.apply(event, profile) } }
            }
    }

    fun disconnect() {
        link?.cancel()
        link = null
        mutableState.update { it.copy(connection = ConnectionState.Idle) }
    }

    private fun openEditor(editor: ProfileEditor) {
        refreshDevices()
        mutableState.update { it.copy(editor = editor) }
    }

    private fun fail(
        profile: DeviceProfile,
        reason: String,
    ) = mutableState.update { it.copy(connection = ConnectionState.Failed(profile, reason)) }

    private fun storeProfiles(change: (Profiles) -> Profiles) {
        val profiles = change(mutableState.value.profiles)
        profileFile.write(profiles)
        mutableState.update { it.copy(profiles = profiles, profilesProblem = null, editor = null) }
    }

    private fun ProbeState.withStoredProfiles() =
        when (val loaded = profileFile.load()) {
            is Success -> copy(profiles = loaded.value)
            is Failure -> copy(profilesProblem = "Saved profiles could not be read: ${loaded.reason.describe()}")
        }

    private fun ProbeState.apply(
        event: LinkEvent,
        profile: DeviceProfile,
    ) = when (event) {
        LinkEvent.Connected -> copy(connection = ConnectionState.Connected(profile))
        is LinkEvent.Received -> copy(lines = (lines + event.lines).takeLast(RECENT_LINE_LIMIT))
    }
}

private fun DeviceProfile.toDraft() = ProfileDraft(name, glider, lxModel, address, transport)

private fun ProfileError.describe() =
    when (this) {
        ProfileError.BlankName -> "Give the profile a name"
        is ProfileError.InvalidAddress -> "Pick a paired device or enter its Bluetooth address"
        is ProfileError.Corrupt -> reason
    }
