package com.n3sensei.app.domain.translate

import kotlinx.coroutines.flow.StateFlow

/** Whether the device can read a language aloud. */
enum class VoiceAvailability {
    /** The speech engine is still starting. */
    PENDING,
    AVAILABLE,

    /** The engine supports the language but its voice is not installed. */
    MISSING_DATA,

    /** The engine has no voice for the language, or there is no working engine. */
    NOT_SUPPORTED,
}

/** What the speaker can do and is doing. */
data class SpeechState(
    val voices: Map<Language, VoiceAvailability> = Language.entries.associateWith { VoiceAvailability.PENDING },
    /** The text being read aloud now, if any. */
    val speaking: Utterance? = null,
    /** The language whose voice failed on the last try (an engine error, e.g. a voice that needs the network); cleared by the next try. */
    val failed: Language? = null,
)

/** A text in a language, read aloud with that language's voice only. */
data class Utterance(val text: String, val language: Language)

/** Reads text aloud on the device (no network service). One utterance at a time. */
interface Speaker {
    val state: StateFlow<SpeechState>

    /** Reads [utterance], stopping the one in progress; false (nothing said) without a ready voice for its language. */
    fun speak(utterance: Utterance): Boolean

    fun stop()

    /** Frees the engine; nothing is said afterwards. */
    fun shutdown()
}
