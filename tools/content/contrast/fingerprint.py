"""Source fingerprint and source records, sourceSchemaVersion 1 (docs/CONTRAST_LAB_CONTENT_SCHEMA.md §5).

Same algorithm as the app's ContrastFingerprint (domain/contrast/ContrastSource.kt); both are checked
against fixtures/fingerprint-cases.json.

Source rows are read the way the app's importer stores them (ContentPackValidator: strings trimmed,
empty list entries dropped), because the app computes fingerprints from the stored rows, never from
the learner's edits (user_content_edits).
"""
import hashlib
import json
import unicodedata
from pathlib import Path

SCHEMA_VERSION = 1
SUPPORTED_SCHEMA_VERSIONS = {SCHEMA_VERSION}
FIELDS = {
    "GRAMMAR": ["pattern", "structure", "meaningVi"],
    "VOCABULARY": ["expression", "reading", "meaningVi"],
    "KANJI": ["character", "onReadings", "kunReadings", "meaningVi"],
}
_PACK_KEYS = {"GRAMMAR": "grammar", "VOCABULARY": "vocabulary", "KANJI": "kanji"}
FIELD_SEPARATOR = "\u001f"
LIST_SEPARATOR = "\u001e"


def _norm(value) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        return LIST_SEPARATOR.join(_norm(v) for v in value)
    return unicodedata.normalize("NFC", value).strip()


def canonical(source_type: str, fields: dict) -> str:
    parts = [source_type, str(SCHEMA_VERSION)] + [_norm(fields.get(f)) for f in FIELDS[source_type]]
    return FIELD_SEPARATOR.join(parts)


def fingerprint(source_type: str, fields: dict) -> str:
    return "sha256:" + hashlib.sha256(canonical(source_type, fields).encode("utf-8")).hexdigest()


def snapshot_of(source_type: str, fields: dict) -> dict:
    """Exactly the fingerprint fields (missing ones read as "")."""
    return {f: fields.get(f, "") for f in FIELDS[source_type]}


def stored_value(value):
    """A pack value as the importer stores it: trimmed; list entries trimmed, empty ones dropped."""
    if isinstance(value, list):
        return [v.strip() for v in value if isinstance(v, str) and v.strip()]
    if isinstance(value, str):
        return value.strip()
    return value


class SourceIndex:
    """Source rows by (type, id) and by snapshot. `version_of` gives each source pack's version."""

    def __init__(self, records: list[dict]):
        self.records = records
        self._by_id = {(r["type"], r["id"]): r for r in records}
        self.version_of = {}
        for r in records:
            if "packVersion" in r:
                self.version_of[r["packId"]] = r["packVersion"]

    def by_id(self, source_type: str, source_id: str):
        return self._by_id.get((source_type, source_id))

    def by_snapshot(self, source_type: str, snapshot: dict) -> list[dict]:
        wanted = snapshot_of(source_type, snapshot)
        return [r for r in self.records if r["type"] == source_type and snapshot_of(source_type, r["fields"]) == wanted]

    @staticmethod
    def from_content_packs(directory: Path) -> "SourceIndex":
        """Every n3sensei-content-pack in `directory` (e.g. app/src/main/assets/content)."""
        records = []
        for path in sorted(Path(directory).glob("*.json")):
            data = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(data, dict) or data.get("format") != "n3sensei-content-pack":
                continue
            pack = data["pack"]
            for lesson in data.get("lessons", []):
                for source_type, key in _PACK_KEYS.items():
                    for row in lesson.get(key, []) or []:
                        fields = {f: stored_value(row.get(f)) for f in FIELDS[source_type]}
                        records.append({"type": source_type, "id": row["id"], "packId": pack["id"], "packVersion": pack["version"], "fields": fields})
        return SourceIndex(records)

    @staticmethod
    def from_file(path: Path) -> "SourceIndex":
        """A fixture file: {"records": [{"type", "id", "packId", "packVersion"?, "fields"}]}."""
        return SourceIndex(json.loads(Path(path).read_text(encoding="utf-8"))["records"])

    @staticmethod
    def load(path: Path) -> "SourceIndex":
        path = Path(path)
        return SourceIndex.from_content_packs(path) if path.is_dir() else SourceIndex.from_file(path)


def verify(reference: dict, index: SourceIndex) -> tuple[str, list[str]]:
    """Status of a reference: OK, SOURCE_CHANGED, SOURCE_MISSING, AMBIGUOUS or UNVERIFIABLE (+ snapshot candidates).
    Same decision as the app's ContrastSourceVerifier: never relinks."""
    if reference.get("sourceSchemaVersion") not in SUPPORTED_SCHEMA_VERSIONS:
        return "UNVERIFIABLE", []
    source_type = reference["sourceType"]
    record = index.by_id(source_type, reference["sourceContentId"])
    snapshot = reference.get("sourceSnapshot") or {}
    if record is not None:
        same = (fingerprint(source_type, record["fields"]) == reference.get("sourceFingerprint")
                and snapshot_of(source_type, record["fields"]) == snapshot_of(source_type, snapshot))
        return ("OK" if same else "SOURCE_CHANGED"), []
    candidates = sorted(r["id"] for r in index.by_snapshot(source_type, snapshot))
    return ("AMBIGUOUS" if len(candidates) >= 2 else "SOURCE_MISSING"), candidates


def export_fingerprints(sources_dir: Path) -> dict:
    """Fingerprint of every source row of the content packs in `sources_dir`, as the app stores them.
    The app's ContrastSourceFingerprintParityTest checks its own fingerprints of the Room rows against it."""
    index = SourceIndex.from_content_packs(sources_dir)
    rows = sorted(index.records, key=lambda r: (r["type"], r["id"]))
    return {
        "description": "Generated by: python3 tools/content/contrast/fingerprint.py --export app/src/main/assets/content OUT. "
                       "Fingerprint (sourceSchemaVersion 1) of every grammar / vocabulary / kanji row of the bundled packs, "
                       "read as the importer stores them.",
        "packs": dict(sorted(index.version_of.items())),
        "rows": [{"type": r["type"], "id": r["id"], "fingerprint": fingerprint(r["type"], r["fields"])} for r in rows],
    }


if __name__ == "__main__":
    import argparse
    import sys
    parser = argparse.ArgumentParser(description="Contrast source fingerprints")
    parser.add_argument("--export", nargs=2, metavar=("SOURCES_DIR", "OUT"), required=True)
    args = parser.parse_args()
    data = export_fingerprints(Path(args.export[0]))
    Path(args.export[1]).write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(data['rows'])} rows → {args.export[1]}", file=sys.stderr)
