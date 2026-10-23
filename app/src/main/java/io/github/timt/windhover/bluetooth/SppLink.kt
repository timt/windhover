package io.github.timt.windhover.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.SystemClock
import io.github.timt.windhover.core.stream.LineFramer
import io.github.timt.windhover.core.stream.ReceivedLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

private val serialPortProfile = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

sealed interface LinkEvent {
    data object Connected : LinkEvent

    data class Received(
        val lines: List<ReceivedLine>,
    ) : LinkEvent
}

@SuppressLint("MissingPermission")
fun BluetoothDevice.sppLines(now: () -> Long = SystemClock::elapsedRealtimeNanos): Flow<LinkEvent> =
    channelFlow {
        val socket = createRfcommSocketToServiceRecord(serialPortProfile)
        launch(Dispatchers.IO) {
            socket.connect()
            send(LinkEvent.Connected)
            val input = socket.inputStream
            val buffer = ByteArray(1024)
            var framer = LineFramer()
            while (isActive) {
                val count = input.read(buffer)
                if (count < 0) break
                val (next, lines) = framer.accept(buffer.copyOf(count), now())
                framer = next
                if (lines.isNotEmpty()) send(LinkEvent.Received(lines))
            }
            channel.close()
        }
        awaitClose { socket.close() }
    }
