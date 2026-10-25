package io.github.timt.windhover.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.SystemClock
import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import io.github.timt.windhover.core.profile.Transport
import io.github.timt.windhover.core.scan.GattCharacteristic
import io.github.timt.windhover.core.scan.GattService
import io.github.timt.windhover.core.scan.NearbyDevice
import io.github.timt.windhover.core.scan.chooseUart
import io.github.timt.windhover.core.stream.LineFramer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID

private const val FIND_TIMEOUT_MILLIS = 15_000L
private const val PREFERRED_MTU = 247
private val clientConfiguration = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

@SuppressLint("MissingPermission")
fun BluetoothAdapter.bleDiscovery(filters: List<ScanFilter> = emptyList()): Flow<ScanResult> =
    callbackFlow {
        val scanner = bluetoothLeScanner ?: throw IOException("Bluetooth is off")
        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    trySend(result)
                }

                override fun onScanFailed(errorCode: Int) {
                    close(IOException("BLE scan failed ($errorCode)"))
                }
            }
        scanner.startScan(
            filters,
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        awaitClose { scanner.stopScan(callback) }
    }

@SuppressLint("MissingPermission")
fun ScanResult.toNearby() =
    NearbyDevice(
        name = scanRecord?.deviceName ?: device.name,
        address = device.address,
        paired = device.bondState == BluetoothDevice.BOND_BONDED,
        transport = Transport.BLE,
    )

fun BluetoothAdapter.bleLines(
    context: Context,
    address: String,
    now: () -> Long = SystemClock::elapsedRealtimeNanos,
): Flow<LinkEvent> =
    flow {
        val device =
            withTimeoutOrNull(FIND_TIMEOUT_MILLIS) {
                bleDiscovery(listOf(ScanFilter.Builder().setDeviceAddress(address).build())).map { it.device }.first()
            } ?: throw IOException("Not found. Is it powered on and advertising?")
        device.gattLines(context, now).collect { emit(it) }
    }

@SuppressLint("MissingPermission")
private fun BluetoothDevice.gattLines(
    context: Context,
    now: () -> Long,
): Flow<LinkEvent> =
    callbackFlow {
        var framer = LineFramer()
        val callback =
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int,
                ) {
                    when {
                        newState == BluetoothProfile.STATE_CONNECTED -> gatt.requestMtu(PREFERRED_MTU)
                        status == BluetoothGatt.GATT_SUCCESS -> close()
                        else -> close(IOException("BLE connection lost (status $status)"))
                    }
                }

                override fun onMtuChanged(
                    gatt: BluetoothGatt,
                    mtu: Int,
                    status: Int,
                ) {
                    gatt.discoverServices()
                }

                override fun onServicesDiscovered(
                    gatt: BluetoothGatt,
                    status: Int,
                ) {
                    when (val uart = chooseUart(gatt.services.map { it.describe() })) {
                        is Success -> {
                            val characteristic =
                                gatt
                                    .getService(UUID.fromString(uart.value.serviceUuid))
                                    .getCharacteristic(UUID.fromString(uart.value.characteristicUuid))
                            if (!gatt.subscribe(characteristic)) trySend(LinkEvent.Connected)
                        }
                        is Failure -> close(IOException(uart.reason))
                    }
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int,
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        trySend(LinkEvent.Connected)
                    } else {
                        close(IOException("Could not subscribe to data (status $status)"))
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                ) = received(value)

                @Deprecated("Called instead of the ByteArray overload before Android 13")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                ) {
                    @Suppress("DEPRECATION")
                    received(characteristic.value ?: return)
                }

                private fun received(value: ByteArray) {
                    val (next, lines) = framer.accept(value, now())
                    framer = next
                    if (lines.isNotEmpty()) trySend(LinkEvent.Received(lines))
                }
            }
        val gatt = connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        awaitClose {
            gatt.disconnect()
            gatt.close()
        }
    }

private fun BluetoothGattService.describe() =
    GattService(
        uuid = uuid.toString(),
        characteristics =
            characteristics.map {
                GattCharacteristic(
                    it.uuid.toString(),
                    notifies =
                        it.properties and
                            (
                                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                                    BluetoothGattCharacteristic.PROPERTY_INDICATE
                            ) !=
                            0,
                )
            },
    )

@SuppressLint("MissingPermission")
private fun BluetoothGatt.subscribe(characteristic: BluetoothGattCharacteristic): Boolean {
    setCharacteristicNotification(characteristic, true)
    val descriptor = characteristic.getDescriptor(clientConfiguration) ?: return false
    val value =
        if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        writeDescriptor(descriptor, value)
    } else {
        @Suppress("DEPRECATION")
        descriptor.value = value
        @Suppress("DEPRECATION")
        writeDescriptor(descriptor)
    }
    return true
}
