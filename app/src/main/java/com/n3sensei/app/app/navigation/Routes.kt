package com.n3sensei.app.app.navigation

import kotlinx.serialization.Serializable

// Every navigation route in the app is declared here, and only here.
// Each leaf route must have an AppScreen entry (enforced by tests).

@Serializable data object HomeRoute

/** Nested graph for the Learn tab; its start destination is [LearnHubRoute]. */
@Serializable data object LearnGraphRoute

@Serializable data object LearnHubRoute

@Serializable data object VocabularyRoute

@Serializable data object KanjiRoute

@Serializable data object GrammarRoute

@Serializable data object ReviewRoute

@Serializable data object StatsRoute

@Serializable data object SettingsRoute
