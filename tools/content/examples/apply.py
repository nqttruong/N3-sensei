"""Adds the examples of tools/content/examples/data/*.tsv to the bundled content packs.

    python3 -I tools/content/examples/apply.py            # writes app/src/main/assets/content/*.json
    python3 -I tools/content/examples/apply.py --check    # only checks, writes nothing
    python3 -I tools/content/examples/apply.py --status   # items still without examples

Rules (docs/EXAMPLES.md):
- only items with no example of their own get examples; existing examples, fields and ids are
  never changed (the script removes its own earlier additions first, so it can be rerun, and
  stops if anything else would change);
- new examples have ids `<item id>-ex<n>` (kanji words `<item id>-w<n>`) and their own source
  `<pack id>-examples` (self-written, licenseVerified false);
- the pack version is the version before the additions + REVISION; raise REVISION whenever the
  example data changes after a release, so installed apps update.
"""
import copy
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    ASSETS, DATA, KANJI, NEEDS_REVIEW, READING_FIXES, base_text, load_packs, read_tsv, target_spans,
)

REVISION = 1
BASE_VERSION_KEY = "examplesBaseVersion"
PACK_OF_FILE = {"vocabulary": "mimikara-n3-vocab", "grammar": "n3-grammar", "kanji": "n3-kanji"}
SOURCE = {
    "name": "N3 Sensei tự soạn · câu ví dụ bổ sung",
    "url": None,
    "license": "Câu ví dụ và bản dịch do N3 Sensei tự soạn (11/2026) theo nghĩa và cách dùng có sẵn trong app; chưa qua kiểm duyệt chuyên môn.",
    "attribution": None,
    "licenseVerified": False,
}


def source_id(pack_id: str) -> str:
    return f"{pack_id}-examples"


def items_of(pack: dict):
    for lesson in pack["lessons"]:
        for kind in ("vocabulary", "kanji", "grammar"):
            for item in lesson.get(kind, []):
                yield kind, item


def strip(pack: dict) -> dict:
    """The pack without this script's additions (the pack as it was before them)."""
    pack = copy.deepcopy(pack)
    sid = source_id(pack["pack"]["id"])
    ours = {e["id"] for e in pack.get("examples", []) if e.get("sourceId") == sid}
    pack["examples"] = [e for e in pack.get("examples", []) if e["id"] not in ours]
    pack["sources"] = [s for s in pack["sources"] if s["id"] != sid]
    for _, item in items_of(pack):
        if "examples" in item:
            item["examples"] = [x for x in item["examples"] if x not in ours]
    if BASE_VERSION_KEY in pack["pack"]:
        pack["pack"]["version"] = pack["pack"].pop(BASE_VERSION_KEY)
    return pack


def load_rows() -> dict[str, list[tuple[str, dict]]]:
    """Rows of every data file, by pack name ('vocabulary-*.tsv' → vocabulary pack, …)."""
    out = defaultdict(list)
    for path in sorted(DATA.glob("*.tsv")):
        kind = path.stem.split("-")[0]
        for row in read_tsv(path):
            if "comment" not in row:
                out[PACK_OF_FILE[kind]].append((path.name, row))
    return out


def add(base: dict, rows: list[tuple[str, dict]], problems: list[str]) -> tuple[dict, Counter]:
    pack = copy.deepcopy(base)
    pid = pack["pack"]["id"]
    sid = source_id(pid)
    by_id = {item["id"]: (kind, item) for kind, item in items_of(pack)}
    taken = {e["id"] for e in pack["examples"]}
    sentences = Counter(e["japanese"] for e in pack["examples"])
    per_item = defaultdict(list)
    for name, row in rows:
        per_item[row["item_id"]].append((name, row))
    added = Counter()
    for item_id, item_rows in per_item.items():
        where = f"{item_rows[0][0]}: {item_id}"
        if item_id not in by_id:
            problems.append(f"{where}: no such item in {pid}")
            continue
        kind, item = by_id[item_id]
        if item.get("examples"):
            problems.append(f"{where}: already has examples of its own; not touched")
            continue
        if item_id in NEEDS_REVIEW:
            problems.append(f"{where}: marked for review ({NEEDS_REVIEW[item_id]})")
            continue
        refs = []
        for n, (_, row) in enumerate(item_rows, start=1):
            japanese, furigana, vi = row["japanese"], row["furigana"] or None, row["translation_vi"]
            if not japanese or not vi:
                problems.append(f"{where}: empty sentence or translation")
                continue
            if any(c in japanese + vi for c in "{}|\t"):
                problems.append(f"{where}: reserved character in {japanese!r}")
            if furigana is not None and base_text(furigana) != japanese:
                problems.append(f"{where}: furigana does not match the sentence: {furigana}")
            if furigana is None and KANJI.search(japanese):
                problems.append(f"{where}: no furigana (run annotate.py): {japanese}")
            if kind == "vocabulary":
                item_info = {"id": item_id, "forms": item["expression"].split("・"), "reading": item["reading"]}
                if not target_spans(item_info, japanese, READING_FIXES):
                    problems.append(f"{where}: the sentence does not contain {item['expression']}: {japanese}")
            if kind == "kanji" and item["character"] not in japanese:
                problems.append(f"{where}: the word does not contain {item['character']}: {japanese}")
            if kind in ("vocabulary", "grammar") and not re.search(r"[。？！」]$", japanese):
                problems.append(f"{where}: the sentence does not end like one: {japanese}")
            # A word may illustrate several kanji (the pack already has such words): kanji words
            # only need to differ within their kanji; sentences must be unique in the pack.
            key = (item_id, japanese) if kind == "kanji" else japanese
            sentences[key] += 1
            if sentences[key] > 1:
                problems.append(f"{where}: duplicate example {japanese}")
            eid = f"{item_id}-{'w' if kind == 'kanji' else 'ex'}{n}"
            if eid in taken:
                problems.append(f"{where}: example id {eid} already exists")
                continue
            taken.add(eid)
            pack["examples"].append({"id": eid, "japanese": japanese, "furigana": furigana, "translationVi": vi, "sourceId": sid})
            refs.append(eid)
            added[kind] += 1
        item["examples"] = refs
    if added:
        pack["sources"].append({"id": sid, **SOURCE})
        pack["pack"][BASE_VERSION_KEY] = base["pack"]["version"]
        pack["pack"]["version"] = base["pack"]["version"] + REVISION
    return pack, added


def status() -> None:
    covered = {row["item_id"] for rows in load_rows().values() for _, row in rows}
    for name, pack in sorted(load_packs().items()):
        base = strip(pack)
        for kind, item in items_of(base):
            if not item.get("examples") and item["id"] not in covered:
                label = item.get("expression") or item.get("character") or item.get("pattern")
                note = f"  (cần kiểm tra: {NEEDS_REVIEW[item['id']]})" if item["id"] in NEEDS_REVIEW else ""
                print(f"{name}\t{kind}\t{item['id']}\t{label}{note}")


def main(argv: list[str]) -> int:
    if "--status" in argv:
        status()
        return 0
    check_only = "--check" in argv
    packs = load_packs()
    rows = load_rows()
    problems: list[str] = []
    out = {}
    for name, pack in packs.items():
        pid = pack["pack"]["id"]
        base = strip(pack)
        new, added = add(base, rows.get(pid, []), problems)
        # Nothing but the additions may differ from the pack before them.
        if strip(new) != base:
            problems.append(f"{pid}: the result changes existing content")
        out[name] = (pack, new, added)
    for p in problems:
        print("PROBLEM", p)
    for name, (old, new, added) in sorted(out.items()):
        total = Counter(kind for kind, item in items_of(new) if item.get("examples"))
        print(f"{name}: v{old['pack']['version']} -> v{new['pack']['version']}, new examples {dict(added)}, items with examples {dict(total)}")
    if problems:
        return 1
    if not check_only:
        for name, (old, new, _) in out.items():
            if new != old:
                (ASSETS / f"{name}.json").write_text(json.dumps(new, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
                print(f"wrote {name}.json")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
