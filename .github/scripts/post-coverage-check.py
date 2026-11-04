#!/usr/bin/env python3
"""Post a check run that explains the result of the backend coverage rules on a pull request.

The coverage-backend-gate job runs the two rules (diff-cover on the changed lines, then
check-coverage-drop.py on the whole backend) and only passes or fails. This check run says, in
words, which rule failed, with the tool output, how to fix it, and why Codecov's comment can show
other numbers. It is posted with the PR checks app, like the PR title check, so it shows as its
own check on the pull request.

Needs `gh`, GH_TOKEN with checks:write, and GITHUB_REPOSITORY.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess

NAME = "Backend Coverage (advisory)"
# GitHub caps a check run summary at 65535 characters; the tool output is cut well before that.
MAX_LOG = 20000

RULES = """\
This pull request is checked against two backend coverage rules:

- **Changed lines**: at least 80% of the backend lines it adds or changes, in \
`openaev-api/src/main/java`, run during the API tests.
- **Whole backend**: backend line coverage does not drop by more than 0.2 point against the \
latest report of the branch it targets.
"""

FIX = """\
### How to fix it

- **Changed lines**: add or extend tests that run the lines listed above. To check locally after \
`mvn test`: `pipx run --spec diff-cover==11.0.0 diff-cover \
openaev-api/target/site/jacoco/jacoco.xml --compare-branch=origin/main \
--src-roots openaev-api/src/main/java --fail-under 80`.
- **Whole backend**: the drop usually comes from the same untested lines, or from tests that were \
removed or no longer reach the code. Covering the changed lines is often enough.
"""

CODECOV = """\
### Why Codecov can show other numbers

This check is the rule; Codecov's comment and checks only report. They can disagree:

- A line where only one branch of an `if` ran counts as covered here, and as partial, so \
missing, in Codecov.
- This check compares with the latest report of the target branch, Codecov with the pull \
request's base commit, which can be older.
"""


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
    parser.add_argument("--patch-outcome", required=True, help="outcome of the changed lines step")
    parser.add_argument("--patch-log", required=True, help="output of diff-cover")
    parser.add_argument("--project-outcome", required=True, help="outcome of the drop step")
    parser.add_argument("--project-log", required=True, help="output of check-coverage-drop.py")
    args = parser.parse_args()

    patch_ok = args.patch_outcome == "success"
    project_ok = args.project_outcome == "success"
    if patch_ok and project_ok:
        title = "Backend coverage rules pass"
    elif not patch_ok and not project_ok:
        title = "Changed backend lines under 80% covered, and backend coverage drops over 0.2 point"
    elif not patch_ok:
        title = "Less than 80% of the changed backend lines are covered by tests"
    else:
        title = "Backend line coverage drops by more than 0.2 point"

    summary = "\n".join(
        [
            RULES,
            "### Result\n",
            section(patch_ok, "Changed lines (diff-cover)", read(args.patch_log)),
            section(project_ok, "Whole backend", read(args.project_log)),
            "" if patch_ok and project_ok else FIX,
            CODECOV,
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
