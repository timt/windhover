package io.github.timt.windhover.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import io.github.timt.windhover.bluetooth.DiscoveryEvent
import io.github.timt.windhover.bluetooth.classicDiscovery
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.profile.ProfileDraft
import io.github.timt.windhover.core.profile.ProfileError
import io.github.timt.windhover.core.profile.Profiles
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.profile.toProfile
import io.github.timt.windhover.core.scan.NearbyDevice
import io.github.timt.windhover.core.scan.NearbyDevices
import io.github.timt.windhover.core.session.LoggingState
import io.github.timt.windhover.logging.LoggingSession
import io.github.timt.windhover.profileFile
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class PairedDevice(
    val name: String,
    val address: String,
)

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
    val nearby: NearbyDevices = NearbyDevices(),
)

private val emptyDraft = ProfileDraft(name = "", glider = "", lxModel = "", address = "", transport = Transport.SPP)

class ProbeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val profileFile = application.profileFile()
    private val mutableState = MutableStateFlow(ProbeState(bluetoothAvailable = adapter != null).withStoredProfiles())
    private var scan: Job? = null

    val state: StateFlow<ProbeState> = mutableState.asStateFlow()
    val logging: StateFlow<LoggingState> = LoggingSession.state

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

    fun cancelEdit() {
        stopScan()
        mutableState.update { it.copy(editor = null) }
    }

    fun scan() {
        val bluetooth = adapter ?: return
        scan?.cancel()
        mutableState.update { it.copy(nearby = it.nearby.restarted()) }
        scan =
            viewModelScope.launch {
                getApplication<Application>().classicDiscovery(bluetooth).collect { event ->
                    mutableState.update {
                        when (event) {
                            is DiscoveryEvent.Found -> it.copy(nearby = it.nearby.found(event.device))
                            DiscoveryEvent.Finished -> it.copy(nearby = it.nearby.finished())
                        }
                    }
                }
            }
    }

    @SuppressLint("MissingPermission")
    fun pickNearby(device: NearbyDevice) {
        val bluetooth = adapter ?: return
        bluetooth.cancelDiscovery()
        if (!device.paired) bluetooth.getRemoteDevice(device.address).createBond()
        mutableState.update { state ->
            state.copy(
                editor =
                    state.editor?.let {
                        it.copy(
                            draft =
                                it.draft.copy(
                                    address = device.address,
                                    name = it.draft.name.ifBlank { device.name.orEmpty() },
                                ),
                        )
                    },
            )
        }
    }

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
        when (profile.transport) {
            Transport.SPP -> LoggingSession.start(getApplication(), profile)
            Transport.BLE -> LoggingSession.reject(profile, "BLE is not supported yet")
        }
    }

    fun disconnect() = LoggingSession.stop(getApplication())

    private fun openEditor(editor: ProfileEditor) {
        refreshDevices()
        mutableState.update { it.copy(editor = editor) }
    }

    private fun stopScan() {
        scan?.cancel()
        scan = null
        mutableState.update { it.copy(nearby = NearbyDevices()) }
    }

    private fun storeProfiles(change: (Profiles) -> Profiles) {
        stopScan()
        val profiles = change(mutableState.value.profiles)
        profileFile.write(profiles)
        mutableState.update { it.copy(profiles = profiles, profilesProblem = null, editor = null) }
    }

    private fun ProbeState.withStoredProfiles() =
        when (val loaded = profileFile.load()) {
            is Success -> copy(profiles = loaded.value)
            is Failure -> copy(profilesProblem = "Saved profiles could not be read: ${loaded.reason.describe()}")
        }
}

private fun DeviceProfile.toDraft() = ProfileDraft(name, glider, lxModel, address, transport)

private fun ProfileError.describe() =
    when (this) {
        ProfileError.BlankName -> "Give the profile a name"
        is ProfileError.InvalidAddress -> "Pick a paired device or enter its Bluetooth address"
        is ProfileError.Corrupt -> reason
    }
