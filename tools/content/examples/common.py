"""Shared helpers of the example tools (no third-party imports)."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "app/src/main/assets/content"
DATA = Path(__file__).resolve().parent / "data"

KANA_CHARS = "぀-ゟ゠-ヺー-ヿ"
KANA = re.compile(f"[{KANA_CHARS}]")
KANJI = re.compile(r"[一-鿿㐀-䶿々〆]")
COLUMNS = ["item_id", "japanese", "furigana", "translation_vi"]

# Readings of the source table that are typos of the word (the word itself is clear): the
# example's ruby uses the right reading; the item's own reading is left as it is (see
# docs/EXAMPLES.md, "Cần kiểm tra").
READING_FIXES = {
    "mimikara-n3-v065": "めいわく",  # 迷惑, table: めわく
    "mimikara-n3-v178": "すれちがう",  # すれ違う, table: すれちあう
    "mimikara-n3-v343": "ほんやく",  # 翻訳, table: ほにゃく
}

# Word and reading of the source disagree (two different words): no example until the owner decides.
NEEDS_REVIEW = {
    "mimikara-n3-v598": "大分 (だいぶ) nhưng bảng ghi cách đọc ずいぶん (随分): hai từ khác nhau",
    "mimikara-n3-v657": "鏡面 (きょうめん) nhưng bảng ghi cách đọc ひょうめん (表面): hai từ khác nhau",
    "mimikara-n3-v358": "自身 (じしん, bản thân) nhưng bảng ghi nghĩa \"tự tin\" (自信): hai từ khác nhau",
    "mimikara-n3-v419": "合う (あう, hợp, khớp) nhưng bảng ghi nghĩa \"gặp\" (会う): hai từ khác nhau",
    "mimikara-n3-v445": "だまる (黙る, im lặng) nhưng bảng ghi nghĩa \"lừa\" (だます): hai từ khác nhau",
    "mimikara-n3-v580": "敵とな (てきとな): chữ và cách đọc có vẻ là lỗi gõ của 適当な (てきとうな, phù hợp); câu ví dụ không thể chứa đúng chữ của mục",
    "mimikara-n3-v664": "記録 (きろく, ghi chép, kỷ lục) nhưng bảng ghi nghĩa \"ghi âm\" (録音): hai từ khác nhau",
    "mimikara-n3-v682": "協調 (きょうちょう, hợp tác, hài hòa) nhưng bảng ghi nghĩa \"nhấn mạnh\" (強調): hai từ khác nhau",
    "mimikara-n3-v739": "臨む (のぞむ, đối mặt, tham dự) nhưng bảng ghi nghĩa \"ước\" (望む): hai từ khác nhau",
    "mimikara-n3-v360": "感心 (かんしん, khâm phục) nhưng bảng ghi nghĩa \"quan tâm\" (関心): hai từ khác nhau",
}


def to_hiragana(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def align(surface: str, reading: str) -> str | None:
    """`{漢字|よみ}` markup for one word: ruby over each non-kana run, the kana of the spelling
    matched in the reading. The word itself when it is all kana; None when it cannot be aligned."""
    runs = re.findall(f"[{KANA_CHARS}]+|[^{KANA_CHARS}]+", surface)
    if all(KANA.match(r[0]) for r in runs):
        return surface
    pattern = "".join(re.escape(to_hiragana(r)) if KANA.match(r[0]) else "(.+?)" for r in runs)
    m = re.fullmatch(pattern, to_hiragana(reading))
    if not m:
        return None
    out, groups = [], iter(m.groups())
    for r in runs:
        out.append(r if KANA.match(r[0]) else "{%s|%s}" % (r, next(groups)))
    return "".join(out)


def parse_markup(markup: str) -> list[tuple[str, str | None]]:
    out, i = [], 0
    for m in re.finditer(r"\{([^{}|]+)\|([^{}|]+)\}", markup):
        if m.start() > i:
            out.append((markup[i:m.start()], None))
        out.append((m.group(1), m.group(2)))
        i = m.end()
    if i < len(markup):
        out.append((markup[i:], None))
    return out


def base_text(markup: str) -> str:
    return "".join(b for b, _ in parse_markup(markup))


def prefix_markup(markup: str, length: int) -> str:
    """The markup of the first [length] characters; never cuts inside a ruby group."""
    out, n = [], 0
    for base, reading in parse_markup(markup):
        if n >= length:
            break
        if reading is None:
            out.append(base[: length - n])
            n += min(len(base), length - n)
        else:
            out.append("{%s|%s}" % (base, reading))
            n += len(base)
    return "".join(out)


def target_spans(item: dict, sentence: str, fixes: dict | None = None) -> list[tuple[int, int, str | None]]:
    """Where a vocabulary item's word stands in [sentence]: (start, end, markup of that span or
    None when it is written in kana). A conjugated word matches by its stem, which keeps every
    kanji of the word (働く → 働いた, 寂しい → 寂しかった)."""
    forms = item["forms"]
    reading = (fixes or {}).get(item["id"], item["reading"])
    readings = reading.split("・")
    spans = []
    for i, form in enumerate(forms):
        r = readings[i] if len(readings) == len(forms) else readings[0]
        word = align(form, r)
        last_kanji = max((j for j, c in enumerate(form) if KANJI.match(c)), default=-1)
        shortest = last_kanji + 1 if last_kanji >= 0 else max(1 if len(form) == 2 else 2, len(form) - 1)
        candidates = [(form, word)] if word else []
        if word:
            candidates += [(form[:n], prefix_markup(word, n)) for n in range(len(form) - 1, shortest - 1, -1)]
        else:
            candidates += [(form[:n], None) for n in range(len(form) - 1, shortest - 1, -1)]
        if KANJI.search(form):  # the word written in kana
            candidates += [(r, None)] + [(r[:n], None) for n in range(len(r) - 1, max(2, len(r) - 2) - 1, -1)]
        for text, markup in candidates:
            pos = find_word(sentence, text, whole=text == form)
            if pos >= 0 and base_text(markup or text) == text:
                spans.append((pos, pos + len(text), markup if markup and KANJI.search(text) else None))
                break
    return spans


def find_word(sentence: str, text: str, whole: bool) -> int:
    """First place of [text] in [sentence]; a stem (not [whole]) only where no kanji follows it,
    so 写る matches 写っている but not 写真."""
    pos = sentence.find(text)
    while pos >= 0 and not whole:
        end = pos + len(text)
        if end >= len(sentence) or not KANJI.match(sentence[end]):
            return pos
        pos = sentence.find(text, pos + 1)
    return pos


def load_packs() -> dict[str, dict]:
    return {p.stem: json.loads(p.read_text(encoding="utf-8")) for p in ASSETS.glob("*.json")
            if json.loads(p.read_text(encoding="utf-8")).get("format") == "n3sensei-content-pack"}


def load_items() -> dict[str, dict]:
    """Every pack item by id: type, word forms and reading, kanji, grammar pattern, examples."""
    items = {}
    for name, pack in load_packs().items():
        for lesson in pack["lessons"]:
            for v in lesson.get("vocabulary", []):
                items[v["id"]] = {"type": "vocabulary", "pack": name, "id": v["id"], "forms": v["expression"].split("・"),
                                  "reading": v["reading"], "meaning": v["meaningVi"], "examples": v.get("examples", [])}
            for k in lesson.get("kanji", []):
                items[k["id"]] = {"type": "kanji", "pack": name, "id": k["id"], "character": k["character"],
                                  "on": k["onReadings"], "kun": k["kunReadings"], "examples": k.get("examples", [])}
            for g in lesson.get("grammar", []):
                items[g["id"]] = {"type": "grammar", "pack": name, "id": g["id"], "pattern": g["pattern"],
                                  "examples": g.get("examples", [])}
    return items


def read_tsv(path) -> list[dict]:
    rows = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip() or line.startswith("#"):
            rows.append({"comment": line})
            continue
        cells = line.split("\t")
        if len(cells) != len(COLUMNS):
            raise ValueError(f"{path}: expected {len(COLUMNS)} columns: {line!r}")
        rows.append({k: c.strip() for k, c in zip(COLUMNS, cells)})
    return rows


def write_tsv(path, rows: list[dict]) -> None:
    lines = [r["comment"] if "comment" in r else "\t".join(r[k] for k in COLUMNS) for r in rows]
    Path(path).write_text("\n".join(lines) + "\n", encoding="utf-8")
