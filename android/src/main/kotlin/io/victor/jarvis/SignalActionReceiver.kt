package io.victor.jarvis

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * R3 — handles "Pin" / "Dismiss" Notification action buttons on a
 * ProactiveSignal. Fires a best-effort POST /api/signals/ack and clears the
 * notification. Network failures are silent per spec — Phase 5 retraining is
 * aggregate, not individual-row sensitive.
 */
class SignalActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val signalId = intent.getStringExtra(EXTRA_SIGNAL_ID).orEmpty()
        val action = intent.getStringExtra(EXTRA_ACTION).orEmpty()
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (signalId.isBlank() || action.isBlank()) return

        // Cancel the notification immediately so the user gets snappy feedback;
        // the network ack is fire-and-forget after that.
        if (notifId >= 0) {
            try {
                NotificationManagerCompat.from(context).cancel(notifId)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS not granted — cancel still safe but skip.
            }
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val baseUrl = Prefs.loadBackendUrl(context, default = "")
                val token = Prefs.loadAuthToken(context, default = "")
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val client = JarvisClient()
                    try {
                        client.ackSignal(baseUrl, signalId, action, token)
                    } finally {
                        client.close()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_ACK = "io.victor.jarvis.action.SIGNAL_ACK"
        const val EXTRA_SIGNAL_ID = "signal_id"
        const val EXTRA_ACTION = "ack_action"
        const val EXTRA_NOTIF_ID = "notif_id"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
