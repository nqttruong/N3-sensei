"""Build the N3 kanji content pack from the kanji part of the user's N3.pdf.

Inputs (not in the repository):
  raw.json       rows extracted from N3.pdf by extract_pdf.py (needs pdfplumber)
  kanjidic2.xml.gz  https://www.edrdg.org/kanjidic/kanjidic2.xml.gz (EDRDG, CC BY-SA 4.0)

Usage:
  python3 -I tools/content/n3_kanji/build_pack.py <raw.json> <kanjidic2.xml.gz> \
      <out pack.json> <out report.json> <out tables dir>

One kanji card = one kanji item; the word cards after it (until the next kanji card) are its
group. The group words belong to the kanji, not to the vocabulary curriculum: each word card
becomes an example of its kanji (kanji_examples + example_sentences: spelling, furigana from
the reading, gloss), with its own id. Nothing is written to the vocabulary table and nothing
points into another pack, so a word may also exist as a vocabulary item elsewhere (two
records, two roles; see docs/AUDIT_KANJI_VOCABULARY.md).
Lessons follow the PDF decks 第01–第10; decks 第11–第18 (15–27 kanji each) are re-cut in
order into lessons of 24–25 kanji. No group is split.
Bundled from the PDF: characters, Hán Việt, word spellings, readings and short glosses.
Not bundled: mnemonics and example sentences (reuse rights not verified).
"""
import csv
import hashlib
import json
import math
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
import kanjidic  # noqa: E402
from furigana import furigana  # noqa: E402
from meanings_vi import KANJIDIC2_TRANSLATED, PDF_MEANING_EDITED, PDF_MEANING_REPLACED  # noqa: E402
from pdf_cards import HAN, groups  # noqa: E402
from word_fixes import ADDED_KANJI, GLOSS_FIX, JMDICT_TRANSLATED, READING_FIX, REGROUP  # noqa: E402

raw_path, kanjidic_path, out_pack, out_report, tables_dir = sys.argv[1:6]
FIXED_DECKS = 10       # 第01–第10 kept as they are
TARGET = (24, 27)      # kanji per lesson


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s).strip()


def first_upper(s: str) -> str:
    return s[:1].upper() + s[1:] if s else s


rows = json.loads(Path(raw_path).read_text(encoding="utf-8"))["rows"]
G = groups(rows)

# Words the PDF puts under the wrong kanji move to the kanji they belong to (REGROUP); a kanji
# the PDF has no card for is added after the card whose words it takes (ADDED_KANJI).
for character, added in ADDED_KANJI.items():
    i = next(i for i, g in enumerate(G) if g["character"] == added["after"])
    G.insert(i + 1, {**{k: G[i][k] for k in ("deck", "card", "pages")}, "character": character, "hanViet": added["hanViet"],
                     "pdfMeaning": None, "issues": [f"added:words-printed-under-{added['after']}"], "words": [], "added": added})
by_char = {g["character"]: g for g in G}
regrouped = []
moves = [(g, w) for g in G for w in g["words"] if (w["deck"], w["card"]) in REGROUP]
for g, w in moves:
    target = REGROUP[(w["deck"], w["card"])]
    g["words"].remove(w)
    w["issues"] = w["issues"] + [f"regrouped-from:{g['character']}"]
    if target is not None:
        by_char[target]["words"].append(w)
    else:
        w["unlinked"] = True
        g.setdefault("unlinked", []).append(w)
    regrouped.append({"card": f"{w['deck']}#{w['card']}", "word": w["expression"], "before": g["character"], "after": target})
if len(regrouped) != len(REGROUP):
    sys.exit(f"REGROUP: {len(regrouped)} of {len(REGROUP)} cards found")
kd = kanjidic.load(kanjidic_path, {g["character"] for g in G})
kd_version = kd.pop("_version")
problems = []

# ---- lessons -------------------------------------------------------------------------
decks = []
for g in G:
    if not decks or decks[-1][0] != g["deck"]:
        decks.append((g["deck"], []))
    decks[-1][1].append(g)
lesson_groups = [gs for _, gs in decks[:FIXED_DECKS]]
rest = [g for _, gs in decks[FIXED_DECKS:] for g in gs]
n_rest = math.ceil(len(rest) / TARGET[1])
sizes = [len(rest) // n_rest + (1 if i < len(rest) % n_rest else 0) for i in range(n_rest)]
start = 0
for size in sizes:
    lesson_groups.append(rest[start:start + size])
    start += size

# ---- items ---------------------------------------------------------------------------
SRC_PDF = "n3pdf-kanji"
SRC_KANJI = "n3pdf-kanji-kanjidic2"
SRC_WORD_JMDICT = "n3pdf-kanji-jmdict"
PDF_LICENSE = ('N3.pdf: tài liệu do chủ dự án tự tổng hợp, có quyền sử dụng. Gói dùng phần Kanji: chữ Hán, âm Hán Việt, '
               'chữ viết, cách đọc và nghĩa ngắn của từ minh họa.')
EDRDG = "CC BY-SA 4.0, Electronic Dictionary Research and Development Group (https://www.edrdg.org/edrdg/licence.html)"
pack = {
    "format": "n3sensei-content-pack", "formatVersion": 1,
    "pack": {"id": "n3-kanji", "title": "Kanji N3 (theo N3.pdf)", "version": 2, "jlptLevel": "N3", "defaultSourceId": SRC_PDF},
    "sources": [
        {"id": SRC_PDF, "name": "N3.pdf · phần Kanji (tài liệu của chủ dự án)", "url": None,
         "license": PDF_LICENSE, "attribution": None, "licenseVerified": True},
        {"id": SRC_KANJI, "name": "Chữ Hán, âm Hán Việt: N3.pdf · âm On/Kun, số nét, nghĩa gốc tiếng Anh: KANJIDIC2 (EDRDG)",
         "url": "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project",
         "license": f"Phần N3.pdf: tài liệu của chủ dự án. Phần KANJIDIC2: {EDRDG}; thay đổi: nghĩa tiếng Việt do N3 Sensei dịch từ nghĩa tiếng Anh (chưa kiểm duyệt), chỉ dùng âm On/Kun, số nét, nghĩa.",
         "attribution": "KANJIDIC2 © Electronic Dictionary Research and Development Group", "licenseVerified": True},
        {"id": SRC_WORD_JMDICT, "name": "Từ, cách đọc: N3.pdf · nghĩa: dịch từ JMdict (EDRDG)",
         "url": "https://www.edrdg.org/wiki/index.php/JMdict-EDICT_Dictionary_Project",
         "license": f"Phần N3.pdf: tài liệu của chủ dự án. Phần JMdict: {EDRDG}; thay đổi: nghĩa tiếng Việt do N3 Sensei dịch từ nghĩa tiếng Anh (chưa kiểm duyệt).",
         "attribution": "JMdict © Electronic Dictionary Research and Development Group", "licenseVerified": True},
    ],
    "examples": [], "lessons": [],
}

kanji_rows, word_rows = [], []
stats = {"words": 0, "meaning_jmdict": 0, "furigana": 0}
for number, lgroups in enumerate(lesson_groups, 1):
    lesson_id = f"n3k-l{number:02d}"
    decks_in = sorted({g["deck"] for g in lgroups})
    lesson = {"id": lesson_id, "number": number,
              "title": f"Kanji N3 · bài {number}: {lgroups[0]['character']}–{lgroups[-1]['character']}",
              "description": f"{len(lgroups)} chữ Hán theo thứ tự N3.pdf (nhóm {', '.join(decks_in)}) và từ minh họa của từng chữ.",
              "vocabulary": [], "kanji": [], "grammar": []}
    for g in lgroups:
        c = g["character"]
        k = kd.get(c)
        if k is None:
            problems.append(f"{c}: not in KANJIDIC2")
            continue
        if c in PDF_MEANING_REPLACED:
            meaning, status = PDF_MEANING_REPLACED[c], "kanjidic2-translated (PDF meaning was a compound's)"
        elif c in PDF_MEANING_EDITED:
            meaning, status = PDF_MEANING_EDITED[c], "pdf (cleaned)"
        elif g["pdfMeaning"]:
            meaning, status = g["pdfMeaning"], "pdf"
        elif "added" in g:
            meaning, status = g["added"]["meaning"], "kanjidic2-translated (kanji added, no PDF card)"
        elif c in KANJIDIC2_TRANSLATED:
            meaning, status = KANJIDIC2_TRANSLATED[c], "kanjidic2-translated"
        else:
            meaning, status = None, "missing"
            problems.append(f"{c}: no meaning")
        meaning = first_upper(nfc(meaning)) if meaning else None
        from_kd = status.startswith("kanjidic2")
        note = (f"Nghĩa dịch từ KANJIDIC2 (tiếng Anh: {'; '.join(k['meanings'][:6])}), chưa kiểm duyệt." if from_kd
                else "Nghĩa theo N3.pdf.")
        example_ids = []
        for w in g["words"] + g.get("unlinked", []):
            stats["words"] += 1
            expression, reading = nfc(w["expression"]), nfc(w["reading"]).replace(" ", "").replace("\u3000", "")
            if (w["deck"], w["card"]) in READING_FIX:
                flags_reading = [f"reading-fixed:{reading}"]
                reading = READING_FIX[(w["deck"], w["card"])]
            else:
                flags_reading = []
            meanings = GLOSS_FIX.get((w["deck"], w["card"]), w["meanings"])
            gloss = "; ".join(first_upper(nfc(m)) for m in meanings)
            flags = list(w["issues"]) + flags_reading
            if c not in expression and not w.get("unlinked"):
                flags.append("word-without-its-kanji")
            if (w["deck"], w["card"]) in GLOSS_FIX:
                flags.append("gloss-fixed-by-hand")
            src = SRC_PDF
            if not gloss:
                if (expression, reading) not in JMDICT_TRANSLATED:
                    problems.append(f"{expression} ({reading}): no meaning")
                    continue
                seq, gloss = JMDICT_TRANSLATED[(expression, reading)]
                gloss, src = first_upper(gloss), SRC_WORD_JMDICT
                flags.append(f"meaning-jmdict-{seq}")
                stats["meaning_jmdict"] += 1
            ruby = furigana(expression, reading)
            if ruby is None and re.search(r"[\u4e00-\u9fff]", expression):
                # The PDF reading does not spell the word (お仕事 read しごと): no ruby, kept for review.
                flags.append("reading-does-not-match-spelling")
            stats["furigana"] += ruby is not None
            # One record per word card (the PDF sometimes repeats a word under two kanji).
            eid = f"n3k-x-{w['deck'][1:3]}-{w['card']:03d}"
            pack["examples"].append({
                "id": eid, "japanese": expression, "furigana": ruby, "translationVi": gloss, "sourceId": src,
                "origin": {"deck": w["deck"], "card": w["card"], "pages": w["pages"], "kanji": c,
                           "reading": reading, "hanViet": w["hanViet"], "flags": flags},
            })
            if not w.get("unlinked"):
                example_ids.append(eid)
            else:
                # Same word as another card of the kanji it belongs to: kept, not shown twice.
                same = next(e["id"] for e in pack["examples"] if e["japanese"] == expression and e["id"] != eid)
                pack["examples"][-1]["origin"]["duplicateOf"] = same
            word_rows.append([lesson_id, c, w["deck"], w["card"], ",".join(map(str, w["pages"])), expression, reading,
                              gloss, w["hanViet"] or "", eid, " ".join(flags)])
        kanji_flags = list(g["issues"]) + (["meaning-replaced"] if c in PDF_MEANING_REPLACED else [])
        lesson["kanji"].append({
            "id": f"n3k-k{ord(c):05x}", "character": c, "onReadings": k["on"], "kunReadings": k["kun"],
            "hanViet": g["hanViet"], "meaningVi": meaning, "strokeCount": k["strokes"], "note": note,
            "examples": example_ids, "sourceId": SRC_KANJI,
            "origin": {"deck": g["deck"], "card": g["card"], "pages": g["pages"],
                       "meaning": {"status": status, "pdf": g["pdfMeaning"], "kanjidic2": k["meanings"]}},
        })
        kanji_rows.append([lesson_id, c, g["deck"], g["card"], ",".join(map(str, g["pages"])), g["hanViet"] or "",
                           meaning or "", status, g["pdfMeaning"] or "", "; ".join(k["meanings"]),
                           "、".join(k["on"]), "、".join(k["kun"]), str(k["strokes"]), len(g["words"]), " ".join(kanji_flags)])
    pack["lessons"].append(lesson)

if problems:
    print("PROBLEMS:", *problems, sep="\n  ")
    sys.exit(1)

Path(out_pack).write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
tables = Path(tables_dir)
tables.mkdir(parents=True, exist_ok=True)
with open(tables / "n3-kanji-meanings.tsv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["lesson", "kanji", "pdf_deck", "pdf_card", "pdf_pages", "han_viet", "meaning_vi", "meaning_status",
                "pdf_meaning", "kanjidic2_en", "on", "kun", "strokes", "words", "flags"])
    w.writerows(kanji_rows)
with open(tables / "n3-kanji-words.tsv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["lesson", "kanji", "pdf_deck", "pdf_card", "pdf_pages", "expression", "reading", "meaning_vi",
                "han_viet", "example_id", "flags"])
    w.writerows(word_rows)
lessons = [{"id": l["id"], "kanji": len(l["kanji"]), "words": sum(len(k["examples"]) for k in l["kanji"]),
            "characters": "".join(k["character"] for k in l["kanji"]), "title": l["title"]} for l in pack["lessons"]]
report = {
    "kanjidic2": {"version": kd_version[0], "date": kd_version[1]},
    "regrouped": regrouped,
    "kanji": len(kanji_rows),
    "kanji_meaning_status": {s: sum(1 for r in kanji_rows if r[7] == s) for s in sorted({r[7] for r in kanji_rows})},
    **stats,
    "lessons": lessons,
}
Path(out_report).write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
print(json.dumps({k: v for k, v in report.items() if k != "lessons"}, ensure_ascii=False))
for l in lessons:
    print(l["id"], l["kanji"], l["words"], l["characters"])
