#!/usr/bin/env python3
"""Bin-pack API test packages into N balanced, numbered shards.

Cost model is fitted in balance-api-shards.py against measured mvn-test times from
run 30624476290 attempt 2: minutes = 4.40 + 0.0405*heavy + 0.0199*light, where
"heavy" is a Spring-context test class. Units are top-level packages, split one
level deeper when a package is large, so the emitted patterns stay readable.
"""

import json
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEST_ROOT = REPO / "openaev-api" / "src" / "test" / "java"
PKG = "io/openaev"
HEAVY = re.compile(r"@SpringBootTest|@IntegrationTest|@DataJpaTest|@WebMvcTest")
ALPHA, BETA = 0.0405, 0.0199
SPLIT_THRESHOLD = 30  # files; larger packages get split one level deeper


def scan():
    files = {}
    for p in (TEST_ROOT / PKG).rglob("*Test.java"):
        rel = p.relative_to(TEST_ROOT / PKG).as_posix()
        files[rel] = bool(HEAVY.search(p.read_text(encoding="utf-8", errors="ignore")))
    return files


def build_units(files):
    """Partition files into units, each expressible as one surefire pattern."""
    top = {}
    for rel in files:
        parts = rel.split("/")
        top.setdefault(parts[0] if len(parts) > 1 else "", []).append(rel)

    units = []
    for name, group in sorted(top.items()):
        if name == "":
            units.append((f"{PKG}/*Test.java", group))
            continue
        if len(group) <= SPLIT_THRESHOLD:
            units.append((f"{PKG}/{name}/**/*Test.java", group))
            continue
        # Split: direct children, then each sub-package as its own unit
        direct = [r for r in group if r.count("/") == 1]
        if direct:
            units.append((f"{PKG}/{name}/*Test.java", direct))
        subs = {}
        for r in group:
            if r.count("/") > 1:
                subs.setdefault(r.split("/")[1], []).append(r)
        for sub, members in sorted(subs.items()):
            units.append((f"{PKG}/{name}/{sub}/**/*Test.java", members))
    return units


def cost(members, files):
    h = sum(1 for m in members if files[m])
    return ALPHA * h + BETA * (len(members) - h)


def pack(units, files, n):
    """Longest-processing-time-first bin packing."""
    ranked = sorted(units, key=lambda u: cost(u[1], files), reverse=True)
    shards = [{"patterns": [], "files": [], "cost": 0.0} for _ in range(n)]
    for pattern, members in ranked:
        target = min(shards, key=lambda s: s["cost"])
        target["patterns"].append(pattern)
        target["files"].extend(members)
        target["cost"] += cost(members, files)
    return shards


def main(n):
    files = scan()
    units = build_units(files)
    assert sum(len(m) for _, m in units) == len(files), "unit partition lost files"
    print(f"{len(files)} test files -> {len(units)} assignable units")

    # The catch-all must never be empty: surefire would run zero tests and the
    # JaCoCo verify step would fail. Reserve the loose classes at the package root.
    root_pattern = f"{PKG}/*Test.java"
    reserved = [u for u in units if u[0] == root_pattern]
    if not reserved:
        reserved = [sorted(units, key=lambda u: (cost(u[1], files), len(u[1])))[0]]
    reserved_files = sum(len(m) for _, m in reserved)
    packable = [u for u in units if u not in reserved]

    shards = pack(packable, files, n)
    print(f"\n{n} numbered shards (predicted = 4.40 fixed + variable):")
    print(f"  {'shard':<8}{'pkgs':>6}{'files':>7}{'heavy':>7}{'var':>7}{'pred min':>10}")
    for i, s in enumerate(shards, 1):
        h = sum(1 for f in s["files"] if files[f])
        print(f"  {i:<8}{len(s['patterns']):>6}{len(s['files']):>7}{h:>7}"
              f"{s['cost']:>7.2f}{4.40 + s['cost']:>10.1f}")
    spread = max(s["cost"] for s in shards) - min(s["cost"] for s in shards)
    print(f"  spread: {spread:.2f} min   max shard: {4.40 + max(s['cost'] for s in shards):.1f} min")
    print(f"  remaining (catch-all): {reserved_files} files, "
          f"{len(reserved)} pkgs, ~{4.40 + sum(cost(m, files) for _, m in reserved):.1f} min")

    all_patterns = [p for s in shards for p in s["patterns"]]
    assert len(all_patterns) == len(set(all_patterns))
    assert len(all_patterns) + len(reserved) == len(units)

    out = {
        "shards": [{"shard": str(i), "patterns": sorted(s["patterns"])}
                   for i, s in enumerate(shards, 1)],
        "explicit": sorted(all_patterns),
        "reserved": sorted(p for p, _ in reserved),
    }
    Path(REPO / ".github/scripts/.shards.json").write_text(
        json.dumps(out, indent=2), encoding="utf-8")
    print("\nwrote .github/scripts/.shards.json")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 6)
