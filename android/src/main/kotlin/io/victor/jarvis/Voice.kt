package io.victor.jarvis

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

/** System speech-to-text picker intent. Launches the platform recognizer
 *  dialog; result is handled by an ActivityResultLauncher in MainActivity. */
fun sttIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(
        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
    )
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    putExtra(RecognizerIntent.EXTRA_PROMPT, "speak to jarvis…")
}

/** Lightweight wrapper around android.speech.tts.TextToSpeech that survives
 *  configuration changes when held in a Compose remember block scoped to
 *  the activity. Call [speak] once initialized; calls before init silently
 *  drop. Always [shutdown] when the owning composable leaves composition. */
class VoiceTts(context: Context) {
    @Volatile private var ready = false

    private val engine: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = (status == TextToSpeech.SUCCESS)
    }

    init {
        // Engine init is async; language is set after onInit fires.
    }

    fun speak(text: String) {
        if (!ready || text.isBlank()) return
        runCatching {
            engine.language = Locale.getDefault()
            engine.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis-${System.currentTimeMillis()}",
            )
        }
    }

    fun stop() {
        runCatching { engine.stop() }
    }

    fun shutdown() {
        runCatching {
            engine.stop()
            engine.shutdown()
        }
    }
}
