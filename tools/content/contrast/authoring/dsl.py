"""Compact authoring DSL for Contrast Lab drafts (docs/CONTRAST_LAB_CONTENT_GENERATION_REPORT.md).

Japanese text is written once, as furigana markup `{漢字|かな}`; the plain `japanese` / `text` field is
derived from it (furigana_markup.base_text), so the two can never disagree. Text without any `{…|…}`
gets no furigana field (used on purpose for single-kanji options whose reading would give the answer away).

Every group is converted by drafts_tool.py into tools/content/contrast/drafts/<groupId>.json in the
existing pack format (docs/contrast/contrast-pack.schema.json); nothing here defines a new format.
"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from furigana_markup import base_text  # noqa: E402

PREFIX = {"GRAMMAR": "g", "VOCABULARY": "v", "KANJI": "k"}
SOURCE_PACK = {"GRAMMAR": "n3-grammar", "VOCABULARY": "mimikara-n3-vocab", "KANJI": "n3-kanji"}


def sentence(markup: str) -> dict:
    """{japanese, furigana?} from markup."""
    out = {"japanese": base_text(markup)}
    if "{" in markup:
        out["furigana"] = markup
    return out


class Opt:
    def __init__(self, oid: str, markup: str, item: str | None = None, fb: str | None = None):
        self.oid, self.markup, self.item, self.fb = oid, markup, item, fb


def opt(oid: str, markup: str, item: str | None = None, fb: str | None = None) -> Opt:
    return Opt(oid, markup, item, fb)


class Seg:
    def __init__(self, markup: str, item: str | None = None):
        self.markup, self.item = markup, item


def seg(markup: str, item: str | None = None) -> Seg:
    return Seg(markup, item)


class Group:
    def __init__(self, content_type: str, slug: str, title: str, summary: str):
        self.type = content_type
        self.p = PREFIX[content_type]
        self.slug = slug
        self.title = title
        self.summary = summary
        self.items: list[dict] = []
        self.theories: list[tuple[str, str]] = []
        self.rows: list[tuple[str, dict]] = []
        self.examples_: list[tuple[str, str, str]] = []
        self.tips: list[str] = []
        self.mistakes: list[dict] = []
        self.questions: list[dict] = []

    # ---- ids ----
    @property
    def id(self) -> str:
        return f"cg-{self.p}-{self.slug}"

    def item_id(self, key: str) -> str:
        return f"ci-{self.p}-{self.slug}-{key}"

    def _ref(self, key: str | None) -> str | None:
        if key is None:
            return None
        if key not in {i["key"] for i in self.items}:
            raise ValueError(f"{self.id}: unknown item key {key!r}")
        return self.item_id(key)

    # ---- items ----
    def grammar(self, key: str, source_id: str, label: str, meaning: str, connection: str, nuance: str):
        self.items.append({"key": key, "source": source_id, "label": label,
                           "grammar": {"meaningVi": meaning, "connection": connection, "nuanceVi": nuance}})

    def vocab(self, key: str, source_id: str, label_markup: str, meaning: str, usage: str, collocations: list[tuple[str, str]], reading: str | None = None):
        self.items.append({"key": key, "source": source_id, "labelMarkup": label_markup, "reading": reading,
                           "vocabulary": {"meaningVi": meaning, "usageVi": usage,
                                          "collocations": [{**sentence(m), "meaningVi": vi} for m, vi in collocations]}})

    def kanji(self, key: str, source_id: str, char: str, meaning: str, radical: tuple[str, str], shape: str):
        self.items.append({"key": key, "source": source_id, "label": char,
                           "kanji": {"meaningVi": meaning, "radical": {"character": radical[0], "nameVi": radical[1]}, "shapeNoteVi": shape}})

    # ---- explanations ----
    def theory(self, title: str, body: str):
        self.theories.append((title, body))

    def compare(self, aspect: str, **values: str):
        self.rows.append((aspect, values))

    def example(self, key: str, markup: str, vi: str):
        self.examples_.append((key, markup, vi))

    def tip(self, text: str):
        self.tips.append(text)

    def mistake(self, situation: str, wrong: str, correct: str, why: str, expected: str | None = None, confused: str | None = None):
        self.mistakes.append({"situation": situation, "wrong": wrong, "correct": correct, "why": why, "expected": expected, "confused": confused})

    # ---- questions ----
    def _q(self, kind: str, target: str, instruction: str, options: list[Opt], correct: str, why: str, d: int, **extra):
        self.questions.append({"kind": kind, "target": target, "instruction": instruction, "options": options,
                               "correct": correct, "why": why, "difficulty": d, **extra})

    def mc(self, target, instruction, options, correct, why, d=1):
        self._q("MULTIPLE_CHOICE", target, instruction, options, correct, why, d)

    def fill(self, target, instruction, prompt, translation, options, correct, why, d=1):
        self._q("FILL_IN_THE_BLANK", target, instruction, options, correct, why, d, prompt=prompt, translation=translation)

    def error(self, target, instruction, translation, segments: list[Seg], wrong: int, fix: str, why: str, d=2):
        """`wrong`: index (0-based) of the wrong segment; `fix`: the correct segment (markup)."""
        letters = "abcdef"
        options = [Opt(letters[i], s.markup, s.item) for i, s in enumerate(segments)]
        prompt = "".join(s.markup for s in segments)
        self._q("ERROR_DETECTION", target, instruction, options, letters[wrong], why, d, prompt=prompt, translation=translation, fix=fix)

    def context(self, target, instruction, situation, options, correct, why, d=2, situation_jp: str | None = None):
        self._q("CONTEXT_SELECTION", target, instruction, options, correct, why, d, situation=situation, situation_jp=situation_jp)

    # ---- conversion ----
    def to_draft(self, sources, fingerprint_mod, updated_at: str, status: str) -> dict:
        fp = fingerprint_mod
        items = []
        for it in self.items:
            record = sources.by_id(self.type, it["source"])
            if record is None:
                raise ValueError(f"{self.id}: source {self.type} {it['source']} not found")
            fields = record["fields"]
            out = {"id": self.item_id(it["key"])}
            if self.type == "VOCABULARY":
                out["label"] = base_text(it["labelMarkup"])
                if "{" in it["labelMarkup"]:
                    out["labelFurigana"] = it["labelMarkup"]
            else:
                out["label"] = it["label"]
            out["source"] = {
                "sourceType": self.type,
                "sourceContentId": it["source"],
                "sourcePackId": record["packId"],
                "sourceSchemaVersion": fp.SCHEMA_VERSION,
                "sourceSnapshot": fp.snapshot_of(self.type, fields),
                "sourceFingerprint": fp.fingerprint(self.type, fields),
            }
            if self.type == "GRAMMAR":
                out["grammar"] = it["grammar"]
            elif self.type == "VOCABULARY":
                v = it["vocabulary"]
                out["vocabulary"] = {"reading": it["reading"] or fields["reading"], "meaningVi": v["meaningVi"], "usageVi": v["usageVi"]}
                if v["collocations"]:
                    out["vocabulary"]["collocations"] = v["collocations"]
            else:
                k = it["kanji"]
                raw = record.get("raw", {})
                out["kanji"] = {"meaningVi": k["meaningVi"]}
                if raw.get("hanViet"):
                    out["kanji"]["hanViet"] = raw["hanViet"]
                out["kanji"]["onReadings"] = fields["onReadings"]
                out["kanji"]["kunReadings"] = fields["kunReadings"]
                out["kanji"]["radical"] = k["radical"]
                out["kanji"]["shapeNoteVi"] = k["shapeNoteVi"]
            items.append(out)

        def opt_json(o: Opt, with_item=True) -> dict:
            j = {"id": o.oid, "text": base_text(o.markup)}
            if "{" in o.markup:
                j["furigana"] = o.markup
            if with_item and o.item:
                j["itemId"] = self._ref(o.item)
            if o.fb:
                j["feedbackVi"] = o.fb
            return j

        questions = []
        counters: dict[str, int] = {}
        short = {"MULTIPLE_CHOICE": "mc", "FILL_IN_THE_BLANK": "fb", "ERROR_DETECTION": "ed", "CONTEXT_SELECTION": "cs"}
        for q in self.questions:
            n = counters[q["kind"]] = counters.get(q["kind"], 0) + 1
            j = {"id": f"cq-{self.p}-{self.slug}-{short[q['kind']]}{n}", "revision": 1, "type": q["kind"], "difficulty": q["difficulty"],
                 "targetItemId": self._ref(q["target"]), "instructionVi": q["instruction"]}
            if "prompt" in q:
                j["prompt"] = {**sentence(q["prompt"]), **({"translationVi": q["translation"]} if q.get("translation") else {})}
            if q["kind"] == "CONTEXT_SELECTION":
                j["context"] = {"descriptionVi": q["situation"]}
                if q.get("situation_jp"):
                    j["context"].update(sentence(q["situation_jp"]))
            j["options"] = [opt_json(o) for o in q["options"]]
            j["correctOptionId"] = q["correct"]
            if q["kind"] == "ERROR_DETECTION":
                j["correction"] = {**{"text": base_text(q["fix"])}, **({"furigana": q["fix"]} if "{" in q["fix"] else {}), "itemId": self._ref(q["target"])}
            j["explanationVi"] = q["why"]
            questions.append(j)

        group = {
            "id": self.id,
            "revision": 1,
            "status": status,
            "contentType": self.type,
            "jlptLevel": "N3",
            "title": self.title,
            "summaryVi": self.summary,
            "updatedAt": updated_at,
            "items": items,
            "theory": [{"id": f"ct-{self.p}-{self.slug}-{i + 1}", "titleVi": t, "bodyVi": b} for i, (t, b) in enumerate(self.theories)],
            "comparison": [{"aspectVi": a, "values": {self._ref(k): v for k, v in vals.items()}} for a, vals in self.rows],
            "examples": [{"id": f"cx-{self.p}-{self.slug}-{i + 1}", "itemId": self._ref(k), **sentence(m), "translationVi": vi}
                         for i, (k, m, vi) in enumerate(self.examples_)],
            "memoryTips": [{"id": f"cm-{self.p}-{self.slug}-{i + 1}", "textVi": t} for i, t in enumerate(self.tips)],
            "commonMistakes": [],
            "questions": questions,
        }
        for i, m in enumerate(self.mistakes):
            row = {"id": f"ce-{self.p}-{self.slug}-{i + 1}", "situationVi": m["situation"], "wrong": sentence(m["wrong"]),
                   "correct": sentence(m["correct"]), "explanationVi": m["why"]}
            if m["expected"]:
                row["expectedItemId"] = self._ref(m["expected"])
            if m["confused"]:
                row["confusedWithItemId"] = self._ref(m["confused"])
            group["commonMistakes"].append(row)
        return group
