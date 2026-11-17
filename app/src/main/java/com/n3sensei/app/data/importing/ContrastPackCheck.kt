package com.n3sensei.app.data.importing

import com.n3sensei.app.domain.contrast.InstalledContrastPack
import com.n3sensei.app.domain.contrast.SourceLookup
import com.n3sensei.app.domain.contrast.validation.ContrastIssue
import com.n3sensei.app.domain.contrast.validation.ContrastPackValidator
import com.n3sensei.app.domain.contrast.validation.ContrastSeverity
import com.n3sensei.app.domain.contrast.validation.ContrastValidationResult
import com.n3sensei.app.domain.contrast.validation.RawContrastPack
import com.n3sensei.app.domain.contrast.validation.ValidatedContrastGroup
import com.n3sensei.app.domain.contrast.validation.ValidationMode

/**
 * Structure ([ContrastPackParser]) + semantics ([ContrastPackValidator]) of one pack text, merged
 * the way tools/content/contrast/validator.py merges schema and semantic findings: one issue per
 * (code, path), semantic first. A validated pack is returned only when there is no ERROR at all.
 */
object ContrastPackCheck {

    data class Result(val raw: RawContrastPack?, val validation: ContrastValidationResult)

    fun check(
        text: String,
        mode: ValidationMode = ValidationMode.IMPORT,
        sources: SourceLookup? = null,
        installed: InstalledContrastPack? = null,
        previousGroups: List<ValidatedContrastGroup>? = null,
    ): Result = check(ContrastPackParser.parse(text, mode), mode, sources, installed, previousGroups)

    fun check(
        parsed: ContrastPackParser.Parsed,
        mode: ValidationMode = ValidationMode.IMPORT,
        sources: SourceLookup? = null,
        installed: InstalledContrastPack? = null,
        previousGroups: List<ValidatedContrastGroup>? = null,
    ): Result {
        val raw = parsed.raw ?: return Result(null, ContrastValidationResult(parsed.issues, null))
        val semantic = ContrastPackValidator(mode).validate(raw, sources, installed, previousGroups)
        val merged = mergeIssues(semantic.issues, parsed.issues)
        val pack = semantic.pack?.takeIf { merged.none { it.severity == ContrastSeverity.ERROR } }
        return Result(raw, ContrastValidationResult(merged, pack))
    }

    fun mergeIssues(first: List<ContrastIssue>, second: List<ContrastIssue>): List<ContrastIssue> {
        val seen = first.mapTo(HashSet()) { it.code to it.path }
        return first + second.filter { seen.add(it.code to it.path) }
    }
}
