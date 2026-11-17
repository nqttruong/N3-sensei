package com.n3sensei.app.domain.contrast.validation

import com.n3sensei.app.domain.contrast.CommonMistake
import com.n3sensei.app.domain.contrast.Confusion
import com.n3sensei.app.domain.contrast.ContrastContentStatus
import com.n3sensei.app.domain.contrast.ContrastContentType
import com.n3sensei.app.domain.contrast.ContrastDetail
import com.n3sensei.app.domain.contrast.ContrastFingerprint
import com.n3sensei.app.domain.contrast.ContrastGroup
import com.n3sensei.app.domain.contrast.ContrastItem
import com.n3sensei.app.domain.contrast.ContrastQuestion
import com.n3sensei.app.domain.contrast.ContrastQuestionType
import com.n3sensei.app.domain.contrast.ContrastSourceVerifier
import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.RetiredGroup
import com.n3sensei.app.domain.contrast.SourceLookup
import com.n3sensei.app.domain.contrast.SourceRefStatus
import com.n3sensei.app.domain.contrast.SourceReference
import com.n3sensei.app.domain.contrast.validation.ContrastIssueCode as C
import com.n3sensei.app.domain.furigana.Furigana

/** A pack that passed validation (no ERROR), as domain models for the importer (M4). */
data class ValidatedContrastPack(
    val id: String,
    val title: String,
    val jlptLevel: String,
    val contentVersion: Int,
    val publishedAt: String,
    val licenseText: String,
    val licenseVerified: Boolean,
    val groups: List<ValidatedContrastGroup>,
    val retiredGroups: List<RetiredGroup>,
)

data class ValidatedContrastGroup(val detail: ContrastDetail, val questions: List<ContrastQuestion>)

/**
 * Semantic validation of a Contrast pack (CONTENT_SCHEMA §8), pure Kotlin. Same rules, codes and
 * paths as `tools/content/contrast/validator.py`; both are checked against the shared fixtures in
 * `tools/content/contrast/fixtures/`. The parser (M4) reports INVALID_JSON and the
 * SCHEMA_VIOLATIONs this model cannot express (unknown fields, wrong JSON types, id patterns…).
 */
class ContrastPackValidator(private val mode: ValidationMode = ValidationMode.IMPORT) {

    /**
     * @param installed the pack on the device (version and group ids).
     * @param previousGroups the installed content (import) or the previous release, for
     *   ITEMS_CHANGED_SAME_GROUP_ID and REVISION_NOT_INCREMENTED.
     */
    fun validate(
        raw: RawContrastPack,
        sources: SourceLookup? = null,
        installed: InstalledContrastPack? = null,
        previousGroups: List<ValidatedContrastGroup>? = null,
    ): ContrastValidationResult = Run(raw, sources, installed, previousGroups).execute()

    private inner class Run(
        private val raw: RawContrastPack,
        private val sources: SourceLookup?,
        private val installed: InstalledContrastPack?,
        private val previousGroups: List<ValidatedContrastGroup>?,
    ) {
        private val issues = mutableListOf<ContrastIssue>()
        private val seen = mutableSetOf<Pair<C, String>>()

        private fun severityOf(code: C, default: ContrastSeverity): ContrastSeverity =
            if (code in C.MODE_DEPENDENT) (if (mode == ValidationMode.BUILD) ContrastSeverity.ERROR else ContrastSeverity.WARNING) else default

        private fun add(code: C, path: String, severity: ContrastSeverity = ContrastSeverity.ERROR, target: String? = null, detail: String? = null) {
            if (seen.add(code to path)) issues += ContrastIssue(code, severityOf(code, severity), path, target, detail = detail)
        }

        private fun warn(code: C, path: String, target: String) = add(code, path, ContrastSeverity.WARNING, target)

        fun execute(): ContrastValidationResult {
            if (raw.format != FORMAT) add(C.UNSUPPORTED_FORMAT, "format")
            if (raw.schemaVersion !in SUPPORTED_SCHEMA_VERSIONS) add(C.UNSUPPORTED_FORMAT, "schemaVersion")
            if (issues.isNotEmpty()) return ContrastValidationResult(issues.toList(), null)

            checkPack()
            val sourcePackIds = raw.sourcePacks.map { it.id }.toSet()
            val groupIds = mutableSetOf<String>()
            val itemIds = mutableSetOf<String>()
            val otherIds = mutableSetOf<String>()
            raw.groups.forEachIndexed { i, group -> checkGroup(i, group, sourcePackIds, groupIds, itemIds, otherIds) }
            raw.retiredGroups.forEachIndexed { k, retired ->
                if (!groupIds.add(retired.id)) add(C.DUPLICATE_GROUP_ID, "retiredGroups[$k].id")
            }
            if (installed != null) {
                val present = raw.groups.mapNotNull { it.id }.toSet() + raw.retiredGroups.map { it.id }
                (installed.groupIds - present).sorted().forEach { add(C.GROUP_DISAPPEARED, "groups[id=$it]") }
            }
            previousGroups?.let(::checkRevisions)
            val acknowledged = acknowledgedIssues()
            return ContrastValidationResult(acknowledged, if (acknowledged.any { it.severity == ContrastSeverity.ERROR }) null else buildPack())
        }

        private fun checkPack() {
            val pack = raw.pack ?: return add(C.MISSING_METADATA, "pack")
            if (pack.id.isNullOrBlank()) add(C.MISSING_METADATA, "pack.id")
            if (pack.title.isNullOrBlank()) add(C.MISSING_METADATA, "pack.title")
            if (pack.jlptLevel.isNullOrBlank()) add(C.MISSING_METADATA, "pack.jlptLevel")
            if (pack.publishedAt.isNullOrBlank()) add(C.MISSING_METADATA, "pack.publishedAt")
            val license = pack.license
            if (license == null) {
                add(C.MISSING_METADATA, "pack.license")
            } else {
                if (license.text.isNullOrBlank()) add(C.MISSING_METADATA, "pack.license.text")
                if (license.licenseVerified == null) add(C.MISSING_METADATA, "pack.license.licenseVerified")
                if (license.licenseVerified == false) add(C.LICENSE_UNVERIFIED, "pack.license.licenseVerified", ContrastSeverity.INFO)
            }
            val version = pack.contentVersion
            when {
                version == null -> add(C.MISSING_METADATA, "pack.contentVersion")
                version < 1 -> add(C.INVALID_CONTENT_VERSION, "pack.contentVersion")
                installed != null && installed.id == pack.id && installed.contentVersion >= version ->
                    add(C.OLDER_OR_SAME_VERSION, "pack.contentVersion", ContrastSeverity.INFO)
            }
        }

        private fun checkGroup(
            i: Int,
            group: RawGroup,
            sourcePackIds: Set<String>,
            groupIds: MutableSet<String>,
            itemIds: MutableSet<String>,
            otherIds: MutableSet<String>,
        ) {
            val g = "groups[$i]"
            when {
                group.id.isNullOrBlank() -> add(C.MISSING_METADATA, "$g.id")
                !groupIds.add(group.id) -> add(C.DUPLICATE_GROUP_ID, "$g.id")
            }
            if (group.revision == null) add(C.MISSING_METADATA, "$g.revision")
            listOf("jlptLevel" to group.jlptLevel, "title" to group.title, "summaryVi" to group.summaryVi, "updatedAt" to group.updatedAt)
                .forEach { (name, value) -> if (value.isNullOrBlank()) add(C.MISSING_METADATA, "$g.$name") }
            val status = ContrastContentStatus.fromCode(group.status)
            if (group.status == null) {
                add(C.MISSING_METADATA, "$g.status")
            } else {
                if (status == null) add(C.SCHEMA_VIOLATION, "$g.status")
                if (status != ContrastContentStatus.PUBLISHED) add(C.STATUS_NOT_PUBLISHED, "$g.status")
            }
            if (group.approval == null && (status == ContrastContentStatus.APPROVED || status == ContrastContentStatus.PUBLISHED)) {
                add(C.MISSING_METADATA, "$g.approval")
            }
            // An approval of another revision does not approve this one.
            if (group.approval != null && group.revision != null && group.approval.approvedRevision != group.revision) {
                add(C.MISSING_METADATA, "$g.approval.approvedRevision")
            }
            val type = ContrastContentType.fromCode(group.contentType)
            when {
                group.contentType == null -> add(C.MISSING_METADATA, "$g.contentType")
                type == null -> add(C.SCHEMA_VIOLATION, "$g.contentType")
            }

            val items = group.items
            when {
                items == null || items.size < 2 -> add(C.TOO_FEW_ITEMS, "$g.items")
                items.size > MAX_ITEMS -> add(C.SCHEMA_VIOLATION, "$g.items")
            }
            val groupItemIds = items.orEmpty().map { it.id }.toSet()
            val sourcesInGroup = mutableSetOf<Pair<String, String>>()
            items.orEmpty().forEachIndexed { j, item ->
                val p = "$g.items[$j]"
                if (!itemIds.add(item.id)) add(C.DUPLICATE_ITEM_ID, "$p.id")
                val blocks = listOfNotNull(item.grammar, item.vocabulary, item.kanji)
                val sourceType = ContrastContentType.fromCode(item.source.sourceType)
                if (sourceType == null) add(C.SCHEMA_VIOLATION, "$p.source.sourceType")
                val typeMismatch = blocks.size != 1 || (type != null && (blocks.single().type != type || (sourceType != null && sourceType != type)))
                if (typeMismatch) add(C.ITEM_TYPE_MISMATCH, p)
                item.labelFurigana?.let { checkFurigana(item.label, it, "$p.labelFurigana", FuriganaPolicy.OPTIONAL_SILENT, null) }
                item.vocabulary?.collocations?.forEachIndexed { c, col ->
                    checkFurigana(col.japanese, col.furigana, "$p.vocabulary.collocations[$c].furigana", FuriganaPolicy.REQUIRED, null)
                }
                val source = item.source
                if (source.sourcePackId !in sourcePackIds) add(C.SOURCE_PACK_NOT_LISTED, "$p.source")
                if (!sourcesInGroup.add(source.sourceType to source.sourceContentId)) add(C.DUPLICATE_SOURCE_IN_GROUP, "$p.source")
                if (!typeMismatch && sourceType != null) checkSource(source, sourceType, "$p.source")
            }

            val theory = group.theory
            if (theory.isNullOrEmpty()) add(C.MISSING_EXPLANATION, "$g.theory")
            theory.orEmpty().forEachIndexed { k, t -> if (!otherIds.add(t.id)) add(C.DUPLICATE_ID, "$g.theory[$k].id") }

            val comparison = group.comparison
            if (comparison == null) add(C.SCHEMA_VIOLATION, "$g.comparison")
            comparison.orEmpty().forEachIndexed { r, row ->
                if (row.values.size < 2) add(C.SCHEMA_VIOLATION, "$g.comparison[$r].values")
                row.values.keys.sorted().forEach { key ->
                    if (key !in groupItemIds) add(C.UNKNOWN_ITEM_REFERENCE, "$g.comparison[$r].values.$key")
                }
            }

            val groupId = group.id.orEmpty()
            val examples = group.examples
            if (examples == null) add(C.SCHEMA_VIOLATION, "$g.examples") else if (examples.isEmpty()) warn(C.EMPTY_SECTION, "$g.examples", groupId)
            examples.orEmpty().forEachIndexed { k, e ->
                val p = "$g.examples[$k]"
                if (!otherIds.add(e.id)) add(C.DUPLICATE_ID, "$p.id")
                if (e.itemId !in groupItemIds) add(C.UNKNOWN_ITEM_REFERENCE, "$p.itemId")
                checkFurigana(e.japanese, e.furigana, "$p.furigana", FuriganaPolicy.REQUIRED, null)
            }

            val tips = group.memoryTips
            if (tips == null) add(C.SCHEMA_VIOLATION, "$g.memoryTips") else if (tips.isEmpty()) warn(C.EMPTY_SECTION, "$g.memoryTips", groupId)
            tips.orEmpty().forEachIndexed { k, t -> if (!otherIds.add(t.id)) add(C.DUPLICATE_ID, "$g.memoryTips[$k].id") }

            val mistakes = group.commonMistakes
            if (mistakes == null) add(C.SCHEMA_VIOLATION, "$g.commonMistakes") else if (mistakes.isEmpty()) warn(C.EMPTY_SECTION, "$g.commonMistakes", groupId)
            mistakes.orEmpty().forEachIndexed { m, mistake ->
                val p = "$g.commonMistakes[$m]"
                if (!otherIds.add(mistake.id)) add(C.DUPLICATE_ID, "$p.id")
                checkFurigana(mistake.wrong.japanese, mistake.wrong.furigana, "$p.wrong.furigana", FuriganaPolicy.REQUIRED, null)
                checkFurigana(mistake.correct.japanese, mistake.correct.furigana, "$p.correct.furigana", FuriganaPolicy.REQUIRED, null)
                if ((mistake.expectedItemId == null) != (mistake.confusedWithItemId == null)) add(C.SCHEMA_VIOLATION, p)
                if (mistake.expectedItemId != null && mistake.expectedItemId !in groupItemIds) add(C.UNKNOWN_ITEM_REFERENCE, "$p.expectedItemId")
                if (mistake.confusedWithItemId != null && mistake.confusedWithItemId !in groupItemIds) add(C.UNKNOWN_ITEM_REFERENCE, "$p.confusedWithItemId")
            }

            val questions = group.questions
            if (questions.isNullOrEmpty()) {
                add(C.NO_QUESTIONS, "$g.questions")
            } else {
                questions.forEachIndexed { k, q -> checkQuestion("$g.questions[$k]", q, groupItemIds, otherIds) }
                val types = questions.mapNotNull { ContrastQuestionType.fromCode(it.type) }.toSet()
                if (types.size < ContrastQuestionType.entries.size) warn(C.QUESTION_TYPES_INCOMPLETE, "$g.questions", groupId)
            }
        }

        private fun checkSource(source: RawSource, type: ContrastContentType, path: String) {
            if (source.sourceSchemaVersion !in ContrastFingerprint.SUPPORTED_SCHEMA_VERSIONS) return add(C.SOURCE_SCHEMA_UNSUPPORTED, path)
            val lookup = sources ?: return
            val reference = SourceReference(type, source.sourceContentId, source.sourcePackId, source.sourceSchemaVersion, source.sourceSnapshot, source.sourceFingerprint)
            val resolved = ContrastSourceVerifier.verify(reference, lookup)
            when (resolved.status) {
                SourceRefStatus.OK, SourceRefStatus.UNVERIFIABLE -> Unit
                SourceRefStatus.SOURCE_CHANGED -> add(C.SOURCE_CHANGED, path)
                SourceRefStatus.SOURCE_MISSING -> add(C.SOURCE_NOT_FOUND, path, detail = resolved.candidateIds.joinToString().ifEmpty { null })
                SourceRefStatus.AMBIGUOUS -> add(C.SOURCE_AMBIGUOUS, path, detail = resolved.candidateIds.joinToString())
            }
        }

        private fun checkQuestion(q: String, question: RawQuestion, groupItemIds: Set<String>, otherIds: MutableSet<String>) {
            if (!otherIds.add(question.id)) add(C.DUPLICATE_ID, "$q.id")
            val type = ContrastQuestionType.fromCode(question.type)
            when {
                question.type == null -> add(C.SCHEMA_VIOLATION, "$q.type")
                type == null -> add(C.UNSUPPORTED_QUESTION_TYPE, "$q.type")
            }
            if (question.difficulty !in 1..3) add(C.SCHEMA_VIOLATION, "$q.difficulty")
            val targetKnown = question.targetItemId in groupItemIds
            if (!targetKnown) add(C.UNKNOWN_ITEM_REFERENCE, "$q.targetItemId")
            if (question.explanationVi.isNullOrBlank()) add(C.MISSING_EXPLANATION, "$q.explanationVi")

            val options = question.options
            val minOptions = if (type == ContrastQuestionType.ERROR_DETECTION) 3 else 2
            if (options.size < minOptions) add(C.TOO_FEW_OPTIONS, "$q.options")
            if (options.size > MAX_OPTIONS) add(C.SCHEMA_VIOLATION, "$q.options")
            val optionIds = mutableSetOf<String>()
            val optionTexts = mutableSetOf<String>()
            options.forEachIndexed { o, option ->
                val p = "$q.options[$o]"
                val newId = optionIds.add(option.id)
                val newText = optionTexts.add(option.text)
                if (!newId || !newText) add(C.DUPLICATE_OPTION, p)
                if (option.itemId != null && option.itemId !in groupItemIds) add(C.UNKNOWN_ITEM_REFERENCE, "$p.itemId")
                checkFurigana(option.text, option.furigana, "$p.furigana", FuriganaPolicy.OPTIONAL_WARN, "${question.id}/${option.id}")
            }
            val correctId = question.correctOptionId
            val correct = options.firstOrNull { it.id == correctId }
            when {
                correctId.isNullOrBlank() -> add(C.MISSING_CORRECT_ANSWER, "$q.correctOptionId")
                correct == null -> add(C.CORRECT_OPTION_NOT_IN_OPTIONS, "$q.correctOptionId")
            }

            question.prompt?.let { checkFurigana(it.japanese, it.furigana, "$q.prompt.furigana", FuriganaPolicy.REQUIRED, null) }
            question.context?.japanese?.let { checkFurigana(it, question.context.furigana, "$q.context.furigana", FuriganaPolicy.OPTIONAL_WARN, question.id) }
            question.correction?.let { checkFurigana(it.text, it.furigana, "$q.correction.furigana", FuriganaPolicy.OPTIONAL_WARN, question.id) }

            when (type) {
                ContrastQuestionType.FILL_IN_THE_BLANK -> {
                    val prompt = question.prompt
                    if (prompt == null) add(C.SCHEMA_VIOLATION, "$q.prompt") else if (countBlanks(prompt.japanese) != 1) add(C.BLANK_MARKER_INVALID, "$q.prompt.japanese")
                }
                ContrastQuestionType.ERROR_DETECTION -> {
                    val prompt = question.prompt
                    if (prompt == null) add(C.SCHEMA_VIOLATION, "$q.prompt") else if (options.joinToString("") { it.text } != prompt.japanese) add(C.SEGMENTS_NOT_SENTENCE, "$q.options")
                    val correction = question.correction
                    when {
                        correction == null -> add(C.SCHEMA_VIOLATION, "$q.correction")
                        correction.itemId != null && correction.itemId !in groupItemIds -> add(C.UNKNOWN_ITEM_REFERENCE, "$q.correction.itemId")
                        targetKnown && correction.itemId != question.targetItemId -> add(C.TARGET_NOT_CORRECT_OPTION, "$q.correction.itemId")
                    }
                }
                ContrastQuestionType.CONTEXT_SELECTION -> if (question.context == null) add(C.SCHEMA_VIOLATION, "$q.context")
                ContrastQuestionType.MULTIPLE_CHOICE, null -> Unit
            }
            if (type != null && type != ContrastQuestionType.ERROR_DETECTION && targetKnown && correct != null && correct.itemId != question.targetItemId) {
                add(C.TARGET_NOT_CORRECT_OPTION, "$q.targetItemId")
            }
            if ((type == ContrastQuestionType.MULTIPLE_CHOICE || type == ContrastQuestionType.CONTEXT_SELECTION) && options.size == 2) {
                warn(C.FEW_OPTIONS, "$q.options", question.id)
            }
        }

        private fun checkFurigana(text: String, furigana: String?, path: String, policy: FuriganaPolicy, target: String?) {
            if (furigana == null) {
                if (!hasKanji(text)) return
                when (policy) {
                    FuriganaPolicy.REQUIRED -> add(C.FURIGANA_MISSING, path)
                    FuriganaPolicy.OPTIONAL_WARN -> warn(C.FURIGANA_MISSING, path, requireNotNull(target))
                    FuriganaPolicy.OPTIONAL_SILENT -> Unit
                }
                return
            }
            when {
                !Furigana.isValid(furigana) -> add(C.FURIGANA_INVALID, path)
                Furigana.baseText(furigana) != text -> add(C.FURIGANA_MISMATCH, path)
            }
        }

        /** Marks each acknowledgeable WARNING with whether its group's reviewer acknowledged it. */
        private fun acknowledgedIssues(): List<ContrastIssue> = issues.map { issue ->
            if (issue.severity != ContrastSeverity.WARNING || issue.code !in C.ACKNOWLEDGEABLE || issue.target == null) return@map issue
            val groupIndex = GROUP_INDEX.find(issue.path)?.groupValues?.get(1)?.toInt()
            val acks = groupIndex?.let { raw.groups.getOrNull(it)?.approval?.acknowledgedWarnings }.orEmpty()
            issue.copy(acknowledged = acks.any { it.code == issue.code.name && it.target == issue.target })
        }

        /**
         * Same rules as `_check_revisions` in validator.py. A group's revision covers its content
         * except identity, lifecycle (status, approval, updatedAt) and questions; a question's
         * revision covers everything but its id. Groups that cannot be converted are skipped (their
         * own errors are reported).
         */
        private fun checkRevisions(previous: List<ValidatedContrastGroup>) {
            val previousById = previous.associateBy { it.detail.group.id }
            val previousQuestions = previous.flatMap { it.questions }.associateBy { it.id }
            raw.groups.forEachIndexed { i, group ->
                val old = previousById[group.id] ?: return@forEachIndexed
                val current = runCatching { toDomain(group) }.getOrNull() ?: return@forEachIndexed
                val g = "groups[$i]"
                if (current.detail.items.map { it.id }.toSet() != old.detail.items.map { it.id }.toSet()) add(C.ITEMS_CHANGED_SAME_GROUP_ID, "$g.items")
                if (!revisionOk(current.detail.group.revision, old.detail.group.revision, groupBody(current) != groupBody(old))) {
                    add(C.REVISION_NOT_INCREMENTED, "$g.revision")
                }
                current.questions.forEachIndexed { k, q ->
                    val oldQ = previousQuestions[q.id] ?: return@forEachIndexed
                    if (!revisionOk(q.revision, oldQ.revision, questionBody(q) != questionBody(oldQ))) add(C.REVISION_NOT_INCREMENTED, "$g.questions[$k].revision")
                }
            }
        }

        private fun revisionOk(current: Int, previous: Int, changed: Boolean) = if (changed) current > previous else current >= previous

        private fun groupBody(group: ValidatedContrastGroup) = group.detail.copy(
            group = group.detail.group.copy(packId = "", revision = 0, questionCount = 0),
            licenseText = "",
            licenseVerified = false,
        )

        private fun questionBody(q: ContrastQuestion) = q.copy(groupId = "", revision = 0)

        private fun toDomain(group: RawGroup): ValidatedContrastGroup {
            val pack = requireNotNull(raw.pack)
            val type = requireNotNull(ContrastContentType.fromCode(group.contentType))
            val items = requireNotNull(group.items).map { item ->
                val source = item.source
                ContrastItem(
                    id = item.id,
                    label = item.label,
                    labelFurigana = item.labelFurigana,
                    source = SourceReference(type, source.sourceContentId, source.sourcePackId, source.sourceSchemaVersion, source.sourceSnapshot, source.sourceFingerprint),
                    details = listOfNotNull(item.grammar, item.vocabulary, item.kanji).single(),
                )
            }
            val questions = requireNotNull(group.questions).map { q ->
                ContrastQuestion(
                    id = q.id,
                    groupId = requireNotNull(group.id),
                    revision = q.revision,
                    type = requireNotNull(ContrastQuestionType.fromCode(q.type)),
                    difficulty = q.difficulty,
                    targetItemId = q.targetItemId,
                    instructionVi = q.instructionVi,
                    prompt = q.prompt,
                    context = q.context,
                    options = q.options,
                    correctOptionId = requireNotNull(q.correctOptionId),
                    correction = q.correction,
                    explanationVi = requireNotNull(q.explanationVi),
                )
            }
            val summary = ContrastGroup(
                id = requireNotNull(group.id),
                packId = requireNotNull(pack.id),
                contentType = type,
                jlptLevel = requireNotNull(group.jlptLevel),
                title = requireNotNull(group.title),
                summaryVi = requireNotNull(group.summaryVi),
                revision = requireNotNull(group.revision),
                itemLabels = items.map { it.label },
                questionCount = questions.size,
            )
            val detail = ContrastDetail(
                group = summary,
                items = items,
                theory = requireNotNull(group.theory),
                comparison = requireNotNull(group.comparison),
                examples = requireNotNull(group.examples),
                memoryTips = requireNotNull(group.memoryTips),
                commonMistakes = requireNotNull(group.commonMistakes).map { m ->
                    CommonMistake(
                        id = m.id,
                        situationVi = m.situationVi,
                        wrong = m.wrong,
                        correct = m.correct,
                        explanationVi = m.explanationVi,
                        confusion = if (m.expectedItemId != null && m.confusedWithItemId != null) Confusion(m.expectedItemId, m.confusedWithItemId) else null,
                    )
                },
                licenseText = pack.license?.text.orEmpty(),
                licenseVerified = pack.license?.licenseVerified ?: false,
            )
            return ValidatedContrastGroup(detail, questions)
        }

        private fun buildPack(): ValidatedContrastPack {
            val pack = requireNotNull(raw.pack)
            val groups = raw.groups.map(::toDomain)
            return ValidatedContrastPack(
                id = requireNotNull(pack.id),
                title = requireNotNull(pack.title),
                jlptLevel = requireNotNull(pack.jlptLevel),
                contentVersion = requireNotNull(pack.contentVersion),
                publishedAt = requireNotNull(pack.publishedAt),
                licenseText = requireNotNull(pack.license?.text),
                licenseVerified = requireNotNull(pack.license?.licenseVerified),
                groups = groups,
                retiredGroups = raw.retiredGroups.map { RetiredGroup(it.id, it.retiredInVersion, it.reasonVi, it.replacedBy) },
            )
        }
    }

    private enum class FuriganaPolicy { REQUIRED, OPTIONAL_WARN, OPTIONAL_SILENT }

    companion object {
        const val FORMAT = "n3sensei-contrast-pack"
        val SUPPORTED_SCHEMA_VERSIONS = setOf(1)
        const val BLANK = "＿＿＿"
        const val MAX_ITEMS = 4
        const val MAX_OPTIONS = 6
        private val GROUP_INDEX = Regex("""^groups\[(\d+)]""")

        /** Non-overlapping occurrences of [BLANK]. */
        fun countBlanks(text: String): Int = Regex.escape(BLANK).toRegex().findAll(text).count()

        /** Kanji = CJK ideographs U+3400–U+4DBF, U+4E00–U+9FFF, U+F900–U+FAFF and 々 (U+3005). Same set in Python. */
        fun hasKanji(text: String): Boolean = text.any { c ->
            c in '㐀'..'䶿' || c in '一'..'鿿' || c in '豈'..'﫿' || c == '々'
        }
    }
}
