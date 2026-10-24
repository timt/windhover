package io.github.timt.windhover.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import io.github.timt.windhover.core.scan.NearbyDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface DiscoveryEvent {
    data class Found(
        val device: NearbyDevice,
    ) : DiscoveryEvent

    data object Finished : DiscoveryEvent
}

@SuppressLint("MissingPermission")
fun Context.classicDiscovery(adapter: BluetoothAdapter): Flow<DiscoveryEvent> =
    callbackFlow {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> trySend(DiscoveryEvent.Finished)
                        else ->
                            IntentCompat
                                .getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                                ?.let { trySend(DiscoveryEvent.Found(it.toNearby())) }
                    }
                }
            }
        val events =
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
        ContextCompat.registerReceiver(this@classicDiscovery, receiver, events, ContextCompat.RECEIVER_NOT_EXPORTED)
        adapter.startDiscovery()
        awaitClose {
            adapter.cancelDiscovery()
            unregisterReceiver(receiver)
        }
    }

@SuppressLint("MissingPermission")
private fun BluetoothDevice.toNearby() = NearbyDevice(name, address, paired = bondState == BluetoothDevice.BOND_BONDED)
