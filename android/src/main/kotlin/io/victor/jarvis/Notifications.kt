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
    const val REAUTH_NOTIFICATION_ID = 0x7AFE_AC1D.toInt()

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
    }

    fun postSignal(ctx: Context, sig: Signal) {
        ensureChannel(ctx)
        val title = "jarvis: signal"
        val short = sig.snippet.take(80)
        val openIntent = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(short)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sig.snippet))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(sig.id.hashCode(), notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted on Android 13+. Silent — UI flow
            // requests permission on first launch; further reminders not our
            // job at the worker layer.
        }
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
