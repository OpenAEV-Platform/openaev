#!/usr/bin/env python3
"""Turn the findings of an Aikido ephemeral image scan into a Markdown report.

The scanner writes every finding to a JSON file. The report lists the high and critical ones in
a table (severity, ID, package, installed and fixed versions, file), followed by their
descriptions, so it can be read from the job summary without opening the JSON.

Usage: aikido-scan-report.py <findings.json> <image>
"""

from __future__ import annotations

import json
import sys

REPORTED_SEVERITIES = ("critical", "high")


def cell(value: object) -> str:
    """One table cell: a single line, with pipes escaped."""
    return " ".join(str(value).split()).replace("|", "\\|")


def report(result: dict, image: str) -> list[str]:
    findings = result.get("findings") or []
    reported = sorted(
        (f for f in findings if str(f.get("severity", "")).lower() in REPORTED_SEVERITIES),
        key=lambda f: -(f.get("aikido_severity") or 0),
    )
    distro = (result.get("distro") or {}).get("pretty_name", "unknown")
    lines = [f"### {image}", "", f"Base image OS: {distro}", ""]
    if not reported:
        lines.append(f"No high or critical vulnerability found ({len(findings)} finding(s) in total).")
        return lines

    lines += [
        f"{len(reported)} high or critical vulnerability(ies) found ({len(findings)} finding(s) in total).",
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
    return lines


def main() -> None:
    if len(sys.argv) != 3:
        sys.exit(__doc__.strip().splitlines()[-1])
    with open(sys.argv[1], encoding="utf-8") as f:
        result = json.load(f)
    print("\n".join(report(result, sys.argv[2])))


if __name__ == "__main__":
    main()
