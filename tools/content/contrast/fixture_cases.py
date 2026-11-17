"""Loads the shared fixture cases (fixtures/cases/*.json), the same way the Kotlin test does
(app/src/test/.../domain/contrast/ContrastSharedFixturesTest.kt).

A case = a base pack (repo-relative path) + a JSON Patch subset (RFC 6902: add, remove, replace,
move) + optional `previous` (previous release for a build, installed pack for an import) +
optional `sources` (default fixtures/sources.json) + hand-written `expect.build` / `expect.import`.
"""
import copy
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
FIXTURES = HERE / "fixtures"
DEFAULT_SOURCES = "tools/content/contrast/fixtures/sources.json"


def _tokens(pointer: str) -> list[str]:
    if not pointer.startswith("/"):
        raise ValueError(f"bad pointer {pointer!r}")
    return [t.replace("~1", "/").replace("~0", "~") for t in pointer[1:].split("/")]


def _parent(doc, pointer: str):
    *parents, last = _tokens(pointer)
    node = doc
    for token in parents:
        node = node[int(token)] if isinstance(node, list) else node[token]
    return node, last


def _remove(doc, pointer: str):
    node, last = _parent(doc, pointer)
    return node.pop(int(last)) if isinstance(node, list) else node.pop(last)


def _add(doc, pointer: str, value):
    node, last = _parent(doc, pointer)
    if isinstance(node, list):
        node.append(value) if last == "-" else node.insert(int(last), value)
    else:
        node[last] = value


def apply_patch(doc, patch: list[dict]):
    doc = copy.deepcopy(doc)
    for op in patch:
        kind = op["op"]
        if kind == "add":
            _add(doc, op["path"], copy.deepcopy(op["value"]))
        elif kind == "remove":
            _remove(doc, op["path"])
        elif kind == "replace":
            node, last = _parent(doc, op["path"])
            if isinstance(node, list):
                node[int(last)] = copy.deepcopy(op["value"])
            else:
                if last not in node:
                    raise KeyError(op["path"])
                node[last] = copy.deepcopy(op["value"])
        elif kind == "move":
            _add(doc, op["path"], _remove(doc, op["from"]))
        else:
            raise ValueError(f"unsupported op {kind}")
    return doc


def load_json(repo_path: str):
    return json.loads((REPO / repo_path).read_text(encoding="utf-8"))


def load_case(path: Path) -> dict:
    case = json.loads(Path(path).read_text(encoding="utf-8"))
    case["name"] = Path(path).stem
    case["pack"] = apply_patch(load_json(case["base"]), case.get("patch", []))
    previous = case.get("previous")
    case["previousPack"] = apply_patch(load_json(previous["base"]), previous.get("patch", [])) if previous else None
    case["sourcesPath"] = REPO / case.get("sources", DEFAULT_SOURCES)
    return case


def cases() -> list[dict]:
    return [load_case(p) for p in sorted((FIXTURES / "cases").glob("*.json"))]


def comparable(issues) -> list[tuple]:
    """Order-independent form of an issue list: (severity, code, path, acknowledged)."""
    return sorted((i["severity"], i["code"], i["path"], i.get("acknowledged")) for i in issues)
