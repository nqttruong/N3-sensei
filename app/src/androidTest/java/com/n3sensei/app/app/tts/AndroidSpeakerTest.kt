package com.n3sensei.app.app.tts

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.n3sensei.app.app.translate.QuickTranslateActivity
import com.n3sensei.app.domain.translate.Language
import com.n3sensei.app.domain.translate.Utterance
import com.n3sensei.app.domain.translate.VoiceAvailability
import com.n3sensei.app.feature.translate.QuickTranslateTags
import com.n3sensei.app.testing.AppTestState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun closingTheDialogWhileReading_isSafe() {
        AppTestState.reset(seedContent = true)
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setClass(context, QuickTranslateActivity::class.java).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "Good morning everyone")
        ActivityScenario.launch<QuickTranslateActivity>(intent).use { scenario ->
            composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag(QuickTranslateTags.SPEAK_SOURCE).fetchSemanticsNodes().isNotEmpty() }
            // Wait for the voice, then read and close at once.
            composeRule.waitUntil(10_000) {
                runCatching { composeRule.onNodeWithTag(QuickTranslateTags.SPEAK_SOURCE).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }.getOrDefault(false)
            }
            composeRule.onNodeWithTag(QuickTranslateTags.SPEAK_SOURCE).performClick()
            composeRule.onNodeWithTag(QuickTranslateTags.CLOSE).performClick()
            composeRule.waitUntil(15_000) { scenario.state == Lifecycle.State.DESTROYED }
        }
    }
}
