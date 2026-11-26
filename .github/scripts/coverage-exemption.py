#!/usr/bin/env python3
"""Print the reason a pull request gives to waive the backend coverage rules, or nothing.

The author waives the rules with a line of the pull request description that starts with
"Coverage exempt:" and gives the reason after it, for a change that cannot be tested. HTML
comments are left out, so a hint in a template never counts. The description is read through the
API when the job runs, not from the event: a re-run replays the event of the first run, which
lacks a line added since.

Needs `gh`, GH_TOKEN and GITHUB_REPOSITORY.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys

# Also matches the line as a list item or in bold: "- Coverage exempt: ...", "**Coverage exempt:** ...".
MARKER = re.compile(
    r"^[ \t]*(?:[-*][ \t]+)?\**coverage exempt\**[ \t]*:[ \t]*\**[ \t]*(\S.*)$",
    re.IGNORECASE | re.MULTILINE,
)
COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
# The reason goes into a step output and a check title: one short line, without the carriage
# returns that the runner would read as a line break.
CONTROL = re.compile(r"[\x00-\x1f\x7f]")
MAX_REASON = 200
# The placeholder of the documented line is not a reason.
PLACEHOLDER = "<why>"
# Emphasis around the reason is formatting, not text: "**Coverage exempt:**" alone gives none.
EMPHASIS = " \t*_"


def reason(body: str) -> str:
    found = MARKER.search(COMMENT.sub("", body))
    if not found:
        return ""
    text = CONTROL.sub(" ", found.group(1)).strip(EMPHASIS)[:MAX_REASON]
    if text.lower() == PLACEHOLDER or not re.search(r"\w", text):
        return ""
    return text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--pr", required=True, help="pull request number")
    args = parser.parse_args()

    pull = subprocess.run(
        ["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/pulls/{args.pr}"],
        capture_output=True,
        text=True,
    )
    if pull.returncode != 0:
        print(
            f"Could not read the pull request description: {pull.stderr.strip()}", file=sys.stderr
        )
        return 1
    print(reason(json.loads(pull.stdout)["body"] or ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
