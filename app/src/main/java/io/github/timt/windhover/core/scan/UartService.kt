package io.github.timt.windhover.core.scan

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Result4k
import dev.forkhandles.result4k.Success

data class GattCharacteristic(
    val uuid: String,
    val notifies: Boolean,
)

data class GattService(
    val uuid: String,
    val characteristics: List<GattCharacteristic>,
)

data class UartChannel(
    val serviceUuid: String,
    val characteristicUuid: String,
)

private const val NORDIC_UART = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
private const val HM10_UART = "0000ffe0-0000-1000-8000-00805f9b34fb"
private const val BLUETOOTH_BASE_SUFFIX = "-0000-1000-8000-00805f9b34fb"

private fun GattService.isStandard() = uuid.lowercase().let { it.endsWith(BLUETOOTH_BASE_SUFFIX) && it != HM10_UART }

private fun GattService.channel() = characteristics.firstOrNull { it.notifies }?.let { UartChannel(uuid, it.uuid) }

fun chooseUart(services: List<GattService>): Result4k<UartChannel, String> =
    (
        services.find { it.uuid.lowercase() == NORDIC_UART }?.channel()
            ?: services.find { it.uuid.lowercase() == HM10_UART }?.channel()
            ?: services.filterNot { it.isStandard() }.firstNotNullOfOrNull { it.channel() }
    )?.let(::Success)
        ?: Failure("No serial data service found. Services: ${services.joinToString { it.uuid.take(8) }}")
