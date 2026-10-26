"""Furigana markup ({漢字|かんじ}) for a word from its spelling and kana reading."""
import re

KANA = re.compile(r"[぀-ヿー]")


def to_hiragana(s: str) -> str:
    return "".join(chr(ord(c) - 0x60) if "ァ" <= c <= "ヶ" else c for c in s)


def furigana(expression: str, reading: str) -> str | None:
    """Ruby over each non-kana run, the kana of the spelling matched in the reading.
    None when the word is all kana or the reading cannot be aligned with the spelling."""
    runs = re.findall(r"[぀-ヿー]+|[^぀-ヿー]+", expression)
    if all(KANA.fullmatch(r[0]) for r in runs):
        return None
    pattern = "".join(re.escape(to_hiragana(r)) if KANA.match(r[0]) else "(.+?)" for r in runs)
    m = re.fullmatch(pattern, to_hiragana(reading))
    if not m:
        return None
    out, groups = [], iter(m.groups())
    for r in runs:
        out.append(r if KANA.match(r[0]) else "{%s|%s}" % (r, next(groups)))
    return "".join(out)
