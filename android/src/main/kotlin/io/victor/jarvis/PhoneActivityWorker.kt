package io.victor.jarvis

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Phone activity logger — periodic Worker reading [UsageStatsManager] for
 * foreground app + window state and POSTing to /api/activity. Server-side
 * ActivityScorer + ActiveDoc drift detection then treat phone activity
 * identically to PC activity.
 *
 * Cadence: 15 min minimum (WorkManager hard floor). Each tick reports the
 * single most-recent foreground event seen in the last 30 min, NOT a stream
 * — single-user scale; we don't need per-second granularity.
 *
 * Permission: PACKAGE_USAGE_STATS is a Special access permission users
 * grant via Settings > Apps > Special access > Usage access. We deep-link
 * to that screen from MainActivity when missing. If absent at runtime,
 * Worker no-ops gracefully.
 */
class PhoneActivityWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val tickTs = Instant.now().toString()
        if (!hasUsageAccess(ctx)) {
            Prefs.savePhoneDiag(ctx, null, null, "no-usage-access", tickTs)
            return Result.success()
        }

        val baseUrl = Prefs.loadBackendUrl(ctx, default = "")
        val token = Prefs.loadAuthToken(ctx, default = "")
        if (baseUrl.isBlank() || token.isBlank()) {
            Prefs.savePhoneDiag(ctx, null, null, "no-config (url/token blank)", tickTs)
            return Result.success()
        }

        val sample = sampleForeground(ctx)
        if (sample == null) {
            Prefs.savePhoneDiag(ctx, null, null, "no-foreground-event-30min", tickTs)
            return Result.success()
        }
        val client = JarvisClient()
        val ok = try {
            client.postActivity(baseUrl, sample, token)
        } finally {
            client.close()
        }
        Prefs.savePhoneDiag(
            ctx,
            sampleTs = sample.ts,
            samplePkg = sample.process,
            postStatus = if (ok) "ok" else "failed",
            postTs = tickTs,
        )
        return Result.success()
    }

    /** Look back 30 min, take the LAST `MOVE_TO_FOREGROUND` event seen.
     *  Returns null if none (means screen was off / launcher only). */
    private fun sampleForeground(ctx: Context): PhoneActivity? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        val now = System.currentTimeMillis()
        val from = now - 30L * 60 * 1000
        val events = usm.queryEvents(from, now)
        var latestPkg: String? = null
        var latestTs: Long = 0L
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                if (ev.timeStamp > latestTs) {
                    latestTs = ev.timeStamp
                    latestPkg = ev.packageName
                }
            }
        }
        if (latestPkg == null) return null
        return PhoneActivity(
            ts = Instant.now().toString(),
            title = "phone:$latestPkg",
            process = latestPkg,
            pid = null,
        )
    }

    companion object {
        private const val WORK_NAME = "jarvis-phone-activity"
        private const val INTERVAL_MINUTES = 15L

        fun hasUsageAccess(ctx: Context): Boolean {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                ctx.packageName,
            )
            return mode == AppOpsManager.MODE_ALLOWED
        }

        fun enqueue(ctx: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val req = PeriodicWorkRequestBuilder<PhoneActivityWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES,
            ).setConstraints(constraints).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun cancel(ctx: Context) {
            WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
        }
    }
}
