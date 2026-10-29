#!/usr/bin/env python3
"""Parse the pipeline's inline matrices the way the runner parses them.

`_ci-pipeline.yml` turns a few of its inputs into job matrices with `fromJSON`, and the callers
pass those inputs as YAML block scalars. YAML and JSON do not agree on what is valid: a missing
quote, or a `#` comment inside the scalar, leaves the file valid YAML and makes `fromJSON` reject
the whole list. The matrix job then errors before a single instance starts, every shard disappears
and the run can still look alive. That happened between 22 and 25 September 2026: three nightly
runs produced no API test shard at all.

So this check loads the caller with a YAML parser, which is what folds the scalar, and then hands
the folded text to a strict JSON parser, which is what the runner does next. On top of that it
checks the few structural facts a broken entry would otherwise hide: a shard that names a shard
file that does not exist, two entries that would upload the same artifact name.

Run it with no arguments from the repository root.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
WORKFLOWS = REPO / ".github" / "workflows"
PIPELINE = WORKFLOWS / "_ci-pipeline.yml"
SHARD_DIR = REPO / ".github" / "shards"

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def json_consuming_inputs() -> set[str]:
    """The inputs `_ci-pipeline.yml` actually feeds to fromJSON, read from the file itself.

    Deriving them rather than listing them here is what keeps this check from going stale the day
    someone adds a third matrix.
    """
    text = PIPELINE.read_text(encoding="utf-8")
    return set(re.findall(r"fromJSON\(\s*inputs\.([A-Za-z0-9_-]+)\s*\)", text))


def expression_string_literals(value: str) -> list[str]:
    """The JSON literals a GitHub expression can evaluate to.

    A caller may wrap its matrix in `${{ cond && '[...]' || '[...]' }}`. The runner evaluates that
    to one of the single-quoted literals and parses that, so each one has to be valid JSON on its
    own. Inside a GitHub single-quoted string a quote is escaped by doubling it.

    Only RESULT alternatives are returned. A literal that is part of a comparison, such as
    `github.event_name == 'pull_request'`, is a condition and is never parsed as a matrix. The
    distinction is made on the literal's position and NOT on its contents: filtering by a leading
    bracket would silently skip a result alternative whose bracket someone deleted, which is exactly
    the breakage this check exists to catch.
    """
    literals: list[str] = []
    i = 0
    while i < len(value):
        if value[i] != "'":
            i += 1
            continue
        before = value[:i].rstrip()
        i += 1
        buf = []
        while i < len(value):
            if value[i] == "'":
                if i + 1 < len(value) and value[i + 1] == "'":
                    buf.append("'")
                    i += 2
                    continue
                i += 1
                break
            buf.append(value[i])
            i += 1
        after = value[i:].lstrip()
        is_comparison = before.endswith(("==", "!=")) or after.startswith(("==", "!="))
        if not is_comparison:
            literals.append("".join(buf))
    return literals


def parse_matrix(where: str, value: str) -> list[list[dict]]:
    """Every JSON list the runner could obtain from this input, or [] once an error is recorded."""
    candidates = expression_string_literals(value) if "${{" in value else [value]
    if not candidates:
        fail(f"{where}: no JSON list found in the value")
        return []
    parsed = []
    for candidate in candidates:
        try:
            matrix = json.loads(candidate)
        except json.JSONDecodeError as exc:
            start = max(0, exc.pos - 90)
            fail(
                f"{where}: not valid JSON for fromJSON: {exc.msg} at character {exc.pos}\n"
                f"    ...{candidate[start:exc.pos]}>>>HERE>>>{candidate[exc.pos:exc.pos + 60]}..."
            )
            continue
        if not isinstance(matrix, list) or not matrix:
            fail(f"{where}: the matrix must be a non-empty list, found {type(matrix).__name__}")
            continue
        if not all(isinstance(entry, dict) for entry in matrix):
            fail(f"{where}: every matrix entry must be an object")
            continue
        parsed.append(matrix)
    return parsed


def check_api_matrix(where: str, matrix: list[dict]) -> None:
    seen: dict[str, int] = {}
    coverage_artifacts: dict[str, str] = {}
    for index, entry in enumerate(matrix):
        name = entry.get("shard_name")
        if not name:
            fail(f"{where}: entry {index} has no shard_name, which names the job and its artifacts")
            continue
        if name in seen:
            fail(f"{where}: shard_name '{name}' is used by entries {seen[name]} and {index}")
        seen[name] = index

        # Coverage artifacts are named from shard + artifact_suffix, not from shard_name
        # (api-tests/action.yml), so two entries with distinct shard_names can still collide there
        # and the second upload fails the run. Shadow entries upload no coverage.
        if not entry.get("tenant_mode"):
            coverage = f"jacoco-exec-shard-{entry.get('shard')}{entry.get('artifact_suffix', '')}"
            if coverage in coverage_artifacts:
                fail(
                    f"{where}: entries '{coverage_artifacts[coverage]}' and '{name}' would both "
                    f"upload the coverage artifact '{coverage}'"
                )
            else:
                coverage_artifacts[coverage] = name

        if entry.get("includes") == "shardfile":
            shard = entry.get("shard")
            shard_file = SHARD_DIR / f"api-{shard}.txt"
            if not shard_file.is_file():
                fail(
                    f"{where}: entry '{name}' reads shard file {shard_file.relative_to(REPO)}, "
                    "which does not exist"
                )

        mode = entry.get("tenant_mode")
        if mode is not None and mode not in ("production", "all"):
            fail(f"{where}: entry '{name}' has tenant_mode '{mode}', expected 'production' or 'all'")


def check_e2e_matrix(where: str, matrix: list[dict]) -> None:
    # The Playwright report artifact is named after the browser and the suffix, and a duplicate
    # name fails the upload at the end of a forty-minute job.
    seen: dict[tuple[str, str], int] = {}
    for index, entry in enumerate(matrix):
        key = (entry.get("browser", ""), entry.get("artifact_suffix", ""))
        if key in seen:
            fail(
                f"{where}: entries {seen[key]} and {index} both upload "
                f"playwright-report-{key[0]}{key[1]}"
            )
        seen[key] = index


def main() -> int:
    try:
        import yaml
    except ImportError:
        print("PyYAML is required: pip install pyyaml", file=sys.stderr)
        return 2

    inputs_to_check = json_consuming_inputs()
    if not inputs_to_check:
        print(f"No fromJSON(inputs.*) found in {PIPELINE.relative_to(REPO)}", file=sys.stderr)
        return 2

    callers = []
    for path in sorted(WORKFLOWS.glob("*.yml")):
        if path == PIPELINE:
            continue
        document = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        for job_name, job in (document.get("jobs") or {}).items():
            if not isinstance(job, dict):
                continue
            if job.get("uses") != "./.github/workflows/_ci-pipeline.yml":
                continue
            callers.append((path, job_name, job.get("with") or {}))

    if not callers:
        print("No caller of the pipeline found", file=sys.stderr)
        return 2

    checked = 0
    for path, job_name, given in callers:
        for name in sorted(inputs_to_check):
            if name not in given:
                fail(f"{path.name} ({job_name}): the pipeline parses input '{name}' but it is not passed")
                continue
            where = f"{path.name} ({job_name}) input '{name}'"
            for matrix in parse_matrix(where, str(given[name])):
                checked += 1
                if name.startswith("api"):
                    check_api_matrix(where, matrix)
                elif name.startswith("e2e"):
                    check_e2e_matrix(where, matrix)
                print(f"OK  {where}: {len(matrix)} entries")

    if errors:
        print("", file=sys.stderr)
        for message in errors:
            print(f"ERROR {message}", file=sys.stderr)
        print(f"\n{len(errors)} problem(s) found", file=sys.stderr)
        return 1

    print(f"\n{checked} matrix list(s) parsed by the JSON parser the runner uses")
    return 0


if __name__ == "__main__":
    sys.exit(main())
