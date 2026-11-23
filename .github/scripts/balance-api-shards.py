#!/usr/bin/env python3
"""Rebalance API test shards from measured per-class runtimes.

Reads .github/shards/.timings.json and bin-packs the test packages into balanced
shards. An earlier attempt modelled cost from @SpringBootTest counts; that fit had
~1 min rms — the same magnitude as the improvement being chased — so this uses
observed times instead.

A shard also pays CONTEXT_SECONDS for each Spring test context its classes need,
once however many of its classes share it: Spring caches a context per
configuration, but each shard is a JVM of its own. The configuration of a class is
read from its source (see context_key), and .timings.json holds each class's time
without that start, so a package costs less in a shard that already starts the
contexts it needs. Packing by those contexts keeps the classes that share one
together instead of starting it again in every shard they are spread over.

Classes with no recorded time get DEFAULT_SECONDS. In run 30795177398 the
measured classes accounted for 14.8 of the ~17.4 min of variable work, so the
unmeasured remainder averages well under a second each.

    python .github/scripts/balance-api-shards.py [num_shards]
"""

import json
import re
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
SPLIT_THRESHOLD = 30  # files; larger packages are split into their subpackages
# Start of one more Spring test context in a shard: the median gap between a class
# that starts its context and the same class reusing it, across two layouts.
CONTEXT_SECONDS = 10.0

# Packages that never share a Surefire fork: each gets a shard of its own, numbered after
# the packed ones. architecture: its ArchUnit rules import all of io.openaev after the
# fork's Spring tests, on top of the contexts they left cached, and ran shard 6 out of
# heap in run 44228102277.
ISOLATED = ["architecture"]

CLASS_DECL = re.compile(
    r"^(?:public |abstract |final )*class (\w+)(?:<[^>{]*>)?(?:\s+extends\s+([\w.]+))?", re.MULTILINE)
# Class-level annotations that are part of the configuration Spring caches a context by.
CONTEXT_ANNOTATION = re.compile(
    r"^@(SpringBootTest|TestPropertySource|Import|ContextConfiguration|ActiveProfiles|"
    r"AutoConfigure\w+|DirtiesContext|WebMvcTest|DataJpaTest|JsonTest|SpringJUnitConfig)\b",
    re.MULTILINE)
BEAN_OVERRIDE = re.compile(
    r"@(MockitoBean|MockitoSpyBean)(\([^)]*\))?\s+(?:(?:private|protected|public|final)\s+)*"
    r"([\w.<>?, \[\]]+?)\s+\w+\s*;")
DYNAMIC_PROPERTIES = re.compile(r"@DynamicPropertySource\s+(?:\w+\s+)*void\s+(\w+)\s*\(")
NESTED_CONFIGURATION = re.compile(
    r"@(?:Test)?Configuration\b(?:\s*\n\s*@\w+(?:\([^)]*\))?)*\s*"
    r"(?:public |private |protected )?static class")


def load_timings():
    if not TIMINGS.exists():
        sys.exit(f"missing {TIMINGS.relative_to(REPO)} — run collect-test-timings.py first")
    raw = json.loads(TIMINGS.read_text(encoding="utf-8"))
    # FQCN -> relative source path
    return {cls.replace(".", "/") + ".java": v["seconds"] for cls, v in raw.items()}


def annotation_text(src, start):
    """The annotation at src[start], parentheses included, comments dropped, spaces squeezed."""
    i = start + 1
    while i < len(src) and (src[i].isalnum() or src[i] == "_"):
        i += 1
    if i < len(src) and src[i] == "(":
        depth = 0
        for j in range(i, len(src)):
            depth += src[j] == "("
            depth -= src[j] == ")"
            if depth == 0:
                i = j + 1
                break
    return re.sub(r"\s+", " ", re.sub(r"//[^\n]*", "", src[start:i])).strip()


class ContextKeys:
    """The Spring test context each class needs, read statically from the test sources.

    Two classes share a context when Spring builds the same configuration for both: the same
    class-level configuration annotations, bean overrides and @DynamicPropertySource methods,
    their superclasses' included. A @DynamicPropertySource method counts by identity, as Spring
    compares it, so classes share it only by inheriting it. Listeners and @Nested classes are left
    out: listeners are not part of the configuration, and a nested class's own context is a cost
    of its enclosing class wherever it runs.
    """

    def __init__(self, root):
        self.root = root
        self.by_name = {}
        for path in root.rglob("*.java"):
            self.by_name.setdefault(path.stem, []).append(path)
        self.cache = {}

    def parts(self, path):
        if path not in self.cache:
            self.cache[path] = self._parts(path)
        return self.cache[path]

    def _parts(self, path):
        src = path.read_text(encoding="utf-8")
        decl = CLASS_DECL.search(src)
        if not decl:
            return False, ()
        header, body = src[: decl.start()], src[decl.start():]
        name = path.relative_to(self.root).with_suffix("").as_posix().replace("/", ".")
        spring = bool(CONTEXT_ANNOTATION.search(header))
        parts = [annotation_text(header, a.start()) for a in CONTEXT_ANNOTATION.finditer(header)]
        for override in BEAN_OVERRIDE.finditer(body):
            bean_type = "".join(override.group(3).split())
            parts.append(f"@{override.group(1)}{override.group(2) or ''} {bean_type}")
        parts += [f"dynamic {name}#{d.group(1)}" for d in DYNAMIC_PROPERTIES.finditer(body)]
        if NESTED_CONFIGURATION.search(body):
            parts.append(f"configuration {name}")
        parent = decl.group(2)
        if parent:
            found = self.by_name.get(parent.rsplit(".", 1)[-1], [])
            found = [p for p in found if p.parent == path.parent] or found
            if len(found) == 1:
                parent_spring, parent_parts = self.parts(found[0])
                spring = spring or parent_spring
                parts = list(parent_parts) + parts
        return spring, tuple(parts)

    def key(self, rel):
        """The context key of a test file relative to the test root, None if Spring builds none."""
        spring, parts = self.parts(self.root / rel)
        return "|".join(sorted(set(parts))) if spring else None


def scan():
    return sorted(p.relative_to(TEST_ROOT).as_posix() for p in TEST_ROOT.rglob("*Test.java"))


def package_units(pkg, group):
    """A package as one unit, or, past SPLIT_THRESHOLD files, its direct classes and each of its
    subpackages, split the same way."""
    if len(group) <= SPLIT_THRESHOLD:
        return [(f"{pkg}/**/*Test.java", group)]
    direct = [r for r in group if "/" not in r[len(pkg) + 1:]]
    subs = {}
    for r in group:
        rest = r[len(pkg) + 1:]
        if "/" in rest:
            subs.setdefault(rest.split("/")[0], []).append(r)
    if not subs:
        return [(f"{pkg}/**/*Test.java", group)]
    out = [(f"{pkg}/*Test.java", direct)] if direct else []
    for sub, members in sorted(subs.items()):
        out += package_units(f"{pkg}/{sub}", members)
    return out


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
        else:
            out += package_units(f"{PKG}/{name}", group)
    return out


def main(n=7):
    timings = load_timings()
    files = scan()
    measured = sum(1 for f in files if f in timings)
    total_s = sum(timings.get(f, DEFAULT_SECONDS) for f in files)
    print(f"{len(files)} test files, {measured} with measured timings "
          f"({measured * 100 // len(files)}%)")
    print(f"variable work: {total_s / 60:.1f} min   fixed per shard: {FIXED_MINUTES:.1f} min")

    keys = ContextKeys(TEST_ROOT)

    def work(members):
        return sum(timings.get(m, DEFAULT_SECONDS) for m in members)

    def contexts(members):
        return {k for k in (keys.key(m) for m in members) if k is not None}

    def cost(members):
        return work(members) + CONTEXT_SECONDS * len(contexts(members))

    isolated = [(name, [f for f in files if f.startswith(f"{PKG}/{name}/")]) for name in ISOLATED]
    isolated_files = {f for _, members in isolated for f in members}
    u = units([f for f in files if f not in isolated_files])
    root = f"{PKG}/*Test.java"
    reserved = [x for x in u if x[0] == root]
    # A package that costs more than a shard's share would alone decide the run length, so its
    # classes are listed one by one instead. A class added to it later is listed by no shard file
    # and runs in the catch-all until the next rebalance.
    everything = [m for pat, members in u if pat != root for m in members]
    share = cost(everything) / n
    split = []
    for pat, members in u:
        if pat != root and len(members) > 1 and cost(members) > share:
            split += [(m, [m]) for m in members]
        else:
            split.append((pat, members))
    packable = [(pat, members, contexts(members)) for pat, members in split if pat != root]
    spread_starts = sum(len(c) for _, _, c in packable)
    all_contexts = set().union(*(c for _, _, c in packable))
    print(f"{len(all_contexts)} Spring test contexts; {spread_starts} starts if every package"
          f" ran in a shard of its own, {CONTEXT_SECONDS:.0f}s each")

    print(f"\n{len(u)} assignable units; 10 most expensive:")
    for pat, members, _ in sorted(packable, key=lambda x: -cost(x[1]))[:10]:
        print(f"  {cost(members):>7.1f}s  {len(members):>4} files  {pat}")

    class Bin:
        def __init__(self):
            self.units = []

        @property
        def files(self):
            return [m for _, members, _ in self.units for m in members]

        def cost(self, plus=(), minus=()):
            kept = [x for x in self.units if x not in minus] + list(plus)
            ctx = set().union(*(c for _, _, c in kept)) if kept else set()
            return sum(work(m) for _, m, _ in kept) + CONTEXT_SECONDS * len(ctx)

    # Largest first, each into the shard it makes cheapest to run: a package whose contexts a
    # shard already starts costs that shard only its own tests.
    bins = [Bin() for _ in range(n)]
    for unit in sorted(packable, key=lambda x: cost(x[1]), reverse=True):
        min(bins, key=lambda b: b.cost(plus=[unit])).units.append(unit)

    # Then move or swap packages out of the slowest shard while that lowers it.
    improved = True
    while improved:
        improved = False
        worst = max(bins, key=lambda b: b.cost())
        top = worst.cost()
        for unit in sorted(worst.units, key=lambda x: -work(x[1])):
            for other in bins:
                if other is worst:
                    continue
                if max(worst.cost(minus=[unit]), other.cost(plus=[unit])) < top - 1e-6:
                    worst.units.remove(unit)
                    other.units.append(unit)
                    improved = True
                    break
                for swap in other.units:
                    if max(worst.cost(minus=[unit], plus=[swap]),
                           other.cost(minus=[swap], plus=[unit])) < top - 1e-6:
                        worst.units.remove(unit)
                        other.units.remove(swap)
                        worst.units.append(swap)
                        other.units.append(unit)
                        improved = True
                        break
                if improved:
                    break
            if improved:
                break

    starts = sum(len(set().union(*(c for _, _, c in b.units))) for b in bins if b.units)
    print(f"\nrepacked into {n} shards + {len(isolated)} isolated + catch-all,"
          f" {starts} context starts:")
    for i, b in enumerate(bins, 1):
        print(f"  api-{i:<6}{len(b.files):>5} files  {b.cost():>7.1f}s var"
              f"   ~{FIXED_MINUTES + b.cost() / 60:>4.1f} min")
    for i, (name, members) in enumerate(isolated, n + 1):
        print(f"  api-{i:<6}{len(members):>5} files  {cost(members):>7.1f}s var"
              f"   ~{FIXED_MINUTES + cost(members) / 60:>4.1f} min  ({name}, isolated)")
    print(f"  remaining{sum(len(m) for _, m in reserved):>5} files"
          f"   ~{FIXED_MINUTES:>17.1f} min")
    costs = [b.cost() for b in bins]
    print(f"\n  predicted max {FIXED_MINUTES + max(costs) / 60:.1f} min"
          f"  (spread {(max(costs) - min(costs)) / 60:.2f} min)")

    for i, b in enumerate(bins, 1):
        (SHARD_DIR / f"api-{i}.txt").write_text(
            "\n".join(sorted(pat for pat, _, _ in b.units)) + "\n", encoding="utf-8", newline="\n")
    for i, (name, _) in enumerate(isolated, n + 1):
        (SHARD_DIR / f"api-{i}.txt").write_text(
            f"{PKG}/{name}/**/*Test.java\n", encoding="utf-8", newline="\n")
    written = n + len(isolated)
    for extra in range(written + 1, 40):
        stale = SHARD_DIR / f"api-{extra}.txt"
        if stale.exists():
            stale.unlink()
            print(f"  removed stale {stale.name}")
    print(f"\nwrote {written} shard file(s) to .github/shards/")


if __name__ == "__main__":
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 7)
