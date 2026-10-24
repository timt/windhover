package io.github.timt.windhover.logging

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import dev.forkhandles.result4k.valueOrNull
import io.github.timt.windhover.BuildConfig
import io.github.timt.windhover.MainActivity
import io.github.timt.windhover.bluetooth.LinkEvent
import io.github.timt.windhover.bluetooth.sppLines
import io.github.timt.windhover.core.profile.DeviceProfile
import io.github.timt.windhover.core.session.SessionHeader
import io.github.timt.windhover.core.session.SessionWriter
import io.github.timt.windhover.profileFile
import io.github.timt.windhover.sessionsRoot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.IOException

private const val CHANNEL_ID = "logging"
private const val NOTIFICATION_ID = 1
private const val RECENT_LINE_LIMIT = 200
private const val WAKE_LOCK_TIMEOUT_MILLIS = 12 * 60 * 60 * 1000L

class LoggingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> intent.getStringExtra(EXTRA_PROFILE_ID)?.let(::start) ?: stopSelf()
            ACTION_STOP -> session?.cancel() ?: stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun start(profileId: String) {
        val profile =
            profileFile()
                .load()
                .valueOrNull()
                ?.all
                ?.find { it.id == profileId }
        if (session?.isActive == true) return
        if (profile == null) return stopSelf()
        startForeground(NOTIFICATION_ID, notification(profile), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        val wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "windhover:logging")
                .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
        session =
            scope.launch {
                try {
                    log(profile)
                } catch (e: IOException) {
                    LoggingSession.reject(profile, "Could not start session: ${e.message}")
                } finally {
                    if (wakeLock.isHeld) wakeLock.release()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
    }

    private suspend fun log(profile: DeviceProfile) {
        val remote = getSystemService(BluetoothManager::class.java).adapter.getRemoteDevice(profile.address)
        SessionWriter.open(sessionsRoot(), header(profile)).use { writer ->
            LoggingSession.update { it.starting(profile, writer.directory.name) }
            remote
                .sppLines()
                .onEach { event ->
                    when (event) {
                        LinkEvent.Connected -> LoggingSession.update { it.connected() }
                        is LinkEvent.Received -> {
                            writer.append(event.lines, SystemClock.elapsedRealtimeNanos())
                            LoggingSession.update { it.received(event.lines, RECENT_LINE_LIMIT) }
                        }
                    }
                }.onCompletion { cause ->
                    when (cause) {
                        null -> LoggingSession.update { it.failed("Connection closed by device") }
                        is CancellationException -> LoggingSession.dismiss()
                    }
                }.catch { error ->
                    LoggingSession.update { it.failed(error.message ?: error.javaClass.simpleName) }
                }.collect()
        }
    }

    private fun header(profile: DeviceProfile) =
        SessionHeader(
            profile = profile,
            appVersion = BuildConfig.VERSION_NAME,
            phoneModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = Build.VERSION.RELEASE,
            startedAtEpochMillis = System.currentTimeMillis(),
            startedAtElapsedNanos = SystemClock.elapsedRealtimeNanos(),
        )

    private fun notification(profile: DeviceProfile): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Logging", NotificationManager.IMPORTANCE_LOW),
        )
        val open =
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop =
            PendingIntent.getService(
                this,
                1,
                Intent(this, LoggingService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Logging ${profile.name}")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    companion object {
        const val ACTION_START = "io.github.timt.windhover.START_LOGGING"
        const val ACTION_STOP = "io.github.timt.windhover.STOP_LOGGING"
        const val EXTRA_PROFILE_ID = "profileId"
    }
}
