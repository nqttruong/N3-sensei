package com.n3sensei.app.data.importing

import kotlinx.serialization.Serializable

// Wire format of a content pack (see docs/CONTENT_FORMAT.md). Every field is optional at the
// parsing level so that missing data is reported by the validator, record by record, instead
// of failing the whole file.

const val CONTENT_PACK_FORMAT = "n3sensei-content-pack"
const val CONTENT_PACK_FORMAT_VERSION = 1

@Serializable
data class ContentPackDto(
    val format: String? = null,
    val formatVersion: Int? = null,
    val pack: PackDto? = null,
    val sources: List<SourceDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val examples: List<ExampleDto> = emptyList(),
    val lessons: List<LessonDto> = emptyList(),
)

@Serializable
data class PackDto(
    val id: String? = null,
    val title: String? = null,
    val version: Int? = null,
    val jlptLevel: String? = null,
    /** Used by records that do not name their own source. */
    val defaultSourceId: String? = null,
)

@Serializable
data class SourceDto(
    val id: String? = null,
    val name: String? = null,
    val url: String? = null,
    val license: String? = null,
    val attribution: String? = null,
    val licenseVerified: Boolean = false,
)

@Serializable
data class TagDto(val id: String? = null, val name: String? = null)

@Serializable
data class ExampleDto(
    val id: String? = null,
    val japanese: String? = null,
    val furigana: String? = null,
    val translationVi: String? = null,
    val sourceId: String? = null,
)

@Serializable
data class LessonDto(
    val id: String? = null,
    val number: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val vocabulary: List<VocabularyDto> = emptyList(),
    val kanji: List<KanjiDto> = emptyList(),
    val grammar: List<GrammarDto> = emptyList(),
)

@Serializable
data class VocabularyDto(
    val id: String? = null,
    val expression: String? = null,
    val reading: String? = null,
    val furigana: String? = null,
    val meaningVi: String? = null,
    val partOfSpeech: String? = null,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val sourceId: String? = null,
)

@Serializable
data class KanjiDto(
    val id: String? = null,
    val character: String? = null,
    val onReadings: List<String> = emptyList(),
    val kunReadings: List<String> = emptyList(),
    val hanViet: String? = null,
    val meaningVi: String? = null,
    val strokeCount: Int? = null,
    val note: String? = null,
    val examples: List<String> = emptyList(),
    val sourceId: String? = null,
)

@Serializable
data class GrammarDto(
    val id: String? = null,
    val pattern: String? = null,
    val structure: String? = null,
    val meaningVi: String? = null,
    val usage: String? = null,
    val cautionNote: String? = null,
    val examples: List<String> = emptyList(),
    val exercises: List<ExerciseDto> = emptyList(),
    val sourceId: String? = null,
)

@Serializable
data class ExerciseDto(
    val id: String? = null,
    val prompt: String? = null,
    val options: List<String> = emptyList(),
    val answerIndex: Int? = null,
    val explanationVi: String? = null,
)
