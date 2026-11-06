#!/usr/bin/env python3
"""Post a check run that explains why the backend coverage rules failed on a pull request.

The backend-coverage-check action runs the two rules (check-patch-coverage.py on the changed
lines, then check-coverage-drop.py on the whole backend) and only passes or fails. When one
fails, this check run says, in words, which one, with the tool output and how to fix it. It is
posted with the PR checks app, like the PR title check, so it shows as its own check on the pull
request.

Needs `gh`, GH_TOKEN with checks:write, and GITHUB_REPOSITORY.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess

NAME = "Backend Coverage (advisory)"
# GitHub caps a check run summary at 65535 characters; the tool output is cut well before that.
MAX_LOG = 20000


def rules(min_coverage: str, max_drop: str) -> str:
    return f"""\
This pull request is checked against two backend coverage rules:

- **Changed lines**: at least {min_coverage}% of the backend lines it adds or changes, in \
`openaev-api/src/main/java`, run during the API tests.
- **Whole backend**: backend line coverage does not drop by more than {max_drop} point against \
the latest report of the branch it targets.
"""


def fix(min_coverage: str) -> str:
    return f"""\
### How to fix it

- **Changed lines**: add or extend tests that run the lines listed above. To check locally after \
`mvn test`: `python3 .github/scripts/check-patch-coverage.py \
--report openaev-api/target/site/jacoco/jacoco.xml --base origin/main \
--fail-under {min_coverage}`. A line where only some branches ran is partial, and counts as \
not covered: cover the other branches too.
- **Whole backend**: the drop usually comes from the same untested lines, or from tests that were \
removed or no longer reach the code. Covering the changed lines is often enough.
"""


def patch_figure(log: str) -> str:
    """Changed lines coverage as check-patch-coverage.py prints it, for the check title."""
    if "No lines with coverage information" in log:
        return "none"
    found = re.search(r"^Coverage: ([\d.]+)%", log, re.MULTILINE)
    return f"{found.group(1)}%" if found else "?"


def project_figure(log: str) -> str:
    """Whole backend change as check-coverage-drop.py prints it, for the check title."""
    if found := re.search(r"drops by ([\d.]+) points", log):
        return f"-{found.group(1)} pt"
    if found := re.search(r"Change: ([+-][\d.]+) points", log):
        return f"{found.group(1)} pt"
    if "the drop cannot be measured" in log:
        return "no base"
    return "?"


def section(passed: bool, label: str, log: str) -> str:
    icon = "✅" if passed else "❌"
    text = log.strip() or "(no output)"
    if len(text) > MAX_LOG:
        text = text[:MAX_LOG] + "\n…"
    return f"{icon} **{label}**\n\n```\n{text}\n```\n"


def read(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as log:
            return log.read()
    except FileNotFoundError:
        return ""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sha", required=True, help="head commit of the pull request")
    parser.add_argument("--min-coverage", required=True, help="changed lines rule, in percent")
    parser.add_argument("--max-drop", required=True, help="whole backend rule, in points")
    parser.add_argument("--patch-outcome", required=True, help="outcome of the changed lines step")
    parser.add_argument("--patch-log", required=True, help="output of check-patch-coverage.py")
    parser.add_argument("--project-outcome", required=True, help="outcome of the drop step")
    parser.add_argument("--project-log", required=True, help="output of check-coverage-drop.py")
    args = parser.parse_args()

    patch_ok = args.patch_outcome == "success"
    project_ok = args.project_outcome == "success"
    patch_log = read(args.patch_log)
    project_log = read(args.project_log)
    # The PR checks list shows about 100 characters of "name — title": the figures come first.
    title = (
        f"{'✅' if patch_ok else '❌'} changed lines {patch_figure(patch_log)} "
        f"(min {args.min_coverage}%) · "
        f"{'✅' if project_ok else '❌'} backend {project_figure(project_log)} "
        f"(max -{args.max_drop})"
    )
    summary = "\n".join(
        [
            rules(args.min_coverage, args.max_drop),
            "### Result\n",
            section(patch_ok, "Changed lines", patch_log),
            section(project_ok, "Whole backend", project_log),
            fix(args.min_coverage),
            "This check is advisory for now: it does not block the merge.",
        ]
    )
    payload = {
        "name": NAME,
        "head_sha": args.sha,
        "status": "completed",
        "conclusion": "success" if patch_ok and project_ok else "failure",
        "output": {"title": title, "summary": summary},
    }
    subprocess.run(
        ["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/check-runs", "--input", "-"],
        input=json.dumps(payload).encode(),
        check=True,
        stdout=subprocess.DEVNULL,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
