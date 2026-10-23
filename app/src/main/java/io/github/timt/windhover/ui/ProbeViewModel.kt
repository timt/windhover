package io.github.timt.windhover.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.timt.windhover.bluetooth.LinkEvent
import io.github.timt.windhover.bluetooth.sppLines
import io.github.timt.windhover.core.stream.ReceivedLine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairedDevice(
    val name: String,
    val address: String,
)

sealed interface ConnectionState {
    data object Idle : ConnectionState

    data class Connecting(
        val device: PairedDevice,
    ) : ConnectionState

    data class Connected(
        val device: PairedDevice,
    ) : ConnectionState

    data class Failed(
        val device: PairedDevice,
        val reason: String,
    ) : ConnectionState
}

data class ProbeState(
    val bluetoothAvailable: Boolean = true,
    val devices: List<PairedDevice> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val lines: List<ReceivedLine> = emptyList(),
)

private const val RECENT_LINE_LIMIT = 200

class ProbeViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableState = MutableStateFlow(ProbeState(bluetoothAvailable = adapter != null))
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

    fun connect(device: PairedDevice) {
        val remote = adapter?.getRemoteDevice(device.address) ?: return
        link?.cancel()
        mutableState.update { it.copy(connection = ConnectionState.Connecting(device), lines = emptyList()) }
        link =
            viewModelScope.launch {
                remote
                    .sppLines()
                    .onCompletion { cause ->
                        if (cause == null) {
                            mutableState.update {
                                it.copy(connection = ConnectionState.Failed(device, "Connection closed by device"))
                            }
                        }
                    }.catch { error ->
                        mutableState.update {
                            it.copy(
                                connection =
                                    ConnectionState.Failed(
                                        device,
                                        error.message ?: error.javaClass.simpleName,
                                    ),
                            )
                        }
                    }.collect { event -> mutableState.update { it.apply(event, device) } }
            }
    }

    fun disconnect() {
        link?.cancel()
        link = null
        mutableState.update { it.copy(connection = ConnectionState.Idle) }
    }

    private fun ProbeState.apply(
        event: LinkEvent,
        device: PairedDevice,
    ) = when (event) {
        LinkEvent.Connected -> copy(connection = ConnectionState.Connected(device))
        is LinkEvent.Received -> copy(lines = (lines + event.lines).takeLast(RECENT_LINE_LIMIT))
    }
}
