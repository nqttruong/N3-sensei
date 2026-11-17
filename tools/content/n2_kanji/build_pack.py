"""Build the N2 kanji content pack straight from the Anki package "N2::Kanji N2".

Inputs (not in the repository):
  N2__Kanji N2.apkg   the user's Anki deck (read only, never changed)
  kanjidic2.xml.gz    https://www.edrdg.org/kanjidic/kanjidic2.xml.gz (EDRDG, CC BY-SA 4.0)
  n3-kanji.json       the bundled N3 kanji pack (app/src/main/assets/content), to leave its kanji out

Usage:
  python3 -I tools/content/n2_kanji/build_pack.py [--strict] "N2__Kanji N2.apkg" kanjidic2.xml.gz \
      app/src/main/assets/content/n3-kanji.json <out pack.json> <out report.json> <out tables dir>

A note that cannot be used (a field missing, no reading, a kanji KANJIDIC2 does not know…) is
skipped and listed in the report ("skipped"); with --strict any skipped note fails the build.

The deck: one note type "Cơ bản" (Mặt trước / Mặt sau), one card per note, decks 第01–第18
(one lesson each, in the deck's own order). A kanji card (one kanji on the front, its Hán Việt
in capitals on the back, then its meaning and sometimes a "=> …" mnemonic) is followed by the
word cards of that kanji (front: the word; back: Hán Việt, kana reading, meaning).
Mapped like the N3 pack (tools/content/n3_kanji): a kanji card is a kanji item; each of its word
cards is an example of it (example_sentences + kanji_examples), never a vocabulary item.
On/Kun readings and stroke counts come from KANJIDIC2 (the deck has none).
Left out: kanji already in the N3 pack (one record per kanji, so their progress stays one),
illustrations (the app shows no images for kanji). A repeated kanji card keeps the first card,
the later one's words join it. Ids are stable: kanji n2k-k<code point>, words n2k-x-<note id>.
"""
import csv
import json
import re
import sys
import unicodedata
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE.parent / "n3_kanji"))
import kanjidic  # noqa: E402
from apkg import Apkg, field_lines, media_refs  # noqa: E402
from fixes import (KANJI_CARD, KANJI_FROM_WORD_CARD, KANJI_MEANING_FROM_IMAGE, TYPO,  # noqa: E402
                   TYPO_MEANING_PREFIX, WORD_FROM_IMAGE)
from furigana import furigana  # noqa: E402

args = sys.argv[1:]
strict = "--strict" in args
args = [a for a in args if a != "--strict"]
apkg_path, kanjidic_path, n3_pack_path, out_pack, out_report, tables_dir = args[:6]

PACK_ID = "n2-kanji"
PACK_VERSION = 1
DECK_PREFIX = "N2::Kanji N2::"
SRC_DECK = "n2apkg-kanji"
SRC_KANJI = "n2apkg-kanji-kanjidic2"
EDRDG = "CC BY-SA 4.0, Electronic Dictionary Research and Development Group (https://www.edrdg.org/edrdg/licence.html)"
DECK_LICENSE = ("Bộ thẻ Anki “N2::Kanji N2” do chủ dự án cung cấp để học trong ứng dụng; quyền dùng lại của nội dung "
                "bộ thẻ chưa được xác minh. Gói dùng: chữ Hán, âm Hán Việt, nghĩa, câu ghi nhớ, từ, cách đọc và nghĩa của từ.")

HAN = re.compile(r"^[一-鿿]$")
HAS_KANJI = re.compile(r"[一-鿿々]")
KANA_LINE = re.compile(r"^[぀-ヿー・〜～/／\s（）()、:：０-９0-9]+$")


def nfc(s: str) -> str:
    return unicodedata.normalize("NFC", s).strip()


def first_upper(s: str) -> str:
    return s[:1].upper() + s[1:] if s else s


def is_kana_line(line: str) -> bool:
    return bool(KANA_LINE.match(line)) and bool(re.search(r"[぀-ヿ]", line))


def strip_parens(s: str) -> str:
    s = s.strip()
    return s[1:-1].strip() if len(s) > 2 and s[0] in "(（" and s[-1] in ")）" else s


def tidy(s: str) -> str:
    """"Cạm bẫy ,Cái bẫy" -> "Cạm bẫy, Cái bẫy"."""
    return re.sub(r"\s*,\s*", ", ", s).strip()


def is_capitals(s: str) -> bool:
    return s.upper() == s and s.lower() != s


# ---- read the deck -------------------------------------------------------------------
deck = Apkg(apkg_path)
cards = deck.cards()
media_in_deck = len(deck.media_names)
deck_format = deck.format
n3_chars = {k["character"] for l in json.loads(Path(n3_pack_path).read_text(encoding="utf-8"))["lessons"] for k in l["kanji"]}

problems, notes_out = [], []
lessons: list[dict] = []          # {"deck", "groups": [group]}
by_char: dict[str, dict] = {}     # first kanji card of each character
skipped_media = []
fixed = []
current = None
for c in cards:
    if not c["deck"].startswith(DECK_PREFIX):
        problems.append(f"card {c['card_id']} outside {DECK_PREFIX}: {c['deck']}")
        continue
    if len(c["values"]) < 2:
        problems.append(f"note {c['note_id']}: fewer than 2 fields")
        continue
    deck_name = c["deck"][len(DECK_PREFIX):]
    if not lessons or lessons[-1]["deck"] != deck_name:
        lessons.append({"deck": deck_name, "groups": []})
        current = None
    front = nfc("".join(field_lines(c["values"][0])))
    back = [nfc(l) for l in field_lines(c["values"][1])]
    for ref in media_refs(c["values"][0] + c["values"][1]):
        skipped_media.append({"note": c["note_id"], "file": ref, "inDeck": deck.has_media(ref)})
    text = [l for l in back if l not in ("[IMG]", "[SOUND]")]
    single = bool(HAN.match(front))
    kanji_card = single and (current is None or front != current["character"]) and (
        (text and is_capitals(text[0])) or front in KANJI_CARD or front in KANJI_FROM_WORD_CARD)

    if kanji_card:
        han_viet = text[0].upper() if text else None
        rest = [l for l in text[1:] if not is_kana_line(l)]
        kana = [l for l in text[1:] if is_kana_line(l)]
        mnemonic = [l.lstrip("=>").strip() for l in rest if l.startswith("=>")]
        plain = [l for l in rest if not l.startswith("=>")]
        if c["note_id"] in KANJI_MEANING_FROM_IMAGE:
            meaning, status = KANJI_MEANING_FROM_IMAGE[c["note_id"]], "image"
        elif plain:
            meaning, status = strip_parens(plain[0]), "card"
            mnemonic = plain[1:] + mnemonic
        else:
            meaning, status = None, "missing"
        group = {"deck": deck_name, "note": c["note_id"], "character": front, "hanViet": han_viet,
                 "meaning": first_upper(tidy(meaning)) if meaning else None, "meaningStatus": status,
                 "mnemonic": mnemonic, "cardKana": kana, "words": [], "mergedFrom": []}
        if front in KANJI_FROM_WORD_CARD:
            # The card is both: the kanji, and its word (瓦 かわら).
            group["words"].append({"note": c["note_id"], "front": front, "back": text, "deck": deck_name})
        if front in by_char:
            first = by_char[front]
            first["mergedFrom"].append(c["note_id"])
            current = first
            continue
        by_char[front] = group
        lessons[-1]["groups"].append(group)
        current = group
    else:
        if current is None:
            problems.append(f"note {c['note_id']} ({front}): word card before any kanji card in {deck_name}")
            continue
        current["words"].append({"note": c["note_id"], "front": front, "back": text, "deck": deck_name})

deck.close()


PAREN = re.compile(r"^(.+?)\s*[（(](.+)[）)]$")


def ruby_of(expression: str, reading: str, flags: list) -> str | None:
    """Furigana of a word; "抱いた (抱く)" read "だいた(だく)" part by part."""
    e, r = PAREN.match(expression), PAREN.match(reading)
    if e and r:
        return f"{ruby_of(e[1], r[1], flags)} ({ruby_of(e[2], r[2], flags)})"
    ruby = furigana(expression, reading)
    if ruby is None and HAS_KANJI.search(expression):
        ruby = f"{{{expression}|{reading}}}"  # reading over the whole word: it does not split by kanji
        flags.append("ruby-whole-word")
    return ruby or expression


def parse_word(w: dict) -> dict:
    """Expression, reading, meaning and Hán Việt of a word card."""
    flags = []
    expression = w["front"]
    lines = w["back"]
    if w["note"] in WORD_FROM_IMAGE:
        reading, meaning = WORD_FROM_IMAGE[w["note"]]
        han_viet = " ".join(l for l in lines if not is_kana_line(l)) or None
        flags.append("from-image")
        extra = []
    else:
        k = next((i for i, l in enumerate(lines) if (expression, l) in TYPO or is_kana_line(l)), None)
        if k is None:
            return {"expression": expression, "problem": f"no reading: {lines}"}
        han_viet = " ".join(lines[:k]) or None
        reading = lines[k].rstrip(":：").strip()
        after = lines[k + 1:]
        meaning = after[0] if after else None
        extra = after[1:]
        if meaning is None and han_viet:
            # 命令 "Mệnh Lệnh / めいれい": the Hán Việt reading is the Vietnamese word for it.
            meaning = han_viet[:1] + han_viet[1:].lower()
            flags.append("meaning-is-han-viet")
        if meaning is None:
            return {"expression": expression, "problem": f"no meaning: {lines}"}
    prefix = TYPO_MEANING_PREFIX.get((expression, reading), "")
    if (expression, reading) in TYPO:
        new_expression, new_reading, why = TYPO[(expression, reading)]
        fixed.append({"note": w["note"], "before": [expression, reading], "after": [new_expression, new_reading], "why": why})
        expression, reading = new_expression, new_reading
        flags.append("fixed")
    reading = reading.replace(" ", "").replace("　", "")
    meaning = prefix + first_upper(tidy(strip_parens(meaning) if meaning.startswith(("(", "（")) else meaning))
    exprs, reads = [p.strip() for p in expression.split("/")], [p.strip() for p in reading.split("/")]
    if len(exprs) > 1 and len(exprs) == len(reads):
        # Two words on one card: "講座 / 受講" read "こうざ / じゅこう".
        ruby = " / ".join(ruby_of(e, r, flags) for e, r in zip(exprs, reads))
    elif len(exprs) == 1 and len(reads) > 1:
        # One word, two readings ("被る": こうむる / かぶる): ruby of the first, both in the meaning.
        ruby = ruby_of(expression, reads[0], flags)
        meaning = f"{meaning} (đọc: {' / '.join(reads)})"
        flags.append("several-readings")
    else:
        ruby = ruby_of(expression, reading, flags)
    return {"expression": expression, "reading": reading, "meaning": meaning, "hanViet": han_viet,
            "furigana": ruby, "extra": extra, "flags": flags}


# ---- pack ----------------------------------------------------------------------------
kd = kanjidic.load(kanjidic_path, set(by_char))
kd_version = kd.pop("_version")
pack = {
    "format": "n3sensei-content-pack", "formatVersion": 1,
    "pack": {"id": PACK_ID, "title": "Kanji N2 (theo bộ thẻ Anki Kanji N2)", "version": PACK_VERSION,
             "jlptLevel": "N2", "defaultSourceId": SRC_DECK},
    "sources": [
        {"id": SRC_DECK, "name": "Bộ thẻ Anki “N2::Kanji N2” (chủ dự án cung cấp)", "url": None,
         "license": DECK_LICENSE, "attribution": None, "licenseVerified": False},
        {"id": SRC_KANJI, "name": "Chữ Hán, âm Hán Việt, nghĩa: bộ thẻ Kanji N2 · âm On/Kun, số nét: KANJIDIC2 (EDRDG)",
         "url": "https://www.edrdg.org/wiki/index.php/KANJIDIC_Project",
         "license": f"Phần bộ thẻ: {DECK_LICENSE} Phần KANJIDIC2: {EDRDG}; chỉ dùng âm On/Kun và số nét.",
         "attribution": "KANJIDIC2 © Electronic Dictionary Research and Development Group", "licenseVerified": False},
    ],
    "examples": [], "lessons": [],
}

kanji_rows, word_rows, left_out = [], [], []
stats = {"kanjiCards": 0, "wordCards": 0, "words": 0, "wordsFromImage": 0, "kanjiMeaningFromImage": 0,
         "rubyWholeWord": 0, "wordWithoutItsKanji": 0}
for number, lesson_in in enumerate(lessons, 1):
    lesson_id = f"n2k-l{number:02d}"
    groups = [g for g in lesson_in["groups"]]
    lesson = {"id": lesson_id, "number": number, "title": "", "description": "",
              "vocabulary": [], "kanji": [], "grammar": []}
    for g in groups:
        c = g["character"]
        stats["kanjiCards"] += 1 + len(g["mergedFrom"])
        if c in n3_chars:
            left_out.append({"kanji": c, "deck": g["deck"], "note": g["note"], "why": "already in the N3 pack",
                             "words": [w["front"] for w in g["words"]]})
            stats["wordCards"] += sum(w["note"] != g["note"] for w in g["words"])
            continue
        k = kd.get(c)
        if k is None:
            problems.append(f"{c}: not in KANJIDIC2")
            continue
        if g["meaning"] is None:
            problems.append(f"{c} (note {g['note']}): no meaning")
            continue
        stats["kanjiMeaningFromImage"] += g["meaningStatus"] == "image"
        example_ids = []
        for w in g["words"]:
            stats["wordCards"] += w["note"] != g["note"]  # 瓦: one card, both the kanji and its word
            p = parse_word(w)
            if "problem" in p:
                problems.append(f"note {w['note']} ({w['front']}): {p['problem']}")
                continue
            flags = list(p["flags"])
            if c not in p["expression"]:
                flags.append("word-without-its-kanji")
                stats["wordWithoutItsKanji"] += 1
            if "from-image" in flags:
                stats["wordsFromImage"] += 1
            if "ruby-whole-word" in flags:
                stats["rubyWholeWord"] += 1
            eid = f"n2k-x-{w['note']}"
            translation = p["meaning"] + ("".join(f" {x}" for x in p["extra"]) if p["extra"] else "")
            pack["examples"].append({
                "id": eid, "japanese": p["expression"], "furigana": p["furigana"], "translationVi": translation,
                "sourceId": SRC_DECK,
                "origin": {"deck": w["deck"], "note": w["note"], "kanji": c, "reading": p["reading"],
                           "hanViet": p["hanViet"], "flags": flags},
            })
            example_ids.append(eid)
            stats["words"] += 1
            word_rows.append([lesson_id, c, w["deck"], w["note"], p["expression"], p["reading"], translation,
                              p["hanViet"] or "", eid, " ".join(flags)])
        source_note = ("Nghĩa theo ảnh minh họa của bộ thẻ Kanji N2." if g["meaningStatus"] == "image"
                       else "Nghĩa theo bộ thẻ Kanji N2.")
        note = source_note + (f" Ghi nhớ: {' '.join(g['mnemonic'])}" if g["mnemonic"] else "")
        lesson["kanji"].append({
            "id": f"n2k-k{ord(c):05x}", "character": c, "onReadings": k["on"], "kunReadings": k["kun"],
            "hanViet": g["hanViet"], "meaningVi": g["meaning"], "strokeCount": k["strokes"], "note": note,
            "examples": example_ids, "sourceId": SRC_KANJI,
            "origin": {"deck": g["deck"], "note": g["note"], "mergedNotes": g["mergedFrom"],
                       "meaning": g["meaningStatus"], "kanjidic2": k["meanings"], "cardKana": g["cardKana"]},
        })
        kanji_rows.append([lesson_id, c, g["deck"], g["note"], g["hanViet"] or "", g["meaning"], g["meaningStatus"],
                           " ".join(g["mnemonic"]), "、".join(k["on"]), "、".join(k["kun"]), str(k["strokes"]),
                           len(example_ids), " ".join(map(str, g["mergedFrom"]))])
    chars = [k["character"] for k in lesson["kanji"]]
    if not chars:
        problems.append(f"deck {lesson_in['deck']}: no usable kanji card, lesson left out")
        continue
    lesson["title"] = f"Kanji N2 · bài {number}: {chars[0]}–{chars[-1]}"
    lesson["description"] = (f"{len(chars)} chữ Hán theo thứ tự bộ thẻ Kanji N2 (nhóm {lesson_in['deck']}) "
                             f"và từ ví dụ của từng chữ.")
    pack["lessons"].append(lesson)

if problems:
    print("SKIPPED:", *problems, sep="\n  ")
    if strict:
        sys.exit(1)
elif stats["kanjiCards"] + stats["wordCards"] != len(cards):
    sys.exit(f"cards: {stats['kanjiCards']} kanji + {stats['wordCards']} words != {len(cards)}")

Path(out_pack).write_text(json.dumps(pack, ensure_ascii=False, indent=1), encoding="utf-8")
tables = Path(tables_dir)
tables.mkdir(parents=True, exist_ok=True)
with open(tables / "n2-kanji.tsv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["lesson", "kanji", "deck", "note", "han_viet", "meaning_vi", "meaning_from", "mnemonic", "on", "kun",
                "strokes", "words", "merged_notes"])
    w.writerows(kanji_rows)
with open(tables / "n2-kanji-words.tsv", "w", encoding="utf-8", newline="") as f:
    w = csv.writer(f, delimiter="\t")
    w.writerow(["lesson", "kanji", "deck", "note", "expression", "reading", "meaning_vi", "han_viet", "example_id", "flags"])
    w.writerows(word_rows)
report = {
    "apkg": {"format": deck_format, "cards": len(cards), "mediaFiles": media_in_deck},
    "kanjidic2": {"version": kd_version[0], "date": kd_version[1]},
    **stats,
    "kanji": len(kanji_rows),
    "lessons": [{"id": l["id"], "kanji": len(l["kanji"]), "words": sum(len(k["examples"]) for k in l["kanji"]),
                 "characters": "".join(k["character"] for k in l["kanji"])} for l in pack["lessons"]],
    "leftOut": left_out,
    "merged": [{"kanji": g["character"], "kept": g["note"], "merged": g["mergedFrom"]} for g in by_char.values() if g["mergedFrom"]],
    "fixed": fixed,
    "skipped": problems,
    "mediaNotImported": {"references": len(skipped_media), "files": len({m["file"] for m in skipped_media}),
                         "missingFromDeck": [m for m in skipped_media if not m["inDeck"]]},
}
Path(out_report).write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
print(json.dumps({k: v for k, v in report.items() if k not in ("lessons", "leftOut")}, ensure_ascii=False))
print("left out:", json.dumps(left_out, ensure_ascii=False))
for l in report["lessons"]:
    print(l["id"], l["kanji"], l["words"], l["characters"])
