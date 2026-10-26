"""Look up words in JMdict (EDRDG, CC BY-SA 4.0): English glosses by spelling + reading."""
import gzip
import xml.etree.ElementTree as ET


def lookup(path: str, words: set[tuple[str, str]]) -> dict:
    spellings = {w for w, _ in words}
    out = {}
    with gzip.open(path) as f:
        for _, el in ET.iterparse(f, events=("end",)):
            if el.tag != "entry":
                continue
            keb = [k.text for k in el.iterfind("k_ele/keb")]
            reb = [r.text for r in el.iterfind("r_ele/reb")]
            for k in keb or reb:
                if k in spellings:
                    for r in reb:
                        if (k, r) in words:
                            senses = [[g.text for g in s.iterfind("gloss")] for s in el.iterfind("sense")]
                            out.setdefault((k, r), []).append({"seq": el.findtext("ent_seq"), "senses": senses})
            el.clear()
    return out
