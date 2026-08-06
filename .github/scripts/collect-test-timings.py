#!/usr/bin/env python3
"""Extract per-test-class runtimes from API shard job logs of a finished run.

Surefire logs one line per class:
    [INFO] Tests run: 5, ..., Time elapsed: 12.34 s -- in io.openaev.rest.FooTest
Aggregating those gives exact costs to rebalance shards with, instead of
inferring them from a model.

    python .github/scripts/collect-test-timings.py <run_id>
"""

import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / ".github" / "shards" / ".timings.json"

LINE = re.compile(
    r"Tests run:\s*(\d+).*?Time elapsed:\s*([\d.,]+)\s*s\s*(?:--)?\s*in\s+"
    r"(io\.openaev\.[A-Za-z0-9_.$]+)"
)


def gh(args):
    return subprocess.run(
        ["gh"] + args, capture_output=True, text=True, encoding="utf-8", errors="replace"
    ).stdout


def main(run_id):
    repo = "OpenAEV-Platform/openaev"
    jobs = json.loads(gh(["api", f"repos/{repo}/actions/runs/{run_id}/jobs?per_page=100",
                          "--paginate", "--slurp"]))
    all_jobs = [j for page in jobs for j in page["jobs"]] if isinstance(jobs, list) else jobs["jobs"]
    api_jobs = [j for j in all_jobs if "API Tests" in j["name"]]
    print(f"found {len(api_jobs)} API test job(s)")

    timings = {}
    for job in api_jobs:
        log = gh(["api", f"repos/{repo}/actions/jobs/{job['id']}/logs"])
        found = 0
        for m in LINE.finditer(log):
            count, elapsed, cls = m.group(1), m.group(2).replace(",", "."), m.group(3)
            try:
                secs = float(elapsed)
            except ValueError:
                continue
            # @Nested classes report separately; fold them into the owning file.
            cls = cls.split("$", 1)[0]
            prev = timings.get(cls, (0.0, 0))
            timings[cls] = (prev[0] + secs, prev[1] + int(count))
            found += 1
        print(f"  {job['name'].replace('pipeline / ',''):<28}{found:>5} classes")

    if not timings:
        print("no timings parsed — check the log format")
        return 1

    total = sum(v[0] for v in timings.values())
    print(f"\n{len(timings)} classes, {total/60:.1f} min of measured class time")

    print("\nslowest 15 classes:")
    for cls, (secs, n) in sorted(timings.items(), key=lambda kv: -kv[1][0])[:15]:
        print(f"  {secs:>7.1f}s  {n:>4} tests  {cls}")

    OUT.write_text(json.dumps(
        {c: {"seconds": s, "tests": n} for c, (s, n) in sorted(timings.items())},
        indent=2), encoding="utf-8")
    print(f"\nwrote {OUT.relative_to(REPO)}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "30795177398"))
