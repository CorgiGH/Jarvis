package io.victor.jarvis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Bg = Color(0xFF14161A)
private val Card = Color(0xFF1C2026)
private val Fg = Color(0xFFD8DEE9)
private val Muted = Color(0xFF7A8499)

/**
 * R7 — Settings screen: quiet hours, importance threshold, per-kind mute,
 * signal history feed.
 */
@Composable
fun SettingsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var quietStart by remember { mutableStateOf(23) }
    var quietEnd by remember { mutableStateOf(7) }
    var threshold by remember { mutableStateOf(0f) }
    val mutedKinds = remember { mutableStateListOf<String>() }
    val historySignals = remember { mutableStateListOf<Signal>() }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        quietStart = Prefs.loadQuietStartHour(context)
        quietEnd = Prefs.loadQuietEndHour(context)
        threshold = Prefs.loadImportanceThreshold(context)
        mutedKinds.clear()
        mutedKinds.addAll(Prefs.loadMutedKinds(context))
        loaded = true

        // Pull recent history.
        val baseUrl = Prefs.loadBackendUrl(context, default = "")
        val token = Prefs.loadAuthToken(context, default = "")
        if (baseUrl.isNotBlank() && token.isNotBlank()) {
            val client = JarvisClient()
            try {
                val signals = client.fetchSignals(baseUrl, since = "", token, limit = 50)
                historySignals.clear()
                historySignals.addAll(signals.sortedByDescending { it.ts })
            } catch (_: Exception) {
                // ignore — empty history shown
            } finally {
                client.close()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("settings", color = Fg)
            OutlinedButton(onClick = onClose) { Text("close") }
        }

        // Quiet hours
        Text("quiet hours (local time)", color = Fg)
        Text("start: $quietStart  end: $quietEnd  ${if (quietStart == quietEnd) "(disabled)" else ""}",
             color = Muted)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { quietStart = (quietStart + 23) % 24; scope.launch { Prefs.saveQuietStartHour(context, quietStart) } }) { Text("start -") }
            Button(onClick = { quietStart = (quietStart + 1) % 24; scope.launch { Prefs.saveQuietStartHour(context, quietStart) } }) { Text("start +") }
            Button(onClick = { quietEnd = (quietEnd + 23) % 24; scope.launch { Prefs.saveQuietEndHour(context, quietEnd) } }) { Text("end -") }
            Button(onClick = { quietEnd = (quietEnd + 1) % 24; scope.launch { Prefs.saveQuietEndHour(context, quietEnd) } }) { Text("end +") }
        }

        Divider(color = Muted)

        // Threshold
        Text("importance threshold: ${"%.2f".format(threshold)}", color = Fg)
        Slider(
            value = threshold,
            onValueChange = { threshold = it; scope.launch { Prefs.saveImportanceThreshold(context, it) } },
            valueRange = 0f..1f,
        )

        Divider(color = Muted)

        // Per-kind mute
        Text("mute kinds", color = Fg)
        listOf("ctx_model_summary", "reflection", "error").forEach { kind ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = kind in mutedKinds,
                    onCheckedChange = { checked ->
                        if (checked) mutedKinds.add(kind) else mutedKinds.remove(kind)
                        scope.launch { Prefs.saveMutedKinds(context, mutedKinds.toSet()) }
                    },
                )
                Text(kind, color = Fg)
            }
        }

        Divider(color = Muted)

        // History feed
        Text("recent signals (${historySignals.size})", color = Fg)
        if (historySignals.isEmpty()) {
            Text("(none yet — pull when you have a token + signals)", color = Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(historySignals) { sig ->
                    Column(modifier = Modifier.fillMaxWidth().background(Card).padding(8.dp)) {
                        Text("[${sig.kind}] imp=${"%.2f".format(sig.importance)}",
                             color = Muted)
                        Text(sig.snippet.take(180), color = Fg)
                        Text(sig.ts, color = Muted)
                    }
                }
            }
        }
    }
}
