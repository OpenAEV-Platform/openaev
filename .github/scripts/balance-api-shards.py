#!/usr/bin/env python3
"""Rebalance API test shards from measured per-class runtimes.

Reads .github/shards/.timings.json (produced by collect-test-timings.py) and
bin-packs the test packages into balanced shards. An earlier attempt modelled
cost from @SpringBootTest counts; that fit had ~1 min rms — the same magnitude
as the improvement being chased — so this uses observed times instead.

Classes with no recorded time get DEFAULT_SECONDS. In run 30795177398 the
measured classes accounted for 14.8 of the ~17.4 min of variable work, so the
unmeasured remainder averages well under a second each.

    python .github/scripts/balance-api-shards.py [num_shards]
"""

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEST_ROOT = REPO / "openaev-api" / "src" / "test" / "java"
SHARD_DIR = REPO / ".github" / "shards"
TIMINGS = SHARD_DIR / ".timings.json"
PKG = "io/openaev"

# Per-shard JVM + Spring context + Maven startup, measured across shards whose
# class-time totals differ by 6x yet whose runtimes differ by far less.
FIXED_MINUTES = 3.4
DEFAULT_SECONDS = 0.6
SPLIT_THRESHOLD = 30  # files; larger packages are split one level deeper


def load_timings():
    if not TIMINGS.exists():
        sys.exit(f"missing {TIMINGS.relative_to(REPO)} — run collect-test-timings.py first")
    raw = json.loads(TIMINGS.read_text(encoding="utf-8"))
    # FQCN -> relative source path
    return {cls.replace(".", "/") + ".java": v["seconds"] for cls, v in raw.items()}


def scan():
    return sorted(p.relative_to(TEST_ROOT).as_posix() for p in TEST_ROOT.rglob("*Test.java"))


def units(files):
    top = {}
    for rel in files:
        rest = rel[len(PKG) + 1:]
        parts = rest.split("/")
        top.setdefault(parts[0] if len(parts) > 1 else "", []).append(rel)
    out = []
    for name, group in sorted(top.items()):
        if name == "":
            out.append((f"{PKG}/*Test.java", group))
            continue
        if len(group) <= SPLIT_THRESHOLD:
            out.append((f"{PKG}/{name}/**/*Test.java", group))
            continue
        direct = [r for r in group if r[len(PKG) + 1:].count("/") == 1]
        if direct:
            out.append((f"{PKG}/{name}/*Test.java", direct))
        subs = {}
        for r in group:
            rest = r[len(PKG) + 1:]
            if rest.count("/") > 1:
                subs.setdefault(rest.split("/")[1], []).append(r)
        for sub, members in sorted(subs.items()):
            out.append((f"{PKG}/{name}/{sub}/**/*Test.java", members))
    return out


def main(n=7):
    timings = load_timings()
    files = scan()
    measured = sum(1 for f in files if f in timings)
    total_s = sum(timings.get(f, DEFAULT_SECONDS) for f in files)
    print(f"{len(files)} test files, {measured} with measured timings "
          f"({measured * 100 // len(files)}%)")
    print(f"variable work: {total_s / 60:.1f} min   fixed per shard: {FIXED_MINUTES:.1f} min")

    def cost(members):
        return sum(timings.get(m, DEFAULT_SECONDS) for m in members)

    u = units(files)
    root = f"{PKG}/*Test.java"
    reserved = [x for x in u if x[0] == root]
    packable = [x for x in u if x not in reserved]

    print(f"\n{len(u)} assignable units; 10 most expensive:")
    for pat, members in sorted(packable, key=lambda x: -cost(x[1]))[:10]:
        print(f"  {cost(members):>7.1f}s  {len(members):>4} files  {pat}")

    bins = [{"pat": [], "files": [], "cost": 0.0} for _ in range(n)]
    for pattern, members in sorted(packable, key=lambda x: cost(x[1]), reverse=True):
        t = min(bins, key=lambda b: b["cost"])
        t["pat"].append(pattern)
        t["files"].extend(members)
        t["cost"] += cost(members)

    print(f"\nrepacked into {n} shards + catch-all:")
    for i, b in enumerate(bins, 1):
        print(f"  api-{i:<6}{len(b['files']):>5} files  {b['cost']:>7.1f}s var"
              f"   ~{FIXED_MINUTES + b['cost'] / 60:>4.1f} min")
    print(f"  remaining{sum(len(m) for _, m in reserved):>5} files"
          f"   ~{FIXED_MINUTES:>17.1f} min")
    worst = FIXED_MINUTES + max(b["cost"] for b in bins) / 60
    spread = (max(b["cost"] for b in bins) - min(b["cost"] for b in bins)) / 60
    print(f"\n  predicted max {worst:.1f} min  (spread {spread:.2f} min)")

    for i, b in enumerate(bins, 1):
        (SHARD_DIR / f"api-{i}.txt").write_text(
            "\n".join(sorted(b["pat"])) + "\n", encoding="utf-8", newline="\n")
    for extra in range(n + 1, 20):
        stale = SHARD_DIR / f"api-{extra}.txt"
        if stale.exists():
            stale.unlink()
            print(f"  removed stale {stale.name}")
    print(f"\nwrote {n} shard file(s) to .github/shards/")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 7)
