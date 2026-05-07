package io.victor.jarvis

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
