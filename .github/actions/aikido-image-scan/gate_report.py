#!/usr/bin/env python3
"""Turn the result of an Aikido release or PR gating scan into a Markdown report.

The scanner writes the issues that fail the gate to a JSON file: in release mode the open issues
of the image, written only when there are some; in PR mode the issues the pull request adds, and
the ones it solves. Each issue only has a severity, a score and a summary such as
"CVE-2026-1234 in curl". The report groups them as Aikido does (one row per package), links every
CVE to its public osv.dev page, where anyone can read the affected and fixed versions, and links
each group to Aikido, which needs access to the workspace.

The report is written to the given file. The number of issues at or above the threshold is
printed as `issues=<n>`, a line the caller can append to $GITHUB_OUTPUT to enforce it.

Usage: gate_report.py <release|pr> <result.json> <image> <severity-threshold> <report.md>
"""

from __future__ import annotations

import json
import re
import sys
from urllib.parse import parse_qs, urlparse

SEVERITY_RANK = {"low": 1, "medium": 2, "high": 3, "critical": 4}
SUMMARY = re.compile(r"^(\S+) in (.+)$")
PUBLIC_PAGES = {
    "CVE-": "https://osv.dev/vulnerability/{}",
    "GHSA-": "https://github.com/advisories/{}",
}


def rank(severity: object) -> int:
    return SEVERITY_RANK.get(str(severity).lower(), 0)


def cell(value: object) -> str:
    """One table cell: a single line, with pipes escaped."""
    return " ".join(str(value).split()).replace("|", "\\|")


def public_link(issue_id: str) -> str:
    for prefix, page in PUBLIC_PAGES.items():
        if issue_id.startswith(prefix):
            return f"[{issue_id}]({page.format(issue_id)})"
    return issue_id


def group_links(result: dict) -> dict[str, str]:
    """The Aikido link of each issue group, keyed by group ID."""
    links = {}
    for link in result.get("issue_links") or []:
        group = parse_qs(urlparse(link).query).get("sidebarIssue")
        if group:
            links[group[0]] = link
    return links


def table(issues: list[dict], links: dict[str, str], fallback_link: str) -> list[str]:
    groups: dict[str, list[dict]] = {}
    for issue in issues:
        groups.setdefault(str(issue.get("issue_group_id", "")), []).append(issue)

    rows = []
    for group, members in groups.items():
        severity = max((m.get("aikido_severity", "") for m in members), key=rank)
        score = max(int(m.get("aikido_score") or 0) for m in members)
        packages, ids, others = [], [], []
        for m in members:
            summary = " ".join(str(m.get("summary", "")).split())
            match = SUMMARY.match(summary)
            if match:
                ids.append(match.group(1))
                if match.group(2) not in packages:
                    packages.append(match.group(2))
            else:
                others.append(summary)
        what = ", ".join(packages) or "; ".join(others)
        found = ", ".join(public_link(i) for i in sorted(set(ids))) or "—"
        link = links.get(group) or fallback_link
        aikido = f"[open]({link})" if link else "—"
        rows.append((rank(severity), score, severity, what, found, aikido))

    lines = ["| Severity | Score | Package | Issues | Aikido |", "|---|---|---|---|---|"]
    for _, score, severity, what, found, aikido in sorted(rows, key=lambda r: (-r[0], -r[1], r[3])):
        lines.append(f"| {cell(severity)} | {score} | {cell(what)} | {found} | {aikido} |")
    return lines


def report(mode: str, result: dict, image: str, threshold: str) -> tuple[list[str], int]:
    issues = [
        i for i in result.get("issue_details") or [] if rank(i.get("aikido_severity")) >= SEVERITY_RANK[threshold]
    ]
    # A gate that failed with no readable issue still counts, so it is never reported as clean.
    if mode == "release":
        failed = int(result.get("open_issues_found") or 0)
    else:
        failed = 0 if result.get("gate_passed", True) else max(int(result.get("new_issues_found") or 0), 1)
    count = max(len(issues), failed)
    diff_url = result.get("diff_url") or ""
    distro = (result.get("distro") or {}).get("pretty_name")

    lines = [f"### {image}", ""]
    if distro:
        lines += [f"Base image OS: {distro}", ""]
    kind = "open" if mode == "release" else "new"
    if not count:
        lines.append(f"No {kind} issue at or above `{threshold}` in Aikido.")
    else:
        lines.append(f"{count} {kind} issue(s) at or above `{threshold}` in Aikido.")
        if issues:
            lines += [""] + table(issues, group_links(result), diff_url)
    if mode == "pr":
        solved = result.get("solved_issue_details") or []
        if solved:
            lines += ["", f"This pull request solves {len(solved)} issue(s)."]
        if diff_url:
            lines += ["", f"[Comparison with the base branch in Aikido]({diff_url})"]
    return lines, count


def main() -> None:
    if len(sys.argv) != 6 or sys.argv[1] not in ("release", "pr") or sys.argv[4] not in SEVERITY_RANK:
        sys.exit(__doc__.strip().splitlines()[-1])
    mode, path, image, threshold, output = sys.argv[1:]
    try:
        with open(path, encoding="utf-8") as f:
            result = json.load(f)
    except FileNotFoundError:
        # Release mode writes no result file when the gate passes.
        result = {}
    lines, count = report(mode, result, image, threshold)
    with open(output, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"issues={count}")


if __name__ == "__main__":
    main()
