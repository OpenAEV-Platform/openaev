#!/usr/bin/env python3
"""Fail when too few of the backend lines a pull request changes are covered by tests.

The changed lines are the lines the pull request adds or changes under the source root, read from
`git diff <base>...HEAD`. Their state comes from the JaCoCo report (see jacoco_lines.py): a line
where only some branches ran is partial, and partial lines are not covered. Only lines that the
report knows count, so blank lines, comments and declarations are left out.

Run it from the repository root, with the pull request's commit checked out and the report built
by `mvn test` or by CI:

    python3 .github/scripts/check-patch-coverage.py \\
        --report openaev-api/target/site/jacoco/jacoco.xml --base origin/main --fail-under 80
"""

from __future__ import annotations

import argparse
import math
import re
import subprocess
import sys

from jacoco_lines import HIT, MISS, PARTIAL, report_lines

HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_lines(base: str, src_root: str) -> dict[str, set[int]]:
    """Lines added or changed under src_root since the merge base with `base`, by path."""
    diff = subprocess.run(
        ["git", "diff", "--unified=0", "--no-color", "--no-ext-diff", "-M", f"{base}...HEAD",
         "--", src_root],
        check=True, capture_output=True, text=True,
    ).stdout
    changed: dict[str, set[int]] = {}
    path = None
    for line in diff.splitlines():
        if line.startswith("+++ "):
            path = None if line == "+++ /dev/null" else line[len("+++ b/"):]
        elif path and (hunk := HUNK.match(line)):
            start = int(hunk.group(1))
            count = 1 if hunk.group(2) is None else int(hunk.group(2))
            changed.setdefault(path, set()).update(range(start, start + count))
    return changed


def ranges(numbers: list[int]) -> str:
    """Line numbers as ranges: 1, 2, 3, 7 -> 1-3, 7."""
    parts, start, previous = [], None, None
    for number in sorted(numbers):
        if start is None:
            start = previous = number
        elif number == previous + 1:
            previous = number
        else:
            parts.append(str(start) if start == previous else f"{start}-{previous}")
            start = previous = number
    if start is not None:
        parts.append(str(start) if start == previous else f"{start}-{previous}")
    return ",".join(parts)


def percent(hits: int, total: int) -> float:
    """Coverage rounded down to two decimals."""
    return math.floor(10000 * hits / total) / 100


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", required=True, help="JaCoCo XML report")
    parser.add_argument("--base", required=True, help="branch or commit to diff against")
    parser.add_argument("--src-root", default="openaev-api/src/main/java")
    parser.add_argument(
        "--fail-under", type=float, required=True, help="smallest share covered, in percent"
    )
    args = parser.parse_args()

    with open(args.report, "rb") as report:
        files = report_lines(report.read(), args.src_root)

    hits = partials = misses = 0
    print("Changed lines coverage")
    print(f"Diff: {args.base}...HEAD")
    print("-------------")
    for path, changed in sorted(changed_lines(args.base, args.src_root).items()):
        states = {number: files.get(path, {}).get(number) for number in changed}
        states = {number: state for number, state in states.items() if state}
        file_hits = sum(state == HIT for state in states.values())
        hits += file_hits
        missing = [number for number, state in states.items() if state == MISS]
        partial = [number for number, state in states.items() if state == PARTIAL]
        partials += len(partial)
        misses += len(missing)
        if states and file_hits < len(states):
            notes = []
            if missing:
                notes.append(f"Missing lines {ranges(missing)}")
            if partial:
                notes.append(f"Partial lines {ranges(partial)}")
            print(f"{path} ({percent(file_hits, len(states)):.2f}%): {'; '.join(notes)}")

    total = hits + partials + misses
    if total == 0:
        print("No lines with coverage information in this diff.")
        return 0
    print("-------------")
    print(f"Total:   {total} lines")
    print(f"Missing: {misses + partials} lines ({partials} partial)")
    print(f"Coverage: {percent(hits, total):.2f}%")
    if 100 * hits / total < args.fail_under:
        print(f"Less than {args.fail_under:g}% of the changed lines are covered.")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
