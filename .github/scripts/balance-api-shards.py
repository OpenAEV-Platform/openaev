#!/usr/bin/env python3
"""Fit a per-package cost model from measured shard times, then bin-pack balanced shards.

Measured mvn-test minutes come from run 30624476290 attempt 2. Cost is modelled as
alpha * (Spring-context tests) + beta * (plain tests) + a fixed per-shard JVM cost;
the model is fitted to the six observed shards, then used to build N balanced shards.
"""

import json
import re
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEST_ROOT = REPO / "openaev-api" / "src" / "test" / "java"
PKG = "io/openaev"

HEAVY = re.compile(r"@SpringBootTest|@IntegrationTest|@DataJpaTest|@WebMvcTest")

MEASURED = {
    "main": 9.1,
    "rest-1": 6.1,
    "rest-2": 7.5,
    "misc-1": 5.4,
    "misc-2": 5.7,
    "remaining": 4.4,
}

SHARD_DIRS = {
    "main": ["api", "integration", "injects", "opencti", "service"],
    "rest-1": ["rest/", "rest/asset_group", "rest/attack_pattern",
               "rest/custom_dashboard", "rest/dashboard", "rest/exercise"],
    "misc-1": ["config", "database", "scheduler"],
    "misc-2": ["datapack", "debug", "output_processor", "executors", "engine",
               "context", "processor", "injector_contract", "helper", "runner",
               "healthcheck", "telemetry", "aop", "architecture", "utils",
               "utilstest", "notification"],
}


def classify():
    """Return {relative_dir: (heavy, light)} for every dir holding test files."""
    stats = {}
    for p in (TEST_ROOT / PKG).rglob("*Test.java"):
        rel = p.parent.relative_to(TEST_ROOT / PKG).as_posix()
        rel = "" if rel == "." else rel
        h, l = stats.get(rel, (0, 0))
        if HEAVY.search(p.read_text(encoding="utf-8", errors="ignore")):
            stats[rel] = (h + 1, l)
        else:
            stats[rel] = (h, l + 1)
    return stats


def units(stats):
    """Assignable units: one per directory, keyed by its glob pattern."""
    out = {}
    for d, (h, l) in stats.items():
        pattern = f"{PKG}/*Test.java" if d == "" else f"{PKG}/{d}/*Test.java"
        out[pattern] = {"dir": d, "heavy": h, "light": l}
    return out


def shard_of(d):
    for name, prefixes in SHARD_DIRS.items():
        for pre in prefixes:
            if pre.endswith("/"):
                if d == pre.rstrip("/"):
                    return name
            elif d == pre or d.startswith(pre + "/"):
                return name
    if d == "" or d.split("/")[0] not in {
        x.split("/")[0] for v in SHARD_DIRS.values() for x in v
    }:
        return "remaining"
    return "rest-2"


def main():
    stats = classify()
    u = units(stats)

    # Aggregate observed shards
    agg = {}
    for pattern, m in u.items():
        s = shard_of(m["dir"])
        a = agg.setdefault(s, [0, 0])
        a[0] += m["heavy"]
        a[1] += m["light"]

    print("Observed shards (fitted inputs):")
    print(f"  {'shard':<12}{'heavy':>7}{'light':>7}{'mvn min':>10}")
    for s in MEASURED:
        h, l = agg.get(s, [0, 0])
        print(f"  {s:<12}{h:>7}{l:>7}{MEASURED[s]:>10.1f}")

    # Least squares: minutes = FIXED + alpha*heavy + beta*light
    import itertools
    best, bestErr = None, 1e9
    for fixed in [x / 10 for x in range(0, 45, 1)]:
        A = [[agg[s][0], agg[s][1]] for s in MEASURED]
        y = [MEASURED[s] - fixed for s in MEASURED]
        # closed-form 2-var normal equations
        s11 = sum(r[0] * r[0] for r in A); s12 = sum(r[0] * r[1] for r in A)
        s22 = sum(r[1] * r[1] for r in A)
        t1 = sum(r[0] * v for r, v in zip(A, y)); t2 = sum(r[1] * v for r, v in zip(A, y))
        det = s11 * s22 - s12 * s12
        if abs(det) < 1e-9:
            continue
        alpha = (t1 * s22 - t2 * s12) / det
        beta = (s11 * t2 - s12 * t1) / det
        if alpha < 0 or beta < 0:
            continue
        err = sum((fixed + alpha * r[0] + beta * r[1] - MEASURED[s]) ** 2
                  for r, s in zip(A, MEASURED))
        if err < bestErr:
            bestErr, best = err, (fixed, alpha, beta)

    fixed, alpha, beta = best
    print(f"\nFitted: minutes = {fixed:.2f} + {alpha:.4f}*heavy + {beta:.4f}*light"
          f"   (rms {(bestErr/len(MEASURED))**0.5:.2f} min)")
    print("  predicted vs actual:")
    for s in MEASURED:
        h, l = agg[s]
        print(f"    {s:<12}{fixed + alpha*h + beta*l:>6.1f} vs {MEASURED[s]:>5.1f}")

    def cost(m):
        return alpha * m["heavy"] + beta * m["light"]

    total_var = sum(cost(m) for m in u.values())
    print(f"\nTotal variable work: {total_var:.1f} min across {len(u)} units")
    for n in (5, 6, 7, 8):
        print(f"  {n} shards -> ~{fixed + total_var/n:.1f} min/shard")
    return u, cost, fixed, total_var


if __name__ == "__main__":
    main()
