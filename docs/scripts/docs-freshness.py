"""Report how fresh the documentation is, from page front matter and git history.

Usage: python docs/scripts/docs-freshness.py [--output report.md]
"""

import argparse
import datetime
import re
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "docs" / "docs"
JOURNEYS = ROOT / "docs" / "journeys"
STALE_AFTER_DAYS = 180

STATUSES = [
    "Outdated",
    "Changed since verified",
    "Stale",
    "Not verified",
    "Verified",
    "Release-managed",
]


class NavLoader(yaml.SafeLoader):
    pass


NavLoader.add_multi_constructor("", lambda loader, suffix, node: None)


def git(*args):
    return subprocess.run(
        ["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8"
    ).stdout.strip()


def nav_pages():
    config = yaml.load((ROOT / "docs" / "mkdocs.yml").read_text(encoding="utf-8"), Loader=NavLoader)
    pages, seen = [], set()

    def walk(items, trail):
        for item in items:
            if isinstance(item, str):
                entries = [(item, item)]
            else:
                entries = list(item.items())
            for title, value in entries:
                if isinstance(value, list):
                    walk(value, trail + [title])
                elif isinstance(value, str) and value.endswith(".md") and value not in seen:
                    seen.add(value)
                    section = trail[0] if trail else title
                    pages.append({"path": value, "title": title, "section": section})

    walk(config["nav"], [])
    return pages


def front_matter(path):
    text = path.read_text(encoding="utf-8")
    match = re.match(r"^---\n(.*?)\n---\n", text, re.S)
    return yaml.safe_load(match.group(1)) or {} if match else {}


def page_status(page):
    path = DOCS / page["path"]
    meta = front_matter(path)
    page["notes"] = meta.get("doc_notes", "")
    verified = meta.get("last_verified")
    page["last_verified"] = str(verified) if verified else ""
    if meta.get("doc_status") == "release-managed":
        return "Release-managed"
    if meta.get("doc_status") == "outdated":
        return "Outdated"
    if not verified:
        return "Not verified"
    rel = path.relative_to(ROOT).as_posix()
    last_change = git("log", "-1", "--format=%cI", "--", rel)
    last_bump = git("log", "-1", "--format=%cI", "-G^last_verified:", "--", rel)
    if last_change and last_bump and last_change > last_bump:
        return "Changed since verified"
    if (datetime.date.today() - datetime.date.fromisoformat(str(verified))).days > STALE_AFTER_DAYS:
        return "Stale"
    return "Verified"


def journeys(statuses_by_path):
    rows = []
    for path in sorted(JOURNEYS.glob("UJ-*.md")):
        meta = front_matter(path)
        pages = meta.get("doc_pages") or []
        missing = [p for p in pages if p not in statuses_by_path]
        to_check = [p for p in pages if statuses_by_path.get(p) not in (None, "Verified", "Release-managed")]
        rows.append({
            "id": meta.get("id", path.stem),
            "title": meta.get("title", ""),
            "status": meta.get("status", "draft"),
            "priority": meta.get("priority", ""),
            "pages": len(pages),
            "to_check": len(to_check),
            "missing": missing,
        })
    return rows


def report():
    pages = nav_pages()
    for page in pages:
        page["status"] = page_status(page)
    by_path = {p["path"]: p["status"] for p in pages}
    sections = list(dict.fromkeys(p["section"] for p in pages))
    counts = defaultdict(Counter)
    for page in pages:
        counts[page["section"]][page["status"]] += 1
    total = Counter(p["status"] for p in pages)
    ok = total["Verified"] + total["Release-managed"]

    out = [
        "# Docs freshness report",
        "",
        f"Generated on {datetime.date.today()} from `{git('rev-parse', '--short', 'HEAD')}`. "
        f"**{ok} of {len(pages)} pages verified** ({round(100 * ok / len(pages))} %).",
        "",
        "| Section | " + " | ".join(STATUSES) + " |",
        "|---|" + "---|" * len(STATUSES),
    ]
    for section in sections + ["**Total**"]:
        row = total if section == "**Total**" else counts[section]
        out.append(f"| {section} | " + " | ".join(str(row[s] or "") for s in STATUSES) + " |")

    out += ["", "## Pages to check", ""]
    for status in STATUSES[:4]:
        listed = [p for p in pages if p["status"] == status]
        if not listed:
            continue
        out += [f"### {status} ({len(listed)})", ""]
        for p in sorted(listed, key=lambda p: (p["last_verified"], p["section"], p["path"])):
            extra = f" (verified {p['last_verified']})" if p["last_verified"] else ""
            note = f" — {p['notes']}" if p["notes"] else ""
            out.append(f"- {p['section']}: `{p['path']}`{extra}{note}")
        out.append("")

    rows = journeys(by_path)
    if rows:
        out += ["## User journeys", "", "| Journey | Status | Priority | Doc pages | To check |", "|---|---|---|---|---|"]
        for j in rows:
            out.append(f"| {j['id']} {j['title']} | {j['status']} | {j['priority']} | {j['pages']} | {j['to_check']} |")
        broken = [(j["id"], m) for j in rows for m in j["missing"]]
        if broken:
            out += ["", "Unknown doc pages referenced by journeys:", ""]
            out += [f"- {jid}: `{path}`" for jid, path in broken]
    return "\n".join(out) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", help="write the report to this file instead of stdout")
    args = parser.parse_args()
    text = report()
    if args.output:
        Path(args.output).write_text(text, encoding="utf-8")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stdout.write(text)


if __name__ == "__main__":
    main()
