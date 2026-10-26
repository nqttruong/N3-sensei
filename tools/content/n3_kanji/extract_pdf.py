"""Extract every card row from N3.pdf: (section, deck, number, front, back, page)."""
import json, re, sys, logging
import pdfplumber
logging.disable(logging.WARNING)
src, out = sys.argv[1], sys.argv[2]
SEC = {"vocab": re.compile(r"N3\s*の?\s*語彙"), "kanji": re.compile(r"N3\s*の?\s*KANJI"), "grammar": re.compile(r"^Ngữ pháp N3$")}
DECK = re.compile(r"^(.*?)\s*\((\d+) thẻ\)$")
rows, decks = [], []
section = deck = None
with pdfplumber.open(src) as pdf:
    for pn, page in enumerate(pdf.pages, 1):
        if pn <= 2: continue
        events = []
        lines = page.extract_text_lines()
        for ln in lines:
            t = ln["text"].strip()
            if t in ("N3 — Từ vựng",): events.append((ln["top"], "sec", "vocab"))
            elif t == "N3 KANJI — Hán tự": events.append((ln["top"], "sec", "kanji"))
            elif t == "Ngữ pháp N3": events.append((ln["top"], "sec", "grammar"))
            m = re.search(r"\((\d+) thẻ\)$", t)
            if m:
                same = [l for l in lines if abs(l["top"] - ln["top"]) < 12 and l["x1"] <= ln["x1"]]
                name = " ".join(l["text"].strip() for l in sorted(same, key=lambda l: l["x0"]))
                name = re.sub(r"\s*\(\d+ thẻ\)$", "", name).strip()
                events.append((ln["top"] - 10, "deck", (name, int(m.group(1)))))
        # Rows from the ruling: 1pt rects. Fragments cut by a page break have no outer
        # border, so the extent of the vertical rules closes them.
        rects = page.rects
        vert = [r for r in rects if r["width"] <= 1.5 and r["height"] > 5]
        horiz = [r for r in rects if r["height"] <= 1.5 and r["width"] > 20 and r["x0"] < 70]
        if vert:
            xs = sorted({round(r["x0"]) for r in vert})
            left = [r for r in vert if abs(r["x0"] - xs[0]) < 2]
            cuts = sorted({round(r["top"], 1) for r in horiz} | {round(r["top"], 1) for r in left} | {round(r["bottom"], 1) for r in left})
            cols = xs  # left border, number|front, front|back, right border
            for t0, t1 in zip(cuts, cuts[1:]):
                if t1 - t0 < 4: continue
                mid = (t0 + t1) / 2
                if not any(r["top"] <= mid <= r["bottom"] for r in left): continue
                cells = []
                for c0, c1 in zip(cols, cols[1:]):
                    cells.append(page.crop((c0, t0, c1, t1)).extract_text() or "")
                events.append((t0, "row", cells))
        for top, kind, v in sorted(events, key=lambda e: e[0]):
            if kind == "sec": section = v
            elif kind == "deck":
                deck = v[0]; decks.append({"section": section, "deck": v[0], "declared": v[1], "page": pn})
            else:
                num, front, back = (v + ["", "", ""])[:3]
                num = (num or "").strip()
                if not num and rows:  # the rest of a row cut by the page break
                    if (front or "").strip(): rows[-1]["front"] += "\n" + front
                    if (back or "").strip(): rows[-1]["back"] += "\n" + back
                    if pn not in rows[-1]["pages"]: rows[-1]["pages"].append(pn)
                    continue
                rows.append({"section": section, "deck": deck, "num": num, "front": front or "", "back": back or "", "pages": [pn]})
json.dump({"decks": decks, "rows": rows}, open(out, "w"), ensure_ascii=False, indent=1)
print(len(decks), "decks", len(rows), "rows")
