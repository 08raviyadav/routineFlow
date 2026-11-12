package com.thor.routineflow.actions

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Small TextToSpeech wrapper for the SPEAK_* actions. Lazily initialises on
 * the main thread (TextToSpeech needs a Looper), speaks one utterance at a
 * time, and suspends until it finishes — or 10 seconds pass, whichever comes
 * first, so a stuck engine can never hang a routine.
 */
class TtsHelper(context: Context) {

    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var engine: TextToSpeech? = null

    @Volatile
    private var initFailed = false

    /** Speaks [text]. Returns false when TTS is unavailable or the utterance did not finish. */
    suspend fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        val tts = ensureInit() ?: return false
        return withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val utteranceId = "routineflow-${System.nanoTime()}"
                tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(id: String?) {
                        if (id == utteranceId && cont.isActive) cont.resume(true)
                    }

                    override fun onError(id: String?) {
                        if (id == utteranceId && cont.isActive) cont.resume(false)
                    }

                    override fun onError(id: String?, errorCode: Int) {
                        if (id == utteranceId && cont.isActive) cont.resume(false)
                    }
                })
                val result = runCatching {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                }.getOrDefault(TextToSpeech.ERROR)
                if (result != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
            }
        } ?: false
    }

    fun shutdown() {
        runCatching {
            engine?.stop()
            engine?.shutdown()
        }
        engine = null
    }

    private suspend fun ensureInit(): TextToSpeech? {
        engine?.let { return it }
        if (initFailed) return null
        return initMutex.withLock {
            engine?.let { return@withLock it }
            if (initFailed) return@withLock null
            val created = withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<TextToSpeech?> { cont ->
                    var tts: TextToSpeech? = null
                    tts = try {
                        TextToSpeech(appContext) { status ->
                            if (status == TextToSpeech.SUCCESS) {
                                cont.resume(tts)
                            } else {
                                initFailed = true
                                cont.resume(null)
                            }
                        }
                    } catch (e: Exception) {
                        initFailed = true
                        cont.resume(null)
                        null
                    }
                    if (tts == null && cont.isActive) cont.resume(null)
                }
            }
            if (created != null) {
                runCatching { created.language = Locale.getDefault() }
                engine = created
            }
            created
        }
    }

    companion object {
        private const val SPEAK_TIMEOUT_MS = 10_000L
    }
}
