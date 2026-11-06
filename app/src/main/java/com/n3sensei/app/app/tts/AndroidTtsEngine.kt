package com.n3sensei.app.app.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.n3sensei.app.domain.translate.VoiceAvailability
import java.util.Locale

/**
 * Android's [TextToSpeech] behind [TtsEngine]. Built from the **application** context only (an
 * activity context would be kept by the engine binding). Package visibility: the manifest
 * declares the `TTS_SERVICE` intent in `<queries>`, needed to reach the engine on Android 11+.
 */
class AndroidTtsEngine(context: Context, listener: TtsEngineListener) : TtsEngine {

    /** The context the engine holds: always the application. */
    val boundContext: Context = context.applicationContext

    private val tts = TextToSpeech(boundContext) { status -> listener.onInit(status == TextToSpeech.SUCCESS) }.apply {
        setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = listener.onStart(utteranceId)
            override fun onDone(utteranceId: String) = listener.onDone(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = listener.onError(utteranceId)
            override fun onError(utteranceId: String, errorCode: Int) = listener.onError(utteranceId)
            override fun onStop(utteranceId: String, interrupted: Boolean) = listener.onDone(utteranceId)
        })
    }

    override fun availability(locale: Locale): VoiceAvailability = when (tts.isLanguageAvailable(locale)) {
        TextToSpeech.LANG_AVAILABLE, TextToSpeech.LANG_COUNTRY_AVAILABLE, TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> VoiceAvailability.AVAILABLE
        TextToSpeech.LANG_MISSING_DATA -> VoiceAvailability.MISSING_DATA
        else -> VoiceAvailability.NOT_SUPPORTED
    }

    override fun setLanguage(locale: Locale): Boolean = tts.setLanguage(locale) >= TextToSpeech.LANG_AVAILABLE

    override fun speakFlush(text: String, utteranceId: String): Boolean =
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.SUCCESS

    override fun stop() {
        tts.stop()
    }

    override fun shutdown() = tts.shutdown()

    companion object {
        fun factory(context: Context): (TtsEngineListener) -> TtsEngine = { listener -> AndroidTtsEngine(context, listener) }

        /** Opens the engine's voice download screen (the learner chooses what to install). */
        fun installVoicesIntent(): Intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
