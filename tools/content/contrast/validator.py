"""Validation of a Contrast pack (docs/CONTRAST_LAB_CONTENT_SCHEMA.md §8).

Three layers:
- `semantic(...)`: the rules shared with the app (ContrastPackValidator.kt): same codes, severities
  and paths, checked against the shared fixtures in fixtures/cases/;
- `schema_issues(...)`: JSON Schema (docs/contrast/contrast-pack.schema.json), each error mapped to
  the specific code when there is one, otherwise SCHEMA_VIOLATION;
- `compare_previous(...)`: build-only checks against the previous release (+ a change report).

An issue is a dict {code, severity, path, target?, acknowledged?, detail?}. Paths use the shared
notation `groups[0].questions[3].options[1].furigana`.
"""
import re

from fingerprint import SUPPORTED_SCHEMA_VERSIONS as SOURCE_SCHEMA_VERSIONS
from fingerprint import SourceIndex, verify
from furigana_markup import base_text, has_kanji, is_valid

FORMAT = "n3sensei-contrast-pack"
SUPPORTED_SCHEMA_VERSIONS = {1}
BLANK = "＿＿＿"
MAX_ITEMS = 4
MAX_OPTIONS = 6
CONTENT_TYPES = ("GRAMMAR", "VOCABULARY", "KANJI")
DETAIL_KEYS = {"grammar": "GRAMMAR", "vocabulary": "VOCABULARY", "kanji": "KANJI"}
STATUSES = ("DRAFT", "AI_GENERATED", "REVIEWING", "APPROVED", "PUBLISHED", "ARCHIVED")
QUESTION_TYPES = ("MULTIPLE_CHOICE", "ERROR_DETECTION", "FILL_IN_THE_BLANK", "CONTEXT_SELECTION")

ERROR, WARNING, INFO = "ERROR", "WARNING", "INFO"
BUILD, IMPORT = "build", "import"

CODES = (
    "INVALID_JSON", "SCHEMA_VIOLATION", "UNSUPPORTED_FORMAT", "MISSING_METADATA", "INVALID_CONTENT_VERSION",
    "OLDER_OR_SAME_VERSION", "STATUS_NOT_PUBLISHED", "DUPLICATE_GROUP_ID", "DUPLICATE_ITEM_ID", "DUPLICATE_ID",
    "DUPLICATE_SOURCE_IN_GROUP", "UNKNOWN_ITEM_REFERENCE", "ITEM_TYPE_MISMATCH", "SOURCE_NOT_FOUND", "SOURCE_CHANGED",
    "SOURCE_AMBIGUOUS", "SOURCE_PACK_NOT_LISTED", "SOURCE_SCHEMA_UNSUPPORTED", "MISSING_CORRECT_ANSWER",
    "CORRECT_OPTION_NOT_IN_OPTIONS", "TOO_FEW_OPTIONS", "DUPLICATE_OPTION", "UNSUPPORTED_QUESTION_TYPE",
    "TARGET_NOT_CORRECT_OPTION", "BLANK_MARKER_INVALID", "SEGMENTS_NOT_SENTENCE", "MISSING_EXPLANATION", "NO_QUESTIONS",
    "TOO_FEW_ITEMS", "FURIGANA_INVALID", "FURIGANA_MISMATCH", "FURIGANA_MISSING", "ANSWER_CHANGED_SAME_ID",
    "GROUP_DISAPPEARED", "FEW_OPTIONS", "EMPTY_SECTION", "QUESTION_TYPES_INCOMPLETE", "LICENSE_UNVERIFIED",
    "ITEMS_CHANGED_SAME_GROUP_ID", "REVISION_NOT_INCREMENTED",
)
MODE_DEPENDENT = {"SOURCE_NOT_FOUND", "SOURCE_CHANGED", "SOURCE_AMBIGUOUS", "SOURCE_SCHEMA_UNSUPPORTED", "GROUP_DISAPPEARED"}
ACKNOWLEDGEABLE = {"FURIGANA_MISSING", "FEW_OPTIONS", "EMPTY_SECTION", "QUESTION_TYPES_INCOMPLETE"}
GROUP_META = ("id", "revision", "status", "contentType", "jlptLevel", "title", "summaryVi", "updatedAt", "approval")
_GROUP_INDEX = re.compile(r"^groups\[(\d+)\]")


def _blank(value) -> bool:
    return value is None or (isinstance(value, str) and not value.strip())


def _is_int(value) -> bool:
    return isinstance(value, int) and not isinstance(value, bool)


def _dicts(value) -> list:
    """The dict entries of a list (anything else is the schema's business)."""
    return [(i, v) for i, v in enumerate(value) if isinstance(v, dict)] if isinstance(value, list) else []


class Issues:
    def __init__(self, mode: str):
        self.mode = mode
        self.items: list[dict] = []
        self._seen: set = set()

    def add(self, code: str, path: str, severity: str = ERROR, target: str | None = None, detail: str | None = None):
        assert code in CODES, code
        if code in MODE_DEPENDENT:
            severity = ERROR if self.mode == BUILD else WARNING
        if (code, path) in self._seen:
            return
        self._seen.add((code, path))
        issue = {"code": code, "severity": severity, "path": path}
        if target is not None:
            issue["target"] = target
        if detail:
            issue["detail"] = detail
        self.items.append(issue)

    def warn(self, code: str, path: str, target: str):
        self.add(code, path, WARNING, target)


def semantic(pack: dict, mode: str = BUILD, sources: SourceIndex | None = None, installed: dict | None = None,
             previous: dict | None = None) -> list[dict]:
    """The rules shared with the app. `installed` = {"id", "contentVersion", "groupIds"} of the pack on the device;
    `previous` = the previous release (build) or the installed content (import), for the revision rules."""
    out = Issues(mode)
    if pack.get("format") != FORMAT:
        out.add("UNSUPPORTED_FORMAT", "format")
    if pack.get("schemaVersion") not in SUPPORTED_SCHEMA_VERSIONS or not _is_int(pack.get("schemaVersion")):
        out.add("UNSUPPORTED_FORMAT", "schemaVersion")
    if out.items:
        return out.items

    _check_pack(pack, out, installed)
    source_pack_ids = {p.get("id") for _, p in _dicts(pack.get("sourcePacks"))}
    group_ids: set = set()
    item_ids: set = set()
    other_ids: set = set()
    for i, group in _dicts(pack.get("groups")):
        _check_group(i, group, out, source_pack_ids, group_ids, item_ids, other_ids, sources)
    for k, retired in _dicts(pack.get("retiredGroups")):
        rid = retired.get("id")
        if rid in group_ids:
            out.add("DUPLICATE_GROUP_ID", f"retiredGroups[{k}].id")
        group_ids.add(rid)
    if installed is not None:
        present = {g.get("id") for _, g in _dicts(pack.get("groups")) if g.get("id") is not None}
        present |= {r.get("id") for _, r in _dicts(pack.get("retiredGroups"))}
        for gid in sorted(set(installed["groupIds"]) - present):
            out.add("GROUP_DISAPPEARED", f"groups[id={gid}]")
    if previous is not None:
        _check_revisions(pack, previous, out)
    return acknowledge(out.items, pack)


# Fields under a group's `revision` (everything but identity, lifecycle and its questions, which
# have their own revision) and under a question's `revision` (everything but id and revision).
_GROUP_UNREVISIONED = {"id", "revision", "status", "approval", "updatedAt", "questions"}
_QUESTION_UNREVISIONED = {"id", "revision"}


def _strip_none(value):
    """JSON null and an absent optional field mean the same (as in the app's models)."""
    if isinstance(value, dict):
        return {k: _strip_none(v) for k, v in value.items() if v is not None}
    if isinstance(value, list):
        return [_strip_none(v) for v in value]
    return value


def _revision_ok(current, previous, changed: bool) -> bool:
    if not (_is_int(current) and _is_int(previous)):
        return True  # a missing revision is reported elsewhere
    return current > previous if changed else current >= previous


def _check_revisions(pack: dict, previous: dict, out: Issues):
    """ITEMS_CHANGED_SAME_GROUP_ID and REVISION_NOT_INCREMENTED (both ERROR, build and import)."""
    prev_groups = {g.get("id"): g for _, g in _dicts(previous.get("groups"))}
    prev_questions = {q.get("id"): q for _, g in _dicts(previous.get("groups")) for _, q in _dicts(g.get("questions"))}
    for i, group in _dicts(pack.get("groups")):
        prev = prev_groups.get(group.get("id"))
        if prev is None:
            continue
        g = f"groups[{i}]"
        if {it.get("id") for _, it in _dicts(group.get("items"))} != {it.get("id") for _, it in _dicts(prev.get("items"))}:
            out.add("ITEMS_CHANGED_SAME_GROUP_ID", f"{g}.items")
        body = _strip_none({k: v for k, v in group.items() if k not in _GROUP_UNREVISIONED})
        prev_body = _strip_none({k: v for k, v in prev.items() if k not in _GROUP_UNREVISIONED})
        if not _revision_ok(group.get("revision"), prev.get("revision"), body != prev_body):
            out.add("REVISION_NOT_INCREMENTED", f"{g}.revision")
        for k, q in _dicts(group.get("questions")):
            prev_q = prev_questions.get(q.get("id"))
            if prev_q is None:
                continue
            q_body = _strip_none({key: v for key, v in q.items() if key not in _QUESTION_UNREVISIONED})
            prev_q_body = _strip_none({key: v for key, v in prev_q.items() if key not in _QUESTION_UNREVISIONED})
            if not _revision_ok(q.get("revision"), prev_q.get("revision"), q_body != prev_q_body):
                out.add("REVISION_NOT_INCREMENTED", f"{g}.questions[{k}].revision")


def _check_pack(pack: dict, out: Issues, installed: dict | None):
    meta = pack.get("pack")
    if not isinstance(meta, dict):
        out.add("MISSING_METADATA", "pack")
        return
    for name in ("id", "title", "jlptLevel", "publishedAt"):
        if _blank(meta.get(name)):
            out.add("MISSING_METADATA", f"pack.{name}")
    license_ = meta.get("license")
    if not isinstance(license_, dict):
        out.add("MISSING_METADATA", "pack.license")
    else:
        if _blank(license_.get("text")):
            out.add("MISSING_METADATA", "pack.license.text")
        if license_.get("licenseVerified") is None:
            out.add("MISSING_METADATA", "pack.license.licenseVerified")
        if license_.get("licenseVerified") is False:
            out.add("LICENSE_UNVERIFIED", "pack.license.licenseVerified", INFO)
    version = meta.get("contentVersion")
    if version is None:
        out.add("MISSING_METADATA", "pack.contentVersion")
    elif _is_int(version):
        if version < 1:
            out.add("INVALID_CONTENT_VERSION", "pack.contentVersion")
        elif installed is not None and installed["id"] == meta.get("id") and installed["contentVersion"] >= version:
            out.add("OLDER_OR_SAME_VERSION", "pack.contentVersion", INFO)


def _check_group(i, group, out, source_pack_ids, group_ids, item_ids, other_ids, sources):
    g = f"groups[{i}]"
    gid = group.get("id")
    if _blank(gid):
        out.add("MISSING_METADATA", f"{g}.id")
    elif gid in group_ids:
        out.add("DUPLICATE_GROUP_ID", f"{g}.id")
    else:
        group_ids.add(gid)
    if group.get("revision") is None:
        out.add("MISSING_METADATA", f"{g}.revision")
    for name in ("jlptLevel", "title", "summaryVi", "updatedAt"):
        if _blank(group.get(name)):
            out.add("MISSING_METADATA", f"{g}.{name}")
    status = group.get("status")
    if status is None:
        out.add("MISSING_METADATA", f"{g}.status")
    else:
        if status not in STATUSES:
            out.add("SCHEMA_VIOLATION", f"{g}.status")
        if status != "PUBLISHED":
            out.add("STATUS_NOT_PUBLISHED", f"{g}.status")
    approval = group.get("approval")
    if approval is None and status in ("APPROVED", "PUBLISHED"):
        out.add("MISSING_METADATA", f"{g}.approval")
    if isinstance(approval, dict) and _is_int(approval.get("approvedRevision")) and _is_int(group.get("revision")) \
            and approval["approvedRevision"] != group["revision"]:
        # The approval is for another revision: this revision has not been approved.
        out.add("MISSING_METADATA", f"{g}.approval.approvedRevision")
    content_type = group.get("contentType")
    if content_type is None:
        out.add("MISSING_METADATA", f"{g}.contentType")
    elif content_type not in CONTENT_TYPES:
        out.add("SCHEMA_VIOLATION", f"{g}.contentType")
        content_type = None

    items = group.get("items") if isinstance(group.get("items"), list) else None
    if items is None or len(items) < 2:
        out.add("TOO_FEW_ITEMS", f"{g}.items")
    elif len(items) > MAX_ITEMS:
        out.add("SCHEMA_VIOLATION", f"{g}.items")
    group_item_ids = {it.get("id") for _, it in _dicts(items)}
    sources_in_group: set = set()
    for j, item in _dicts(items):
        p = f"{g}.items[{j}]"
        if item.get("id") in item_ids:
            out.add("DUPLICATE_ITEM_ID", f"{p}.id")
        item_ids.add(item.get("id"))
        blocks = [DETAIL_KEYS[k] for k in DETAIL_KEYS if isinstance(item.get(k), dict)]
        source = item.get("source") if isinstance(item.get("source"), dict) else {}
        source_type = source.get("sourceType")
        if source_type not in CONTENT_TYPES:
            out.add("SCHEMA_VIOLATION", f"{p}.source.sourceType")
            source_type = None
        mismatch = len(blocks) != 1 or (content_type is not None and (blocks[0] != content_type or (source_type is not None and source_type != content_type)))
        if mismatch:
            out.add("ITEM_TYPE_MISMATCH", p)
        if item.get("labelFurigana") is not None:
            _furigana(out, item.get("label", ""), item.get("labelFurigana"), f"{p}.labelFurigana", "silent", None)
        vocabulary = item.get("vocabulary") if isinstance(item.get("vocabulary"), dict) else {}
        for c, col in _dicts(vocabulary.get("collocations")):
            _furigana(out, col.get("japanese", ""), col.get("furigana"), f"{p}.vocabulary.collocations[{c}].furigana", "required", None)
        if source.get("sourcePackId") not in source_pack_ids:
            out.add("SOURCE_PACK_NOT_LISTED", f"{p}.source")
        key = (source.get("sourceType"), source.get("sourceContentId"))
        if key in sources_in_group:
            out.add("DUPLICATE_SOURCE_IN_GROUP", f"{p}.source")
        sources_in_group.add(key)
        if not mismatch and source_type is not None:
            _check_source(out, source, f"{p}.source", sources)

    theory = group.get("theory") if isinstance(group.get("theory"), list) else None
    if not theory:
        out.add("MISSING_EXPLANATION", f"{g}.theory")
    for k, t in _dicts(theory):
        _unique(out, other_ids, t.get("id"), f"{g}.theory[{k}].id")

    comparison = group.get("comparison")
    if comparison is None:
        out.add("SCHEMA_VIOLATION", f"{g}.comparison")
    for r, row in _dicts(comparison):
        values = row.get("values") if isinstance(row.get("values"), dict) else {}
        if len(values) < 2:
            out.add("SCHEMA_VIOLATION", f"{g}.comparison[{r}].values")
        for key in sorted(values):
            if key not in group_item_ids:
                out.add("UNKNOWN_ITEM_REFERENCE", f"{g}.comparison[{r}].values.{key}")

    group_id = gid or ""
    examples = group.get("examples")
    if examples is None:
        out.add("SCHEMA_VIOLATION", f"{g}.examples")
    elif examples == []:
        out.warn("EMPTY_SECTION", f"{g}.examples", group_id)
    for k, e in _dicts(examples):
        p = f"{g}.examples[{k}]"
        _unique(out, other_ids, e.get("id"), f"{p}.id")
        if e.get("itemId") not in group_item_ids:
            out.add("UNKNOWN_ITEM_REFERENCE", f"{p}.itemId")
        _furigana(out, e.get("japanese", ""), e.get("furigana"), f"{p}.furigana", "required", None)

    tips = group.get("memoryTips")
    if tips is None:
        out.add("SCHEMA_VIOLATION", f"{g}.memoryTips")
    elif tips == []:
        out.warn("EMPTY_SECTION", f"{g}.memoryTips", group_id)
    for k, t in _dicts(tips):
        _unique(out, other_ids, t.get("id"), f"{g}.memoryTips[{k}].id")

    mistakes = group.get("commonMistakes")
    if mistakes is None:
        out.add("SCHEMA_VIOLATION", f"{g}.commonMistakes")
    elif mistakes == []:
        out.warn("EMPTY_SECTION", f"{g}.commonMistakes", group_id)
    for m, mistake in _dicts(mistakes):
        p = f"{g}.commonMistakes[{m}]"
        _unique(out, other_ids, mistake.get("id"), f"{p}.id")
        for side in ("wrong", "correct"):
            sentence = mistake.get(side) if isinstance(mistake.get(side), dict) else {}
            _furigana(out, sentence.get("japanese", ""), sentence.get("furigana"), f"{p}.{side}.furigana", "required", None)
        expected, confused = mistake.get("expectedItemId"), mistake.get("confusedWithItemId")
        if (expected is None) != (confused is None):
            out.add("SCHEMA_VIOLATION", p)
        if expected is not None and expected not in group_item_ids:
            out.add("UNKNOWN_ITEM_REFERENCE", f"{p}.expectedItemId")
        if confused is not None and confused not in group_item_ids:
            out.add("UNKNOWN_ITEM_REFERENCE", f"{p}.confusedWithItemId")

    questions = group.get("questions") if isinstance(group.get("questions"), list) else None
    if not questions:
        out.add("NO_QUESTIONS", f"{g}.questions")
    else:
        for k, q in _dicts(questions):
            _check_question(out, f"{g}.questions[{k}]", q, group_item_ids, other_ids)
        types = {q.get("type") for _, q in _dicts(questions) if q.get("type") in QUESTION_TYPES}
        if len(types) < len(QUESTION_TYPES):
            out.warn("QUESTION_TYPES_INCOMPLETE", f"{g}.questions", group_id)


def _unique(out: Issues, seen: set, value, path: str):
    if value in seen:
        out.add("DUPLICATE_ID", path)
    seen.add(value)


def _check_source(out: Issues, source: dict, path: str, sources: SourceIndex | None):
    if source.get("sourceSchemaVersion") not in SOURCE_SCHEMA_VERSIONS:
        out.add("SOURCE_SCHEMA_UNSUPPORTED", path)
        return
    if sources is None:
        return
    status, candidates = verify(source, sources)
    if status == "SOURCE_CHANGED":
        out.add("SOURCE_CHANGED", path)
    elif status == "SOURCE_MISSING":
        out.add("SOURCE_NOT_FOUND", path, detail=", ".join(candidates) or None)
    elif status == "AMBIGUOUS":
        out.add("SOURCE_AMBIGUOUS", path, detail=", ".join(candidates))


def _check_question(out: Issues, q: str, question: dict, group_item_ids: set, other_ids: set):
    _unique(out, other_ids, question.get("id"), f"{q}.id")
    qtype = question.get("type")
    if qtype is None:
        out.add("SCHEMA_VIOLATION", f"{q}.type")
    elif qtype not in QUESTION_TYPES:
        out.add("UNSUPPORTED_QUESTION_TYPE", f"{q}.type")
        qtype = None
    difficulty = question.get("difficulty")
    if not _is_int(difficulty) or not 1 <= difficulty <= 3:
        out.add("SCHEMA_VIOLATION", f"{q}.difficulty")
    target = question.get("targetItemId")
    target_known = target in group_item_ids
    if not target_known:
        out.add("UNKNOWN_ITEM_REFERENCE", f"{q}.targetItemId")
    if _blank(question.get("explanationVi")):
        out.add("MISSING_EXPLANATION", f"{q}.explanationVi")

    options = question.get("options") if isinstance(question.get("options"), list) else []
    if len(options) < (3 if qtype == "ERROR_DETECTION" else 2):
        out.add("TOO_FEW_OPTIONS", f"{q}.options")
    if len(options) > MAX_OPTIONS:
        out.add("SCHEMA_VIOLATION", f"{q}.options")
    option_ids: set = set()
    option_texts: set = set()
    for o, option in _dicts(options):
        p = f"{q}.options[{o}]"
        new_id = option.get("id") not in option_ids
        new_text = option.get("text") not in option_texts
        option_ids.add(option.get("id"))
        option_texts.add(option.get("text"))
        if not new_id or not new_text:
            out.add("DUPLICATE_OPTION", p)
        if option.get("itemId") is not None and option.get("itemId") not in group_item_ids:
            out.add("UNKNOWN_ITEM_REFERENCE", f"{p}.itemId")
        _furigana(out, option.get("text", ""), option.get("furigana"), f"{p}.furigana", "warn", f"{question.get('id')}/{option.get('id')}")
    correct_id = question.get("correctOptionId")
    correct = next((o for _, o in _dicts(options) if o.get("id") == correct_id), None)
    if _blank(correct_id):
        out.add("MISSING_CORRECT_ANSWER", f"{q}.correctOptionId")
    elif correct is None:
        out.add("CORRECT_OPTION_NOT_IN_OPTIONS", f"{q}.correctOptionId")

    prompt = question.get("prompt") if isinstance(question.get("prompt"), dict) else None
    context = question.get("context") if isinstance(question.get("context"), dict) else None
    correction = question.get("correction") if isinstance(question.get("correction"), dict) else None
    if prompt is not None:
        _furigana(out, prompt.get("japanese", ""), prompt.get("furigana"), f"{q}.prompt.furigana", "required", None)
    if context is not None and context.get("japanese") is not None:
        _furigana(out, context["japanese"], context.get("furigana"), f"{q}.context.furigana", "warn", question.get("id"))
    if correction is not None:
        _furigana(out, correction.get("text", ""), correction.get("furigana"), f"{q}.correction.furigana", "warn", question.get("id"))

    if qtype == "FILL_IN_THE_BLANK":
        if prompt is None:
            out.add("SCHEMA_VIOLATION", f"{q}.prompt")
        elif prompt.get("japanese", "").count(BLANK) != 1:
            out.add("BLANK_MARKER_INVALID", f"{q}.prompt.japanese")
    elif qtype == "ERROR_DETECTION":
        if prompt is None:
            out.add("SCHEMA_VIOLATION", f"{q}.prompt")
        elif "".join(o.get("text", "") for _, o in _dicts(options)) != prompt.get("japanese"):
            out.add("SEGMENTS_NOT_SENTENCE", f"{q}.options")
        if correction is None:
            out.add("SCHEMA_VIOLATION", f"{q}.correction")
        elif correction.get("itemId") is not None and correction.get("itemId") not in group_item_ids:
            out.add("UNKNOWN_ITEM_REFERENCE", f"{q}.correction.itemId")
        elif target_known and correction.get("itemId") != target:
            out.add("TARGET_NOT_CORRECT_OPTION", f"{q}.correction.itemId")
    elif qtype == "CONTEXT_SELECTION":
        if context is None:
            out.add("SCHEMA_VIOLATION", f"{q}.context")
    if qtype is not None and qtype != "ERROR_DETECTION" and target_known and correct is not None and correct.get("itemId") != target:
        out.add("TARGET_NOT_CORRECT_OPTION", f"{q}.targetItemId")
    if qtype in ("MULTIPLE_CHOICE", "CONTEXT_SELECTION") and len(options) == 2:
        out.warn("FEW_OPTIONS", f"{q}.options", question.get("id"))


def _furigana(out: Issues, text: str, furigana, path: str, policy: str, target):
    if furigana is None:
        if not has_kanji(text):
            return
        if policy == "required":
            out.add("FURIGANA_MISSING", path)
        elif policy == "warn":
            out.warn("FURIGANA_MISSING", path, target)
        return
    if not isinstance(furigana, str) or not is_valid(furigana):
        out.add("FURIGANA_INVALID", path)
    elif base_text(furigana) != text:
        out.add("FURIGANA_MISMATCH", path)


def acknowledge(issues: list[dict], pack: dict) -> list[dict]:
    """Sets `acknowledged` on each acknowledgeable WARNING, from its group's approval.acknowledgedWarnings."""
    groups = pack.get("groups") if isinstance(pack.get("groups"), list) else []
    for issue in issues:
        if issue["severity"] != WARNING or issue["code"] not in ACKNOWLEDGEABLE or "target" not in issue:
            continue
        match = _GROUP_INDEX.match(issue["path"])
        group = groups[int(match.group(1))] if match and int(match.group(1)) < len(groups) else {}
        approval = group.get("approval") if isinstance(group, dict) and isinstance(group.get("approval"), dict) else {}
        acks = [a for a in approval.get("acknowledgedWarnings", []) if isinstance(a, dict)]
        issue["acknowledged"] = any(a.get("code") == issue["code"] and a.get("target") == issue["target"] for a in acks)
    return issues


# ---- JSON Schema ---------------------------------------------------------------------------------

def _path(parts) -> str:
    out = ""
    for part in parts:
        out += f"[{part}]" if isinstance(part, int) else (f".{part}" if out else str(part))
    return out


def _join(parent: str, field: str) -> str:
    return f"{parent}.{field}" if parent else field


_GROUP = re.compile(r"^groups\[\d+\]$")
_QUESTION = re.compile(r"^groups\[\d+\]\.questions\[\d+\]$")
_METADATA_TEXT = re.compile(r"^(pack\.(id|title|jlptLevel|publishedAt|license\.text)|groups\[\d+\]\.(id|jlptLevel|title|summaryVi|updatedAt))$")


def _map_schema_error(error) -> list[tuple[str, str]]:
    parent = _path(error.absolute_path)
    keyword = error.validator
    if keyword == "required":
        field = re.match(r"'([^']+)' is a required property", error.message).group(1)
        path = _join(parent, field)
        if parent == "" and field in ("format", "schemaVersion"):
            return [("UNSUPPORTED_FORMAT", path)]
        if parent in ("", "pack", "pack.license") and (parent != "" or field == "pack") or re.match(r"^groups\[\d+\]\.approval$", parent):
            return [("MISSING_METADATA", path)]
        if _GROUP.match(parent):
            if field in GROUP_META:
                return [("MISSING_METADATA", path)]
            return [({"items": "TOO_FEW_ITEMS", "questions": "NO_QUESTIONS", "theory": "MISSING_EXPLANATION"}.get(field, "SCHEMA_VIOLATION"), path)]
        if _QUESTION.match(parent):
            return [({"explanationVi": "MISSING_EXPLANATION", "correctOptionId": "MISSING_CORRECT_ANSWER"}.get(field, "SCHEMA_VIOLATION"), path)]
        return [("SCHEMA_VIOLATION", path)]
    if keyword == "minItems":
        for suffix, code in ((".items", "TOO_FEW_ITEMS"), (".questions", "NO_QUESTIONS"), (".theory", "MISSING_EXPLANATION"), (".options", "TOO_FEW_OPTIONS")):
            if parent.endswith(suffix) and parent.startswith("groups["):
                return [(code, parent)]
        return [("SCHEMA_VIOLATION", parent)]
    if keyword == "enum" and re.match(r"^groups\[\d+\]\.questions\[\d+\]\.type$", parent):
        return [("UNSUPPORTED_QUESTION_TYPE", parent)]
    if keyword == "const":
        if parent in ("format", "schemaVersion"):
            return [("UNSUPPORTED_FORMAT", parent)]
        if parent.endswith(".source.sourceSchemaVersion"):
            return [("SOURCE_SCHEMA_UNSUPPORTED", parent.rsplit(".", 1)[0])]
    if keyword in ("pattern", "minLength"):
        blank = isinstance(error.instance, str) and not error.instance.strip()
        if blank and _METADATA_TEXT.match(parent):
            return [("MISSING_METADATA", parent)]
        if blank and parent.endswith(".explanationVi") and _QUESTION.match(parent.rsplit(".", 1)[0]):
            return [("MISSING_EXPLANATION", parent)]
        if keyword == "pattern" and parent.endswith(".prompt.japanese") and error.validator_value == BLANK:
            return [("BLANK_MARKER_INVALID", parent)]
    if keyword == "minimum" and parent == "pack.contentVersion":
        return [("INVALID_CONTENT_VERSION", parent)]
    if keyword == "oneOf" and re.match(r"^groups\[\d+\]\.items\[\d+\]$", parent):
        return [("ITEM_TYPE_MISMATCH", parent)]
    if keyword == "additionalProperties" and isinstance(error.instance, dict):
        allowed = set(error.schema.get("properties", {}))
        return [("SCHEMA_VIOLATION", _join(parent, key)) for key in sorted(set(error.instance) - allowed)]
    return [("SCHEMA_VIOLATION", parent)]


def schema_issues(pack, schema: dict, mode: str = BUILD) -> list[dict]:
    try:
        import jsonschema
    except ImportError as e:  # pragma: no cover - environment problem, reported by the CLI
        raise SystemExit("jsonschema is required: pip install jsonschema (run without python -I)") from e
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    out = Issues(mode)
    for error in sorted(validator.iter_errors(pack), key=lambda e: (_path(e.absolute_path), e.validator, e.message)):
        for code, path in _map_schema_error(error):
            out.add(code, path, detail=None if code != "SCHEMA_VIOLATION" else error.message[:200])
    return out.items


# ---- Previous release (build only) ---------------------------------------------------------------

def _groups_by_id(pack: dict) -> dict:
    return {g.get("id"): (i, g) for i, g in _dicts(pack.get("groups"))}


def _answer_key(question: dict):
    correct = next((o for _, o in _dicts(question.get("options")) if o.get("id") == question.get("correctOptionId")), {})
    return question.get("type"), question.get("targetItemId"), correct.get("text")


def compare_previous(pack: dict, previous: dict) -> tuple[list[dict], dict]:
    """ERRORs that need the previous release, and a change report. Never moves progress: it only reports."""
    out = Issues(BUILD)
    prev_meta = previous.get("pack") or {}
    meta = pack.get("pack") or {}
    cur_v, prev_v = meta.get("contentVersion"), prev_meta.get("contentVersion")
    if _is_int(cur_v) and cur_v >= 1 and _is_int(prev_v) and cur_v <= prev_v:
        out.add("INVALID_CONTENT_VERSION", "pack.contentVersion", detail=f"previous release is {prev_v}")
    cur_groups, prev_groups = _groups_by_id(pack), _groups_by_id(previous)
    retired = {r.get("id"): r for _, r in _dicts(pack.get("retiredGroups"))}
    for gid in sorted(set(prev_groups) - set(cur_groups) - set(retired)):
        out.add("GROUP_DISAPPEARED", f"groups[id={gid}]")

    diff = {"previousVersion": prev_v, "contentVersion": cur_v, "groupsAdded": sorted(set(cur_groups) - set(prev_groups)),
            "groupsRetired": sorted(set(prev_groups) & set(retired)), "groupsChanged": []}
    for gid in sorted(set(cur_groups) & set(prev_groups)):
        i, cur = cur_groups[gid]
        _, prev = prev_groups[gid]
        change: dict = {"groupId": gid}
        cur_items = {it.get("id"): it for _, it in _dicts(cur.get("items"))}
        prev_items = {it.get("id"): it for _, it in _dicts(prev.get("items"))}
        if set(cur_items) != set(prev_items):
            # Also an ERROR: ITEMS_CHANGED_SAME_GROUP_ID (a group with other items needs a new id).
            change["itemsAdded"] = sorted(set(cur_items) - set(prev_items))
            change["itemsRemoved"] = sorted(set(prev_items) - set(cur_items))
        fingerprints = [iid for iid in sorted(set(cur_items) & set(prev_items))
                        if (cur_items[iid].get("source") or {}).get("sourceFingerprint") != (prev_items[iid].get("source") or {}).get("sourceFingerprint")]
        if fingerprints:
            change["sourceFingerprintChanged"] = fingerprints
        if cur.get("status") != prev.get("status"):
            change["status"] = [prev.get("status"), cur.get("status")]
        cur_q = {q.get("id"): (k, q) for k, q in _dicts(cur.get("questions"))}
        prev_q = {q.get("id"): q for _, q in _dicts(prev.get("questions"))}
        if set(cur_q) - set(prev_q):
            change["questionsAdded"] = sorted(set(cur_q) - set(prev_q))
        if set(prev_q) - set(cur_q):
            change["questionsRemoved"] = sorted(set(prev_q) - set(cur_q))
        for qid in sorted(set(cur_q) & set(prev_q)):
            k, q = cur_q[qid]
            if _answer_key(q) != _answer_key(prev_q[qid]):
                out.add("ANSWER_CHANGED_SAME_ID", f"groups[{i}].questions[{k}]")
        if cur.get("revision") != prev.get("revision"):
            change["revision"] = [prev.get("revision"), cur.get("revision")]
        if len(change) > 1:
            diff["groupsChanged"].append(change)
    return out.items, diff


# ---- Everything ------------------------------------------------------------------------------------

def validate(pack, schema: dict, mode: str = BUILD, sources: SourceIndex | None = None, previous: dict | None = None) -> tuple[list[dict], dict | None]:
    """All issues for `mode` (BUILD: schema + semantic + previous release; IMPORT: what the app's parser
    and validator report, with `previous` as the installed pack) and the change report (BUILD with previous)."""
    if not isinstance(pack, dict):
        return [{"code": "SCHEMA_VIOLATION", "severity": ERROR, "path": ""}], None
    installed = None
    if mode == IMPORT and previous is not None:
        installed = {"id": (previous.get("pack") or {}).get("id"), "contentVersion": (previous.get("pack") or {}).get("contentVersion"),
                     "groupIds": [g.get("id") for _, g in _dicts(previous.get("groups"))]}
    issues = semantic(pack, mode, sources, installed, previous)
    if any(i["code"] == "UNSUPPORTED_FORMAT" for i in issues):
        return issues, None
    diff = None
    # The structure is checked in both modes: the app's JSON parser (ContrastPackParser) reports the
    # same SCHEMA_VIOLATIONs at the same paths.
    seen = {(i["code"], i["path"]) for i in issues}
    for issue in schema_issues(pack, schema, mode):
        if (issue["code"], issue["path"]) not in seen:
            seen.add((issue["code"], issue["path"]))
            issues.append(issue)
    if mode == BUILD:
        if previous is not None:
            extra, diff = compare_previous(pack, previous)
            for issue in extra:
                if (issue["code"], issue["path"]) not in seen:
                    seen.add((issue["code"], issue["path"]))
                    issues.append(issue)
    acknowledge(issues, pack)
    return issues, diff


def approvals(pack: dict) -> list[dict]:
    """Who approved each group, and whether the author approved their own work (allowed, reported openly)."""
    out = []
    for _, group in _dicts(pack.get("groups")):
        approval = group.get("approval") if isinstance(group.get("approval"), dict) else None
        if approval is None:
            continue
        out.append({"groupId": group.get("id"), "revision": group.get("revision"), "approvedRevision": approval.get("approvedRevision"),
                    "authoredBy": approval.get("authoredBy"), "approvedBy": approval.get("approvedBy"), "approvedAt": approval.get("approvedAt"),
                    "selfApproved": approval.get("authoredBy") == approval.get("approvedBy")})
    return out


def reviewer_issues(pack: dict, reviewers: set) -> list[dict]:
    """A release needs approvals by registered reviewers: an id outside the registry (e.g. the sample's
    illustrative "reviewer-01") is not evidence of review."""
    out = Issues(BUILD)
    for i, group in _dicts(pack.get("groups")):
        approval = group.get("approval") if isinstance(group.get("approval"), dict) else None
        if approval is not None and approval.get("approvedBy") not in reviewers:
            out.add("MISSING_METADATA", f"groups[{i}].approval.approvedBy", detail="người duyệt không có trong danh sách người duyệt")
    return out.items


def failed(issues: list[dict], strict: bool) -> bool:
    """ERROR always fails; with `strict`, an unacknowledged editorial WARNING fails too (its severity is unchanged)."""
    return any(i["severity"] == ERROR for i in issues) or (strict and any(i.get("acknowledged") is False for i in issues))
