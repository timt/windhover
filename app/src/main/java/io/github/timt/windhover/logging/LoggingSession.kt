package io.github.timt.windhover.logging

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.session.ConnectionState
import io.github.timt.windhover.core.session.LoggingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private val ConnectionState.isActive
    get() = this is ConnectionState.Connecting || this is ConnectionState.Connected

object LoggingSession {
    private val mutableState = MutableStateFlow(LoggingState())

    val state: StateFlow<LoggingState> = mutableState.asStateFlow()

    fun start(
        context: Context,
        profile: DeviceProfile,
    ) = ContextCompat.startForegroundService(
        context,
        Intent(context, LoggingService::class.java)
            .setAction(LoggingService.ACTION_START)
            .putExtra(LoggingService.EXTRA_PROFILE_ID, profile.id),
    )

    fun stop(context: Context) {
        if (state.value.connection.isActive) {
            context.startService(Intent(context, LoggingService::class.java).setAction(LoggingService.ACTION_STOP))
        }
        dismiss()
    }

    fun dismiss() = mutableState.update { it.copy(connection = ConnectionState.Idle) }

    internal fun update(change: (LoggingState) -> LoggingState) = mutableState.update(change)
}
