package com.n3sensei.app.domain.contrast

import java.security.MessageDigest
import java.text.Normalizer

/**
 * One existing content row as the source resolver reads it: its id and the snapshot fields of
 * [ContrastFingerprint.FIELDS] with their **stored** values (not the learner's edits, which live
 * in user_content_edits).
 */
data class SourceRecord(
    val type: ContrastContentType,
    val id: String,
    val fields: Map<String, SnapshotValue>,
)

/**
 * Source fingerprint, `sourceSchemaVersion` 1 (CONTENT_SCHEMA §5). Same algorithm as
 * `tools/content/contrast/fingerprint.py`; both are checked against
 * `tools/content/contrast/fixtures/fingerprint-cases.json`.
 */
object ContrastFingerprint {
    const val SCHEMA_VERSION = 1
    val SUPPORTED_SCHEMA_VERSIONS = setOf(SCHEMA_VERSION)

    val FIELDS: Map<ContrastContentType, List<String>> = mapOf(
        ContrastContentType.GRAMMAR to listOf("pattern", "structure", "meaningVi"),
        ContrastContentType.VOCABULARY to listOf("expression", "reading", "meaningVi"),
        ContrastContentType.KANJI to listOf("character", "onReadings", "kunReadings", "meaningVi"),
    )

    private const val FIELD_SEPARATOR = '\u001F'
    private const val LIST_SEPARATOR = '\u001E'

    private fun norm(text: String?): String = Normalizer.normalize(text.orEmpty(), Normalizer.Form.NFC).trim()

    private fun norm(value: SnapshotValue?): String = when (value) {
        null -> ""
        is SnapshotValue.Text -> norm(value.value)
        is SnapshotValue.Texts -> value.values.joinToString(LIST_SEPARATOR.toString()) { norm(it) }
    }

    fun canonical(type: ContrastContentType, fields: Map<String, SnapshotValue>): String =
        buildString {
            append(type.name).append(FIELD_SEPARATOR).append(SCHEMA_VERSION)
            FIELDS.getValue(type).forEach { append(FIELD_SEPARATOR).append(norm(fields[it])) }
        }

    fun of(type: ContrastContentType, fields: Map<String, SnapshotValue>): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(type, fields).toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it) }
    }

    /** The snapshot of [record]: exactly the fingerprint fields, as stored. */
    fun snapshotOf(record: SourceRecord): Map<String, SnapshotValue> =
        FIELDS.getValue(record.type).associateWith { record.fields[it] ?: SnapshotValue.Text("") }
}

/** Read access to existing content rows, by primary key or by snapshot (secondary lookup). */
interface SourceLookup {
    fun byId(type: ContrastContentType, id: String): SourceRecord?

    /** Rows whose snapshot fields equal [snapshot] exactly. */
    fun bySnapshot(type: ContrastContentType, snapshot: Map<String, SnapshotValue>): List<SourceRecord>
}

/**
 * Status of one reference against the current content (ARCHITECTURE §7.3). Pure: the data layer
 * supplies the rows. Never relinks a reference: a single snapshot match is only reported.
 */
object ContrastSourceVerifier {

    fun verify(reference: SourceReference, lookup: SourceLookup): ResolvedSource {
        if (reference.sourceSchemaVersion !in ContrastFingerprint.SUPPORTED_SCHEMA_VERSIONS) {
            return ResolvedSource(SourceRefStatus.UNVERIFIABLE)
        }
        val record = lookup.byId(reference.sourceType, reference.sourceContentId)
        if (record != null) {
            val same = ContrastFingerprint.of(record.type, record.fields) == reference.sourceFingerprint &&
                ContrastFingerprint.snapshotOf(record) == normalizedSnapshot(reference)
            return ResolvedSource(if (same) SourceRefStatus.OK else SourceRefStatus.SOURCE_CHANGED, currentId = record.id)
        }
        val candidates = lookup.bySnapshot(reference.sourceType, reference.sourceSnapshot).map { it.id }.sorted()
        return if (candidates.size >= 2) {
            ResolvedSource(SourceRefStatus.AMBIGUOUS, candidateIds = candidates)
        } else {
            ResolvedSource(SourceRefStatus.SOURCE_MISSING, candidateIds = candidates)
        }
    }

    /** The reference snapshot restricted to the fingerprint fields (missing ones read as ""). */
    private fun normalizedSnapshot(reference: SourceReference): Map<String, SnapshotValue> =
        ContrastFingerprint.FIELDS.getValue(reference.sourceType).associateWith { reference.sourceSnapshot[it] ?: SnapshotValue.Text("") }
}
