package io.victor.jarvis

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Phase 3.1 (council 1778184643) — periodic poll of /api/signals from the
 * Android APK. WorkManager fires every 15 min (Google's hard minimum for
 * PeriodicWorkRequest) when network is available.
 *
 * On each tick:
 *  1. Read backend URL + auth token + last-seen-ts from DataStore.
 *  2. If url or token is blank, succeed immediately (nothing to poll yet).
 *  3. GET /api/signals?since=<lastSeenTs>&limit=10. Server already
 *     PII-filters the snippet body.
 *  4. If 401/403 → post the sticky re-auth notification, succeed (no retry —
 *     the token isn't coming back without user action).
 *  5. Otherwise post one Notification per signal, advance lastSeenTs to the
 *     newest ts, succeed.
 *  6. On any other error, return Result.retry() so WorkManager applies its
 *     exponential backoff. The loop never crashes the host process.
 */
class SignalWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return when (signalPollOnce(applicationContext)) {
            PollOutcome.OK, PollOutcome.NO_CONFIG, PollOutcome.AUTH_FAILED -> Result.success()
            PollOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "jarvis-signal-poll"
        private const val INTERVAL_MINUTES = 15L

        fun enqueue(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<SignalWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                // KEEP: don't restart the schedule on every recomposition; the
                // existing periodic survives recompose+process death.
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}

/**
 * Outcome of a single poll cycle. Consumed both by [SignalWorker.doWork]
 * (mapping to WorkManager's success/retry semantics) and by
 * [BackgroundPollService] (which loops on its own timer and ignores
 * RETRY since it'll just retry next tick anyway).
 */
enum class PollOutcome { OK, NO_CONFIG, AUTH_FAILED, RETRY }

/**
 * Single poll cycle of /api/signals + /api/focus, surface notifications,
 * advance lastSeenTs. Extracted from SignalWorker.doWork so the
 * foreground BackgroundPollService can drive its own 15-min timer
 * (Android 12+ throttles WorkManager periodic when app is fully
 * backgrounded; the service keeps the process alive so this loop
 * actually fires when phone is locked + app closed).
 */
suspend fun signalPollOnce(ctx: Context): PollOutcome {
    val baseUrl = Prefs.loadBackendUrl(ctx, default = "")
    val token = Prefs.loadAuthToken(ctx, default = "")
    if (baseUrl.isBlank() || token.isBlank()) return PollOutcome.NO_CONFIG
    val since = Prefs.loadLastSeenTs(ctx, default = "")
    val client = JarvisClient()
    try {
        val signals = try {
            client.fetchSignals(baseUrl, since, token, limit = 10)
        } catch (e: JarvisAuthException) {
            Notifications.postReauth(ctx, baseUrl)
            return PollOutcome.AUTH_FAILED
        } catch (e: Exception) {
            return PollOutcome.RETRY
        }
        Notifications.clearReauth(ctx)
        try {
            val focus = client.fetchFocus(baseUrl, token)
            if (focus != null) {
                if (focus.active && focus.process != null) {
                    Notifications.postFocus(
                        ctx, focus.process, focus.title, focus.durationMin,
                    )
                } else {
                    Notifications.clearFocus(ctx)
                }
            }
        } catch (_: Exception) {
            // focus is optional surface; never block the signal path.
        }
        if (signals.isEmpty()) return PollOutcome.OK
        val muted = Prefs.loadMutedKinds(ctx)
        val threshold = Prefs.loadImportanceThreshold(ctx)
        val qStart = Prefs.loadQuietStartHour(ctx)
        val qEnd = Prefs.loadQuietEndHour(ctx)
        signals
            .filter {
                SignalFilter.shouldSurface(
                    it, muted, threshold, qStart, qEnd,
                )
            }
            .forEach { Notifications.postSignal(ctx, it) }
        val newest = signals.maxByOrNull { it.ts }?.ts
        if (newest != null) Prefs.saveLastSeenTs(ctx, newest)
        return PollOutcome.OK
    } finally {
        client.close()
    }
}
