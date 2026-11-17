"""Tests of the N2 kanji pack builder on a small made-up deck (no real deck needed).

Run: python3 -I tools/content/n2_kanji/test_build_pack.py
"""
import gzip
import json
import sqlite3
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).parent
BUILD = HERE / "build_pack.py"

KANJIDIC = """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2><header><file_version>4</file_version><database_version>test</database_version><date_of_creation>2026-01-01</date_of_creation></header>
<character><literal>井</literal><misc><stroke_count>4</stroke_count></misc>
<reading_meaning><rmgroup><reading r_type="ja_on">セイ</reading><reading r_type="ja_kun">い</reading><meaning>well</meaning></rmgroup></reading_meaning></character>
<character><literal>祭</literal><misc><stroke_count>11</stroke_count></misc>
<reading_meaning><rmgroup><reading r_type="ja_on">サイ</reading><meaning>festival</meaning></rmgroup></reading_meaning></character>
<character><literal>丼</literal><misc><stroke_count>5</stroke_count></misc>
<reading_meaning><rmgroup><reading r_type="ja_kun">どんぶり</reading><meaning>bowl</meaning></rmgroup></reading_meaning></character>
</kanjidic2>"""

DECKS = {"1": {"id": 1, "name": "Default"}, "10": {"id": 10, "name": "N2::Kanji N2::第01"}, "11": {"id": 11, "name": "N2::Kanji N2::第02"}}
MODELS = {"100": {"id": 100, "name": "Cơ bản", "flds": [{"name": "Mặt trước"}, {"name": "Mặt sau"}]}}

# (deck, due, fields)
NOTES = [
    (10, 1, ["井", "TỈNH<br>(Cái giếng)<br>=&gt; Ghi nhớ thử"]),
    (10, 2, ["<b>井戸</b>", "Tỉnh Hộ<br>いど<br>Cái giếng"]),
    (10, 3, ["天井", "Thiên Tỉnh<br>てんじょう<br>Trần nhà<br><img src=\"missing.png\">[sound:a.mp3]"]),
    (10, 4, ["井戸水"]),                                    # back field missing
    (10, 5, ["祭", "TẾ<br>Lễ hội"]),                        # already in the N3 pack
    (10, 6, ["祭日", "Tế Nhật<br>さいじつ<br>Ngày lễ"]),
    (11, 1, ["畑", "ĐIỀN<br>Ruộng"]),                       # not in KANJIDIC2 (test file)
    (11, 2, ["畑仕事", "Điền Sĩ Sự<br>はたけしごと<br>Việc đồng áng"]),
    (11, 3, ["丼", "TÍNH<br>Bát tô"]),
    (11, 4, ["丼", "Tính<br>どんぶり<br>Cái bát tô"]),       # a one-kanji word of 丼, not a second kanji
]


def make_deck(folder: Path) -> Path:
    db = folder / "collection.anki2"
    con = sqlite3.connect(db)
    con.executescript("""
        create table col (id integer primary key, decks text, models text);
        create table notes (id integer primary key, guid text, mid integer, tags text, flds text);
        create table cards (id integer primary key, nid integer, did integer, ord integer, due integer, type integer);
    """)
    con.execute("insert into col values (1, ?, ?)", (json.dumps(DECKS), json.dumps(MODELS)))
    for i, (deck, due, fields) in enumerate(NOTES, 1):
        con.execute("insert into notes values (?, ?, 100, '', ?)", (1000 + i, f"g{i}", "\x1f".join(fields)))
        con.execute("insert into cards values (?, ?, ?, 0, ?, 0)", (5000 + i, 1000 + i, deck, due))
    con.commit()
    con.close()
    apkg = folder / "deck.apkg"
    with zipfile.ZipFile(apkg, "w") as z:
        z.write(db, "collection.anki2")
        z.writestr("media", json.dumps({"0": "a.mp3"}))
        z.writestr("0", b"ID3")
    return apkg


class BuildPackTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.apkg = make_deck(self.dir)
        self.kanjidic = self.dir / "kanjidic2.xml.gz"
        self.kanjidic.write_bytes(gzip.compress(KANJIDIC.encode("utf-8")))
        self.n3 = self.dir / "n3.json"
        self.n3.write_text(json.dumps({"lessons": [{"kanji": [{"character": "祭"}]}]}), encoding="utf-8")

    def tearDown(self):
        self.tmp.cleanup()

    def build(self, name: str, *flags: str) -> subprocess.CompletedProcess:
        out = self.dir / name
        out.mkdir()
        return subprocess.run(
            [sys.executable, "-I", str(BUILD), *flags, str(self.apkg), str(self.kanjidic), str(self.n3),
             str(out / "pack.json"), str(out / "report.json"), str(out / "tables")],
            capture_output=True, text=True,
        )

    def outputs(self, name: str):
        out = self.dir / name
        return json.loads((out / "pack.json").read_text("utf-8")), json.loads((out / "report.json").read_text("utf-8"))

    def test_usableNotesBecomeThePack_unusableOnesAreSkippedAndReported_neverACrash(self):
        run = self.build("a")
        self.assertEqual(0, run.returncode, run.stderr)
        pack, report = self.outputs("a")
        self.assertEqual("N2", pack["pack"]["jlptLevel"])
        kanji = {k["character"]: k for lesson in pack["lessons"] for k in lesson["kanji"]}
        # 井 and 丼 kept; 祭 is the N3 pack's; 畑 is not in KANJIDIC2.
        self.assertEqual({"井", "丼"}, set(kanji))
        self.assertEqual("n2k-k%05x" % ord("井"), kanji["井"]["id"])
        self.assertEqual(["セイ"], kanji["井"]["onReadings"])
        self.assertEqual("Cái giếng", kanji["井"]["meaningVi"])
        self.assertIn("Ghi nhớ: Ghi nhớ thử", kanji["井"]["note"])
        examples = {e["id"]: e for e in pack["examples"]}
        self.assertEqual(["{井戸|いど}", "{天井|てんじょう}"], [examples[i]["furigana"] for i in kanji["井"]["examples"]])
        # The one-kanji word 丼 (どんぶり) is a word of the kanji 丼, not a second kanji.
        self.assertEqual(["{丼|どんぶり}"], [examples[i]["furigana"] for i in kanji["丼"]["examples"]])
        self.assertEqual(["祭"], [x["kanji"] for x in report["leftOut"]])
        self.assertTrue(any("fewer than 2 fields" in s for s in report["skipped"]), report["skipped"])
        self.assertTrue(any("畑: not in KANJIDIC2" in s for s in report["skipped"]), report["skipped"])
        # Media: none imported; the image the card names but the deck lacks is listed.
        self.assertEqual(2, report["mediaNotImported"]["references"])
        self.assertEqual(["missing.png"], [m["file"] for m in report["mediaNotImported"]["missingFromDeck"]])
        # Lesson 2 keeps its deck number even though 畑 was skipped.
        self.assertEqual([1, 2], [lesson["number"] for lesson in pack["lessons"]])

    def test_strict_failsOnSkippedNotes(self):
        self.assertNotEqual(0, self.build("strict", "--strict").returncode)

    def test_buildingTwice_givesTheSamePack_sameIds(self):
        self.assertEqual(0, self.build("one").returncode)
        self.assertEqual(0, self.build("two").returncode)
        self.assertEqual((self.dir / "one" / "pack.json").read_bytes(), (self.dir / "two" / "pack.json").read_bytes())


if __name__ == "__main__":
    unittest.main()
