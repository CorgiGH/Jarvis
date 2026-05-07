package io.victor.jarvis

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Phase 3.1 (council 1778184643) — system notification surface for
 * ProactiveSignal rows fetched by [SignalWorker].
 *
 * Privacy posture (Devil's Advocate concern):
 *  - setVisibility(VISIBILITY_PRIVATE) → lockscreen shows notification title
 *    only ("jarvis: signal"); the snippet body is hidden until unlock.
 *  - Server-side /api/signals already runs CoreMemory.scanTextForPii on the
 *    snippet; this is the second-line defense.
 */
object Notifications {

    private const val CHANNEL_ID = "jarvis-signals"
    private const val CHANNEL_NAME = "Jarvis signals"
    private const val FOCUS_CHANNEL_ID = "jarvis-focus"
    private const val FOCUS_CHANNEL_NAME = "Focus session"
    const val REAUTH_NOTIFICATION_ID = 0x7AFE_AC1D.toInt()
    const val FOCUS_NOTIFICATION_ID = 0x70CCC_5ED.toInt()

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Proactive signals from your jarvis observer."
                setShowBadge(true)
            }
            nm.createNotificationChannel(channel)
        }
        // R6 — separate quiet channel for focus-session ongoing notification.
        if (nm.getNotificationChannel(FOCUS_CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                FOCUS_CHANNEL_ID,
                FOCUS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,  // no sound, no vibrate
            ).apply {
                description = "Glanceable indicator while you're in a focus block."
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    /** R6 — quiet ongoing notification for the live focus session. Posted +
     *  refreshed by SignalWorker; cleared via [clearFocus] when active=false. */
    fun postFocus(ctx: Context, process: String, title: String?, durationMin: Long) {
        ensureChannel(ctx)
        val openIntent = PendingIntent.getActivity(
            ctx,
            2,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val contentText = buildString {
            append("$process · ${durationMin}m")
            title?.takeIf { it.isNotBlank() }?.let { append(" · ${it.take(40)}") }
        }
        val notif = NotificationCompat.Builder(ctx, FOCUS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("🧠 deep work")
            .setContentText(contentText)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(FOCUS_NOTIFICATION_ID, notif)
        } catch (_: SecurityException) {
        }
    }

    fun clearFocus(ctx: Context) {
        try {
            NotificationManagerCompat.from(ctx).cancel(FOCUS_NOTIFICATION_ID)
        } catch (_: SecurityException) {
        }
    }

    fun postSignal(ctx: Context, sig: Signal) {
        ensureChannel(ctx)
        val title = "jarvis: signal"
        val short = sig.snippet.take(80)
        val notifId = sig.id.hashCode()
        val openIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pinIntent = ackPendingIntent(ctx, sig.id, "pinned", notifId, requestCode = notifId * 2)
        val dismissIntent = ackPendingIntent(ctx, sig.id, "dismissed", notifId, requestCode = notifId * 2 + 1)
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sig.snippet))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            // R3 — Pin / Dismiss action buttons
            .addAction(android.R.drawable.ic_menu_save, "Pin", pinIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissIntent)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(notifId, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+. Silent — UI flow
            // requests permission on first launch; further reminders not our
            // job at the worker layer.
        }
    }

    private fun ackPendingIntent(
        ctx: Context,
        signalId: String,
        action: String,
        notifId: Int,
        requestCode: Int,
    ): PendingIntent {
        val intent = Intent(ctx, SignalActionReceiver::class.java).apply {
            this.action = SignalActionReceiver.ACTION_ACK
            putExtra(SignalActionReceiver.EXTRA_SIGNAL_ID, signalId)
            putExtra(SignalActionReceiver.EXTRA_ACTION, action)
            putExtra(SignalActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        return PendingIntent.getBroadcast(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun postReauth(ctx: Context, baseUrl: String) {
        ensureChannel(ctx)
        val openIntent = PendingIntent.getActivity(
            ctx,
            1,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("jarvis: re-authentication needed")
            .setContentText("$baseUrl rejected the saved token. Tap to re-enter it.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(REAUTH_NOTIFICATION_ID, notif)
        } catch (_: SecurityException) {
            // ditto — silent if no permission.
        }
    }

    fun clearReauth(ctx: Context) {
        try {
            NotificationManagerCompat.from(ctx).cancel(REAUTH_NOTIFICATION_ID)
        } catch (_: SecurityException) {
        }
    }
}
