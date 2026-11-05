"""Fills the furigana column of example TSV files and checks each sentence against its item.

Usage (janome is a development tool only, never shipped with the app):
    python3 -m venv /tmp/venv && /tmp/venv/bin/pip install janome
    /tmp/venv/bin/python tools/content/examples/annotate.py tools/content/examples/data/*.tsv

TSV columns: item_id, japanese, furigana, translation_vi. Lines starting with '#' are comments.
- An empty furigana column is filled; a filled one is kept as it is (hand corrections survive).
- For a vocabulary item the target word's ruby comes from the item's own reading (or from
  READING_FIXES when that reading is a known typo); the rest of the sentence from janome.
- Problems (target missing, unknown or ambiguous readings) are printed for review; the
  script exits with 1 when a sentence does not contain its item.
"""
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import (  # noqa: E402
    KANJI, READING_FIXES, align, load_items, read_tsv, target_spans, to_hiragana, write_tsv,
)

from janome.tokenizer import Tokenizer  # noqa: E402

TOKENIZER = Tokenizer()

# Characters whose reading janome often guesses from too little context: always reviewed.
AMBIGUOUS = set("何今明後方間中上下生日月人年分前行入通開空来")


def annotate_plain(text: str, flags: list[str]) -> str:
    out = []
    for tok in TOKENIZER.tokenize(text):
        surface = tok.surface
        if not KANJI.search(surface):
            out.append(surface)
            continue
        reading = tok.reading
        if reading == "*" or not reading:
            flags.append(f"no reading: {surface}")
            out.append(surface)
            continue
        if AMBIGUOUS & set(surface) and len(surface) <= 2:
            flags.append(f"check: {surface}={to_hiragana(reading)}")
        markup = align(surface, to_hiragana(reading))
        if markup is None:
            flags.append(f"unaligned: {surface}={to_hiragana(reading)}")
            markup = "{%s|%s}" % (surface, to_hiragana(reading))
        out.append(markup)
    return "".join(out)


# Readings janome gets wrong in these sentences (checked by hand): applied to the markup.
FIXES = [
    (r"\{日本\|にっぽん\}", "{日本|にほん}"),
    (r"\{日本人\|にっぽんじん\}", "{日本人|にほんじん}"),
    (r"\{一\|いち\}\{人\|にん\}", "{一人|ひとり}"),
    (r"\{二\|に\}\{人\|にん\}", "{二人|ふたり}"),
    (r"\{一\|いち\}\{日\|にち\}\{中\|ちゅう\}", "{一日中|いちにちじゅう}"),
    (r"\{一\|いち\}\{日\|にち\}", "{一日|いちにち}"),
    (r"\{今日\|きょう\}\{中\|ちゅう\}", "{今日中|きょうじゅう}"),
    (r"(?<!という)(?<!いつの)\{間\|ま\}(?!に\{合)", "{間|あいだ}"),
    (r"\{一\|いち\}か\{月\|げつ\}", "{一|いっ}か{月|げつ}"),
    (r"\{十分\|じゅうぶん\}おき", "{十分|じゅっぷん}おき"),
    (r"\{四\|よん\}\{年\|ねん\}", "{四年|よねん}"),
    (r"\{一\|いち\}\{晩\|ばん\}", "{一晩|ひとばん}"),
    (r"\{十\|じゅう\}\{八\|はち\}\{歳\|さい\}", "{十八歳|じゅうはっさい}"),
    (r"\{六\|ろく\}\{十\|じゅう\}\{歳\|さい\}", "{六十歳|ろくじゅっさい}"),
    (r"\{二\|に\}\{十\|じゅう\}\{歳\|さい\}", "{二十歳|はたち}"),
    (r"\{三\|さん\}\{十\|じゅう\}\{分\|[ふぶ]ん\}", "{三十分|さんじゅっぷん}"),
    (r"\{十分\|じゅうぶん\}(ぐらい|ほど|おき|で|後|前|\{遅|\{過)", r"{十分|じゅっぷん}\1"),
    (r"\{三\|さん\}\{階\|かい\}", "{三階|さんがい}"),
    (r"\{九\|きゅう\}\{時\|じ\}", "{九時|くじ}"),
    (r"\{何\|なに\}(です|だ|の\{)", r"{何|なん}\1"),
    (r"\{七\|なな\}\{十\|じゅう\}\{点\|てん\}", "{七十点|ななじゅってん}"),
    (r"\{洗濯\|せんたく\}\{物\|ぶつ\}", "{洗濯物|せんたくもの}"),
    (r"\{雨\|う\}", "{雨|あめ}"),
    (r"\{一\|いち\}\{本\|ほん\}", "{一本|いっぽん}"),
    (r"\{三\|さん\}\{日\|にち\}", "{三日|みっか}"),
    (r"\{九\|きゅう\}\{十\|じゅう\}\{歳\|さい\}", "{九十歳|きゅうじゅっさい}"),
    (r"\{三\|さん\}\{匹\|ひき\}", "{三匹|さんびき}"),
    (r"\{角\|かく\}", "{角|かど}"),
    (r"\{的\|てき\}に\{当", "{的|まと}に{当"),
    (r"\{後\|のち\}", "{後|あと}"),
    (r"\{学校\|がっこう\}\{中\|ちゅう\}", "{学校中|がっこうじゅう}"),
    (r"の\{海\|かい\}", "の{海|うみ}"),
    (r"\{穴\|あな\}が\{開\|ひら\}い", "{穴|あな}が{開|あ}い"),
    (r"\{家\|か\}を", "{家|いえ}を"),
    (r"\{夜中\|やちゅう\}", "{夜中|よなか}"),
    (r"\{一\|いち\}\{週間\|しゅうかん\}", "{一週間|いっしゅうかん}"),
    (r"\{八\|はち\}\{十\|じゅう\}\{歳\|さい\}", "{八十歳|はちじゅっさい}"),
    (r"\{一\|いち\}\{点\|てん\}", "{一点|いってん}"),
    (r"\{運転\|うんてん\}\{手\|しゅ\}", "{運転手|うんてんしゅ}"),
    (r"\{降\|お\}りそう", "{降|ふ}りそう"),
]


def fix(markup: str) -> str:
    for pattern, replacement in FIXES:
        markup = re.sub(pattern, replacement, markup)
    return markup


def annotate(item, japanese: str, flags: list[str]) -> str | None:
    """Furigana for the sentence; the item's own word (vocabulary) gets the item's reading."""
    if item is None or item["type"] != "vocabulary":
        return annotate_plain(japanese, flags)
    spans = target_spans(item, japanese, READING_FIXES)
    if not spans:
        return None
    start, end, markup = spans[0]
    if markup is None:  # kana-only target
        return annotate_plain(japanese, flags)
    return annotate_plain(japanese[:start], flags) + markup + annotate_plain(japanese[end:], flags)


def main(args: list[str]) -> int:
    force = "--force" in args
    verbose = "--flags" in args  # recompute every row (drops hand corrections)
    paths = [a for a in args if not a.startswith("--")]
    items = load_items()
    failed = False
    for path in paths:
        rows = read_tsv(path)
        changed = False
        for row in rows:
            if "comment" in row:
                continue
            item = items.get(row["item_id"])
            if item is None:
                print(f"{path}: unknown item {row['item_id']}")
                failed = True
                continue
            flags: list[str] = []
            if force:
                row["furigana"] = ""
            if not row["furigana"]:
                if not KANJI.search(row["japanese"]):
                    continue
                markup = annotate(item, row["japanese"], flags)
                if markup is None:
                    print(f"{path}: {row['item_id']} does not contain its word: {row['japanese']}")
                    failed = True
                    continue
                row["furigana"] = fix(markup)
                changed = True
            elif item["type"] == "vocabulary" and not target_spans(item, row["japanese"], READING_FIXES):
                print(f"{path}: {row['item_id']} does not contain its word: {row['japanese']}")
                failed = True
            if verbose:
                for f in flags:
                    print(f"{path}: {row['item_id']}: {f}  | {row['furigana']}")
        if changed:
            write_tsv(path, rows)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
