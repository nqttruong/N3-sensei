package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.contrast.Collocation
import com.n3sensei.app.domain.contrast.ComparisonRow
import com.n3sensei.app.domain.contrast.ContrastExample
import com.n3sensei.app.domain.contrast.ContrastOption
import com.n3sensei.app.domain.contrast.Correction
import com.n3sensei.app.domain.contrast.ItemDetails
import com.n3sensei.app.domain.contrast.MemoryTip
import com.n3sensei.app.domain.contrast.QuestionContext
import com.n3sensei.app.domain.contrast.QuestionPrompt
import com.n3sensei.app.domain.contrast.Radical
import com.n3sensei.app.domain.contrast.Sentence
import com.n3sensei.app.domain.contrast.SnapshotValue
import com.n3sensei.app.domain.contrast.TheoryBlock
import com.n3sensei.app.domain.contrast.validation.ContrastIssue
import com.n3sensei.app.domain.contrast.validation.ContrastIssueCode
import com.n3sensei.app.domain.contrast.validation.ContrastSeverity
import com.n3sensei.app.domain.contrast.validation.RawApproval
import com.n3sensei.app.domain.contrast.validation.RawContrastPack
import com.n3sensei.app.domain.contrast.validation.RawGroup
import com.n3sensei.app.domain.contrast.validation.RawItem
import com.n3sensei.app.domain.contrast.validation.RawLicense
import com.n3sensei.app.domain.contrast.validation.RawMistake
import com.n3sensei.app.domain.contrast.validation.RawPackMeta
import com.n3sensei.app.domain.contrast.validation.RawQuestion
import com.n3sensei.app.domain.contrast.validation.RawRetiredGroup
import com.n3sensei.app.domain.contrast.validation.RawSource
import com.n3sensei.app.domain.contrast.validation.RawSourcePack
import com.n3sensei.app.domain.contrast.validation.RawWarningAck
import com.n3sensei.app.domain.contrast.validation.ValidationMode
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * JSON → [RawContrastPack] for a Contrast pack (format `n3sensei-contrast-pack`, schema 1).
 *
 * Two jobs, kept apart:
 * 1. STRUCTURE: the rules of docs/contrast/contrast-pack.schema.json (required fields, unknown
 *    fields, types, enums, patterns, ranges), each finding mapped to a code exactly as
 *    `_map_schema_error` in tools/content/contrast/validator.py (a specific code where one exists,
 *    otherwise SCHEMA_VIOLATION), at the same path.
 * 2. A lenient [RawContrastPack] (absent or ill-typed values read as null / "" / 0 / empty, like the
 *    Python validator's `.get` defaults) so the semantic validator can still run and report too.
 *
 * Nothing is ever filled in for the learner: a pack with any ERROR is refused by the importer.
 */
object ContrastPackParser {

    data class Parsed(
        /** Null only when the text is not a JSON object. */
        val raw: RawContrastPack?,
        val issues: List<ContrastIssue>,
    )

    private val json = Json

    fun parse(text: String, mode: ValidationMode = ValidationMode.IMPORT): Parsed {
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return Parsed(null, listOf(ContrastIssue(ContrastIssueCode.INVALID_JSON, ContrastSeverity.ERROR, "", detail = e.message?.take(200))))
        } catch (e: IllegalArgumentException) {
            return Parsed(null, listOf(ContrastIssue(ContrastIssueCode.INVALID_JSON, ContrastSeverity.ERROR, "", detail = e.message?.take(200))))
        }
        if (element !is JsonObject) return Parsed(null, listOf(ContrastIssue(ContrastIssueCode.SCHEMA_VIOLATION, ContrastSeverity.ERROR, "")))
        val structure = Structure(mode).apply { checkRoot(element) }
        return Parsed(Lenient.pack(element), structure.issues)
    }

    // ---- 1. Structure (schema) -------------------------------------------------------------------

    private class Structure(private val mode: ValidationMode) {
        val issues = mutableListOf<ContrastIssue>()
        private val seen = mutableSetOf<Pair<ContrastIssueCode, String>>()

        private fun add(code: ContrastIssueCode, path: String) {
            if (!seen.add(code to path)) return
            val severity = if (code in ContrastIssueCode.MODE_DEPENDENT && mode == ValidationMode.IMPORT) ContrastSeverity.WARNING else ContrastSeverity.ERROR
            issues += ContrastIssue(code, severity, path)
        }

        private fun sv(path: String) = add(ContrastIssueCode.SCHEMA_VIOLATION, path)

        private fun join(parent: String, field: String) = if (parent.isEmpty()) field else "$parent.$field"

        // -- mapping, as validator._map_schema_error --

        private val groupPath = Regex("""^groups\[\d+]$""")
        private val questionPath = Regex("""^groups\[\d+]\.questions\[\d+]$""")
        private val approvalPath = Regex("""^groups\[\d+]\.approval$""")
        private val metadataText = Regex("""^(pack\.(id|title|jlptLevel|publishedAt|license\.text)|groups\[\d+]\.(id|jlptLevel|title|summaryVi|updatedAt))$""")
        private val groupMeta = setOf("id", "revision", "status", "contentType", "jlptLevel", "title", "summaryVi", "updatedAt", "approval")

        fun required(parent: String, field: String) {
            val path = join(parent, field)
            val code = when {
                parent.isEmpty() && (field == "format" || field == "schemaVersion") -> ContrastIssueCode.UNSUPPORTED_FORMAT
                parent.isEmpty() && field == "pack" -> ContrastIssueCode.MISSING_METADATA
                parent == "pack" || parent == "pack.license" || approvalPath.matches(parent) -> ContrastIssueCode.MISSING_METADATA
                groupPath.matches(parent) -> when (field) {
                    in groupMeta -> ContrastIssueCode.MISSING_METADATA
                    "items" -> ContrastIssueCode.TOO_FEW_ITEMS
                    "questions" -> ContrastIssueCode.NO_QUESTIONS
                    "theory" -> ContrastIssueCode.MISSING_EXPLANATION
                    else -> ContrastIssueCode.SCHEMA_VIOLATION
                }
                questionPath.matches(parent) -> when (field) {
                    "explanationVi" -> ContrastIssueCode.MISSING_EXPLANATION
                    "correctOptionId" -> ContrastIssueCode.MISSING_CORRECT_ANSWER
                    else -> ContrastIssueCode.SCHEMA_VIOLATION
                }
                else -> ContrastIssueCode.SCHEMA_VIOLATION
            }
            add(code, path)
        }

        private fun minItems(path: String) {
            val code = when {
                !path.startsWith("groups[") -> ContrastIssueCode.SCHEMA_VIOLATION
                path.endsWith(".items") -> ContrastIssueCode.TOO_FEW_ITEMS
                path.endsWith(".questions") -> ContrastIssueCode.NO_QUESTIONS
                path.endsWith(".theory") -> ContrastIssueCode.MISSING_EXPLANATION
                path.endsWith(".options") -> ContrastIssueCode.TOO_FEW_OPTIONS
                else -> ContrastIssueCode.SCHEMA_VIOLATION
            }
            add(code, path)
        }

        /** A `pattern` / `minLength` failure (blank metadata and explanations have their own codes). */
        private fun badText(path: String, value: String) {
            val blank = value.isBlank()
            when {
                blank && metadataText.matches(path) -> add(ContrastIssueCode.MISSING_METADATA, path)
                blank && path.endsWith(".explanationVi") && questionPath.matches(path.substringBeforeLast('.')) -> add(ContrastIssueCode.MISSING_EXPLANATION, path)
                else -> sv(path)
            }
        }

        // -- value checks --

        private fun stringOf(value: JsonElement, path: String): String? {
            if (value is JsonPrimitive && value.isString) return value.content
            sv(path)
            return null
        }

        private fun text(value: JsonElement, path: String) {
            val s = stringOf(value, path) ?: return
            if (s.isEmpty() || !Regex("""\S""").containsMatchIn(s)) badText(path, s)
        }

        private fun furigana(value: JsonElement, path: String) {
            val s = stringOf(value, path) ?: return
            if (s.isEmpty()) sv(path)
        }

        private fun pattern(value: JsonElement, path: String, regex: Regex, maxLength: Int? = null) {
            val s = stringOf(value, path) ?: return
            if (!regex.containsMatchIn(s)) badText(path, s)
            if (maxLength != null && s.codePointCount(0, s.length) > maxLength) sv(path)
        }

        private fun id(value: JsonElement, path: String) = pattern(value, path, ID)

        private fun date(value: JsonElement, path: String) {
            val s = stringOf(value, path) ?: return
            if (!DATE.containsMatchIn(s)) badText(path, s)
            val valid = try {
                LocalDate.parse(s)
                true
            } catch (e: DateTimeParseException) {
                false
            }
            if (!valid) sv(path) // `format: date`
        }

        private fun enumOf(value: JsonElement, path: String, allowed: Set<String>, code: ContrastIssueCode = ContrastIssueCode.SCHEMA_VIOLATION) {
            if (!(value is JsonPrimitive && value.isString && value.content in allowed)) add(code, path)
        }

        private fun integer(value: JsonElement, path: String, min: Long? = null, max: Long? = null, minCode: ContrastIssueCode = ContrastIssueCode.SCHEMA_VIOLATION): Long? {
            val n = (value as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
            if (n == null) {
                sv(path)
                return null
            }
            if (min != null && n < min) add(minCode, path)
            if (max != null && n > max) sv(path)
            return n
        }

        private fun bool(value: JsonElement, path: String) {
            if (!(value is JsonPrimitive && !value.isString && value.booleanOrNull != null)) sv(path)
        }

        /** Checks `obj` has every required field and no unknown one; returns it, or null when it is not an object. */
        private fun obj(value: JsonElement, path: String, required: List<String>, allowed: Set<String>): JsonObject? {
            if (value !is JsonObject) {
                sv(path)
                return null
            }
            required.filter { it !in value }.forEach { required(path, it) }
            value.keys.filter { it !in allowed }.sorted().forEach { sv(join(path, it)) }
            return value
        }

        private fun array(value: JsonElement, path: String, minItems: Int = 0, maxItems: Int? = null): JsonArray? {
            if (value !is JsonArray) {
                sv(path)
                return null
            }
            if (value.size < minItems) minItems(path)
            if (maxItems != null && value.size > maxItems) sv(path)
            return value
        }

        private inline fun JsonObject.field(name: String, path: String, check: (JsonElement, String) -> Unit) {
            this[name]?.let { check(it, join(path, name)) }
        }

        private inline fun JsonArray?.each(path: String, check: (JsonElement, String) -> Unit) {
            this?.forEachIndexed { i, e -> check(e, "$path[$i]") }
        }

        // -- the schema --

        fun checkRoot(root: JsonObject) {
            obj(root, "", listOf("format", "schemaVersion", "pack", "sourcePacks", "groups"), setOf("\$schema", "format", "schemaVersion", "pack", "sourcePacks", "groups", "retiredGroups"))
            root.field("\$schema", "") { v, p -> stringOf(v, p) }
            root.field("format", "") { v, p -> if (!(v is JsonPrimitive && v.isString && v.content == FORMAT)) add(ContrastIssueCode.UNSUPPORTED_FORMAT, p) }
            root.field("schemaVersion", "") { v, p -> if (!(v is JsonPrimitive && !v.isString && v.longOrNull == 1L)) add(ContrastIssueCode.UNSUPPORTED_FORMAT, p) }
            root.field("pack", "") { v, p -> pack(v, p) }
            root.field("sourcePacks", "") { v, p ->
                array(v, p).each(p) { e, ep ->
                    obj(e, ep, listOf("id", "version"), setOf("id", "version"))?.let {
                        it.field("id", ep, ::id)
                        it.field("version", ep) { x, xp -> integer(x, xp, min = 1) }
                    }
                }
            }
            root.field("groups", "") { v, p -> array(v, p, minItems = 1).each(p) { e, ep -> group(e, ep) } }
            root.field("retiredGroups", "") { v, p ->
                array(v, p).each(p) { e, ep ->
                    obj(e, ep, listOf("id", "retiredInVersion", "reasonVi"), setOf("id", "retiredInVersion", "reasonVi", "replacedBy"))?.let {
                        it.field("id", ep, ::id)
                        it.field("retiredInVersion", ep) { x, xp -> integer(x, xp, min = 1) }
                        it.field("reasonVi", ep, ::text)
                        it.field("replacedBy", ep, ::id)
                    }
                }
            }
        }

        private fun pack(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("id", "title", "jlptLevel", "contentVersion", "publishedAt", "license"),
                setOf("id", "title", "description", "jlptLevel", "contentVersion", "publishedAt", "license")) ?: return
            o.field("id", p, ::id)
            o.field("title", p, ::text)
            o.field("description", p, ::text)
            o.field("jlptLevel", p) { x, xp -> enumOf(x, xp, JLPT) }
            o.field("contentVersion", p) { x, xp -> integer(x, xp, min = 1, minCode = ContrastIssueCode.INVALID_CONTENT_VERSION) }
            o.field("publishedAt", p, ::date)
            o.field("license", p) { x, xp ->
                obj(x, xp, listOf("text", "licenseVerified"), setOf("text", "licenseVerified"))?.let {
                    it.field("text", xp, ::text)
                    it.field("licenseVerified", xp, ::bool)
                }
            }
        }

        private fun group(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("id", "revision", "status", "contentType", "jlptLevel", "title", "summaryVi", "updatedAt", "items", "theory", "comparison", "examples", "memoryTips", "commonMistakes", "questions"),
                setOf("id", "revision", "status", "contentType", "jlptLevel", "title", "summaryVi", "updatedAt", "approval", "items", "theory", "comparison", "examples", "memoryTips", "commonMistakes", "questions")) ?: return
            o.field("id", p, ::id)
            o.field("revision", p) { x, xp -> integer(x, xp, min = 1) }
            o.field("status", p) { x, xp -> enumOf(x, xp, STATUSES) }
            o.field("contentType", p) { x, xp -> enumOf(x, xp, CONTENT_TYPES) }
            o.field("jlptLevel", p) { x, xp -> enumOf(x, xp, JLPT) }
            o.field("title", p, ::text)
            o.field("summaryVi", p, ::text)
            o.field("updatedAt", p, ::date)
            val status = (o["status"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if ((status == "APPROVED" || status == "PUBLISHED") && "approval" !in o) required(p, "approval")
            o.field("approval", p) { x, xp -> approval(x, xp) }
            o.field("items", p) { x, xp -> array(x, xp, minItems = 2, maxItems = 4).each(xp) { e, ep -> item(e, ep) } }
            o.field("theory", p) { x, xp ->
                array(x, xp, minItems = 1).each(xp) { e, ep ->
                    obj(e, ep, listOf("id", "titleVi", "bodyVi"), setOf("id", "titleVi", "bodyVi"))?.let {
                        it.field("id", ep, ::id); it.field("titleVi", ep, ::text); it.field("bodyVi", ep, ::text)
                    }
                }
            }
            o.field("comparison", p) { x, xp ->
                array(x, xp).each(xp) { e, ep ->
                    obj(e, ep, listOf("aspectVi", "values"), setOf("aspectVi", "values"))?.let {
                        it.field("aspectVi", ep, ::text)
                        it.field("values", ep) { vv, vp ->
                            if (vv !is JsonObject) {
                                sv(vp)
                            } else {
                                if (vv.size < 2) sv(vp)
                                vv.forEach { (key, value) ->
                                    if (!ID.containsMatchIn(key)) sv(vp) // propertyNames
                                    text(value, join(vp, key))
                                }
                            }
                        }
                    }
                }
            }
            o.field("examples", p) { x, xp ->
                array(x, xp).each(xp) { e, ep ->
                    obj(e, ep, listOf("id", "itemId", "japanese", "translationVi"), setOf("id", "itemId", "japanese", "furigana", "translationVi"))?.let {
                        it.field("id", ep, ::id); it.field("itemId", ep, ::id); it.field("japanese", ep, ::text)
                        it.field("furigana", ep, ::furigana); it.field("translationVi", ep, ::text)
                    }
                }
            }
            o.field("memoryTips", p) { x, xp ->
                array(x, xp).each(xp) { e, ep ->
                    obj(e, ep, listOf("id", "textVi"), setOf("id", "textVi"))?.let { it.field("id", ep, ::id); it.field("textVi", ep, ::text) }
                }
            }
            o.field("commonMistakes", p) { x, xp -> array(x, xp).each(xp) { e, ep -> mistake(e, ep) } }
            o.field("questions", p) { x, xp -> array(x, xp, minItems = 1).each(xp) { e, ep -> question(e, ep) } }
        }

        private fun approval(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("authoredBy", "approvedBy", "approvedAt", "approvedRevision", "aiAssisted", "acknowledgedWarnings"),
                setOf("authoredBy", "approvedBy", "approvedAt", "approvedRevision", "aiAssisted", "acknowledgedWarnings")) ?: return
            o.field("authoredBy", p) { x, xp -> pattern(x, xp, PERSON) }
            o.field("approvedBy", p) { x, xp -> pattern(x, xp, PERSON) }
            o.field("approvedAt", p, ::date)
            o.field("approvedRevision", p) { x, xp -> integer(x, xp, min = 1) }
            o.field("aiAssisted", p, ::bool)
            o.field("acknowledgedWarnings", p) { x, xp ->
                array(x, xp).each(xp) { e, ep ->
                    obj(e, ep, listOf("code", "target"), setOf("code", "target"))?.let {
                        it.field("code", ep) { c, cp -> pattern(c, cp, ACK_CODE) }
                        it.field("target", ep) { t, tp -> pattern(t, tp, ACK_TARGET) }
                    }
                }
            }
        }

        private fun item(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("id", "label", "source"), setOf("id", "label", "labelFurigana", "source", "grammar", "vocabulary", "kanji")) ?: return
            o.field("id", p, ::id)
            o.field("label", p, ::text)
            o.field("labelFurigana", p, ::furigana)
            o.field("source", p) { x, xp -> source(x, xp) }
            if (listOf("grammar", "vocabulary", "kanji").count { it in o } != 1) add(ContrastIssueCode.ITEM_TYPE_MISMATCH, p) // oneOf
            o.field("grammar", p) { x, xp ->
                obj(x, xp, listOf("meaningVi", "connection", "nuanceVi"), setOf("meaningVi", "connection", "nuanceVi"))?.let {
                    it.field("meaningVi", xp, ::text); it.field("connection", xp, ::text); it.field("nuanceVi", xp, ::text)
                }
            }
            o.field("vocabulary", p) { x, xp ->
                obj(x, xp, listOf("reading", "meaningVi", "usageVi"), setOf("reading", "meaningVi", "usageVi", "collocations"))?.let {
                    it.field("reading", xp) { r, rp -> pattern(r, rp, KANA) }
                    it.field("meaningVi", xp, ::text); it.field("usageVi", xp, ::text)
                    it.field("collocations", xp) { c, cp ->
                        array(c, cp).each(cp) { e, ep ->
                            obj(e, ep, listOf("japanese", "meaningVi"), setOf("japanese", "furigana", "meaningVi"))?.let { col ->
                                col.field("japanese", ep, ::text); col.field("furigana", ep, ::furigana); col.field("meaningVi", ep, ::text)
                            }
                        }
                    }
                }
            }
            o.field("kanji", p) { x, xp ->
                obj(x, xp, listOf("meaningVi", "onReadings", "kunReadings", "shapeNoteVi"), setOf("meaningVi", "hanViet", "onReadings", "kunReadings", "radical", "shapeNoteVi"))?.let {
                    it.field("meaningVi", xp, ::text); it.field("hanViet", xp, ::text); it.field("shapeNoteVi", xp, ::text)
                    it.field("onReadings", xp) { r, rp -> array(r, rp).each(rp) { e, ep -> pattern(e, ep, ON_READING) } }
                    it.field("kunReadings", xp) { r, rp -> array(r, rp).each(rp) { e, ep -> pattern(e, ep, KUN_READING) } }
                    it.field("radical", xp) { r, rp ->
                        obj(r, rp, listOf("character", "nameVi"), setOf("character", "nameVi"))?.let { rad ->
                            rad.field("character", rp) { c, cp ->
                                val s = stringOf(c, cp)
                                if (s != null && (s.isEmpty() || s.codePointCount(0, s.length) > 2)) sv(cp)
                            }
                            rad.field("nameVi", rp, ::text)
                        }
                    }
                }
            }
        }

        private fun source(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("sourceType", "sourceContentId", "sourcePackId", "sourceSchemaVersion", "sourceSnapshot", "sourceFingerprint"),
                setOf("sourceType", "sourceContentId", "sourcePackId", "sourceSchemaVersion", "sourceSnapshot", "sourceFingerprint")) ?: return
            o.field("sourceType", p) { x, xp -> enumOf(x, xp, CONTENT_TYPES) }
            o.field("sourceContentId", p) { x, xp ->
                val s = stringOf(x, xp)
                if (s != null && (s.isEmpty() || s.codePointCount(0, s.length) > 120)) sv(xp)
            }
            o.field("sourcePackId", p, ::id)
            o.field("sourceSchemaVersion", p) { x, xp ->
                if (!(x is JsonPrimitive && !x.isString && x.longOrNull == 1L)) add(ContrastIssueCode.SOURCE_SCHEMA_UNSUPPORTED, p) // const 1
            }
            o.field("sourceFingerprint", p) { x, xp -> pattern(x, xp, FINGERPRINT) }
            o.field("sourceSnapshot", p) { x, xp ->
                val snapshot = x as? JsonObject ?: return@field sv(xp)
                val type = (o["sourceType"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                val fields = SNAPSHOT_FIELDS[type] ?: return@field
                obj(snapshot, xp, fields.keys.toList(), fields.keys)
                fields.forEach { (name, isList) ->
                    snapshot.field(name, xp) { f, fp ->
                        if (isList) array(f, fp).each(fp) { e, ep -> stringOf(e, ep) } else stringOf(f, fp)
                    }
                }
            }
        }

        private fun mistake(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("id", "situationVi", "wrong", "correct", "explanationVi"),
                setOf("id", "situationVi", "wrong", "correct", "explanationVi", "expectedItemId", "confusedWithItemId")) ?: return
            o.field("id", p, ::id)
            o.field("situationVi", p, ::text)
            o.field("explanationVi", p, ::text)
            listOf("wrong", "correct").forEach { side ->
                o.field(side, p) { x, xp ->
                    obj(x, xp, listOf("japanese"), setOf("japanese", "furigana"))?.let { it.field("japanese", xp, ::text); it.field("furigana", xp, ::furigana) }
                }
            }
            o.field("expectedItemId", p, ::id)
            o.field("confusedWithItemId", p, ::id)
            if (("expectedItemId" in o) != ("confusedWithItemId" in o)) sv(p) // dependentRequired
        }

        private fun question(v: JsonElement, p: String) {
            val o = obj(v, p, listOf("id", "revision", "type", "difficulty", "targetItemId", "instructionVi", "options", "correctOptionId", "explanationVi"),
                setOf("id", "revision", "type", "difficulty", "targetItemId", "instructionVi", "prompt", "context", "options", "correctOptionId", "correction", "explanationVi")) ?: return
            o.field("id", p, ::id)
            o.field("revision", p) { x, xp -> integer(x, xp, min = 1) }
            o.field("type", p) { x, xp -> enumOf(x, xp, QUESTION_TYPES, ContrastIssueCode.UNSUPPORTED_QUESTION_TYPE) }
            o.field("difficulty", p) { x, xp -> integer(x, xp, min = 1, max = 3) }
            o.field("targetItemId", p, ::id)
            o.field("instructionVi", p, ::text)
            o.field("correctOptionId", p) { x, xp -> pattern(x, xp, OPTION_ID) }
            o.field("explanationVi", p, ::text)
            o.field("prompt", p) { x, xp ->
                obj(x, xp, listOf("japanese"), setOf("japanese", "furigana", "translationVi"))?.let {
                    it.field("japanese", xp, ::text); it.field("furigana", xp, ::furigana); it.field("translationVi", xp, ::text)
                }
            }
            o.field("context", p) { x, xp ->
                obj(x, xp, listOf("descriptionVi"), setOf("descriptionVi", "japanese", "furigana"))?.let {
                    it.field("descriptionVi", xp, ::text); it.field("japanese", xp, ::text); it.field("furigana", xp, ::furigana)
                }
            }
            o.field("correction", p) { x, xp ->
                obj(x, xp, listOf("text"), setOf("text", "furigana", "itemId"))?.let {
                    it.field("text", xp, ::text); it.field("furigana", xp, ::furigana); it.field("itemId", xp, ::id)
                }
            }
            o.field("options", p) { x, xp ->
                array(x, xp, minItems = 2, maxItems = 6).each(xp) { e, ep ->
                    obj(e, ep, listOf("id", "text"), setOf("id", "text", "furigana", "itemId", "feedbackVi"))?.let {
                        it.field("id", ep) { i, ip -> pattern(i, ip, OPTION_ID) }
                        it.field("text", ep, ::text); it.field("furigana", ep, ::furigana)
                        it.field("itemId", ep, ::id); it.field("feedbackVi", ep, ::text)
                    }
                }
            }
            // allOf: type-dependent requirements.
            when ((o["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                "FILL_IN_THE_BLANK" -> {
                    if ("prompt" !in o) required(p, "prompt")
                    val japanese = ((o["prompt"] as? JsonObject)?.get("japanese") as? JsonPrimitive)?.takeIf { it.isString }?.content
                    if (japanese != null && BLANK !in japanese) add(ContrastIssueCode.BLANK_MARKER_INVALID, "$p.prompt.japanese")
                }
                "ERROR_DETECTION" -> {
                    if ("prompt" !in o) required(p, "prompt")
                    if ("correction" !in o) required(p, "correction")
                    if (((o["options"] as? JsonArray)?.size ?: 3) < 3) minItems("$p.options")
                }
                "CONTEXT_SELECTION" -> if ("context" !in o) required(p, "context")
            }
        }
    }

    // ---- 2. Lenient raw model ----------------------------------------------------------------------

    private object Lenient {
        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        private fun JsonObject.text(key: String): String = str(key).orEmpty()
        private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.toInt()
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull
        private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
        private fun JsonObject.objects(key: String): List<JsonObject>? = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>()
        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()

        fun pack(root: JsonObject): RawContrastPack {
            val meta = root.obj("pack")?.let { p ->
                RawPackMeta(p.str("id"), p.str("title"), p.str("description"), p.str("jlptLevel"), p.int("contentVersion"), p.str("publishedAt"),
                    p.obj("license")?.let { RawLicense(it.str("text"), it.bool("licenseVerified")) })
            }
            return RawContrastPack(
                format = root.str("format"),
                schemaVersion = root.int("schemaVersion"),
                pack = meta,
                sourcePacks = root.objects("sourcePacks").orEmpty().map { RawSourcePack(it.text("id"), it.int("version") ?: 0) },
                groups = root.objects("groups").orEmpty().map(::group),
                retiredGroups = root.objects("retiredGroups").orEmpty().map { RawRetiredGroup(it.text("id"), it.int("retiredInVersion") ?: 0, it.text("reasonVi"), it.str("replacedBy")) },
            )
        }

        private fun group(g: JsonObject) = RawGroup(
            id = g.str("id"),
            revision = g.int("revision"),
            status = g.str("status"),
            contentType = g.str("contentType"),
            jlptLevel = g.str("jlptLevel"),
            title = g.str("title"),
            summaryVi = g.str("summaryVi"),
            updatedAt = g.str("updatedAt"),
            approval = g.obj("approval")?.let { a ->
                RawApproval(
                    authoredBy = a.text("authoredBy"),
                    approvedBy = a.text("approvedBy"),
                    approvedAt = a.text("approvedAt"),
                    // Absent: reported by the structure check; the revision rule then has nothing to compare.
                    approvedRevision = a.int("approvedRevision") ?: g.int("revision") ?: 0,
                    aiAssisted = a.bool("aiAssisted") ?: false,
                    acknowledgedWarnings = a.objects("acknowledgedWarnings").orEmpty().map { RawWarningAck(it.text("code"), it.text("target")) },
                )
            },
            items = g.objects("items")?.map(::item),
            theory = g.objects("theory")?.map { TheoryBlock(it.text("id"), it.text("titleVi"), it.text("bodyVi")) },
            comparison = g.objects("comparison")?.map { row ->
                ComparisonRow(row.text("aspectVi"), row.obj("values")?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }?.toMap().orEmpty())
            },
            examples = g.objects("examples")?.map { ContrastExample(it.text("id"), it.text("itemId"), it.text("japanese"), it.str("furigana"), it.text("translationVi")) },
            memoryTips = g.objects("memoryTips")?.map { MemoryTip(it.text("id"), it.text("textVi")) },
            commonMistakes = g.objects("commonMistakes")?.map { m ->
                RawMistake(m.text("id"), m.text("situationVi"), sentence(m.obj("wrong")), sentence(m.obj("correct")), m.text("explanationVi"),
                    m.str("expectedItemId"), m.str("confusedWithItemId"))
            },
            questions = g.objects("questions")?.map(::question),
        )

        private fun sentence(o: JsonObject?) = Sentence(o?.text("japanese").orEmpty(), o?.str("furigana"))

        private fun item(it: JsonObject): RawItem {
            val s = it.obj("source")
            val snapshot = s?.obj("sourceSnapshot")?.mapNotNull { (k, v) ->
                when {
                    v is JsonPrimitive && v.isString -> k to SnapshotValue.Text(v.content)
                    v is JsonArray -> k to SnapshotValue.Texts(v.mapNotNull { e -> (e as? JsonPrimitive)?.takeIf { p -> p.isString }?.content })
                    else -> null
                }
            }?.toMap().orEmpty()
            return RawItem(
                id = it.text("id"),
                label = it.text("label"),
                labelFurigana = it.str("labelFurigana"),
                source = RawSource(s?.text("sourceType").orEmpty(), s?.text("sourceContentId").orEmpty(), s?.text("sourcePackId").orEmpty(),
                    s?.int("sourceSchemaVersion") ?: 0, snapshot, s?.text("sourceFingerprint").orEmpty()),
                grammar = it.obj("grammar")?.let { d -> ItemDetails.Grammar(d.text("meaningVi"), d.text("connection"), d.text("nuanceVi")) },
                vocabulary = it.obj("vocabulary")?.let { d ->
                    ItemDetails.Vocabulary(d.text("reading"), d.text("meaningVi"), d.text("usageVi"),
                        d.objects("collocations").orEmpty().map { c -> Collocation(c.text("japanese"), c.str("furigana"), c.text("meaningVi")) })
                },
                kanji = it.obj("kanji")?.let { d ->
                    ItemDetails.Kanji(d.text("meaningVi"), d.str("hanViet"), d.strings("onReadings"), d.strings("kunReadings"),
                        d.obj("radical")?.let { r -> Radical(r.text("character"), r.text("nameVi")) }, d.text("shapeNoteVi"))
                },
            )
        }

        private fun question(q: JsonObject) = RawQuestion(
            id = q.text("id"),
            revision = q.int("revision") ?: 0,
            type = q.str("type"),
            difficulty = q.int("difficulty") ?: 0,
            targetItemId = q.text("targetItemId"),
            instructionVi = q.text("instructionVi"),
            prompt = q.obj("prompt")?.let { QuestionPrompt(it.text("japanese"), it.str("furigana"), it.str("translationVi")) },
            context = q.obj("context")?.let { QuestionContext(it.text("descriptionVi"), it.str("japanese"), it.str("furigana")) },
            options = q.objects("options").orEmpty().map { ContrastOption(it.text("id"), it.text("text"), it.str("furigana"), it.str("itemId"), it.str("feedbackVi")) },
            correctOptionId = q.str("correctOptionId"),
            correction = q.obj("correction")?.let { Correction(it.text("text"), it.str("furigana"), it.str("itemId")) },
            explanationVi = q.str("explanationVi"),
        )
    }

    // ---- Schema constants (contrast-pack.schema.json) ----

    const val FORMAT = "n3sensei-contrast-pack"
    private const val BLANK = "＿＿＿"
    private val ID = Regex("""^[a-z0-9][a-z0-9-]{1,79}$""")
    private val OPTION_ID = Regex("""^[a-z0-9]{1,8}$""")
    private val PERSON = Regex("""^[a-z0-9][a-z0-9-]{1,39}$""")
    private val DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val FINGERPRINT = Regex("""^sha256:[0-9a-f]{64}$""")
    private val ACK_CODE = Regex("""^[A-Z][A-Z_]+$""")
    private val ACK_TARGET = Regex("""^[a-z0-9][a-z0-9-]*(/[a-z0-9]{1,8})?$""")
    private val KANA = Regex("""^[぀-ヿー・]+$""")
    private val ON_READING = Regex("""^[゠-ヿ.\-ー]+$""")
    private val KUN_READING = Regex("""^[぀-ゟ.\-ー]+$""")
    private val JLPT = setOf("N5", "N4", "N3", "N2", "N1")
    private val CONTENT_TYPES = setOf("GRAMMAR", "VOCABULARY", "KANJI")
    private val STATUSES = setOf("DRAFT", "AI_GENERATED", "REVIEWING", "APPROVED", "PUBLISHED", "ARCHIVED")
    private val QUESTION_TYPES = setOf("MULTIPLE_CHOICE", "ERROR_DETECTION", "FILL_IN_THE_BLANK", "CONTEXT_SELECTION")

    /** Snapshot fields per source type (true = list of strings), `sourceSchemaVersion` 1. */
    private val SNAPSHOT_FIELDS: Map<String?, Map<String, Boolean>> = mapOf(
        "GRAMMAR" to mapOf("pattern" to false, "structure" to false, "meaningVi" to false),
        "VOCABULARY" to mapOf("expression" to false, "reading" to false, "meaningVi" to false),
        "KANJI" to mapOf("character" to false, "onReadings" to true, "kunReadings" to true, "meaningVi" to false),
    )
}
