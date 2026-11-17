"""Read an Anki package (.apkg) without Anki: notes, cards, decks and the media index.

Handles the current format (collection.anki21b and media compressed with zstd, media index as
protobuf) and the older ones (collection.anki21 / collection.anki2, media index as JSON). The
collection is copied to a temporary file and opened read-only; the package is never changed.
"""
import html
import json
import re
import sqlite3
import subprocess
import tempfile
import zipfile
from pathlib import Path


def _zstd(data: bytes) -> bytes:
    try:
        import zstandard  # optional; falls back to the zstd command
        return zstandard.ZstdDecompressor().decompressobj().decompress(data)
    except ImportError:
        return subprocess.run(["zstd", "-dc"], input=data, capture_output=True, check=True).stdout


def _varint(b: bytes, i: int) -> tuple[int, int]:
    r = s = 0
    while True:
        x = b[i]
        i += 1
        r |= (x & 0x7F) << s
        s += 7
        if x < 0x80:
            return r, i


def _media_names_protobuf(b: bytes) -> list[str]:
    """MediaEntries { repeated MediaEntry entries = 1 { string name = 1; … } }: name of file i."""
    names, i = [], 0
    while i < len(b):
        key, i = _varint(b, i)
        length, i = _varint(b, i)
        entry, i = b[i:i + length], i + length
        if key != 0x0A:
            continue
        j, name = 0, None
        while j < len(entry):
            k, j = _varint(entry, j)
            if k & 7 == 2:
                n, j = _varint(entry, j)
                if k >> 3 == 1:
                    name = entry[j:j + n].decode("utf-8")
                j += n
            elif k & 7 == 0:
                _, j = _varint(entry, j)
            else:
                raise ValueError(f"unexpected wire type in media index: {k & 7}")
        names.append(name)
    return names


class Apkg:
    def __init__(self, path: str):
        self.path = path
        self._zip = zipfile.ZipFile(path)
        members = set(self._zip.namelist())
        self._tmp = tempfile.TemporaryDirectory()
        db = Path(self._tmp.name) / "collection.db"
        if "collection.anki21b" in members:
            db.write_bytes(_zstd(self._zip.read("collection.anki21b")))
            self.format = "anki21b"
        elif "collection.anki21" in members:
            db.write_bytes(self._zip.read("collection.anki21"))
            self.format = "anki21"
        else:
            db.write_bytes(self._zip.read("collection.anki2"))
            self.format = "anki2"
        self.db = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
        # Anki's own collation; only needed for queries that sort on it.
        self.db.create_collation("unicase", lambda a, b: (a.casefold() > b.casefold()) - (a.casefold() < b.casefold()))
        raw = self._zip.read("media") if "media" in members else b""
        if self.format == "anki21b":
            self.media_names = _media_names_protobuf(_zstd(raw))
        else:
            index = json.loads(raw or b"{}")
            self.media_names = [index[k] for k in sorted(index, key=int)]
        self._media_member = {name: str(i) for i, name in enumerate(self.media_names)}

    def close(self):
        self.db.close()
        self._zip.close()
        self._tmp.cleanup()

    # ---- collection ------------------------------------------------------------------
    def decks(self) -> dict[int, str]:
        """Deck id -> full name, levels joined with '::'."""
        if self.format == "anki21b":
            return {i: n.replace("\x1f", "::") for i, n in self.db.execute("select id, name from decks")}
        decks = json.loads(self.db.execute("select decks from col").fetchone()[0])
        return {int(i): d["name"] for i, d in decks.items()}

    def field_names(self) -> dict[int, list[str]]:
        """Note type id -> its field names in order."""
        if self.format == "anki21b":
            out: dict[int, list[str]] = {}
            for ntid, _, name in self.db.execute("select ntid, ord, name from fields order by ntid, ord"):
                out.setdefault(ntid, []).append(name)
            return out
        models = json.loads(self.db.execute("select models from col").fetchone()[0])
        return {int(i): [f["name"] for f in m["flds"]] for i, m in models.items()}

    def cards(self) -> list[dict]:
        """Every card with its note, in deck order (deck name, then the deck's own order: due)."""
        decks = self.decks()
        fields = self.field_names()
        rows = self.db.execute(
            "select c.id, c.did, c.ord, c.due, c.type, n.id, n.guid, n.mid, n.tags, n.flds "
            "from cards c join notes n on n.id = c.nid"
        ).fetchall()
        out = []
        for cid, did, ord_, due, ctype, nid, guid, mid, tags, flds in rows:
            values = flds.split("\x1f")
            names = fields.get(mid, [f"field{i}" for i in range(len(values))])
            out.append({
                "card_id": cid, "deck": decks.get(did, str(did)), "ord": ord_, "due": due, "type": ctype,
                "note_id": nid, "guid": guid, "note_type": mid, "tags": tags.split(),
                "fields": dict(zip(names, values)), "values": values,
            })
        out.sort(key=lambda c: (c["deck"], c["due"], c["card_id"]))
        return out

    # ---- media -----------------------------------------------------------------------
    def has_media(self, name: str) -> bool:
        return name in self._media_member

    def media_bytes(self, name: str) -> bytes:
        data = self._zip.read(self._media_member[name])
        return _zstd(data) if self.format == "anki21b" else data


IMG = re.compile(r"<img[^>]*src=\"([^\"]+)\"[^>]*>", re.I)
SOUND = re.compile(r"\[sound:([^\]]+)\]")


def field_lines(value: str) -> list[str]:
    """Text lines of a field: HTML removed, entities decoded, images as '[IMG]', blanks dropped."""
    s = IMG.sub("\n[IMG]\n", value)
    s = SOUND.sub("\n[SOUND]\n", s)
    s = re.sub(r"<br\s*/?>|</div>|<div[^>]*>|</p>|<p[^>]*>", "\n", s, flags=re.I)
    s = html.unescape(re.sub(r"<[^>]+>", "", s)).replace("\xa0", " ")
    return [re.sub(r"\s+", " ", line).strip() for line in s.split("\n") if line.strip()]


def media_refs(value: str) -> list[str]:
    return IMG.findall(value) + SOUND.findall(value)


