"""Turn the kanji-section rows extracted from N3.pdf into kanji groups.

Input: the JSON written by extract_pdf.py (rows of the 3-column card table).
Only facts are kept for the app: the character, Hán Việt, and for each word its
spelling, kana reading and short Vietnamese gloss. Mnemonics and example sentences
are recorded for traceability only (see build_pack.py: they are not bundled).
"""
import re
import unicodedata

# Characters the PDF text layer maps to the radical blocks instead of the unified ideograph.
# NFKC fixes the Kangxi Radicals block; these CJK Radicals Supplement forms are used as
# whole characters in words and need an explicit mapping. Radical shapes such as ⺡ (氵)
# or ⺅ (亻) inside mnemonics are real radicals and are left alone.
SUPPLEMENT = {"⻑": "長", "⺠": "民", "⻭": "歯", "⻨": "麦", "⻄": "西", "⻘": "青", "⻝": "食", "⻤": "鬼", "⻩": "黄", "⻣": "骨", "⻆": "角"}
# Traditional forms in the PDF where Japanese uses another code point.
JAPANESE_FORM = {"戶": "戸"}
# Kanji cards whose character is not the one the card teaches (its words and mnemonic
# use the Japanese form): 贊 is the traditional form of 賛, 劳 the simplified form of 労.
KANJI_CARD_FIX = {"贊": "賛", "劳": "労"}

HAN = re.compile(r"[一-鿿々]")
KANA_LINE = re.compile(r"^[぀-ヿー・〜～（）()\s]+$")
VI_UPPER = re.compile(r"^[A-ZĐÀ-Ỹ][A-ZĐÀ-Ỹ ,./\-–]*$")
JA_CHARS = re.compile(r"[぀-ヿ一-鿿]")


def norm(text: str) -> str:
    text = "".join(SUPPLEMENT.get(c, c) for c in text)
    text = unicodedata.normalize("NFKC", text)
    return "".join(JAPANESE_FORM.get(c, c) for c in text)


def lines_of(text: str) -> list[str]:
    return [l.strip() for l in norm(text).split("\n") if l.strip()]


def is_kana_line(line: str) -> bool:
    return bool(KANA_LINE.match(line))


def classify(row) -> str:
    front = norm(row["front"]).replace("\n", "").strip()
    lines = lines_of(row["back"])
    if len(front) == 1 and HAN.match(front):
        if not any(is_kana_line(l.rstrip(" -")) for l in lines[:3]):
            return "kanji"
        # 狼: a kanji card ("LANG - CON SÓI" + mnemonic) that also gives the word's reading.
        if lines and re.match(r"^[A-ZĐÀ-Ỹ ]+ - [A-ZĐÀ-Ỹ ]+$", lines[0]):
            return "kanji+word"
    return "word"


def line_kind(line: str) -> str:
    s = line.rstrip(" -").strip()
    if is_kana_line(s):
        return "reading"
    if VI_UPPER.match(s):
        return "hanviet"
    if s.startswith(("→", "->", "=>", "➡")):
        return "note"
    if JA_CHARS.search(s):
        return "japanese"
    if "Mẹo" in s or s.startswith(("#", ":")) is False and re.match(r"^[^\w\s(（\"“]", s):
        return "mnemonic"
    return "text"


def gloss(text: str) -> str:
    """Short gloss only: drop explanations after an arrow and leading punctuation."""
    text = re.split(r"\s*(?:→|=>|➡)\s*", text)[0]
    return text.strip(" :.;,").strip()


def parse_word(row) -> dict:
    lines = lines_of(row["back"])
    kinds = [line_kind(l) for l in lines]
    issues = []
    reading_at = next((i for i, k in enumerate(kinds) if k == "reading"), None)
    reading = lines[reading_at].rstrip(" -").strip() if reading_at is not None else None
    hanviet = next((lines[i].rstrip(".").strip() for i in range(reading_at or 0) if kinds[i] == "hanviet"), None)
    meaning_lines = []
    if reading_at is not None:
        for line, kind in zip(lines[reading_at + 1:], kinds[reading_at + 1:]):
            if kind in ("text", "mnemonic") and line.startswith(":"):
                kind = "text"
            if kind != "text":
                break
            meaning_lines.append(line)
    # The PDF wraps long glosses: a line starting in lower case continues the previous one.
    parts = []
    for line in meaning_lines:
        if parts and (line[:1].islower() or parts[-1].endswith((",", "/", "("))):
            parts[-1] += " " + line
        else:
            parts.append(line)
    # The first gloss, then further senses only while they look like glosses: mnemonics
    # (caps runs, quotes, '#', stories) and explanations follow the gloss on many cards.
    meanings, dropped = [], []
    for part in parts:
        bare = part.rstrip(".")
        if bare.startswith("(") and bare.endswith(")") and meanings and len(bare) <= 70:
            meanings[-1] = meanings[-1].rstrip(" .") + " " + bare  # qualifier of the previous sense
        elif not meanings and not (part.startswith("(") and part.endswith(")")):
            meanings.append(part)
        elif meanings and looks_like_sense(part):
            meanings.append(part)
        else:
            dropped.append(part)
    meanings = [g for g in (gloss(m) for m in meanings) if g]
    if reading is None:
        issues.append("no-reading")
    if not meanings:
        issues.append("no-meaning")
    if any(kinds[i] == "reading" for i in range((reading_at or 0) + 1, len(kinds))):
        issues.append("second-reading")
    if len(meanings) > 1:
        issues.append("several-senses")
    if dropped:
        issues.append("dropped-lines")
    if reading_at not in (None, 0) and any(kinds[i] != "hanviet" for i in range(reading_at)):
        issues.append("text-before-reading")
    return {
        "expression": norm(row["front"]).replace("\n", "").strip(),
        "reading": reading,
        "hanViet": hanviet,
        "meanings": meanings,
        "dropped": dropped,
        "issues": issues,
    }


CAPS_RUN = re.compile(r"(?:\b[A-ZĐÀ-Ỹ][A-ZĐÀ-Ỹ\-]+\b[\s!.,]*){2,}")


def looks_like_sense(part: str) -> bool:
    return (
        len(part) <= 45
        and not CAPS_RUN.search(part)
        and not part.startswith(("#", '"', "“", "*", "("))
        and "!" not in part
        and not re.search(r"[\U0001F300-\U0001FAFF\u2600-\u27BF]", part)
    )


def kanji_head(row) -> dict:
    lines = lines_of(row["back"])
    head = lines[0] if lines else ""
    m = re.match(r"^([^-–]+?)\s*[-–]\s*(.*)$", head)
    hanviet, meaning = (m.group(1), m.group(2)) if m else (head, "")
    hanviet = hanviet.strip(" .")
    issues = []
    if not VI_UPPER.match(hanviet):
        issues.append("hanviet-not-upper")
    meaning = meaning.strip(" .")
    # "HV - NGHĨA" in caps, or the gloss on its own second line ("LẠC / rơi rớt; …").
    second = lines[1] if len(lines) > 1 else ""
    if not meaning and second and not JA_CHARS.search(second) and not second.startswith("Mẹo") \
            and sum(ch.isupper() for ch in second) < len(second) * 0.3:
        meaning = second.strip(" .")
    if not meaning:
        issues.append("no-meaning")
    printed = norm(row["front"]).strip()
    character = KANJI_CARD_FIX.get(printed, printed)
    if character != printed:
        issues.append(f"character-fixed:{printed}")
    return {
        "character": character,
        "hanViet": hanviet.upper() if hanviet else None,
        "pdfMeaning": meaning.lower() if meaning.isupper() else (meaning or None),
        "issues": issues,
    }


def embedded_words(row) -> list[dict]:
    """易しい / お湯: a word with its reading written inside the kanji card."""
    lines = lines_of(row["back"])
    out = []
    for i in range(1, len(lines) - 1):
        if JA_CHARS.search(lines[i]) and not is_kana_line(lines[i]) and len(lines[i]) <= 8 \
                and not re.search(r"[A-Za-zÀ-ỹ]", lines[i]) and is_kana_line(lines[i + 1]):
            glosses = []
            for line in lines[i + 2:]:
                if line_kind(line) != "text":
                    break
                glosses.append(gloss(line))
            out.append({"expression": lines[i], "reading": lines[i + 1], "hanViet": None,
                        "meanings": [g for g in glosses if g], "dropped": [], "issues": ["embedded-in-kanji-card"]})
    return out


def groups(rows) -> list[dict]:
    """Kanji card followed by its word cards, until the next kanji card or deck."""
    out = []
    for row in rows:
        if row["section"] != "kanji":
            continue
        ref = {"deck": row["deck"], "card": int(row["num"]), "pages": row["pages"]}
        kind = classify(row)
        if kind in ("kanji", "kanji+word"):
            words = [{**ref, **w} for w in embedded_words(row)] if kind == "kanji" else []
            out.append({**ref, **kanji_head(row), "words": words})
            if kind == "kanji+word":
                word = parse_word(row)
                word["issues"] = [i for i in word["issues"] if i != "text-before-reading"] + ["kanji-card-with-reading"]
                out[-1]["words"].append({**ref, **word})
        elif not out or out[-1]["deck"] != row["deck"]:
            raise ValueError(f"word card without a kanji before it: {ref}")
        else:
            out[-1]["words"].append({**ref, **parse_word(row)})
    return out
