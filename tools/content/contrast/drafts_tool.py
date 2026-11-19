"""Contrast Lab content drafts: generate, check, and build a TEST-ONLY pack.

Docs: docs/CONTRAST_LAB_CONTENT_GENERATION_REPORT.md, docs/CONTRAST_LAB_TOOLING.md.

    python3 tools/content/contrast/drafts_tool.py catalog      # C1: read-only catalog + data limitations of the N3 sources
    python3 tools/content/contrast/drafts_tool.py generate     # authoring/*.py → drafts/<groupId>.json + release.json
    python3 tools/content/contrast/drafts_tool.py check        # validator (build mode) + editorial QA, on the drafts as they are
    python3 tools/content/contrast/drafts_tool.py test-pack OUT.json   # TEST-ONLY pack (never for the app's assets)
    python3 tools/content/contrast/drafts_tool.py debug-preview        # DEBUG-BUILD preview pack (app/src/debug/assets only)

The drafts are written with status AI_GENERATED: they are AI-written and nobody has reviewed them.
A release pack is built only by build_pack.py --drafts …, which needs APPROVED drafts approved by a
registered reviewer (reviewers.json). This tool never sets APPROVED or PUBLISHED on a draft.

The TEST-ONLY pack marks the groups PUBLISHED with approvedBy "chua-duyet-test-only" so instrumented
tests can import it; it is written only where the caller says (androidTest assets), never into
app/src/main/assets.
"""
from __future__ import annotations

import argparse
import importlib
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "authoring"))

import fingerprint as fp  # noqa: E402
from fingerprint import SourceIndex  # noqa: E402
from furigana_markup import has_kanji, parse  # noqa: E402

REPO = HERE.parents[2]
SOURCES = REPO / "app" / "src" / "main" / "assets" / "content"
SCHEMA = REPO / "docs" / "contrast" / "contrast-pack.schema.json"
DRAFTS = HERE / "drafts"
RELEASE_META = HERE / "release.json"
MODULES = ["grammar_groups", "vocabulary_groups", "kanji_groups"]
UPDATED_AT = "2026-11-18"
STATUS = "AI_GENERATED"
N3_PACKS = {"n3-grammar", "mimikara-n3-vocab", "n3-kanji"}

PACK_META = {
    "id": "contrast-n3",
    "title": "Contrast Lab N3",
    "description": "Các nhóm kiến thức N3 dễ nhầm (ngữ pháp, từ vựng, Kanji) dựng trên dữ liệu học có sẵn của N3 Sensei: giải thích, ví dụ có furigana và bốn dạng bài tập.",
    "jlptLevel": "N3",
    "contentVersion": 1,
    "publishedAt": UPDATED_AT,
    "license": {
        "text": "Giải thích, ví dụ và câu hỏi do N3 Sensei biên soạn với hỗ trợ của AI. CHƯA qua kiểm duyệt của người duyệt thật; không được phát hành trước khi được duyệt.",
        "licenseVerified": False,
    },
}


def load_groups():
    groups = []
    for name in MODULES:
        groups += importlib.import_module(name).GROUPS
    return groups


def load_sources() -> SourceIndex:
    index = SourceIndex.from_content_packs(SOURCES)
    raw = {}
    for path in sorted(SOURCES.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("format") != "n3sensei-content-pack":
            continue
        for lesson in data.get("lessons", []):
            for key, t in (("grammar", "GRAMMAR"), ("vocabulary", "VOCABULARY"), ("kanji", "KANJI")):
                for row in lesson.get(key) or []:
                    raw[(t, row["id"])] = {**row, "lesson": lesson.get("number"), "packId": data["pack"]["id"]}
    for r in index.records:
        r["raw"] = raw.get((r["type"], r["id"]), {})
    return index


# ---- editorial QA (beyond the shared validator) ----

def _markups(group: dict):
    """(path, markup) of every furigana field in a draft."""
    def walk(node, path):
        if isinstance(node, dict):
            for k, v in node.items():
                p = f"{path}.{k}" if path else k
                if k in ("furigana", "labelFurigana") and isinstance(v, str):
                    yield p, v
                else:
                    yield from walk(v, p)
        elif isinstance(node, list):
            for i, v in enumerate(node):
                yield from walk(v, f"{path}[{i}]")
    yield from walk(group, "")


def reading_dictionary(sources: SourceIndex) -> dict[str, set[str]]:
    """{base: {readings}} harvested from the furigana of the source packs' examples and vocabulary rows.
    Only compounds (2+ characters) are compared: a single kanji's reading depends on its okurigana."""
    seen: dict[str, set[str]] = defaultdict(set)
    for path in sorted(SOURCES.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("format") != "n3sensei-content-pack" or data["pack"]["id"] not in N3_PACKS:
            continue
        for ex in data.get("examples", []):
            if ex.get("furigana"):
                try:
                    for base, reading in parse(ex["furigana"]):
                        if reading:
                            seen[base].add(reading)
                except ValueError:
                    pass
    for r in sources.records:
        if r["type"] == "VOCABULARY" and r["packId"] in N3_PACKS:
            f = r["fields"]
            if f["expression"] and f["reading"] and all(has_kanji(c) for c in f["expression"]):
                seen[f["expression"]].add(f["reading"])
    return seen


def _hira(text: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in text)


_VOICED = dict(zip("かきくけこさしすせそたちつてとはひふへほ", "がぎぐげござじずぜぞだぢづでどばびぶべぼ"))
_SEMI = dict(zip("はひふへほ", "ぱぴぷぺぽ"))


def kanji_readings() -> dict[str, set[str]]:
    """Single-kanji readings from the app's kanji packs (N3 and N2): on (as hiragana) and kun stems,
    with rendaku (voiced first kana) and sokuon (final つ/く/ち/き → っ) variants."""
    out: dict[str, set[str]] = defaultdict(set)
    for path in sorted(SOURCES.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("format") != "n3sensei-content-pack":
            continue
        for lesson in data.get("lessons", []):
            for k in lesson.get("kanji") or []:
                base = {_hira(r) for r in k.get("onReadings") or []}
                base |= {r.strip("-").split(".")[0] for r in k.get("kunReadings") or [] if r.strip("-")}
                variants = set()
                for r in base:
                    if not r:
                        continue
                    variants.add(r)
                    if r[0] in _VOICED:
                        variants.add(_VOICED[r[0]] + r[1:])
                    if r[0] in _SEMI:
                        variants.add(_SEMI[r[0]] + r[1:])
                    if len(r) > 1 and r[-1] in "つくちき":
                        variants |= {r[:-1] + "っ"} | ({_VOICED.get(r[0], r[0]) + r[1:-1] + "っ"} if r[0] in _VOICED else set())
                out[k["character"]] |= variants
    return out


def qa(groups_json: list[dict], groups_src, sources: SourceIndex) -> tuple[list[str], list[str]]:
    errors, notes = [], []
    dictionary = reading_dictionary(sources)
    single = kanji_readings()
    source_sets = Counter()
    for g, src in zip(groups_json, groups_src):
        gid = g["id"]
        source_sets[frozenset((it["source"]["sourceType"], it["source"]["sourceContentId"]) for it in g["items"])] += 1
        for path, markup in _markups(g):
            try:
                segments = parse(markup)
            except ValueError as e:
                errors.append(f"{gid} {path}: {e}")
                continue
            for text, reading in segments:
                if reading is None and has_kanji(text):
                    errors.append(f"{gid} {path}: Kanji ngoài furigana: {text!r} trong {markup!r}")
                if reading is not None and not has_kanji(text):
                    errors.append(f"{gid} {path}: furigana trên chữ không có Kanji: {text!r}")
                if reading is not None and re.search(r"[^぀-ゟー]", reading):
                    errors.append(f"{gid} {path}: cách đọc không phải hiragana: {reading!r}")
                if reading is not None and len(text) == 1 and text in single and reading not in single[text]:
                    notes.append(f"{gid} {path}: {text}|{reading} — không khớp âm On/Kun của {text} trong dữ liệu Kanji ({'/'.join(sorted(single[text]))})")
                if reading is not None and len(text) >= 2 and text in dictionary and reading not in dictionary[text]:
                    notes.append(f"{gid} {path}: {text}|{reading} — nguồn đọc là {'/'.join(sorted(dictionary[text]))}")
        per_item = Counter(e["itemId"] for e in g["examples"])
        for it in g["items"]:
            if per_item[it["id"]] < 2:
                errors.append(f"{gid}: mục {it['id']} có {per_item[it['id']]} ví dụ (< 2)")
        types = {q["type"] for q in g["questions"]}
        if len(types) < 4:
            notes.append(f"{gid}: thiếu dạng bài {sorted({'MULTIPLE_CHOICE', 'ERROR_DETECTION', 'FILL_IN_THE_BLANK', 'CONTEXT_SELECTION'} - types)}")
        for q in g["questions"]:
            if q["type"] in ("MULTIPLE_CHOICE", "CONTEXT_SELECTION") and len(q["options"]) < 3:
                errors.append(f"{gid} {q['id']}: < 3 lựa chọn")
            wrong = [o for o in q["options"] if o["id"] != q["correctOptionId"]]
            if q["type"] != "ERROR_DETECTION" and any(not o.get("itemId") and not o.get("feedbackVi") for o in wrong):
                errors.append(f"{gid} {q['id']}: lựa chọn sai không có itemId cũng không có feedbackVi")
        if not g["commonMistakes"] or not g["memoryTips"] or len(g["theory"]) < 2 or len(g["comparison"]) < 2:
            errors.append(f"{gid}: thiếu phần giải thích (theory ≥ 2, comparison ≥ 2, memoryTips, commonMistakes)")
        for it in g["items"]:
            if it["source"]["sourcePackId"] not in N3_PACKS:
                errors.append(f"{gid}: {it['id']} trỏ tới gói không phải N3 ({it['source']['sourcePackId']})")
    for s, n in source_sets.items():
        if n > 1:
            errors.append(f"nhóm trùng tập nguồn: {sorted(s)}")
    return errors, notes


# ---- commands ----

def build_all(sources):
    src = load_groups()
    ids = [g.id for g in src]
    dup = [i for i, n in Counter(ids).items() if n > 1]
    if dup:
        raise SystemExit(f"id nhóm trùng: {dup}")
    return src, [g.to_draft(sources, fp, UPDATED_AT, STATUS) for g in src]


def dump(data) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def cmd_generate(_args) -> int:
    sources = load_sources()
    src, drafts = build_all(sources)
    DRAFTS.mkdir(exist_ok=True)
    keep = {f"{d['id']}.json" for d in drafts}
    for old in DRAFTS.glob("*.json"):
        if old.name not in keep:
            old.unlink()
    for d in drafts:
        (DRAFTS / f"{d['id']}.json").write_text(dump(d), encoding="utf-8")
    RELEASE_META.write_text(dump({"pack": PACK_META, "groupOrder": [d["id"] for d in drafts]}), encoding="utf-8")
    print(f"Đã ghi {len(drafts)} bản nháp vào {DRAFTS.relative_to(REPO)} và {RELEASE_META.relative_to(REPO)}")
    return 0


def assembled(drafts: list[dict], sources: SourceIndex) -> dict:
    used = sorted({it["source"]["sourcePackId"] for g in drafts for it in g["items"]})
    return {"format": "n3sensei-contrast-pack", "schemaVersion": 1, "pack": PACK_META,
            "sourcePacks": [{"id": p, "version": sources.version_of[p]} for p in used], "groups": drafts}


def read_drafts() -> list[dict]:
    meta = json.loads(RELEASE_META.read_text(encoding="utf-8"))
    return [json.loads((DRAFTS / f"{gid}.json").read_text(encoding="utf-8")) for gid in meta["groupOrder"]]


def stats(drafts: list[dict]) -> dict:
    by_type = Counter(g["contentType"] for g in drafts)
    q_types = Counter(q["type"] for g in drafts for q in g["questions"])
    return {
        "groups": len(drafts),
        "byContentType": dict(sorted(by_type.items())),
        "itemsPerGroup": dict(sorted(Counter(len(g["items"]) for g in drafts).items())),
        "items": sum(len(g["items"]) for g in drafts),
        "distinctSources": len({(it["source"]["sourceType"], it["source"]["sourceContentId"]) for g in drafts for it in g["items"]}),
        "examples": sum(len(g["examples"]) for g in drafts),
        "commonMistakes": sum(len(g["commonMistakes"]) for g in drafts),
        "memoryTips": sum(len(g["memoryTips"]) for g in drafts),
        "theory": sum(len(g["theory"]) for g in drafts),
        "comparisonRows": sum(len(g["comparison"]) for g in drafts),
        "questions": sum(q_types.values()),
        "questionsByType": dict(sorted(q_types.items())),
        "groupsWithAllFourTypes": sum(len({q["type"] for q in g["questions"]}) == 4 for g in drafts),
        "status": dict(Counter(g["status"] for g in drafts)),
    }


def cmd_check(args) -> int:
    from validator import BUILD, ERROR, WARNING, validate
    sources = load_sources()
    drafts = read_drafts()
    # The drafts on disk must be exactly what the authoring files generate.
    src, fresh = build_all(sources)
    stale = [d["id"] for d, f in zip(drafts, fresh) if d != f] if len(drafts) == len(fresh) else ["(số nhóm khác)"]
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    pack = assembled(drafts, sources)
    issues, _ = validate(pack, schema, BUILD, sources, None)
    expected = [i for i in issues if i["code"] == "STATUS_NOT_PUBLISHED"]
    other = [i for i in issues if i["code"] != "STATUS_NOT_PUBLISHED"]
    errors = [i for i in other if i["severity"] == ERROR]
    warnings = [i for i in other if i["severity"] == WARNING]
    for i in sorted(other, key=lambda i: (i["severity"], i["path"])):
        print(f"{i['severity']:<7} {i['code']:<26} {i['path']}{' — ' + i['detail'] if i.get('detail') else ''}")
    qa_errors, qa_notes = qa(drafts, src, sources)
    for e in qa_errors:
        print(f"QA-ERROR {e}")
    for n in qa_notes:
        print(f"QA-NOTE  {n}")
    s = stats(drafts)
    print(json.dumps(s, ensure_ascii=False, indent=2))
    print(f"\nValidator (build): {len(errors)} ERROR, {len(warnings)} WARNING (người duyệt phải xác nhận), "
          f"{len(expected)} STATUS_NOT_PUBLISHED (đúng: bản nháp {STATUS}, chưa duyệt). QA: {len(qa_errors)} lỗi, {len(qa_notes)} ghi chú. "
          f"Bản nháp khớp nguồn soạn: {'có' if not stale else 'KHÔNG ' + str(stale)}")
    if args.report:
        Path(args.report).write_text(dump({"stats": s, "issues": other, "statusNotPublished": len(expected), "qaErrors": qa_errors, "qaNotes": qa_notes}), encoding="utf-8")
    return 1 if errors or qa_errors or stale else 0


PREVIEW_PACK_ID = "contrast-n3-preview"  # same constant as the app's ContrastPreview.PACK_ID
PREVIEW_TITLE = "NỘI DUNG THỬ NGHIỆM — CHƯA KIỂM DUYỆT"


def _unreviewed_pack(approved_by: str, pack_meta: dict) -> tuple[dict, int]:
    """A pack the importer accepts, built from the drafts WITHOUT touching them: every group is set
    PUBLISHED with approvedBy = [approved_by] (a label saying it is NOT reviewed) and the validator's
    warnings listed, so it can be imported on a test device or a debug build. Never a release pack."""
    from validator import IMPORT, WARNING, validate
    sources = load_sources()
    groups = []
    for g in read_drafts():
        g = dict(g)
        g["status"] = "PUBLISHED"
        g["approval"] = {"authoredBy": "claude-ai-draft", "approvedBy": approved_by, "approvedAt": UPDATED_AT,
                         "approvedRevision": g["revision"], "aiAssisted": True, "acknowledgedWarnings": []}
        groups.append(g)
    pack = assembled(groups, sources)
    pack["pack"] = pack_meta
    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    issues, _ = validate(pack, schema, IMPORT, sources, None)
    if any(i["severity"] == "ERROR" for i in issues):
        raise SystemExit("Bản nháp còn ERROR: chạy drafts_tool.py check trước.")
    by_group = defaultdict(list)
    for i in issues:
        if i["severity"] == WARNING and i.get("target"):
            m = re.match(r"groups\[(\d+)\]", i["path"])
            by_group[int(m.group(1))].append({"code": i["code"], "target": i["target"]})
    for idx, acks in by_group.items():
        pack["groups"][idx]["approval"]["acknowledgedWarnings"] = acks
    return pack, sum(map(len, by_group.values()))


def cmd_test_pack(args) -> int:
    out = Path(args.output).resolve()
    if (REPO / "app" / "src" / "main") in out.parents:
        raise SystemExit("Gói TEST-ONLY không được ghi vào app/src/main (bản phát hành).")
    pack, acks = _unreviewed_pack("chua-duyet-test-only", {**PACK_META, "title": "Contrast Lab N3 — CHƯA DUYỆT, CHỈ DÙNG CHO TEST"})
    out.write_text(dump(pack), encoding="utf-8")
    print(f"Đã ghi gói TEST-ONLY ({len(pack['groups'])} nhóm, {acks} cảnh báo ghi nhận cho test) vào {out}")
    return 0


def cmd_debug_preview(args) -> int:
    """The DEBUG-BUILD preview pack (app/src/debug/assets only): own pack id, title and approvedBy
    labels that say it is unreviewed; the app shows it with a banner and never in a release APK."""
    out = Path(args.output).resolve()
    debug_assets = REPO / "app" / "src" / "debug" / "assets"
    if debug_assets not in out.parents:
        raise SystemExit("Gói xem trước chỉ được ghi vào app/src/debug/assets (không có trong bản release).")
    meta = {**PACK_META, "id": PREVIEW_PACK_ID, "title": PREVIEW_TITLE,
            "description": "Bản xem trước chỉ dành cho bản debug: các nhóm do AI soạn, đã qua kiểm tra kỹ thuật nhưng CHƯA được người duyệt kiểm tra. Không phát hành."}
    pack, acks = _unreviewed_pack("chua-duyet-debug-preview", meta)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(dump(pack), encoding="utf-8")
    print(f"Đã ghi gói XEM TRƯỚC DEBUG ({len(pack['groups'])} nhóm, {acks} cảnh báo chưa được người duyệt xác nhận) vào {out}")
    return 0


def cmd_catalog(args) -> int:
    """C1: read-only catalog of the N3 grammar / vocabulary / kanji rows (as the importer stores them)
    and the data limitations that matter for contrast content. Never modifies a source pack."""
    import math
    rows = {"GRAMMAR": [], "VOCABULARY": [], "KANJI": []}
    lessons = {}
    for path in sorted(SOURCES.glob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8"))
        if data.get("format") != "n3sensei-content-pack" or data["pack"]["id"] not in N3_PACKS:
            continue
        pid = data["pack"]["id"]
        lessons[pid] = len(data.get("lessons", []))
        for lesson in data.get("lessons", []):
            for key, t in (("grammar", "GRAMMAR"), ("vocabulary", "VOCABULARY"), ("kanji", "KANJI")):
                for row in lesson.get(key) or []:
                    rows[t].append({**row, "packId": pid, "lesson": lesson.get("number")})
    kana = re.compile(r"^[\u3040-\u30ffー・]+$")
    limits = {
        "GRAMMAR": {
            "missingUsage": [r["id"] for r in rows["GRAMMAR"] if not (r.get("usage") or "").strip()],
            "missingCautionNote": [r["id"] for r in rows["GRAMMAR"] if not (r.get("cautionNote") or "").strip()],
            "noExamples": [r["id"] for r in rows["GRAMMAR"] if not r.get("examples")],
            "duplicatePattern": sorted({r["pattern"] for r in rows["GRAMMAR"] if sum(x["pattern"] == r["pattern"] for x in rows["GRAMMAR"]) > 1}),
        },
        "VOCABULARY": {
            "noPartOfSpeech": len([r for r in rows["VOCABULARY"] if not r.get("partOfSpeech")]),
            "noFurigana": len([r for r in rows["VOCABULARY"] if not r.get("furigana")]),
            "noExamples": [r["id"] for r in rows["VOCABULARY"] if not r.get("examples")],
            "readingNotKana": [r["id"] for r in rows["VOCABULARY"] if not kana.match((r.get("reading") or "").replace(" ", ""))],
        },
        "KANJI": {
            "noHanViet": [r["id"] for r in rows["KANJI"] if not (r.get("hanViet") or "").strip()],
            "meaningMachineTranslated": len([r for r in rows["KANJI"] if "chưa kiểm duyệt" in (r.get("note") or "")]),
            "noExamples": [r["id"] for r in rows["KANJI"] if not r.get("examples")],
        },
    }
    counts = {t: len(v) for t, v in rows.items()}
    course_lessons = max(math.ceil(counts["KANJI"] / 24), math.ceil(counts["VOCABULARY"] / 30), math.ceil(counts["GRAMMAR"] / 7))
    out = {"items": counts, "sourcePackLessons": lessons,
           "courseLessonsN3 (CoursePlan 24/30/7, Kanji N3 only)": course_lessons, "limitations": limits}
    print(json.dumps(out, ensure_ascii=False, indent=2))
    if args.output:
        Path(args.output).write_text(dump({**out, "rows": rows}), encoding="utf-8")
    return 0


def main(argv=None) -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    cat = sub.add_parser("catalog")
    cat.add_argument("--output", help="also write the full catalog (rows) as JSON, e.g. into a scratch dir")
    sub.add_parser("generate")
    c = sub.add_parser("check")
    c.add_argument("--report")
    t = sub.add_parser("test-pack")
    t.add_argument("output")
    dp = sub.add_parser("debug-preview")
    dp.add_argument("output", nargs="?", default=str(REPO / "app" / "src" / "debug" / "assets" / "contrast-preview" / "contrast-n3-preview.json"))
    args = p.parse_args(argv)
    return {"catalog": cmd_catalog, "generate": cmd_generate, "check": cmd_check, "test-pack": cmd_test_pack, "debug-preview": cmd_debug_preview}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
