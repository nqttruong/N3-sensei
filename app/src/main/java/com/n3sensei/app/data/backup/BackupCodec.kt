package com.n3sensei.app.data.backup

import com.n3sensei.app.domain.backup.BackupData
import com.n3sensei.app.domain.backup.BackupIssue
import com.n3sensei.app.domain.backup.BackupIssueCode
import com.n3sensei.app.domain.backup.MarkBackup
import com.n3sensei.app.domain.backup.ProgressBackup
import com.n3sensei.app.domain.backup.ReminderBackup
import com.n3sensei.app.domain.backup.ReviewRecordBackup
import com.n3sensei.app.domain.backup.SessionBackup
import com.n3sensei.app.domain.backup.SettingsBackup
import com.n3sensei.app.domain.importing.IssueSeverity
import com.n3sensei.app.domain.model.ItemRef
import com.n3sensei.app.domain.model.ItemType
import com.n3sensei.app.domain.model.ProgressState
import com.n3sensei.app.domain.model.ReviewRating
import com.n3sensei.app.domain.model.ThemeMode
import com.n3sensei.app.domain.reminder.LockScreenPrivacy
import com.n3sensei.app.domain.reminder.ReminderContent
import com.n3sensei.app.domain.reminder.ReminderFrequency
import com.n3sensei.app.domain.srs.DailyLimits
import com.n3sensei.app.domain.srs.ReviewLadder
import com.n3sensei.app.domain.srs.ReviewSettings
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream

/** Backup file ⇄ [BackupData], with every check needed before anything is written. */
object BackupCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Longest accepted id; content and request ids are far shorter. */
    const val MAX_ID_LENGTH = 200

    sealed interface Decoded {
        data class Ok(
            val createdAt: Long,
            val formatVersion: Int,
            val appVersion: String?,
            val data: BackupData,
            val skippedEntries: Int,
            val issues: List<BackupIssue>,
        ) : Decoded

        data class Rejected(val issues: List<BackupIssue>) : Decoded
    }

    /** Whole backup as a string; for small data and tests. Files are written with [Writer]. */
    fun encode(data: BackupData, createdAt: Long, appVersion: String?): String {
        val out = StringBuilder()
        Writer(out, createdAt, appVersion).apply {
            begin(data.settings, data.progress, data.marks, data.sessions)
            data.reviewRecords.forEach { record(it) }
            end()
        }
        return out.toString()
    }

    /**
     * Writes a backup piece by piece so the review history (the only part that grows without
     * bound) is never held in memory as a whole: [begin], then [record] for each answer, then [end].
     */
    class Writer(private val out: Appendable, private val createdAt: Long, private val appVersion: String?) {
        private var records = 0

        fun begin(settings: SettingsBackup?, progress: List<ProgressBackup>, marks: List<MarkBackup>, sessions: List<SessionBackup>) {
            out.append("{\"format\":").append(json.encodeToString(String.serializer(), BACKUP_FORMAT))
            out.append(",\"formatVersion\":").append(BACKUP_FORMAT_VERSION.toString())
            out.append(",\"createdAt\":").append(createdAt.toString())
            out.append(",\"appVersion\":").append(json.encodeToString(String.serializer().nullable, appVersion))
            out.append(",\"includesContent\":false")
            out.append(",\"settings\":").append(json.encodeToString(SettingsDto.serializer().nullable, settings?.toDto()))
            array("progress", progress) { json.encodeToString(ProgressDto.serializer(), it.toDto()) }
            array("marks", marks) { json.encodeToString(MarkDto.serializer(), MarkDto(it.ref.type.name, it.ref.id, it.markedAt)) }
            array("reviewSessions", sessions) {
                json.encodeToString(SessionDto.serializer(), SessionDto(it.id, it.startedAt, it.lastActivityAt, it.planned, it.answered, it.finished))
            }
            out.append(",\"reviewRecords\":[")
        }

        fun record(record: ReviewRecordBackup) {
            if (records++ > 0) out.append(',')
            out.append(json.encodeToString(ReviewRecordDto.serializer(), record.toDto()))
        }

        fun end() {
            out.append("]}")
        }

        private fun <T> array(name: String, items: List<T>, encode: (T) -> String) {
            out.append(",\"").append(name).append("\":[")
            items.forEachIndexed { i, item ->
                if (i > 0) out.append(',')
                out.append(encode(item))
            }
            out.append(']')
        }
    }

    fun decode(text: String): Decoded = decode(text.byteInputStream())

    /**
     * Reads a backup from [input] as a stream: each entry is checked and converted as soon as
     * it is read, so memory grows with the number of entries, not with the JSON text.
     * I/O errors from [input] are not caught here.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun decode(input: InputStream): Decoded {
        val stream = BufferedInputStream(NestingLimitedInputStream(input, MAX_NESTING))
        val reader = Reader()
        val raw = try {
            // Reading ahead for the blank check may already hit the nesting limit.
            if (isBlank(stream)) return reject(BackupIssueCode.EMPTY_FILE)
            json.decodeFromStream(RawBackupDeserializer(reader), stream)
        } catch (e: SerializationException) {
            return reject(BackupIssueCode.INVALID_JSON, detail = e.message?.take(MAX_DETAIL))
        } catch (e: IllegalArgumentException) {
            return reject(BackupIssueCode.INVALID_JSON, detail = e.message?.take(MAX_DETAIL))
        }
        if (raw.format != BACKUP_FORMAT) return reject(BackupIssueCode.NOT_A_BACKUP, "format", raw.format)
        val version = raw.formatVersion ?: return reject(BackupIssueCode.MISSING_FIELD, "formatVersion")
        if (version > SUPPORTED_BACKUP_VERSIONS.last) return reject(BackupIssueCode.NEWER_VERSION, "formatVersion", version.toString())
        if (version !in SUPPORTED_BACKUP_VERSIONS) return reject(BackupIssueCode.UNSUPPORTED_VERSION, "formatVersion", version.toString())
        val createdAt = raw.createdAt?.takeIf { it >= 0 } ?: return reject(BackupIssueCode.MISSING_FIELD, "createdAt")
        val missing = listOf(
            "progress" to raw.progress, "reviewRecords" to raw.reviewRecords,
            "marks" to raw.marks, "reviewSessions" to raw.sessions,
        ).firstOrNull { it.second == null }
        if (missing != null) return reject(BackupIssueCode.MISSING_FIELD, missing.first)
        val settings = raw.settings?.takeIf { it !is JsonNull }?.let { reader.settings(it) }

        return Decoded.Ok(
            createdAt = createdAt,
            formatVersion = version,
            appVersion = raw.appVersion,
            data = BackupData(raw.progress!!, raw.reviewRecords!!, raw.marks!!, raw.sessions!!, settings),
            skippedEntries = reader.skipped,
            issues = reader.issues.sortedBy { SECTION_ORDER.indexOf(it.location.substringBefore('[')) },
        )
    }

    /** Issues are reported in this order whatever the order of the sections in the file. */
    private val SECTION_ORDER = listOf("progress", "reviewRecords", "marks", "reviewSessions", "settings")

    private fun isBlank(stream: BufferedInputStream): Boolean {
        stream.mark(MAX_LEADING_BLANK)
        var read = 0
        while (read < MAX_LEADING_BLANK) {
            val b = stream.read()
            if (b < 0) return true
            read++
            if (b.toChar() !in " \t\r\n\uFEFF") break
        }
        stream.reset()
        return false
    }

    private class RawBackup(
        val format: String?,
        val formatVersion: Int?,
        val createdAt: Long?,
        val appVersion: String?,
        val settings: JsonElement?,
        val progress: List<ProgressBackup>?,
        val reviewRecords: List<ReviewRecordBackup>?,
        val marks: List<MarkBackup>?,
        val sessions: List<SessionBackup>?,
    )

    /** Top-level object; the four data sections go through [Section] entry by entry. */
    // Nullable descriptors are still marked experimental in kotlinx-serialization 1.7.
    @OptIn(ExperimentalSerializationApi::class)
    private class RawBackupDeserializer(private val reader: Reader) : DeserializationStrategy<RawBackup> {
        private val progress = Section("progress", ProgressDto.serializer(), { it.ref }) { d, r -> readProgress(d, r.refs) }
        private val records = Section("reviewRecords", ReviewRecordDto.serializer(), { it.requestId ?: it.key() }) { d, r -> readRecord(d, r.refs) }
        private val marks = Section("marks", MarkDto.serializer(), { it.ref }) { d, r -> readMark(d, r.refs) }
        private val sessions = Section("reviewSessions", SessionDto.serializer(), { it.id }) { d, _ -> readSession(d) }

        override val descriptor = buildClassSerialDescriptor("n3sensei.Backup") {
            element("format", String.serializer().nullable.descriptor, isOptional = true)
            element("formatVersion", Int.serializer().nullable.descriptor, isOptional = true)
            element("createdAt", Long.serializer().nullable.descriptor, isOptional = true)
            element("appVersion", String.serializer().nullable.descriptor, isOptional = true)
            element("settings", JsonElement.serializer().descriptor, isOptional = true)
            element("progress", progress.descriptor, isOptional = true)
            element("reviewRecords", records.descriptor, isOptional = true)
            element("marks", marks.descriptor, isOptional = true)
            element("reviewSessions", sessions.descriptor, isOptional = true)
        }

        override fun deserialize(decoder: Decoder): RawBackup {
            var format: String? = null
            var version: Int? = null
            var createdAt: Long? = null
            var appVersion: String? = null
            var settings: JsonElement? = null
            var p: List<ProgressBackup>? = null
            var r: List<ReviewRecordBackup>? = null
            var m: List<MarkBackup>? = null
            var s: List<SessionBackup>? = null
            val c = decoder.beginStructure(descriptor)
            while (true) {
                when (val i = c.decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> format = c.decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    1 -> version = c.decodeNullableSerializableElement(descriptor, i, Int.serializer().nullable)
                    2 -> createdAt = c.decodeNullableSerializableElement(descriptor, i, Long.serializer().nullable)
                    3 -> appVersion = c.decodeNullableSerializableElement(descriptor, i, String.serializer().nullable)
                    4 -> settings = c.decodeSerializableElement(descriptor, i, JsonElement.serializer())
                    5 -> p = c.decodeNullableSerializableElement(descriptor, i, progress.with(reader).nullable)
                    6 -> r = c.decodeNullableSerializableElement(descriptor, i, records.with(reader).nullable)
                    7 -> m = c.decodeNullableSerializableElement(descriptor, i, marks.with(reader).nullable)
                    8 -> s = c.decodeNullableSerializableElement(descriptor, i, sessions.with(reader).nullable)
                    else -> throw SerializationException("Unexpected index $i")
                }
            }
            c.endStructure(descriptor)
            return RawBackup(format, version, createdAt, appVersion, settings, p, r, m, s)
        }
    }

    /** One data section: a JSON array read one entry at a time. */
    private class Section<D, T>(
        val name: String,
        val dto: KSerializer<D>,
        val identity: (T) -> Any,
        val read: (D, Reader) -> T,
    ) {
        val descriptor = ListSerializer(JsonElement.serializer()).descriptor

        fun with(reader: Reader): KSerializer<List<T>> = object : KSerializer<List<T>> {
            override val descriptor = this@Section.descriptor

            override fun serialize(encoder: Encoder, value: List<T>) = throw UnsupportedOperationException("read only")

            override fun deserialize(decoder: Decoder): List<T> {
                val seen = HashSet<Any>()
                val out = ArrayList<T>()
                val c = decoder.beginStructure(descriptor)
                var index = 0
                while (c.decodeElementIndex(descriptor) != CompositeDecoder.DECODE_DONE) {
                    val element = c.decodeSerializableElement(descriptor, index, JsonElement.serializer())
                    reader.entry("$name[$index]", element, dto, read)?.let { value ->
                        if (seen.add(identity(value))) out += value else reader.error(BackupIssueCode.DUPLICATE_ID, "$name[$index]", identity(value).toString())
                    }
                    index++
                }
                c.endStructure(descriptor)
                out.trimToSize()
                return out
            }
        }
    }

    private fun reject(code: BackupIssueCode, location: String = "", detail: String? = null) =
        Decoded.Rejected(listOf(BackupIssue(IssueSeverity.FATAL, code, location, detail)))

    private fun ReviewRecordBackup.key() = "${ref.type}/${ref.id}@$reviewedAt/$rating"

    /** Thrown inside an entry check; becomes an ERROR issue for that entry. */
    private class Invalid(val code: BackupIssueCode, val field: String) : Exception()

    private class Reader {
        val issues = mutableListOf<BackupIssue>()
        var skipped = 0

        /** One shared ItemRef per item while reading this file. */
        val refs = HashMap<ItemRef, ItemRef>()

        /** Checks and converts one entry; null (and an ERROR issue) when it is invalid. */
        fun <D, T> entry(location: String, element: JsonElement, dto: KSerializer<D>, read: (D, Reader) -> T): T? = try {
            read(json.decodeFromJsonElement(dto, element), this)
        } catch (e: Invalid) {
            error(e.code, location, e.field)
            null
        } catch (e: SerializationException) {
            error(BackupIssueCode.INVALID_VALUE, location, e.message?.take(MAX_DETAIL))
            null
        } catch (e: IllegalArgumentException) {
            error(BackupIssueCode.INVALID_VALUE, location, e.message?.take(MAX_DETAIL))
            null
        }

        fun settings(element: JsonElement): SettingsBackup? = try {
            readSettings(json.decodeFromJsonElement(SettingsDto.serializer(), element))
        } catch (e: Invalid) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.field)
            null
        } catch (e: SerializationException) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.message?.take(MAX_DETAIL))
            null
        } catch (e: IllegalArgumentException) {
            issues += BackupIssue(IssueSeverity.ERROR, BackupIssueCode.INVALID_SETTINGS, "settings", e.message?.take(MAX_DETAIL))
            null
        }

        fun error(code: BackupIssueCode, location: String, detail: String?) {
            skipped++
            issues += BackupIssue(IssueSeverity.ERROR, code, location, detail)
        }
    }

    // --- field checks -------------------------------------------------------------------------

    private fun <T> required(value: T?, field: String): T = value ?: throw Invalid(BackupIssueCode.MISSING_FIELD, field)

    private fun check(ok: Boolean, field: String) {
        if (!ok) throw Invalid(BackupIssueCode.INVALID_VALUE, field)
    }

    private inline fun <reified E : Enum<E>> enumField(value: String?, field: String): E =
        enumValues<E>().firstOrNull { it.name == required(value, field) } ?: throw Invalid(BackupIssueCode.INVALID_VALUE, field)

    private fun ref(type: String?, id: String?, refs: MutableMap<ItemRef, ItemRef>): ItemRef {
        val itemType = enumField<ItemType>(type, "itemType")
        val itemId = required(id, "itemId")
        check(itemId.isNotBlank() && itemId.length <= MAX_ID_LENGTH, "itemId")
        // A history repeats the same few thousand items: share one ItemRef per item.
        return ItemRef(itemType, itemId).let { refs.getOrPut(it) { it } }
    }

    private fun time(value: Long?, field: String): Long = required(value, field).also { check(it >= 0, field) }

    private fun nonNegative(value: Long?, field: String): Long = required(value, field).also { check(it >= 0, field) }

    private fun nonNegative(value: Int?, field: String): Int = required(value, field).also { check(it >= 0, field) }

    private fun readProgress(d: ProgressDto, refs: MutableMap<ItemRef, ItemRef>) = ProgressBackup(
        ref = ref(d.itemType, d.itemId, refs),
        state = enumField(d.state, "state"),
        stepIndex = nonNegative(d.stepIndex, "stepIndex").also { check(it < ReviewLadder.MAX_STEPS, "stepIndex") },
        intervalMinutes = nonNegative(d.intervalMinutes, "intervalMinutes"),
        dueAt = d.dueAt?.also { check(it >= 0, "dueAt") },
        lastReviewedAt = d.lastReviewedAt?.also { check(it >= 0, "lastReviewedAt") },
        reviewCount = nonNegative(d.reviewCount, "reviewCount"),
        lapseCount = nonNegative(d.lapseCount, "lapseCount"),
        createdAt = time(d.createdAt, "createdAt"),
        updatedAt = time(d.updatedAt, "updatedAt"),
    )

    private fun readRecord(d: ReviewRecordDto, refs: MutableMap<ItemRef, ItemRef>) = ReviewRecordBackup(
        ref = ref(d.itemType, d.itemId, refs),
        rating = enumField(d.rating, "rating"),
        reviewedAt = time(d.reviewedAt, "reviewedAt"),
        intervalBeforeMinutes = nonNegative(d.intervalBeforeMinutes, "intervalBeforeMinutes"),
        intervalAfterMinutes = nonNegative(d.intervalAfterMinutes, "intervalAfterMinutes"),
        dueAtAfter = time(d.dueAtAfter, "dueAtAfter"),
        requestId = d.requestId?.also { check(it.isNotBlank() && it.length <= MAX_ID_LENGTH, "requestId") },
        wasNew = required(d.wasNew, "wasNew"),
    )

    private fun readMark(d: MarkDto, refs: MutableMap<ItemRef, ItemRef>) = MarkBackup(ref(d.itemType, d.itemId, refs), time(d.markedAt, "markedAt"))

    private fun readSession(d: SessionDto): SessionBackup {
        val id = required(d.id, "id").also { check(it.isNotBlank() && it.length <= MAX_ID_LENGTH, "id") }
        val planned = nonNegative(d.planned, "planned")
        val answered = nonNegative(d.answered, "answered").also { check(it <= planned, "answered") }
        return SessionBackup(id, time(d.startedAt, "startedAt"), time(d.lastActivityAt, "lastActivityAt"), planned, answered, required(d.finished, "finished"))
    }

    private fun readSettings(d: SettingsDto): SettingsBackup {
        val review = required(d.review, "review")
        val ladder = ReviewLadder.decodeOrNull(required(review.intervals, "review.intervals"))
            ?: throw Invalid(BackupIssueCode.INVALID_VALUE, "review.intervals")
        val newPerDay = required(review.newPerDay, "review.newPerDay").also { check(it in 0..DailyLimits.MAX_NEW, "review.newPerDay") }
        val reviewsPerDay = required(review.reviewsPerDay, "review.reviewsPerDay").also { check(it in 1..DailyLimits.MAX_REVIEWS, "review.reviewsPerDay") }
        val r = required(d.reminder, "reminder")
        return SettingsBackup(
            themeMode = enumField(d.themeMode, "themeMode"),
            showFurigana = required(d.showFurigana, "showFurigana"),
            review = ReviewSettings(ladder, DailyLimits(newPerDay, reviewsPerDay)),
            reminder = ReminderBackup(
                startHour = required(r.startHour, "reminder.startHour").also { check(it in 0..23, "reminder.startHour") },
                endHour = required(r.endHour, "reminder.endHour").also { check(it in 0..23, "reminder.endHour") },
                frequency = enumField<ReminderFrequency>(r.frequency, "reminder.frequency"),
                showReading = required(r.showReading, "reminder.showReading"),
                showMeaning = required(r.showMeaning, "reminder.showMeaning"),
                lockScreen = enumField<LockScreenPrivacy>(r.lockScreen, "reminder.lockScreen"),
                // Optional: absent in older backups. Unknown kinds are ignored, not an error.
                maxPerDay = r.maxPerDay?.also { check(it in 0..100, "reminder.maxPerDay") },
                content = r.content?.mapNotNull { name -> ReminderContent.entries.firstOrNull { it.name == name } }?.toSet(),
                reviewOnly = r.reviewOnly,
            ),
        )
    }

    // --- to wire ------------------------------------------------------------------------------

    private fun ProgressBackup.toDto() = ProgressDto(
        ref.type.name, ref.id, state.name, stepIndex, intervalMinutes, dueAt, lastReviewedAt,
        reviewCount, lapseCount, createdAt, updatedAt,
    )

    private fun ReviewRecordBackup.toDto() = ReviewRecordDto(
        ref.type.name, ref.id, rating.name, reviewedAt, intervalBeforeMinutes, intervalAfterMinutes, dueAtAfter, requestId, wasNew,
    )

    private fun SettingsBackup.toDto() = SettingsDto(
        themeMode = themeMode.name,
        showFurigana = showFurigana,
        review = ReviewSettingsDto(review.ladder.encode(), review.limits.newPerDay, review.limits.reviewsPerDay),
        reminder = ReminderDto(
            reminder.startHour, reminder.endHour, reminder.frequency.name,
            reminder.showReading, reminder.showMeaning, reminder.lockScreen.name,
            reminder.maxPerDay, reminder.content?.sortedBy { it.ordinal }?.map { it.name }, reminder.reviewOnly,
        ),
    )

    private const val MAX_DETAIL = 300
    private const val MAX_LEADING_BLANK = 4096

    /** A real backup nests 4 levels deep; anything far deeper is hostile or broken. */
    const val MAX_NESTING = 32
}

/**
 * Rejects JSON nested deeper than [maxDepth] while it is read. Entries are parsed into JSON
 * trees recursively, so a file with thousands of nested brackets would otherwise overflow the
 * stack (a StackOverflowError, which the import code cannot catch) instead of being reported.
 * Brackets inside strings are ignored; UTF-8 multi-byte sequences never contain ASCII bytes.
 */
internal class NestingLimitedInputStream(input: InputStream, private val maxDepth: Int) : FilterInputStream(input) {
    private var depth = 0
    private var inString = false
    private var escaped = false

    override fun read(): Int = super.read().also { if (it >= 0) track(it) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { n ->
        for (i in off until off + maxOf(n, 0)) track(b[i].toInt() and 0xFF)
    }

    // mark/reset are not supported, so every byte is seen exactly once.
    override fun markSupported(): Boolean = false

    private fun track(byte: Int) {
        when {
            escaped -> escaped = false
            inString && byte == '\\'.code -> escaped = true
            byte == '"'.code -> inString = !inString
            inString -> Unit
            byte == '['.code || byte == '{'.code -> if (++depth > maxDepth) throw SerializationException("JSON nested deeper than $maxDepth levels")
            byte == ']'.code || byte == '}'.code -> depth--
        }
    }
}
