#!/usr/bin/env python3
"""Turn the findings of an Aikido ephemeral image scan into a Markdown report.

The scanner writes every finding to a JSON file. The report lists the findings at or above the
severity threshold in a table (severity, ID, package, installed and fixed versions, file),
followed by their descriptions, so it can be read from the job summary without opening the JSON.

The report is written to the given file. The number of reported findings that have a fix is
printed as `issues=<n>`, a line the caller can append to $GITHUB_OUTPUT to enforce it.

Usage: report.py <findings.json> <image> <severity-threshold> <report.md>
"""

from __future__ import annotations

import json
import sys

SEVERITY_RANK = {"low": 1, "medium": 2, "high": 3, "critical": 4}


def rank(severity: object) -> int:
    return SEVERITY_RANK.get(str(severity).lower(), 0)


def cell(value: object) -> str:
    """One table cell: a single line, with pipes escaped."""
    return " ".join(str(value).split()).replace("|", "\\|")


def report(result: dict, image: str, threshold: str) -> tuple[list[str], int]:
    findings = result.get("findings") or []
    reported = sorted(
        (f for f in findings if rank(f.get("severity")) >= SEVERITY_RANK[threshold]),
        key=lambda f: (-rank(f.get("severity")), -(f.get("aikido_severity") or 0)),
    )
    fixable = sum(1 for f in reported if f.get("fix_versions"))
    distro = (result.get("distro") or {}).get("pretty_name", "unknown")
    lines = [f"### {image}", "", f"Base image OS: {distro}", ""]
    if not reported:
        lines.append(f"No finding at or above `{threshold}` ({len(findings)} finding(s) in total).")
        return lines, fixable

    lines += [
        f"{len(reported)} finding(s) at or above `{threshold}`, {fixable} with a fix"
        f" ({len(findings)} finding(s) in total).",
        "",
        "| Severity | ID | Package | Installed | Fixed in | File |",
        "|---|---|---|---|---|---|",
    ]
    for f in reported:
        fixes = ", ".join(f.get("fix_versions") or []) or "no fix"
        lines.append(
            f"| {cell(f.get('severity', ''))} | {cell(f.get('cve_id', ''))} | {cell(f.get('package_name', ''))}"
            f" | {cell(f.get('installed_version', ''))} | {cell(fixes)} | {cell(f.get('file', ''))} |"
        )
    lines.append("")
    for f in reported:
        description = " ".join(str(f.get("description") or "no description").split())
        lines.append(
            f"- **{f.get('cve_id', '')}** in {f.get('package_name', '')} {f.get('installed_version', '')}: {description}"
        )
    return lines, fixable


def main() -> None:
    if len(sys.argv) != 5 or sys.argv[3] not in SEVERITY_RANK:
        sys.exit(__doc__.strip().splitlines()[-1])
    with open(sys.argv[1], encoding="utf-8") as f:
        result = json.load(f)
    lines, fixable = report(result, sys.argv[2], sys.argv[3])
    with open(sys.argv[4], "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"issues={fixable}")


if __name__ == "__main__":
    main()
