"""Read the KANJIDIC2 entries (EDRDG, CC BY-SA 4.0) for a set of characters."""
import gzip
import xml.etree.ElementTree as ET


def load(path: str, characters: set[str]) -> dict:
    out = {}
    with gzip.open(path) as f:
        for _, el in ET.iterparse(f, events=("end",)):
            if el.tag == "header":
                out["_version"] = el.findtext("database_version"), el.findtext("date_of_creation")
            if el.tag != "character":
                continue
            lit = el.findtext("literal")
            if lit in characters:
                rm = el.find("reading_meaning")
                group = rm.find("rmgroup") if rm is not None else None
                readings = lambda t: [r.text for r in group.findall("reading") if r.get("r_type") == t] if group is not None else []
                out[lit] = {
                    "on": readings("ja_on"),
                    "kun": readings("ja_kun"),
                    "vietnam": readings("vietnam"),
                    "meanings": [m.text for m in group.findall("meaning") if m.get("m_lang") is None] if group is not None else [],
                    "strokes": int(el.find("misc").findtext("stroke_count")),
                    "jlpt": el.find("misc").findtext("jlpt"),
                    "grade": el.find("misc").findtext("grade"),
                }
            el.clear()
    return out
