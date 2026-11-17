package com.n3sensei.app.domain.contrast

import com.n3sensei.app.domain.contrast.validation.ContrastPackValidator
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
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastPack
import com.n3sensei.app.domain.contrast.validation.ValidationMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.File

/**
 * Test-side reader of the shared fixtures (tools/content/contrast/fixtures), mirroring
 * tools/content/contrast/fixture_cases.py: JSON → plain tree → JSON Patch subset → [RawContrastPack].
 *
 * The JSON → raw mapping here is lenient on purpose and is NOT the production parser (M4): it
 * only turns fixture JSON into the validator's input. A field the raw model requires that is
 * missing throws ([ParserLevel]): that is a SCHEMA_VIOLATION for the M4 parser, not an M1 rule.
 */
object ContrastFixtures {
    /** Unit tests run with the app module as working directory. */
    val repoRoot: File = File("..").canonicalFile
    val fixturesDir = File(repoRoot, "tools/content/contrast/fixtures")
    const val DEFAULT_SOURCES = "tools/content/contrast/fixtures/sources.json"
    const val SAMPLE = "docs/contrast/contrast-pack.sample.json"

    class ParserLevel(message: String) : IllegalStateException(message)

    fun readTree(repoPath: String): Any? = toTree(Json.parseToJsonElement(File(repoRoot, repoPath).readText()))

    /** The plain tree back to JSON text (for the production parser). */
    fun toJsonText(tree: Any?): String = toElement(tree).toString()

    private fun toElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k as String to toElement(v) })
        is List<*> -> JsonArray(value.map(::toElement))
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Long -> JsonPrimitive(value)
        is Double -> JsonPrimitive(value)
        else -> error("unexpected ${value::class}")
    }

    fun toTree(element: JsonElement): Any? = when (element) {
        JsonNull -> null
        is JsonObject -> LinkedHashMap<String, Any?>().apply { element.forEach { (k, v) -> put(k, toTree(v)) } }
        is JsonArray -> element.mapTo(ArrayList()) { toTree(it) }
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.booleanOrNull
            element.longOrNull != null -> element.longOrNull
            else -> element.doubleOrNull
        }
    }

    // ---- JSON Patch subset (add, remove, replace, move), same as fixture_cases.py ----

    private fun tokens(pointer: String): List<String> {
        require(pointer.startsWith("/")) { "bad pointer $pointer" }
        return pointer.substring(1).split("/").map { it.replace("~1", "/").replace("~0", "~") }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parent(doc: Any?, pointer: String): Pair<Any?, String> {
        val t = tokens(pointer)
        var node = doc
        t.dropLast(1).forEach { token -> node = if (node is MutableList<*>) (node as List<Any?>)[token.toInt()] else (node as Map<String, Any?>)[token] }
        return node to t.last()
    }

    @Suppress("UNCHECKED_CAST")
    private fun remove(doc: Any?, pointer: String): Any? {
        val (node, last) = parent(doc, pointer)
        return if (node is MutableList<*>) (node as MutableList<Any?>).removeAt(last.toInt()) else (node as MutableMap<String, Any?>).remove(last)
    }

    @Suppress("UNCHECKED_CAST")
    private fun add(doc: Any?, pointer: String, value: Any?) {
        val (node, last) = parent(doc, pointer)
        if (node is MutableList<*>) {
            val list = node as MutableList<Any?>
            if (last == "-") list.add(value) else list.add(last.toInt(), value)
        } else {
            (node as MutableMap<String, Any?>)[last] = value
        }
    }

    private fun deepCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> LinkedHashMap<String, Any?>().apply { value.forEach { (k, v) -> put(k as String, deepCopy(v)) } }
        is List<*> -> value.mapTo(ArrayList()) { deepCopy(it) }
        else -> value
    }

    @Suppress("UNCHECKED_CAST")
    fun applyPatch(doc: Any?, patch: List<Any?>): Any? {
        val out = deepCopy(doc)
        patch.forEach { raw ->
            val op = raw as Map<String, Any?>
            val path = op["path"] as String
            when (op["op"]) {
                "add" -> add(out, path, deepCopy(op["value"]))
                "remove" -> remove(out, path)
                "replace" -> {
                    val (node, last) = parent(out, path)
                    if (node is MutableList<*>) {
                        (node as MutableList<Any?>)[last.toInt()] = deepCopy(op["value"])
                    } else {
                        val map = node as MutableMap<String, Any?>
                        check(last in map) { "replace of missing $path" }
                        map[last] = deepCopy(op["value"])
                    }
                }
                "move" -> add(out, path, remove(out, op["from"] as String))
                else -> error("unsupported op ${op["op"]}")
            }
        }
        return out
    }

    // ---- Tree → raw model ----

    @Suppress("UNCHECKED_CAST")
    private fun Any?.obj(): Map<String, Any?>? = this as? Map<String, Any?>
    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String
    private fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Long)?.toInt()
    private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
    private fun Map<String, Any?>.objs(key: String): List<Map<String, Any?>>? = (this[key] as? List<*>)?.mapNotNull { it.obj() }
    private fun Map<String, Any?>.need(key: String): String = str(key) ?: throw ParserLevel("missing $key")
    private fun Map<String, Any?>.needInt(key: String): Int = int(key) ?: throw ParserLevel("missing $key")
    private fun Map<String, Any?>.strings(key: String): List<String> = (this[key] as? List<*>)?.map { it as String } ?: throw ParserLevel("missing $key")

    private fun snapshotValue(value: Any?): SnapshotValue = when (value) {
        is List<*> -> SnapshotValue.Texts(value.map { it as String })
        is String -> SnapshotValue.Text(value)
        else -> throw ParserLevel("bad snapshot value $value")
    }

    fun toRaw(tree: Any?): RawContrastPack {
        val root = tree.obj() ?: throw ParserLevel("root is not an object")
        val pack = root["pack"].obj()?.let { p ->
            RawPackMeta(p.str("id"), p.str("title"), p.str("description"), p.str("jlptLevel"), p.int("contentVersion"), p.str("publishedAt"),
                p["license"].obj()?.let { RawLicense(it.str("text"), it.bool("licenseVerified")) })
        }
        return RawContrastPack(
            format = root.str("format"),
            schemaVersion = root.int("schemaVersion"),
            pack = pack,
            sourcePacks = root.objs("sourcePacks").orEmpty().map { RawSourcePack(it.need("id"), it.needInt("version")) },
            groups = root.objs("groups").orEmpty().map(::group),
            retiredGroups = root.objs("retiredGroups").orEmpty().map { RawRetiredGroup(it.need("id"), it.needInt("retiredInVersion"), it.need("reasonVi"), it.str("replacedBy")) },
        )
    }

    private fun group(g: Map<String, Any?>) = RawGroup(
        id = g.str("id"),
        revision = g.int("revision"),
        status = g.str("status"),
        contentType = g.str("contentType"),
        jlptLevel = g.str("jlptLevel"),
        title = g.str("title"),
        summaryVi = g.str("summaryVi"),
        updatedAt = g.str("updatedAt"),
        approval = g["approval"].obj()?.let { a ->
            RawApproval(a.need("authoredBy"), a.need("approvedBy"), a.need("approvedAt"), a.needInt("approvedRevision"), a.bool("aiAssisted") ?: throw ParserLevel("aiAssisted"),
                a.objs("acknowledgedWarnings").orEmpty().map { RawWarningAck(it.need("code"), it.need("target")) })
        },
        items = g.objs("items")?.map(::item),
        theory = g.objs("theory")?.map { TheoryBlock(it.need("id"), it.need("titleVi"), it.need("bodyVi")) },
        comparison = g.objs("comparison")?.map { row ->
            ComparisonRow(row.need("aspectVi"), row["values"].obj()?.mapValues { it.value as String } ?: throw ParserLevel("values"))
        },
        examples = g.objs("examples")?.map { ContrastExample(it.need("id"), it.need("itemId"), it.need("japanese"), it.str("furigana"), it.need("translationVi")) },
        memoryTips = g.objs("memoryTips")?.map { MemoryTip(it.need("id"), it.need("textVi")) },
        commonMistakes = g.objs("commonMistakes")?.map { m ->
            RawMistake(m.need("id"), m.need("situationVi"), sentence(m["wrong"]), sentence(m["correct"]), m.need("explanationVi"),
                m.str("expectedItemId"), m.str("confusedWithItemId"))
        },
        questions = g.objs("questions")?.map(::question),
    )

    private fun sentence(value: Any?): Sentence = value.obj()?.let { Sentence(it.need("japanese"), it.str("furigana")) } ?: throw ParserLevel("sentence")

    private fun item(it: Map<String, Any?>): RawItem {
        val s = it["source"].obj() ?: throw ParserLevel("source")
        return RawItem(
            id = it.need("id"),
            label = it.need("label"),
            labelFurigana = it.str("labelFurigana"),
            source = RawSource(
                s.need("sourceType"), s.need("sourceContentId"), s.need("sourcePackId"), s.needInt("sourceSchemaVersion"),
                s["sourceSnapshot"].obj()?.mapValues { (_, v) -> snapshotValue(v) } ?: throw ParserLevel("sourceSnapshot"),
                s.need("sourceFingerprint"),
            ),
            grammar = it["grammar"].obj()?.let { d -> ItemDetails.Grammar(d.need("meaningVi"), d.need("connection"), d.need("nuanceVi")) },
            vocabulary = it["vocabulary"].obj()?.let { d ->
                ItemDetails.Vocabulary(d.need("reading"), d.need("meaningVi"), d.need("usageVi"),
                    d.objs("collocations").orEmpty().map { c -> Collocation(c.need("japanese"), c.str("furigana"), c.need("meaningVi")) })
            },
            kanji = it["kanji"].obj()?.let { d ->
                ItemDetails.Kanji(d.need("meaningVi"), d.str("hanViet"), d.strings("onReadings"), d.strings("kunReadings"),
                    d["radical"].obj()?.let { r -> Radical(r.need("character"), r.need("nameVi")) }, d.need("shapeNoteVi"))
            },
        )
    }

    private fun question(q: Map<String, Any?>) = RawQuestion(
        id = q.need("id"),
        revision = q.needInt("revision"),
        type = q.str("type"),
        difficulty = q.needInt("difficulty"),
        targetItemId = q.need("targetItemId"),
        instructionVi = q.need("instructionVi"),
        prompt = q["prompt"].obj()?.let { QuestionPrompt(it.need("japanese"), it.str("furigana"), it.str("translationVi")) },
        context = q["context"].obj()?.let { QuestionContext(it.need("descriptionVi"), it.str("japanese"), it.str("furigana")) },
        options = q.objs("options").orEmpty().map { ContrastOption(it.need("id"), it.need("text"), it.str("furigana"), it.str("itemId"), it.str("feedbackVi")) },
        correctOptionId = q.str("correctOptionId"),
        correction = q["correction"].obj()?.let { Correction(it.need("text"), it.str("furigana"), it.str("itemId")) },
        explanationVi = q.str("explanationVi"),
    )

    // ---- Sources and installed pack ----

    fun sources(repoPath: String): SourceLookup {
        val records = readTree(repoPath).obj()!!.objs("records")!!.map { r ->
            SourceRecord(
                type = ContrastContentType.fromCode(r.need("type"))!!,
                id = r.need("id"),
                fields = r["fields"].obj()!!.mapValues { (_, v) -> snapshotValue(v) },
            )
        }
        return object : SourceLookup {
            override fun byId(type: ContrastContentType, id: String) = records.firstOrNull { it.type == type && it.id == id }
            override fun bySnapshot(type: ContrastContentType, snapshot: Map<String, SnapshotValue>): List<SourceRecord> {
                val wanted = ContrastFingerprint.FIELDS.getValue(type).associateWith { snapshot[it] ?: SnapshotValue.Text("") }
                return records.filter { it.type == type && ContrastFingerprint.snapshotOf(it) == wanted }
            }
        }
    }

    fun installed(tree: Any?): InstalledContrastPack {
        val root = tree.obj()!!
        val pack = root["pack"].obj()!!
        return InstalledContrastPack(pack.need("id"), pack.needInt("contentVersion"), pack["license"].obj()?.bool("licenseVerified") ?: false,
            root.objs("groups").orEmpty().mapNotNull { it.str("id") }.toSet())
    }

    /** The content of a (previous / installed) pack as domain models, as the importer will read it back from Room. */
    fun contentOf(tree: Any?): List<com.n3sensei.app.domain.contrast.validation.ValidatedContrastGroup> =
        checkNotNull(ContrastPackValidator(ValidationMode.IMPORT).validate(toRaw(tree)).pack) { "previous pack does not validate" }.groups

    /** The real sample pack, validated, for use-case tests. */
    fun samplePack(): ValidatedContrastPack {
        val result = ContrastPackValidator(ValidationMode.IMPORT).validate(toRaw(readTree(SAMPLE)), sources(DEFAULT_SOURCES))
        return checkNotNull(result.pack) { "sample does not validate: ${result.issues}" }
    }
}
