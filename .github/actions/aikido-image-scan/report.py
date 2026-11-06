#!/usr/bin/env python3
"""Turn the findings of an Aikido ephemeral image scan into a Markdown report.

The scanner writes every finding to a JSON file. The report lists the findings at or above the
severity threshold in a table (severity, ID, package, installed and fixed versions, file),
followed by their descriptions, so it can be read from the job summary without opening the JSON.

Findings listed in the ignore file (`<ID> | <package> | <reason>`) are false positives: they
are left out of the table and of the count, and listed at the end with their reason.

The report is written to the given file. The number of reported findings that have a fix is
printed as `fixable=<n>`, a line the caller can append to $GITHUB_OUTPUT to enforce it.

Usage: report.py <findings.json> <image> <severity-threshold> <report.md> <ignore-file>
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


def read_ignored(path: str) -> dict[tuple[str, str], str]:
    """(ID, lowercased package) -> reason, from lines `<ID> | <package> | <reason>`; `#` starts a comment."""
    ignored = {}
    with open(path, encoding="utf-8") as lines:
        for number, line in enumerate(lines, 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            parts = [part.strip() for part in line.split("|", 2)]
            if len(parts) < 3 or not all(parts):
                sys.exit(f"{path}:{number}: expected `<ID> | <package> | <reason>`")
            ignored[(parts[0], parts[1].lower())] = parts[2]
    return ignored


def ignore_reason(finding: dict, ignored: dict[tuple[str, str], str]) -> str | None:
    return ignored.get((str(finding.get("cve_id", "")), str(finding.get("package_name", "")).lower()))


def report(
    result: dict, image: str, threshold: str, ignored: dict[tuple[str, str], str]
) -> tuple[list[str], int]:
    findings = result.get("findings") or []
    above = sorted(
        (f for f in findings if rank(f.get("severity")) >= SEVERITY_RANK[threshold]),
        key=lambda f: (-rank(f.get("severity")), -(f.get("aikido_severity") or 0)),
    )
    reported = [f for f in above if ignore_reason(f, ignored) is None]
    skipped = [f for f in above if ignore_reason(f, ignored) is not None]
    fixable = sum(1 for f in reported if f.get("fix_versions"))
    distro = (result.get("distro") or {}).get("pretty_name", "unknown")
    lines = [f"### {image}", "", f"Base image OS: {distro}", ""]
    if not reported:
        lines.append(f"No finding at or above `{threshold}` ({len(findings)} finding(s) in total).")
    else:
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
                f"- **{f.get('cve_id', '')}** in {f.get('package_name', '')} {f.get('installed_version', '')}:"
                f" {description}"
            )
    if skipped:
        lines += ["", f"Ignored as false positives ({len(skipped)}):", ""]
        for f in skipped:
            lines.append(
                f"- {f.get('cve_id', '')} in {f.get('package_name', '')} {f.get('installed_version', '')}:"
                f" {ignore_reason(f, ignored)}"
            )
    return lines, fixable


def main() -> None:
    if len(sys.argv) != 6 or sys.argv[3] not in SEVERITY_RANK:
        sys.exit(__doc__.strip().splitlines()[-1])
    with open(sys.argv[1], encoding="utf-8") as f:
        result = json.load(f)
    lines, fixable = report(result, sys.argv[2], sys.argv[3], read_ignored(sys.argv[5]))
    with open(sys.argv[4], "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"fixable={fixable}")


if __name__ == "__main__":
    main()
