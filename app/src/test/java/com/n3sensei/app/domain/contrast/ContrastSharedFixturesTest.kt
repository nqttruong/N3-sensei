package com.n3sensei.app.domain.contrast

import com.n3sensei.app.data.importing.ContrastPackCheck
import com.n3sensei.app.domain.contrast.validation.ContrastIssue
import com.n3sensei.app.domain.contrast.validation.ContrastIssueCode
import com.n3sensei.app.domain.contrast.validation.ContrastPackValidator
import com.n3sensei.app.domain.contrast.validation.ValidationMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The same fixtures as tools/content/contrast/test_contrast_tools.py: the Kotlin validator must give
 * exactly the hand-written `expect.import` of every case (Python checks the same expectation), and
 * the Kotlin fingerprint must give the hashes of fingerprint-cases.json.
 */
class ContrastSharedFixturesTest {

    private data class Case(val name: String, val tree: Map<String, Any?>)

    @Suppress("UNCHECKED_CAST")
    private fun cases(): List<Case> =
        File(ContrastFixtures.fixturesDir, "cases").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }.map {
            Case(it.nameWithoutExtension, ContrastFixtures.readTree("tools/content/contrast/fixtures/cases/${it.name}") as Map<String, Any?>)
        }

    private fun comparable(issues: List<ContrastIssue>) =
        issues.map { listOf(it.severity.name, it.code.name, it.path, it.acknowledged?.toString()) }.sortedBy { it.joinToString("|") }

    @Suppress("UNCHECKED_CAST")
    private fun expected(list: List<Any?>) = list.map { raw ->
        val m = raw as Map<String, Any?>
        listOf(m["severity"] as String, m["code"] as String, m["path"] as String, (m["acknowledged"] as Boolean?)?.toString())
    }.sortedBy { it.joinToString("|") }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun everySharedCase_givesTheExpectedImportResult_throughTheProductionParser() {
        val all = cases()
        assertTrue("fixtures found: ${all.size}", all.size >= 50)
        all.forEach { case ->
            val expect = (case.tree["expect"] as Map<String, Any?>)["import"] as List<Any?>
            val pack = ContrastFixtures.applyPatch(ContrastFixtures.readTree(case.tree["base"] as String), case.tree["patch"] as List<Any?>)
            val previous = (case.tree["previous"] as Map<String, Any?>?)?.let {
                ContrastFixtures.applyPatch(ContrastFixtures.readTree(it["base"] as String), it["patch"] as List<Any?>)
            }
            val sources = ContrastFixtures.sources(case.tree["sources"] as String? ?: ContrastFixtures.DEFAULT_SOURCES)
            // JSON text → ContrastPackParser (structure) + ContrastPackValidator (semantics), as the importer does.
            val result = ContrastPackCheck.check(
                ContrastFixtures.toJsonText(pack),
                ValidationMode.IMPORT,
                sources,
                previous?.let(ContrastFixtures::installed),
                previous?.let(ContrastFixtures::contentOf),
            ).validation
            assertEquals("case ${case.name}", expected(expect), comparable(result.issues))
            // A pack is produced exactly when there is no ERROR.
            assertEquals("case ${case.name}", result.hasErrors, result.pack == null)
        }
    }

    @Test
    fun invalidJson_andANonObject_areRefusedByTheParser() {
        val broken = ContrastPackCheck.check("{ not json").validation
        assertEquals(listOf(ContrastIssueCode.INVALID_JSON), broken.issues.map { it.code })
        assertNull(broken.pack)
        assertEquals(listOf(ContrastIssueCode.SCHEMA_VIOLATION to ""), ContrastPackCheck.check("[1, 2]").validation.issues.map { it.code to it.path })
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun fingerprintCases() {
        val data = ContrastFixtures.readTree("tools/content/contrast/fixtures/fingerprint-cases.json") as Map<String, Any?>
        val cases = data["cases"] as List<Map<String, Any?>>
        assertTrue(cases.size >= 9)
        cases.forEach { case ->
            val type = ContrastContentType.fromCode(case["type"] as String)!!
            val fields = (case["fields"] as Map<String, Any?>).mapValues { (_, v) ->
                if (v is List<*>) SnapshotValue.Texts(v.map { it as String }) else SnapshotValue.Text(v as String)
            }
            assertEquals(case["name"] as String, case["expected"], ContrastFingerprint.of(type, fields))
        }
    }

    @Test
    fun theSamplePack_validates_withTheRealSourceRows_andBuildsDomainModels() {
        val pack = ContrastFixtures.samplePack()
        assertEquals("contrast-n3", pack.id)
        assertEquals(1, pack.contentVersion)
        assertEquals(3, pack.groups.size)
        val grammar = pack.groups[0]
        assertEquals(ContrastContentType.GRAMMAR, grammar.detail.group.contentType)
        assertEquals(listOf("n3g-g001", "n3g-g002"), grammar.detail.items.map { it.source.sourceContentId })
        assertEquals(5, grammar.questions.size)
        assertEquals(ContrastQuestionType.entries.toSet(), grammar.questions.map { it.type }.toSet())
        assertEquals(Confusion("ci-g-koto-ni-shiteiru", "ci-g-koto-ni-natteiru"), grammar.detail.commonMistakes[0].confusion)
        assertTrue(pack.groups[2].detail.items.first().details is ItemDetails.Kanji)
    }

    @Test
    fun buildMode_makesSourceMismatchesErrors() {
        val tree = ContrastFixtures.applyPatch(
            ContrastFixtures.readTree(ContrastFixtures.SAMPLE),
            listOf(mapOf("op" to "replace", "path" to "/groups/0/items/0/source/sourceContentId", "value" to "n3g-g999")),
        )
        val sources = ContrastFixtures.sources(ContrastFixtures.DEFAULT_SOURCES)
        val build = ContrastPackValidator(ValidationMode.BUILD).validate(ContrastFixtures.toRaw(tree), sources)
        val import = ContrastPackValidator(ValidationMode.IMPORT).validate(ContrastFixtures.toRaw(tree), sources)
        assertNull(build.pack)
        assertNotNull(import.pack)
        val issue = import.issues.single { it.code == ContrastIssueCode.SOURCE_NOT_FOUND }
        // The single snapshot match is reported, never used to relink.
        assertEquals("n3g-g001", issue.detail)
    }
}
