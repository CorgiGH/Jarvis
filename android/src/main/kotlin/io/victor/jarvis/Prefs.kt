package io.victor.jarvis

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.prefsStore by preferencesDataStore(name = "jarvis-prefs")

object PrefKeys {
    val BackendUrl = stringPreferencesKey("backend_url")
    val AuthToken = stringPreferencesKey("auth_token")
    val TtsOn = booleanPreferencesKey("tts_on")
    /** Phase 3.1: ISO-8601 ts of last signal the user has been notified about.
     *  Functions as the client-side ack — server's /api/signals is read-only. */
    val LastSeenTs = stringPreferencesKey("last_seen_ts")
    // R7 — granular notification controls
    val QuietStartHour = intPreferencesKey("quiet_start_hour")
    val QuietEndHour = intPreferencesKey("quiet_end_hour")
    val ImportanceThreshold = floatPreferencesKey("importance_threshold")
    /** Comma-separated list of muted signal kinds. */
    val MutedKinds = stringPreferencesKey("muted_kinds")
}

object Prefs {
    suspend fun loadBackendUrl(context: Context, default: String): String =
        context.prefsStore.data.first()[PrefKeys.BackendUrl] ?: default

    suspend fun saveBackendUrl(context: Context, value: String) {
        context.prefsStore.edit { it[PrefKeys.BackendUrl] = value }
    }

    suspend fun loadAuthToken(context: Context, default: String): String =
        context.prefsStore.data.first()[PrefKeys.AuthToken] ?: default

    suspend fun saveAuthToken(context: Context, value: String) {
        context.prefsStore.edit { it[PrefKeys.AuthToken] = value }
    }

    suspend fun loadTtsOn(context: Context, default: Boolean): Boolean =
        context.prefsStore.data.first()[PrefKeys.TtsOn] ?: default

    suspend fun saveTtsOn(context: Context, value: Boolean) {
        context.prefsStore.edit { it[PrefKeys.TtsOn] = value }
    }

    suspend fun loadLastSeenTs(context: Context, default: String): String =
        context.prefsStore.data.first()[PrefKeys.LastSeenTs] ?: default

    suspend fun saveLastSeenTs(context: Context, value: String) {
        context.prefsStore.edit { it[PrefKeys.LastSeenTs] = value }
    }

    // R7 — granular notification controls

    suspend fun loadQuietStartHour(context: Context, default: Int = 23): Int =
        context.prefsStore.data.first()[PrefKeys.QuietStartHour] ?: default

    suspend fun saveQuietStartHour(context: Context, value: Int) {
        context.prefsStore.edit { it[PrefKeys.QuietStartHour] = value.coerceIn(0, 23) }
    }

    suspend fun loadQuietEndHour(context: Context, default: Int = 7): Int =
        context.prefsStore.data.first()[PrefKeys.QuietEndHour] ?: default

    suspend fun saveQuietEndHour(context: Context, value: Int) {
        context.prefsStore.edit { it[PrefKeys.QuietEndHour] = value.coerceIn(0, 23) }
    }

    suspend fun loadImportanceThreshold(context: Context, default: Float = 0f): Float =
        context.prefsStore.data.first()[PrefKeys.ImportanceThreshold] ?: default

    suspend fun saveImportanceThreshold(context: Context, value: Float) {
        context.prefsStore.edit {
            it[PrefKeys.ImportanceThreshold] = value.coerceIn(0f, 1f)
        }
    }

    suspend fun loadMutedKinds(context: Context): Set<String> =
        context.prefsStore.data.first()[PrefKeys.MutedKinds]
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    suspend fun saveMutedKinds(context: Context, kinds: Set<String>) {
        context.prefsStore.edit {
            it[PrefKeys.MutedKinds] = kinds.joinToString(",")
        }
    }
}
