"""Furigana markup `{base|reading}`: the same rules as the app's
app/src/main/java/com/n3sensei/app/domain/furigana/Furigana.kt (parse / isValid / baseText)."""


class MalformedFurigana(ValueError):
    pass


def parse(markup: str) -> list[tuple[str, str | None]]:
    segments: list[tuple[str, str | None]] = []
    plain: list[str] = []
    i = 0
    while i < len(markup):
        c = markup[i]
        if c == "{":
            close = markup.find("}", i + 1)
            if close < 0:
                raise MalformedFurigana(f"Unclosed '{{' at {i}")
            body = markup[i + 1:close]
            parts = body.split("|")
            if len(parts) != 2 or not parts[0] or not parts[1] or "{" in body:
                raise MalformedFurigana(f"Expected {{base|reading}} at {i}")
            if plain:
                segments.append(("".join(plain), None))
                plain = []
            segments.append((parts[0], parts[1]))
            i = close + 1
        elif c in "}|":
            raise MalformedFurigana(f"Unexpected '{c}' at {i}")
        else:
            plain.append(c)
            i += 1
    if plain:
        segments.append(("".join(plain), None))
    return segments


def is_valid(markup: str) -> bool:
    try:
        parse(markup)
        return True
    except MalformedFurigana:
        return False


def base_text(markup: str) -> str:
    return "".join(text for text, _ in parse(markup))


def has_kanji(text: str) -> bool:
    """CJK ideographs U+3400–U+4DBF, U+4E00–U+9FFF, U+F900–U+FAFF and 々 (U+3005); same set as the app."""
    return any("㐀" <= c <= "䶿" or "一" <= c <= "鿿" or "豈" <= c <= "﫿" or c == "々" for c in text)
