"""Contrast Lab content packs: check a pack, or build a release pack from approved drafts.

Docs: docs/CONTRAST_LAB_TOOLING.md. Needs the `jsonschema` package (run with python3, not python3 -I,
if it is installed in the user site).

Check a pack (writes nothing):
    python3 tools/content/contrast/build_pack.py --check PACK.json [--strict] \
        [--sources app/src/main/assets/content] [--previous releases/contrast-n3.v1.json] \
        [--mode build|import] [--report report.json]

Build a release pack from drafts (always strict; writes only when there is no ERROR, every
editorial WARNING is acknowledged and every approval is by a registered reviewer):
    python3 tools/content/contrast/build_pack.py --drafts drafts/ --meta release.json --output PACK.json \
        --reviewers reviewers.json [--sources app/src/main/assets/content] [--previous releases/contrast-n3.v1.json] [--report report.json]

reviewers.json: {"reviewers": ["<id>", ...]}: the ids of the people who really review content.

Exit codes: 0 passed, 1 validation failed, 2 usage or input error.
"""
import argparse
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

from fingerprint import SourceIndex  # noqa: E402
from validator import BUILD, ERROR, IMPORT, WARNING, approvals, failed, reviewer_issues, validate  # noqa: E402

REPO = HERE.parents[2]
DEFAULT_SCHEMA = REPO / "docs" / "contrast" / "contrast-pack.schema.json"
DEFAULT_SOURCES = REPO / "app" / "src" / "main" / "assets" / "content"


class InputError(Exception):
    pass


def read_json(path: Path, what: str):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError as e:
        raise InputError(f"{what} not found: {path}") from e
    except json.JSONDecodeError as e:
        raise InputError(f"{what} is not valid JSON ({path}:{e.lineno}:{e.colno}): {e.msg}") from e


def dump(data) -> str:
    """Deterministic output: key order as built, UTF-8, 2-space indent, final newline."""
    return json.dumps(data, ensure_ascii=False, indent=2) + "\n"


def assemble(drafts_dir: Path, meta: dict, sources: SourceIndex) -> dict:
    """The release pack: the drafts listed in meta.groupOrder (APPROVED or PUBLISHED) become PUBLISHED.
    A draft in another status is kept as it is, so validation reports STATUS_NOT_PUBLISHED and nothing is written."""
    groups = []
    for group_id in meta.get("groupOrder", []):
        group = read_json(drafts_dir / f"{group_id}.json", f"draft {group_id}")
        if group.get("id") != group_id:
            raise InputError(f"draft {group_id}.json has id {group.get('id')!r}")
        if group.get("status") in ("APPROVED", "PUBLISHED"):
            group = {**group, "status": "PUBLISHED"}
        groups.append(group)
    used = sorted({(it.get("source") or {}).get("sourcePackId") for g in groups for it in g.get("items", []) if isinstance(it, dict)} - {None})
    missing = [p for p in used if p not in sources.version_of]
    if missing:
        raise InputError(f"source packs not found in --sources: {', '.join(missing)}")
    pack = {
        "format": "n3sensei-contrast-pack",
        "schemaVersion": 1,
        "pack": meta.get("pack"),
        "sourcePacks": [{"id": p, "version": sources.version_of[p]} for p in used],
        "groups": groups,
    }
    if meta.get("retiredGroups"):
        pack["retiredGroups"] = meta["retiredGroups"]
    return pack


def print_approvals(pack: dict, out=sys.stdout):
    rows = approvals(pack)
    if not rows:
        return
    print("\nDuyệt nội dung:", file=out)
    for row in rows:
        self_note = " — TỰ DUYỆT (tác giả = người duyệt)" if row["selfApproved"] else ""
        print(f"  {row['groupId']} r{row['revision']}: soạn {row['authoredBy']}, duyệt {row['approvedBy']} ngày {row['approvedAt']} (revision {row['approvedRevision']}){self_note}", file=out)


def print_report(issues: list[dict], diff: dict | None, strict: bool, out=sys.stdout):
    order = {ERROR: 0, WARNING: 1}
    for issue in sorted(issues, key=lambda i: (order.get(i["severity"], 2), i["path"], i["code"])):
        ack = ""
        if issue.get("acknowledged") is True:
            ack = " (đã xác nhận)"
        elif issue.get("acknowledged") is False:
            ack = f" (CHƯA xác nhận: thêm {{\"code\": \"{issue['code']}\", \"target\": \"{issue['target']}\"}} vào approval.acknowledgedWarnings)"
        detail = f" — {issue['detail']}" if issue.get("detail") else ""
        print(f"{issue['severity']:<7} {issue['code']:<28} {issue['path']}{ack}{detail}", file=out)
    errors = sum(i["severity"] == ERROR for i in issues)
    unacknowledged = sum(i.get("acknowledged") is False for i in issues)
    print(f"\n{errors} ERROR, {sum(i['severity'] == WARNING for i in issues)} WARNING ({unacknowledged} chưa xác nhận), "
          f"{sum(i['severity'] == 'INFO' for i in issues)} INFO → {'KHÔNG ĐẠT' if failed(issues, strict) else 'ĐẠT'}"
          f"{' (--strict)' if strict else ''}", file=out)
    if diff:
        print("\nSo với bản phát hành trước:", file=out)
        print(json.dumps(diff, ensure_ascii=False, indent=2), file=out)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description="Check or build a Contrast Lab content pack.")
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--check", metavar="PACK", type=Path, help="check this pack; never modifies it")
    action.add_argument("--drafts", metavar="DIR", type=Path, help="build from the group drafts in DIR (needs --meta and --output)")
    parser.add_argument("--meta", type=Path, help="release metadata: {pack, groupOrder, retiredGroups?}")
    parser.add_argument("--output", type=Path, help="where to write the built pack")
    parser.add_argument("--strict", action="store_true", help="also fail on unacknowledged editorial WARNINGs (always on for a build)")
    parser.add_argument("--previous", type=Path, help="previous release, to check versions, ids and answers")
    parser.add_argument("--sources", type=Path, default=DEFAULT_SOURCES, help="content packs dir or fixture records file")
    parser.add_argument("--schema", type=Path, default=DEFAULT_SCHEMA)
    parser.add_argument("--mode", choices=(BUILD, IMPORT), default=BUILD, help="severity rules (import = what the app reports)")
    parser.add_argument("--report", type=Path, help="also write the issues and the change report as JSON")
    parser.add_argument("--reviewers", type=Path, help='registered reviewers {"reviewers": [ids]}; required to build, optional for --check')
    args = parser.parse_args(argv)

    try:
        schema = read_json(args.schema, "schema")
        sources = SourceIndex.load(args.sources)
        previous = read_json(args.previous, "previous release") if args.previous else None
        if args.check:
            strict = args.strict
            try:
                pack = json.loads(args.check.read_text(encoding="utf-8"))
            except FileNotFoundError as e:
                raise InputError(f"pack not found: {args.check}") from e
            except json.JSONDecodeError as e:
                issues = [{"code": "INVALID_JSON", "severity": ERROR, "path": "", "detail": f"{e.lineno}:{e.colno} {e.msg}"}]
                print_report(issues, None, strict)
                return 1
        else:
            if not args.meta or not args.output or not args.reviewers:
                parser.error("--drafts needs --meta, --output and --reviewers")
            strict = True
            pack = assemble(args.drafts, read_json(args.meta, "release metadata"), sources)
        reviewers = None
        if args.reviewers:
            data = read_json(args.reviewers, "reviewers")
            if not isinstance(data, dict) or not isinstance(data.get("reviewers"), list) or not data["reviewers"]:
                raise InputError('reviewers must be {"reviewers": ["<id>", ...]} with at least one id')
            reviewers = set(data["reviewers"])
    except InputError as e:
        print(f"Lỗi đầu vào: {e}", file=sys.stderr)
        return 2

    issues, diff = validate(pack, schema, args.mode, sources, previous)
    if reviewers is not None and isinstance(pack, dict):
        seen = {(i["code"], i["path"]) for i in issues}
        issues += [i for i in reviewer_issues(pack, reviewers) if (i["code"], i["path"]) not in seen]
    print_report(issues, diff, strict)
    if isinstance(pack, dict):
        print_approvals(pack)
    if args.report:
        args.report.write_text(dump({"passed": not failed(issues, strict), "strict": strict, "mode": args.mode, "issues": issues, "changes": diff,
                                     "approvals": approvals(pack) if isinstance(pack, dict) else []}), encoding="utf-8")
    if failed(issues, strict):
        return 1
    if args.drafts:
        args.output.write_text(dump(pack), encoding="utf-8")
        print(f"Đã ghi {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
