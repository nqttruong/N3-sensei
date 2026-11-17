package com.n3sensei.app.data.repository

import com.n3sensei.app.data.local.ContentEdits
import com.n3sensei.app.data.local.dao.ContrastDao
import com.n3sensei.app.data.local.entity.UserContentEditEntity
import com.n3sensei.app.data.local.entity.GrammarEntity
import com.n3sensei.app.data.local.entity.KanjiEntity
import com.n3sensei.app.data.local.entity.VocabularyEntity
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastFingerprint
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastSourceVerifier
import com.n3sensei.app.domain.contrast.ResolvedSource
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.SourceLookup
import com.n3sensei.app.domain.contrast.SourceRecord
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.repository.ContrastSourceResolver

/**
 * The source row values a fingerprint is computed from: the PACK values as the importer stored them
 * (trimmed, empty readings dropped). Callers pass rows rebuilt with [ContentEdits.base], because the
 * content tables hold the learner's edits merged in.
 */
internal fun GrammarEntity.toSourceRecord() = SourceRecord(
    ContrastContentType.GRAMMAR, id,
    mapOf("pattern" to SnapshotValue.Text(pattern), "structure" to SnapshotValue.Text(structure), "meaningVi" to SnapshotValue.Text(meaningVi)),
)

internal fun VocabularyEntity.toSourceRecord() = SourceRecord(
    ContrastContentType.VOCABULARY, id,
    mapOf("expression" to SnapshotValue.Text(expression), "reading" to SnapshotValue.Text(reading), "meaningVi" to SnapshotValue.Text(meaningVi)),
)

internal fun KanjiEntity.toSourceRecord() = SourceRecord(
    ContrastContentType.KANJI, id,
    mapOf(
        "character" to SnapshotValue.Text(character),
        "onReadings" to SnapshotValue.Texts(onReadings),
        "kunReadings" to SnapshotValue.Texts(kunReadings),
        "meaningVi" to SnapshotValue.Text(meaningVi),
    ),
)

/**
 * Checks Contrast items against the grammar / vocabulary / kanji rows in Room (D3: no foreign key).
 * The learner's own edits are never part of a fingerprint (D5): content rows hold the merged values,
 * so each row is rebuilt with the app's own [ContentEdits.base] (the pack value of each edited field,
 * user_content_edits.base_value) before it is fingerprinted. Never relinks, never deletes; the decision is
 * [ContrastSourceVerifier]'s (OK, SOURCE_CHANGED, SOURCE_MISSING, AMBIGUOUS, UNVERIFIABLE).
 */
class RoomContrastSourceResolver(private val dao: ContrastDao) : ContrastSourceResolver {

    override suspend fun resolve(items: List<ContrastItem>): Map<String, ResolvedSource> {
        val lookup = lookupFor(items.map { it.source })
        return items.associate { it.id to ContrastSourceVerifier.verify(it.source, lookup) }
    }

    /**
     * Loads, in a few queries, every row the [references] need (by id, then by snapshot for ids that
     * are gone), and answers [SourceLookup] from memory: the domain verifier and validator are synchronous.
     */
    suspend fun lookupFor(references: List<SourceReference>): SourceLookup {
        val byType = references.groupBy { it.sourceType }
        val records = mutableListOf<SourceRecord>()
        byType.forEach { (type, refs) ->
            val ids = refs.map { it.sourceContentId }.distinct()
            records += ids.chunked(MAX_IN).flatMap { chunk -> packRecords(type, chunk) }
            val found = records.filter { it.type == type }.map { it.id }.toSet()
            refs.filter { it.sourceContentId !in found }.forEach { ref ->
                fun text(name: String) = (ref.sourceSnapshot[name] as? SnapshotValue.Text)?.value
                val candidateIds = when (type) {
                    ContrastContentType.GRAMMAR -> text("pattern")?.let { dao.grammarByPattern(it) }.orEmpty().map { it.id }
                    ContrastContentType.VOCABULARY -> {
                        val expression = text("expression")
                        val reading = text("reading")
                        if (expression != null && reading != null) dao.vocabularyByExpression(expression, reading).map { it.id } else emptyList()
                    }
                    ContrastContentType.KANJI -> text("character")?.let { dao.kanjiByCharacter(it) }.orEmpty().map { it.id }
                }
                if (candidateIds.isNotEmpty()) records += packRecords(type, candidateIds)
            }
        }
        return InMemorySourceLookup(records.distinctBy { it.type to it.id })
    }

    /** Rows of [ids] with their pack values (learner edits undone), as source records. */
    private suspend fun packRecords(type: ContrastContentType, ids: List<String>): List<SourceRecord> {
        fun editsBy(edits: List<UserContentEditEntity>) = edits.groupBy { it.itemId }
        return when (type) {
            ContrastContentType.GRAMMAR -> {
                val edits = editsBy(dao.edits(ItemType.GRAMMAR, ids))
                dao.grammarRows(ids).map { ContentEdits.base(it, edits[it.id].orEmpty()).toSourceRecord() }
            }
            ContrastContentType.VOCABULARY -> {
                val edits = editsBy(dao.edits(ItemType.VOCABULARY, ids))
                dao.vocabularyRows(ids).map { ContentEdits.base(it, edits[it.id].orEmpty()).toSourceRecord() }
            }
            ContrastContentType.KANJI -> {
                val edits = editsBy(dao.edits(ItemType.KANJI, ids))
                dao.kanjiRows(ids).map { ContentEdits.base(it, edits[it.id].orEmpty()).toSourceRecord() }
            }
        }
    }

    private class InMemorySourceLookup(private val records: List<SourceRecord>) : SourceLookup {
        private val byKey = records.associateBy { it.type to it.id }

        override fun byId(type: ContrastContentType, id: String): SourceRecord? = byKey[type to id]

        override fun bySnapshot(type: ContrastContentType, snapshot: Map<String, SnapshotValue>): List<SourceRecord> {
            val wanted = ContrastFingerprint.FIELDS.getValue(type).associateWith { snapshot[it] ?: SnapshotValue.Text("") }
            return records.filter { it.type == type && ContrastFingerprint.snapshotOf(it) == wanted }
        }
    }

    private companion object {
        /** Stay well below SQLite's bound-parameter limit. */
        const val MAX_IN = 500
    }
}
