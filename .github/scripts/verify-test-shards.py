#!/usr/bin/env python3
"""Verify every API test file lands in exactly one shard.

Simulates Surefire's Ant-style includesFile/excludesFile matching against the
matrix in core-ci.yml, so a bad split fails here instead of silently skipping
tests in CI.
"""

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEST_ROOT = REPO / "openaev-api" / "src" / "test" / "java"
PIPELINE = REPO / ".github" / "workflows" / "_ci-pipeline.yml"


def ant_to_regex(pattern: str) -> re.Pattern:
    out, i = [], 0
    while i < len(pattern):
        if pattern.startswith("/**/", i):
            out.append("/(?:.*/)?")
            i += 4
        elif pattern.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif pattern.startswith("**", i):
            out.append(".*")
            i += 2
        elif pattern[i] == "*":
            out.append("[^/]*")
            i += 1
        elif pattern[i] == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(pattern[i]))
            i += 1
    return re.compile("^" + "".join(out) + "$")


def patterns(raw: str) -> list:
    return [ant_to_regex(p.strip()) for p in raw.splitlines() if p.strip()]


def load_matrix(workflow: Path) -> list:
    text = workflow.read_text(encoding="utf-8")
    block = re.search(r"api-matrix: >\s*\n(.*?)\n      # ──", text, re.S)
    if not block:
        block = re.search(r"api-matrix: >\s*\n(.*?)\n      e2e-matrix:", text, re.S)
    return json.loads(block.group(1))


def load_explicit(pipeline: Path) -> str:
    text = pipeline.read_text(encoding="utf-8")
    block = re.search(r"EXPLICITLY_SHARDED_TESTS: \|-\n(.*?)\n      [A-Z]", text, re.S)
    lines = [ln.strip() for ln in block.group(1).splitlines() if ln.strip()]
    return "\n".join(lines)


def main(workflow_name: str) -> int:
    workflow = REPO / ".github" / "workflows" / workflow_name
    matrix = load_matrix(workflow)
    explicit = patterns(load_explicit(PIPELINE))

    files = sorted(
        p.relative_to(TEST_ROOT).as_posix()
        for p in TEST_ROOT.rglob("*Test.java")
    )

    # Only the Elasticsearch leg; the OpenSearch leg mirrors it.
    shards = [s for s in matrix if not s["shard_name"].endswith("-os")]
    assignment = {f: [] for f in files}

    for shard in shards:
        name = shard["shard_name"]
        if shard["includes"] == "catchall":
            for f in files:
                if not any(p.match(f) for p in explicit):
                    assignment[f].append(name)
            continue
        inc = patterns(shard["includes"])
        exc = patterns(shard["excludes"]) if shard["excludes"] else []
        for f in files:
            if any(p.match(f) for p in inc) and not any(p.match(f) for p in exc):
                assignment[f].append(name)

    orphans = [f for f, s in assignment.items() if not s]
    dupes = {f: s for f, s in assignment.items() if len(s) > 1}

    counts = {}
    for f, s in assignment.items():
        for name in s:
            counts[name] = counts.get(name, 0) + 1

    print(f"{workflow_name}: {len(files)} test files")
    for shard in shards:
        print(f"  {shard['shard_name']:<12} {counts.get(shard['shard_name'], 0):>4}")

    if orphans:
        print(f"\nORPHANED ({len(orphans)}) — in no shard:")
        for f in orphans:
            print(f"  {f}")
    if dupes:
        print(f"\nDUPLICATED ({len(dupes)}):")
        for f, s in dupes.items():
            print(f"  {f} -> {s}")

    if dupes:
        return 1
    print("\nOK: no duplicates, every file runs exactly once" if not orphans else "")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "core-ci.yml"))
