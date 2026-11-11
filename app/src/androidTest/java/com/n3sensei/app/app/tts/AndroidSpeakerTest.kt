package com.n3sensei.app.app.tts

import android.app.Application
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import com.n3sensei.app.testing.OverlayTesting
import com.n3sensei.app.domain.translate.QuickTranslateSettings
import com.n3sensei.app.domain.translate.TranslationMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The device's real speech engine (the emulator's Google TTS) behind [TtsSpeaker]. */
@RunWith(AndroidJUnit4::class)
class AndroidSpeakerTest {

    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(100)
        }
    }

    @Test
    fun startsReadsEnglish_onlyWithTheApplicationContext_andShutsDown() {
        var engine: AndroidTtsEngine? = null
        lateinit var speaker: TtsSpeaker
        instrumentation.runOnMainSync {
            speaker = TtsSpeaker { listener -> AndroidTtsEngine(context, listener).also { engine = it } }
        }
        // Never an activity: the engine holds the application.
        assertTrue(engine!!.boundContext is Application)
        waitUntil { speaker.state.value.voices.values.none { it == VoiceAvailability.PENDING } }
        val voices = speaker.state.value.voices
        Log.i("N3S_TTS", "voices on this device: $voices")
        assertNotEquals(VoiceAvailability.NOT_SUPPORTED, voices[Language.ENGLISH])
        if (voices[Language.ENGLISH] == VoiceAvailability.AVAILABLE) {
            assertTrue(speaker.speak(Utterance("Good morning", Language.ENGLISH)))
            waitUntil(15_000) { speaker.state.value.speaking == null }
        }
        speaker.shutdown()
        assertFalse(speaker.speak(Utterance("Good morning", Language.ENGLISH)))
    }

    @Test
    fun closingThePopupWhileReading_isSafe() {
        AppTestState.reset(seedContent = true)
        OverlayTesting.permission(true)
        // Japanese → Vietnamese only: the dictionary answers, no model needed.
        runBlocking { AppTestState.appContainer.userPreferencesRepository.setQuickTranslateSettings(QuickTranslateSettings(mode = TranslationMode.JAPANESE)) }
        try {
            assertTrue(OverlayTesting.show("経験"))
            val speaker = QuickTranslateTags.speak(Language.VIETNAMESE)
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(speaker).fetchSemanticsNodes().isNotEmpty() }
            // Wait for the voice, then read and close at once.
            composeRule.waitUntil(10_000) {
                runCatching { composeRule.onNodeWithTag(speaker).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(speaker).performClick()
            composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
            composeRule.waitUntil(5_000) { !OverlayTesting.isShowing }
        } finally {
            OverlayTesting.close()
            AppTestState.reset(seedContent = false)
        }
    }
}
