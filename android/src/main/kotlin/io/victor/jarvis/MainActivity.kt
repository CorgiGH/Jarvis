package io.victor.jarvis

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val Bg = Color(0xFF14161A)
private val UserBg = Color(0xFF2A2F3A)
private val JarvisBg = Color(0xFF1C2026)
private val UserAccent = Color(0xFF88C0D0)
private val JarvisAccent = Color(0xFFA3BE8C)
private val ErrorAccent = Color(0xFFBF616A)
private val FgMain = Color(0xFFD8DEE9)
private val FgMeta = Color(0xFF7A8499)

private data class Turn(
    val role: String,
    val content: String,
    val model: String? = null,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Bg,
                    surface = JarvisBg,
                    onBackground = FgMain,
                    onSurface = FgMain,
                    primary = UserAccent,
                ),
            ) {
                JarvisApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisApp() {
    val context = LocalContext.current
    var backendUrl by remember { mutableStateOf("http://192.168.1.10:8080") }
    var authToken by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    val turns = remember { mutableStateListOf<Turn>() }
    var sending by remember { mutableStateOf(false) }
    var ttsOn by remember { mutableStateOf(true) }
    var prefsLoaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val client = remember { JarvisClient() }
    val tts = remember { VoiceTts(context) }

    LaunchedEffect(Unit) {
        backendUrl = Prefs.loadBackendUrl(context, default = backendUrl)
        authToken = Prefs.loadAuthToken(context, default = authToken)
        ttsOn = Prefs.loadTtsOn(context, default = ttsOn)
        prefsLoaded = true
    }

    LaunchedEffect(backendUrl, prefsLoaded) {
        if (prefsLoaded) Prefs.saveBackendUrl(context, backendUrl)
    }
    LaunchedEffect(authToken, prefsLoaded) {
        if (prefsLoaded) {
            Prefs.saveAuthToken(context, authToken)
            // Phase 3.1: enqueue the periodic poll only when we have a token;
            // cancel when the user clears it. KEEP-policy on the worker means
            // we don't churn the schedule on recompose.
            if (authToken.isNotBlank()) {
                SignalWorker.enqueue(context)
            } else {
                SignalWorker.cancel(context)
            }
        }
    }
    LaunchedEffect(ttsOn, prefsLoaded) {
        if (prefsLoaded) Prefs.saveTtsOn(context, ttsOn)
    }

    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    val sttLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val first = matches?.firstOrNull()?.trim().orEmpty()
            if (first.isNotEmpty()) {
                msg = if (msg.isBlank()) first else "$msg $first"
            }
        }
    }

    // Phase 3.1: ask for POST_NOTIFICATIONS once on Android 13+. Silent on
    // older versions; SignalWorker no-ops gracefully if the user denies.
    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* result unused — Worker handles missing-perm by skipping notify */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("jarvis", fontFamily = FontFamily.Monospace) },
                actions = {
                    IconButton(onClick = {
                        ttsOn = !ttsOn
                        if (!ttsOn) tts.stop()
                    }) {
                        Icon(
                            imageVector = if (ttsOn) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (ttsOn) "tts on" else "tts off",
                            tint = FgMain,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Bg,
                    titleContentColor = FgMain,
                ),
            )
        },
        containerColor = Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Bg)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("backend url") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = authToken,
                onValueChange = { authToken = it },
                label = { Text("auth token (JARVIS_AUTH_TOKEN)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(turns) { turn ->
                    TurnBubble(turn)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { sttLauncher.launch(sttIntent()) },
                    enabled = !sending,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "voice input",
                        tint = if (sending) FgMeta else UserAccent,
                    )
                }
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    label = { Text("message") },
                    modifier = Modifier.weight(1f),
                    enabled = !sending,
                    minLines = 1,
                    maxLines = 4,
                )
                Button(
                    onClick = {
                        if (msg.isBlank() || sending) return@Button
                        val toSend = msg.trim()
                        msg = ""
                        turns += Turn("user", toSend)
                        sending = true
                        scope.launch {
                            val out = try {
                                if (toSend.startsWith("/sub ")) {
                                    val r = client.runSub(backendUrl, toSend.removePrefix("/sub ").trim(), authToken)
                                    Turn("assistant", r.text, r.model)
                                } else {
                                    val r = client.chat(backendUrl, toSend, authToken)
                                    Turn("assistant", r.reply, r.model)
                                }
                            } catch (e: Exception) {
                                Turn("error", "ERR: ${e.javaClass.simpleName}: ${e.message}")
                            }
                            turns += out
                            sending = false
                            if (ttsOn && out.role == "assistant") {
                                tts.speak(out.content)
                            }
                        }
                    },
                    enabled = !sending && msg.isNotBlank(),
                ) {
                    Text(if (sending) "…" else "send")
                }
            }
        }
    }
}

@Composable
private fun TurnBubble(turn: Turn) {
    val bg = when (turn.role) {
        "user" -> UserBg
        "error" -> JarvisBg
        else -> JarvisBg
    }
    val accent = when (turn.role) {
        "user" -> UserAccent
        "error" -> ErrorAccent
        else -> JarvisAccent
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp)
                .background(accent)
                .padding(start = 8.dp)
                .background(bg)
                .padding(8.dp),
        ) {
            Column {
                if (turn.model != null) {
                    Text(
                        turn.model,
                        color = FgMeta,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    turn.content,
                    color = FgMain,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
