package com.n3sensei.app.app.tts

import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsSpeakerTest {

    /** Records what the speaker asks of the engine; voices per language set by the test. */
    private class FakeEngine(val voices: Map<String, VoiceAvailability>) : TtsEngine {
        lateinit var listener: TtsEngineListener
        val calls = mutableListOf<String>()
        var language: Locale? = null
        var engines = 0
        override fun availability(locale: Locale) = voices[locale.language] ?: VoiceAvailability.NOT_SUPPORTED
        override fun setLanguage(locale: Locale): Boolean {
            language = locale
            calls += "lang:${locale.language}"
            return availability(locale) == VoiceAvailability.AVAILABLE
        }
        var lastId: String? = null
        override fun speakFlush(text: String, utteranceId: String): Boolean {
            lastId = utteranceId
            calls += "flush:$text"
            return true
        }
        override fun stop() {
            calls += "stop"
        }
        override fun shutdown() {
            calls += "shutdown"
        }
    }

    private val all = mapOf("en" to VoiceAvailability.AVAILABLE, "ja" to VoiceAvailability.AVAILABLE, "vi" to VoiceAvailability.AVAILABLE)
    private var created = 0

    private fun speaker(engine: FakeEngine) = TtsSpeaker { listener ->
        created++
        engine.also { it.listener = listener }
    }

    private val hello = Utterance("hello", Language.ENGLISH)
    private val keiken = Utterance("けいけん", Language.JAPANESE)

    @Test
    fun english_isReadWithAnEnglishVoice_japaneseWithAJapaneseOne() {
        val engine = FakeEngine(all)
        val s = speaker(engine)
        engine.listener.onInit(true)
        assertTrue(s.speak(hello))
        assertEquals("en", engine.language?.language)
        assertTrue(s.speak(keiken))
        assertEquals("ja", engine.language?.language)
        assertEquals(listOf("lang:en", "flush:hello", "lang:ja", "flush:けいけん"), engine.calls)
        assertEquals(1, created) // one engine for every tap
    }

    @Test
    fun switchingOrTappingAgain_flushesTheOldOne_noQueue() {
        val engine = FakeEngine(all)
        val s = speaker(engine)
        engine.listener.onInit(true)
        repeat(5) { s.speak(hello) }
        s.speak(keiken)
        // Every utterance replaces the previous one (QUEUE_FLUSH): no queue of six.
        assertEquals(6, engine.calls.count { it.startsWith("flush:") })
        assertEquals(keiken, s.state.value.speaking)
        engine.listener.onStart("n3s-6")
        engine.listener.onDone("n3s-6")
        assertNull(s.state.value.speaking)
    }

    @Test
    fun aVoiceThatFailsToSpeak_isReported_untilTheNextTry() {
        // Seen on the emulator offline: the Japanese voice was "available" but its engine
        // failed (code -4); the tap did nothing and said nothing.
        val engine = FakeEngine(all)
        val s = speaker(engine)
        engine.listener.onInit(true)
        assertTrue(s.speak(keiken))
        engine.listener.onError(engine.lastId!!)
        assertEquals(Language.JAPANESE, s.state.value.failed)
        assertNull(s.state.value.speaking)
        // An error of an utterance that was flushed away says nothing.
        assertTrue(s.speak(hello))
        assertNull(s.state.value.failed)
        val helloId = engine.lastId!!
        assertTrue(s.speak(keiken))
        engine.listener.onError(helloId)
        assertNull(s.state.value.failed)
        assertEquals(keiken, s.state.value.speaking)
    }

    @Test
    fun beforeTheEngineIsReady_nothingIsSaid() {
        val engine = FakeEngine(all)
        val s = speaker(engine)
        assertEquals(VoiceAvailability.PENDING, s.state.value.voices[Language.ENGLISH])
        assertFalse(s.speak(hello))
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun anEngineThatFailsToStart_hasNoVoices() {
        val engine = FakeEngine(all)
        val s = speaker(engine)
        engine.listener.onInit(false)
        assertEquals(VoiceAvailability.NOT_SUPPORTED, s.state.value.voices[Language.JAPANESE])
        assertFalse(s.speak(hello))
    }

    @Test
    fun noJapaneseVoice_japaneseIsNotRead_byAnyOtherVoice() {
        val engine = FakeEngine(mapOf("en" to VoiceAvailability.AVAILABLE, "ja" to VoiceAvailability.MISSING_DATA))
        val s = speaker(engine)
        engine.listener.onInit(true)
        assertEquals(VoiceAvailability.MISSING_DATA, s.state.value.voices[Language.JAPANESE])
        assertFalse(s.speak(keiken))
        assertTrue(engine.calls.none { it.startsWith("flush:") })
        assertTrue(s.speak(hello))
    }

    @Test
    fun noEnglishVoice_englishIsNotRead() {
        val engine = FakeEngine(mapOf("ja" to VoiceAvailability.AVAILABLE))
        val s = speaker(engine)
        engine.listener.onInit(true)
        assertEquals(VoiceAvailability.NOT_SUPPORTED, s.state.value.voices[Language.ENGLISH])
        assertFalse(s.speak(hello))
        assertTrue(s.speak(keiken))
    }

    @Test
    fun stop_andShutdown_thenNothingMore() {
        val engine = FakeEngine(all)
        val s = speaker(engine)
        engine.listener.onInit(true)
        s.speak(hello)
        s.stop()
        assertNull(s.state.value.speaking)
        s.speak(hello)
        s.shutdown()
        assertEquals("shutdown", engine.calls.last())
        assertNull(s.state.value.speaking)
        engine.calls.clear()
        assertFalse(s.speak(hello))
        s.stop()
        s.shutdown()
        assertTrue(engine.calls.isEmpty())
        // A late init after shutdown changes nothing.
        engine.listener.onInit(true)
        assertFalse(s.speak(hello))
    }
}
