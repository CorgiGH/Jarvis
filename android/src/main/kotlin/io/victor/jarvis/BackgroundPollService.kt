package io.victor.jarvis

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Foreground service that drives the /api/signals + /api/focus poll loop.
 *
 * Why a service in addition to (and not via) [SignalWorker]:
 *   Android 12+ aggressively throttles WorkManager periodic work when the
 *   app process has been fully backgrounded — that's the bug the user
 *   reported: notifications only fire when the app is opened (which wakes
 *   the WorkManager queue). Promoting the poll to a foreground service
 *   pins the process via a low-importance persistent notification, and
 *   the OS no longer throttles its coroutine timer.
 *
 * Started from [MainActivity] when an auth token is configured. Survives
 * app death (START_STICKY) and self-no-ops if config is missing. The
 * existing [SignalWorker] is kept enqueued as a belt-and-suspenders
 * fallback for the case where the system kills the service entirely.
 *
 * Foreground service type: dataSync (Android 14 mandatory). Permissions
 * declared in AndroidManifest.xml: FOREGROUND_SERVICE +
 * FOREGROUND_SERVICE_DATA_SYNC.
 */
class BackgroundPollService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureBackgroundChannel(this)
        val notification = NotificationCompat.Builder(this, Notifications.BACKGROUND_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("jarvis")
            .setContentText("background sync running")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }

        pollJob = scope.launch {
            // First poll fires immediately so the user gets the surface
            // populated within seconds of starting the service, then
            // settles into the 15-min cadence used by SignalWorker.
            while (isActive) {
                try {
                    signalPollOnce(applicationContext)
                } catch (_: Throwable) {
                    // Swallow — next tick will retry. Never crash the
                    // service or the host app.
                }
                delay(TimeUnit.MINUTES.toMillis(POLL_INTERVAL_MINUTES))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        pollJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 0x70_11_5E_E0.toInt()
        private const val POLL_INTERVAL_MINUTES = 15L

        /** Idempotent — Android coalesces start commands for an
         *  already-running service, and START_STICKY resurrects it
         *  on its own if killed. Safe to call from MainActivity on
         *  every recompose. */
        fun start(ctx: Context) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, BackgroundPollService::class.java),
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, BackgroundPollService::class.java))
        }
    }
}
