package io.github.timt.windhover.core.scan

import dev.forkhandles.result4k.Failure
import dev.forkhandles.result4k.Success
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class UartServiceTest {
    private val genericAccess =
        GattService(
            "00001800-0000-1000-8000-00805f9b34fb",
            listOf(GattCharacteristic("00002a00-0000-1000-8000-00805f9b34fb", notifies = false)),
        )
    private val battery =
        GattService(
            "0000180f-0000-1000-8000-00805f9b34fb",
            listOf(GattCharacteristic("00002a19-0000-1000-8000-00805f9b34fb", notifies = true)),
        )
    private val nordicUart =
        GattService(
            "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
            listOf(
                GattCharacteristic("6e400002-b5a3-f393-e0a9-e50e24dcca9e", notifies = false),
                GattCharacteristic("6e400003-b5a3-f393-e0a9-e50e24dcca9e", notifies = true),
            ),
        )
    private val hm10 =
        GattService(
            "0000ffe0-0000-1000-8000-00805f9b34fb",
            listOf(GattCharacteristic("0000ffe1-0000-1000-8000-00805f9b34fb", notifies = true)),
        )
    private val vendor =
        GattService(
            "a1b2c3d4-5e6f-4a1b-9c2d-3e4f5a6b7c8d",
            listOf(GattCharacteristic("a1b2c3d5-5e6f-4a1b-9c2d-3e4f5a6b7c8d", notifies = true)),
        )

    @Test
    fun `Nordic UART is preferred and its notifying characteristic chosen`() {
        expectThat(chooseUart(listOf(genericAccess, vendor, hm10, nordicUart)))
            .isEqualTo(Success(UartChannel(nordicUart.uuid, "6e400003-b5a3-f393-e0a9-e50e24dcca9e")))
    }

    @Test
    fun `HM-10 style service is used when there is no Nordic UART`() {
        expectThat(chooseUart(listOf(genericAccess, vendor, hm10)))
            .isEqualTo(Success(UartChannel(hm10.uuid, "0000ffe1-0000-1000-8000-00805f9b34fb")))
    }

    @Test
    fun `otherwise the first non-standard service with a notifying characteristic is used`() {
        expectThat(chooseUart(listOf(genericAccess, battery, vendor)))
            .isEqualTo(Success(UartChannel(vendor.uuid, "a1b2c3d5-5e6f-4a1b-9c2d-3e4f5a6b7c8d")))
    }

    @Test
    fun `failure lists the services the device exposes`() {
        expectThat(chooseUart(listOf(genericAccess, battery)))
            .isEqualTo(Failure("No serial data service found. Services: 00001800, 0000180f"))
    }

    @Test
    fun `service UUIDs are matched case-insensitively`() {
        val upper = nordicUart.copy(uuid = nordicUart.uuid.uppercase())

        expectThat(
            chooseUart(listOf(upper)),
        ).isEqualTo(Success(UartChannel(upper.uuid, "6e400003-b5a3-f393-e0a9-e50e24dcca9e")))
    }
}
