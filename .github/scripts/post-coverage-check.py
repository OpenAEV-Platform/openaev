#!/usr/bin/env python3
"""Post a check run that explains why the backend coverage rules failed on a pull request.

The backend-coverage-check action runs the two rules (check-patch-coverage.py on the changed
lines, then check-coverage-drop.py on the whole backend) and only passes or fails. When one
fails, this check run says, in words, which one, with the tool output and how to fix it. A rule
that passed is left out. When the pull request is behind main, the fix it gives for a drop is a
rebase. When both rules pass, it posts nothing, unless an earlier run on the same commit posted a
failure: a passing check then replaces it. It is posted with the PR checks app, like the PR title check, so it shows as its own check
on the pull request.

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


def output(log: str) -> str:
    text = log.strip() or "(no output)"
    if len(text) > MAX_LOG:
        text = text[:MAX_LOG] + "\n…"
    return f"```\n{text}\n```\n"


def patch_title(log: str, min_coverage: str) -> str:
    """Changed lines figure as check-patch-coverage.py prints it."""
    found = re.search(r"^Coverage: ([\d.]+)%", log, re.MULTILINE)
    return f"❌ Changed lines {found.group(1) if found else '?'}% covered, needs {min_coverage}%"


def patch_section(log: str, min_coverage: str) -> str:
    return f"""\
### ❌ Changed lines

At least {min_coverage}% of the lines this pull request adds or changes in \
`openaev-api/src/main/java` must run during the API tests.

{output(log)}
Add or extend tests that run the lines listed above. A line where only some branches ran is \
partial, and counts as not covered: cover the other branches too. To check locally after \
`mvn test`: `python3 .github/scripts/check-patch-coverage.py \
--report openaev-api/target/site/jacoco/jacoco.xml --base origin/main \
--fail-under {min_coverage}`.
"""


def behind_main(log: str) -> str | None:
    """Commits the pull request lacks from main's report, as check-coverage-drop.py prints them."""
    found = re.search(r"is (\d+) commits behind main", log)
    return found.group(1) if found else None


def project_title(log: str, max_drop: str) -> str:
    """Coverage drop as check-coverage-drop.py prints it."""
    found = re.search(r"drops by ([\d.]+) points", log)
    title = f"❌ Coverage down {found.group(1) if found else '?'} points"
    if behind := behind_main(log):
        return f"{title}, branch {behind} commits behind main: rebase"
    return f"{title}, max {max_drop}"


def project_section(log: str, max_drop: str) -> str:
    if behind := behind_main(log):
        fix = f"""\
This branch is {behind} commits behind main, and the report it is compared with has their \
tests: the drop may come from them alone. Rebase it on main, or update it with main, and let CI \
run again. For a stacked pull request, update the branch it targets first. Codecov compares with \
an older commit of main, so its comment can show another figure until then."""
    else:
        fix = """\
The drop usually comes from untested lines this pull request adds, or from tests that were \
removed or no longer reach the code."""
    return f"""\
### ❌ Coverage drop

Line coverage must not drop by more than {max_drop} point against the latest report of main.

{output(log)}
{fix}
"""


def gh_api(*args: str, payload: dict | None = None) -> bytes:
    return subprocess.run(
        ["gh", "api", *args],
        input=json.dumps(payload).encode() if payload else None,
        check=True,
        capture_output=True,
    ).stdout


def failed_before(sha: str) -> bool:
    """Whether an earlier run posted this check as a failure on the commit."""
    runs = json.loads(
        gh_api(
            f"repos/{os.environ['GITHUB_REPOSITORY']}/commits/{sha}/check-runs?filter=latest",
            "-f", f"check_name={NAME}", "-X", "GET",
        )
    )["check_runs"]
    return any(run["conclusion"] == "failure" for run in runs)


def post(sha: str, conclusion: str, title: str, summary: str) -> None:
    gh_api(
        f"repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
        "--input", "-",
        payload={
            "name": NAME,
            "head_sha": sha,
            "status": "completed",
            "conclusion": conclusion,
            "output": {"title": title, "summary": summary},
        },
    )


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

    titles, sections = [], []
    if args.patch_outcome != "success":
        patch_log = read(args.patch_log)
        titles.append(patch_title(patch_log, args.min_coverage))
        sections.append(patch_section(patch_log, args.min_coverage))
    if args.project_outcome != "success":
        project_log = read(args.project_log)
        titles.append(project_title(project_log, args.max_drop))
        sections.append(project_section(project_log, args.max_drop))
    if not titles:
        if failed_before(args.sha):
            post(args.sha, "success", "✅ Coverage rules pass", "Both rules pass on the latest run.")
        return 0

    # The PR checks list shows about 100 characters of "name — title": the figures come first.
    post(
        args.sha,
        "failure",
        " · ".join(titles),
        "\n".join(sections + ["This check is advisory for now: it does not block the merge."]),
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
