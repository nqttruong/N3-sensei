"""Tests of the Contrast Lab content tools.

Run: python3 tools/content/contrast/test_contrast_tools.py   (needs jsonschema; not python3 -I)
"""
import hashlib
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from fingerprint import SourceIndex, fingerprint, verify  # noqa: E402
from fixture_cases import FIXTURES, REPO, apply_patch, cases, comparable, load_json  # noqa: E402
from furigana_markup import base_text, has_kanji, is_valid  # noqa: E402
from validator import BUILD, CODES, IMPORT, failed, validate  # noqa: E402

SCHEMA = json.loads((REPO / "docs/contrast/contrast-pack.schema.json").read_text(encoding="utf-8"))
SAMPLE_PATH = REPO / "docs/contrast/contrast-pack.sample.json"
SAMPLE = json.loads(SAMPLE_PATH.read_text(encoding="utf-8"))
FIXTURE_SOURCES = FIXTURES / "sources.json"
BUILD_PY = HERE / "build_pack.py"


def run_cli(*args):
    return subprocess.run([sys.executable, str(BUILD_PY), *map(str, args)], capture_output=True, text=True)


class FuriganaTest(unittest.TestCase):
    """Same rules as domain/furigana/Furigana.kt."""

    def test_valid_and_base_text(self):
        self.assertTrue(is_valid("{日本|にほん}に{来|き}た"))
        self.assertEqual("日本に来た", base_text("{日本|にほん}に{来|き}た"))
        self.assertTrue(is_valid("かな only"))
        self.assertTrue(is_valid(""))

    def test_malformed(self):
        for bad in ("{日本|にほん", "{日本}", "{|にほん}", "{日本|}", "{日|に|ほん}", "日本}", "a|b", "{{日|に}"):
            self.assertFalse(is_valid(bad), bad)

    def test_has_kanji(self):
        self.assertTrue(has_kanji("成績"))
        self.assertTrue(has_kanji("人々"))
        self.assertFalse(has_kanji("ことにしている。"))
        self.assertFalse(has_kanji("＿＿＿"))


class FingerprintTest(unittest.TestCase):
    def test_shared_cases(self):
        data = json.loads((FIXTURES / "fingerprint-cases.json").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(data["cases"]), 9)
        for case in data["cases"]:
            with self.subTest(case["name"]):
                self.assertEqual(case["expected"], fingerprint(case["type"], case["fields"]))

    def test_sample_fingerprints_match_the_real_source_packs(self):
        sources = SourceIndex.load(REPO / "app/src/main/assets/content")
        for group in SAMPLE["groups"]:
            for item in group["items"]:
                with self.subTest(item["id"]):
                    self.assertEqual(("OK", []), verify(item["source"], sources))

    def test_importer_storage_is_mimicked(self):
        # The app trims strings and drops empty reading entries when it stores a pack row.
        index = SourceIndex([{"type": "KANJI", "id": "k", "packId": "p", "fields": {}}])
        self.assertIsNone(index.by_id("KANJI", "missing"))
        from fingerprint import stored_value
        self.assertEqual(["つ.む"], stored_value([" つ.む ", "", "  "]))
        self.assertEqual("積", stored_value(" 積 "))


class SharedFixturesTest(unittest.TestCase):
    """Every case in fixtures/cases: the Python result must equal the hand-written expectation
    (build: schema + semantic + previous; import: what the app's validator must report)."""

    def test_every_case(self):
        all_cases = cases()
        self.assertGreaterEqual(len(all_cases), 30)
        for case in all_cases:
            sources = SourceIndex.load(case["sourcesPath"])
            for mode in (BUILD, IMPORT):
                expected = case["expect"][mode]
                if expected is None:
                    continue
                with self.subTest(case=case["name"], mode=mode):
                    issues, _ = validate(case["pack"], SCHEMA, mode, sources, case["previousPack"])
                    self.assertEqual(comparable(expected), comparable(issues))

    def test_expectations_use_known_codes_only(self):
        for case in cases():
            for mode in (BUILD, IMPORT):
                for issue in case["expect"][mode] or []:
                    self.assertIn(issue["code"], CODES, case["name"])

    def test_required_scenarios_are_covered(self):
        names = {c["name"] for c in cases()}
        for required in ("items-changed-same-group-id", "revision-not-incremented", "revision-incremented", "approval-for-another-revision", "valid-sample", "missing-metadata", "duplicate-ids", "unsupported-question-type", "missing-correct-answer",
                         "conflicting-answer", "accepted-answers-not-in-contract", "furigana", "source-bad-fingerprint", "source-missing",
                         "source-ambiguous", "content-version-same-as-previous", "answer-changed-same-id", "warning-unacknowledged",
                         "warning-acknowledged"):
            self.assertIn(required, names)


class SchemaTest(unittest.TestCase):
    def test_schema_is_valid_and_sample_conforms(self):
        import jsonschema
        jsonschema.Draft202012Validator.check_schema(SCHEMA)
        errors = list(jsonschema.Draft202012Validator(SCHEMA, format_checker=jsonschema.FormatChecker()).iter_errors(SAMPLE))
        self.assertEqual([], [e.message for e in errors])


class PreviousTest(unittest.TestCase):
    def test_change_report(self):
        current = apply_patch(SAMPLE, [
            {"op": "replace", "path": "/pack/contentVersion", "value": 2},
            {"op": "replace", "path": "/groups/0/questions/0/explanationVi", "value": "Giải thích đã sửa."},
            {"op": "replace", "path": "/groups/1/questions/1/options/2/text", "value": "今朝は六時に目が冷え込んだ。"},
            {"op": "remove", "path": "/groups/1/questions/1/options/2/furigana"},
            {"op": "replace", "path": "/groups/1/questions/1/revision", "value": 2},
        ])
        issues, diff = validate(current, SCHEMA, BUILD, SourceIndex.load(FIXTURE_SOURCES), SAMPLE)
        # A distractor changed with its revision raised: no answer change, no revision error.
        self.assertFalse(any(i["code"] == "ANSWER_CHANGED_SAME_ID" for i in issues))
        # An explanation edited without raising the question's revision: ERROR (D3), nothing else.
        self.assertEqual([("REVISION_NOT_INCREMENTED", "groups[0].questions[0].revision")],
                         [(i["code"], i["path"]) for i in issues if i["severity"] == "ERROR"])
        self.assertEqual(1, diff["previousVersion"])
        self.assertEqual(2, diff["contentVersion"])
        self.assertEqual([], diff["groupsAdded"])

    def test_fingerprint_and_item_changes_are_reported(self):
        current = apply_patch(SAMPLE, [
            {"op": "replace", "path": "/pack/contentVersion", "value": 2},
            {"op": "replace", "path": "/groups/0/items/0/source/sourceFingerprint", "value": "sha256:" + "1" * 64},
        ])
        _, diff = validate(current, SCHEMA, BUILD, SourceIndex.load(FIXTURE_SOURCES), SAMPLE)
        change = diff["groupsChanged"][0]
        self.assertEqual(["ci-g-koto-ni-shiteiru"], change["sourceFingerprintChanged"])


class CliTest(unittest.TestCase):
    def test_check_passes_and_never_modifies_the_input(self):
        before = hashlib.sha256(SAMPLE_PATH.read_bytes()).hexdigest()
        result = run_cli("--check", SAMPLE_PATH, "--strict", "--sources", FIXTURE_SOURCES)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("ĐẠT", result.stdout)
        self.assertEqual(before, hashlib.sha256(SAMPLE_PATH.read_bytes()).hexdigest())

    def test_check_against_the_real_source_packs(self):
        result = run_cli("--check", SAMPLE_PATH, "--strict")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_exit_codes_and_report(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            broken = tmp / "broken.json"
            broken.write_text("{ not json", encoding="utf-8")
            result = run_cli("--check", broken)
            self.assertEqual(1, result.returncode)
            self.assertIn("INVALID_JSON", result.stdout)
            self.assertEqual(2, run_cli("--check", tmp / "absent.json").returncode)

            unacknowledged = tmp / "unack.json"
            unacknowledged.write_text(json.dumps(apply_patch(SAMPLE, [{"op": "remove", "path": "/groups/1/approval/acknowledgedWarnings/0"}]), ensure_ascii=False), encoding="utf-8")
            self.assertEqual(0, run_cli("--check", unacknowledged, "--sources", FIXTURE_SOURCES).returncode)
            report = tmp / "report.json"
            strict = run_cli("--check", unacknowledged, "--strict", "--sources", FIXTURE_SOURCES, "--report", report)
            self.assertEqual(1, strict.returncode)
            data = json.loads(report.read_text(encoding="utf-8"))
            self.assertFalse(data["passed"])
            warning = next(i for i in data["issues"] if i.get("acknowledged") is False)
            # The severity stays WARNING: --strict fails because it is unacknowledged, not because it became an ERROR.
            self.assertEqual(("WARNING", "QUESTION_TYPES_INCOMPLETE", "cg-v-sameru-cold-awake"), (warning["severity"], warning["code"], warning["target"]))

            changed = tmp / "changed.json"
            changed.write_text(json.dumps(apply_patch(SAMPLE, [{"op": "replace", "path": "/groups/0/items/0/source/sourceFingerprint", "value": "sha256:" + "0" * 64}]), ensure_ascii=False), encoding="utf-8")
            self.assertEqual(1, run_cli("--check", changed, "--sources", FIXTURE_SOURCES).returncode)
            self.assertEqual(0, run_cli("--check", changed, "--sources", FIXTURE_SOURCES, "--mode", "import").returncode)

    def _drafts(self, tmp: Path, status_of=lambda g: "APPROVED"):
        drafts = tmp / "drafts"
        drafts.mkdir()
        for group in SAMPLE["groups"]:
            draft = {**group, "status": status_of(group)}
            (drafts / f"{group['id']}.json").write_text(json.dumps(draft, ensure_ascii=False, indent=2), encoding="utf-8")
        meta = tmp / "release.json"
        meta.write_text(json.dumps({"pack": SAMPLE["pack"], "groupOrder": [g["id"] for g in SAMPLE["groups"]]}, ensure_ascii=False), encoding="utf-8")
        # Test-only registry: the sample's illustrative id stands for a registered reviewer here, never in a real release.
        (tmp / "reviewers.json").write_text(json.dumps({"reviewers": ["reviewer-01"]}), encoding="utf-8")
        return drafts, meta

    def test_build_publishes_approved_drafts_deterministically(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            drafts, meta = self._drafts(tmp)
            out1, out2 = tmp / "a.json", tmp / "b.json"
            for out in (out1, out2):
                result = run_cli("--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES, "--reviewers", tmp / "reviewers.json")
                self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual(out1.read_bytes(), out2.read_bytes())
            built = json.loads(out1.read_text(encoding="utf-8"))
            self.assertEqual({"PUBLISHED"}, {g["status"] for g in built["groups"]})
            # The sample, minus its $schema hint, is what the build produces from its approved groups.
            # The build lists sourcePacks sorted by id (the sample lists them in another order; order has no meaning).
            self.assertEqual(["mimikara-n3-vocab", "n3-grammar", "n3-kanji"], [p["id"] for p in built["sourcePacks"]])
            expected = {k: v for k, v in SAMPLE.items() if k not in ("$schema", "retiredGroups", "sourcePacks")}
            self.assertEqual(sorted(SAMPLE["sourcePacks"], key=lambda p: p["id"]), built.pop("sourcePacks"))
            self.assertEqual(expected, built)

    def test_build_refuses_unapproved_drafts_and_writes_nothing(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            drafts, meta = self._drafts(tmp, lambda g: "REVIEWING" if g["contentType"] == "KANJI" else "APPROVED")
            out = tmp / "pack.json"
            result = run_cli("--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES, "--reviewers", tmp / "reviewers.json")
            self.assertEqual(1, result.returncode)
            self.assertIn("STATUS_NOT_PUBLISHED", result.stdout)
            self.assertFalse(out.exists())

    def test_build_refuses_unacknowledged_warnings(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            drafts, meta = self._drafts(tmp)
            path = drafts / "cg-v-sameru-cold-awake.json"
            draft = json.loads(path.read_text(encoding="utf-8"))
            draft["approval"]["acknowledgedWarnings"] = []
            path.write_text(json.dumps(draft, ensure_ascii=False), encoding="utf-8")
            out = tmp / "pack.json"
            self.assertEqual(1, run_cli("--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES, "--reviewers", tmp / "reviewers.json").returncode)
            self.assertFalse(out.exists())

    def test_build_against_previous_release(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            drafts, meta = self._drafts(tmp)
            out = tmp / "pack.json"
            # Same contentVersion as the previous release: refused.
            result = run_cli("--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES, "--previous", SAMPLE_PATH, "--reviewers", tmp / "reviewers.json")
            self.assertEqual(1, result.returncode)
            self.assertIn("INVALID_CONTENT_VERSION", result.stdout)
            data = json.loads(meta.read_text(encoding="utf-8"))
            data["pack"]["contentVersion"] = 2
            meta.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            self.assertEqual(0, run_cli("--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES, "--previous", SAMPLE_PATH, "--reviewers", tmp / "reviewers.json").returncode)


class ReviewTest(unittest.TestCase):
    def _build(self, reviewers):
        tmp = Path(tempfile.mkdtemp())
        drafts, meta = CliTest._drafts(CliTest(), tmp)
        if reviewers is not None:
            (tmp / "reviewers.json").write_text(json.dumps({"reviewers": reviewers}), encoding="utf-8")
        out = tmp / "pack.json"
        args = ["--drafts", drafts, "--meta", meta, "--output", out, "--sources", FIXTURE_SOURCES]
        if reviewers is not None:
            args += ["--reviewers", tmp / "reviewers.json"]
        return run_cli(*args), out

    def test_a_release_needs_a_registered_reviewer(self):
        result, out = self._build(["someone-else"])
        self.assertEqual(1, result.returncode)
        self.assertIn("MISSING_METADATA", result.stdout)
        self.assertIn("approval.approvedBy", result.stdout)
        self.assertFalse(out.exists())
        result, out = self._build(None)
        self.assertEqual(2, result.returncode)  # --reviewers is required to build
        self.assertFalse(out.exists())

    def test_self_approval_is_allowed_and_reported(self):
        result, out = self._build(["reviewer-01"])
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("TỰ DUYỆT", result.stdout)
        from validator import approvals
        rows = approvals(json.loads(out.read_text(encoding="utf-8")))
        self.assertTrue(all(r["selfApproved"] and r["approvedRevision"] == r["revision"] for r in rows))


class AndroidTestAssetsTest(unittest.TestCase):
    """Test-only copies used by the app's instrumented tests must not drift from their sources."""
    ASSETS = REPO / "app/src/androidTest/assets/contrast"

    def test_the_test_pack_is_an_exact_copy_of_the_sample(self):
        self.assertEqual(SAMPLE_PATH.read_bytes(), (self.ASSETS / "test-pack-sample-copy.json").read_bytes())

    def test_exported_source_fingerprints_are_current(self):
        from fingerprint import export_fingerprints
        current = export_fingerprints(REPO / "app/src/main/assets/content")
        stored = json.loads((self.ASSETS / "source-fingerprints.json").read_text(encoding="utf-8"))
        self.assertEqual(current, stored)

    def test_the_sample_is_not_shipped(self):
        shipped = [p.name for p in (REPO / "app/src/main/assets/content").glob("*.json")]
        self.assertFalse(any("contrast" in name for name in shipped), shipped)


class StrictRuleTest(unittest.TestCase):
    def test_failed(self):
        self.assertTrue(failed([{"severity": "ERROR"}], strict=False))
        self.assertFalse(failed([{"severity": "WARNING", "acknowledged": False}], strict=False))
        self.assertTrue(failed([{"severity": "WARNING", "acknowledged": False}], strict=True))
        self.assertFalse(failed([{"severity": "WARNING", "acknowledged": True}, {"severity": "INFO"}], strict=True))


if __name__ == "__main__":
    unittest.main()
