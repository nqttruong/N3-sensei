package com.n3sensei.app.app.tts

import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.SpeechState
import com.n3sensei.app.domain.translate.Speaker
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** The speech engine as [TtsSpeaker] uses it (Android's TextToSpeech in the app, a fake in tests). */
interface TtsEngine {
    /** [locale]'s voice: available, installed-data missing, or not supported. */
    fun availability(locale: Locale): VoiceAvailability

    /** Sets the voice; false when it cannot be used. */
    fun setLanguage(locale: Locale): Boolean

    /** Speaks [text] after dropping anything queued or playing (QUEUE_FLUSH); false on engine error. */
    fun speakFlush(text: String, utteranceId: String): Boolean

    fun stop()
    fun shutdown()
}

/** Engine events, delivered on any thread. */
interface TtsEngineListener {
    fun onInit(success: Boolean)
    fun onStart(utteranceId: String)
    fun onDone(utteranceId: String)
    fun onError(utteranceId: String)
}

/**
 * Reads the dialog's texts aloud with the device's speech engine.
 *
 * - One engine for the speaker's life (not one per tap), created from the application context
 *   by [createEngine] so no activity is kept.
 * - Nothing is said before the engine is ready or with a missing voice: never another language's
 *   voice instead (no English voice reading Japanese, no romaji).
 * - Each utterance flushes the previous one, so taps never build a queue and two languages
 *   never overlap.
 * - A voice the engine fails to use (it may need the network) is reported in [SpeechState.failed],
 *   never replaced by another one.
 * - After [shutdown] nothing is said and the engine is released.
 */
class TtsSpeaker(createEngine: (TtsEngineListener) -> TtsEngine) : Speaker {

    private val _state = MutableStateFlow(SpeechState())
    override val state: StateFlow<SpeechState> = _state.asStateFlow()

    @Volatile private var ready = false

    @Volatile private var shutDown = false
    private var counter = 0
    private val pending = mutableMapOf<String, Utterance>()

    private val engine: TtsEngine = createEngine(object : TtsEngineListener {
        override fun onInit(success: Boolean) {
            if (shutDown) return
            ready = success
            _state.update { s ->
                s.copy(voices = Language.entries.associateWith { if (success) engineAvailability(it) else VoiceAvailability.NOT_SUPPORTED })
            }
        }

        override fun onStart(utteranceId: String) {
            val utterance = synchronized(pending) { pending[utteranceId] } ?: return
            _state.update { it.copy(speaking = utterance) }
        }

        override fun onDone(utteranceId: String) = finished(utteranceId)
        override fun onError(utteranceId: String) = finished(utteranceId, failed = true)
    })

    private fun engineAvailability(language: Language): VoiceAvailability =
        runCatching { engine.availability(localeOf(language)) }.getOrDefault(VoiceAvailability.NOT_SUPPORTED)

    private fun finished(utteranceId: String, failed: Boolean = false) {
        val utterance = synchronized(pending) { pending.remove(utteranceId) } ?: return
        _state.update {
            val stopped = if (it.speaking == utterance) it.copy(speaking = null) else it
            if (failed) stopped.copy(failed = utterance.language) else stopped
        }
    }

    override fun speak(utterance: Utterance): Boolean {
        if (shutDown || !ready || utterance.text.isBlank()) return false
        if (_state.value.voices[utterance.language] != VoiceAvailability.AVAILABLE) return false
        if (!engine.setLanguage(localeOf(utterance.language))) return false
        val id = synchronized(pending) {
            // The flush drops what was playing: it will not report "done", so forget it here.
            pending.clear()
            "n3s-${++counter}".also { pending[it] = utterance }
        }
        _state.update { it.copy(speaking = utterance, failed = null) }
        return engine.speakFlush(utterance.text, id).also { ok -> if (!ok) finished(id, failed = true) }
    }

    override fun stop() {
        if (shutDown) return
        synchronized(pending) { pending.clear() }
        _state.update { it.copy(speaking = null) }
        if (ready) engine.stop()
    }

    override fun shutdown() {
        if (shutDown) return
        shutDown = true
        ready = false
        synchronized(pending) { pending.clear() }
        _state.update { it.copy(speaking = null) }
        runCatching { engine.stop() }
        engine.shutdown()
    }

    companion object {
        fun localeOf(language: Language): Locale = when (language) {
            Language.VIETNAMESE -> Locale("vi", "VN")
            Language.ENGLISH -> Locale.US
            Language.JAPANESE -> Locale.JAPAN
        }
    }
}
