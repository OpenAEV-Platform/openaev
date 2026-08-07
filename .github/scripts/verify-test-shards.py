#!/usr/bin/env python3
"""Verify every API test file lands in exactly one shard.

Simulates Surefire's Ant-style includesFile/excludesFile matching against
.github/shards/api-*.txt plus the catch-all, so a bad split fails here instead of
silently skipping tests in CI.
"""

import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEST_ROOT = REPO / "openaev-api" / "src" / "test" / "java"
SHARD_DIR = REPO / ".github" / "shards"


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


def main() -> int:
    shard_files = sorted(SHARD_DIR.glob("api-*.txt"))
    if not shard_files:
        print(f"No shard files found in {SHARD_DIR}")
        return 1

    shards = {}
    for f in shard_files:
        pats = [ln.strip() for ln in f.read_text(encoding="utf-8").splitlines() if ln.strip()]
        shards[f.stem] = [ant_to_regex(p) for p in pats]

    files = sorted(
        p.relative_to(TEST_ROOT).as_posix() for p in TEST_ROOT.rglob("*Test.java")
    )

    assignment = {f: [] for f in files}
    for name, pats in shards.items():
        for f in files:
            if any(p.match(f) for p in pats):
                assignment[f].append(name)
    # The catch-all shard runs whatever no shard file claimed.
    for f in files:
        if not assignment[f]:
            assignment[f].append("remaining")

    counts = {}
    for names in assignment.values():
        for n in names:
            counts[n] = counts.get(n, 0) + 1

    print(f"{len(files)} test files across {len(shard_files)} shard files + catch-all")
    for name in [f.stem for f in shard_files] + ["remaining"]:
        print(f"  {name:<12}{counts.get(name, 0):>4}")

    dupes = {f: n for f, n in assignment.items() if len(n) > 1}
    if dupes:
        print(f"\nDUPLICATED ({len(dupes)}) — runs in more than one shard:")
        for f, n in dupes.items():
            print(f"  {f} -> {n}")
        return 1

    if counts.get("remaining", 0) == 0:
        print("\nCatch-all shard is EMPTY — surefire would run zero tests there "
              "and the JaCoCo verify step would fail.")
        return 1

    print("\nOK: no duplicates, every file runs exactly once, catch-all non-empty")
    return 0


if __name__ == "__main__":
    sys.exit(main())
