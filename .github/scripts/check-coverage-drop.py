#!/usr/bin/env python3
"""Fail when a pull request drops backend line coverage by more than a given number of points.

The pull request's JaCoCo report is compared with the report of its base commit. Core CI uploads
that report as the `jacoco-api` artifact on every push to the base branch, so the base report is
the newest such artifact whose commit is the base commit or one of its ancestors: when the run on
the base commit itself has not finished yet, the closest earlier one stands in for it.

The base commit is the tip of the target branch, the first parent of the merge commit CI checks
out.

Line coverage counts a line where only some branches ran as not covered (see jacoco_lines.py).

No base report (artifacts expired, or no green run on the base branch for a while) is not a
failure: the check cannot measure a drop, says so in its output and passes.

Needs `gh` with a token that can read the repository's Actions artifacts (GH_TOKEN), and
GITHUB_REPOSITORY.
"""

from __future__ import annotations

import argparse
import io
import json
import os
import subprocess
import sys
import zipfile

from jacoco_lines import line_totals, report_lines

SRC_ROOT = "openaev-api/src/main/java"
ARTIFACT = "jacoco-api"
WORKFLOW = ".github/workflows/core-ci.yml"
# Artifacts come newest first; past this many pages the base branch has had no usable run for long.
MAX_PAGES = 10


def gh_api(path: str) -> bytes:
    return subprocess.run(["gh", "api", path], check=True, capture_output=True).stdout


def line_coverage(xml: bytes) -> tuple[int, int]:
    """Covered and total lines of a JaCoCo XML report."""
    hits, partials, misses = line_totals(report_lines(xml, SRC_ROOT))
    return hits, hits + partials + misses


def is_ancestor(repo: str, sha: str, of: str) -> bool:
    if sha == of:
        return True
    status = json.loads(gh_api(f"repos/{repo}/compare/{sha}...{of}"))["status"]
    return status in ("ahead", "identical")


def find_base_report(repo: str, base_sha: str, base_branch: str) -> tuple[str, bytes] | None:
    for page in range(1, MAX_PAGES + 1):
        artifacts = json.loads(
            gh_api(f"repos/{repo}/actions/artifacts?name={ARTIFACT}&per_page=100&page={page}")
        )["artifacts"]
        if not artifacts:
            return None
        for artifact in artifacts:
            run = artifact.get("workflow_run") or {}
            if artifact["expired"] or run.get("head_branch") != base_branch:
                continue
            details = json.loads(gh_api(f"repos/{repo}/actions/runs/{run['id']}"))
            if details["path"] != WORKFLOW or details["event"] != "push":
                continue
            if not is_ancestor(repo, run["head_sha"], base_sha):
                continue
            archive = gh_api(f"repos/{repo}/actions/artifacts/{artifact['id']}/zip")
            return run["head_sha"], zipfile.ZipFile(io.BytesIO(archive)).read("jacoco.xml")
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", required=True, help="JaCoCo XML report of the pull request")
    parser.add_argument("--base-sha", required=True, help="base commit of the pull request")
    parser.add_argument("--base-branch", required=True, help="branch the pull request targets")
    parser.add_argument(
        "--max-drop", type=float, required=True, help="largest drop allowed, in points"
    )
    args = parser.parse_args()
    repo = os.environ["GITHUB_REPOSITORY"]

    with open(args.report, "rb") as report:
        head_covered, head_total = line_coverage(report.read())
    head = 100 * head_covered / head_total

    base = find_base_report(repo, args.base_sha, args.base_branch)
    if base is None:
        print(
            f"No {ARTIFACT} report found for {args.base_branch} at or before "
            f"{args.base_sha[:10]}: the drop cannot be measured. Pull request: {head:.2f}%."
        )
        return 0

    base_sha, base_xml = base
    base_covered, base_total = line_coverage(base_xml)
    base_cov = 100 * base_covered / base_total
    drop = base_cov - head
    compared = base_sha[:10]
    if base_sha != args.base_sha:
        compared += f", closest report before {args.base_sha[:10]}"
    print(f"Base ({compared}): {base_covered} / {base_total} lines, {base_cov:.2f}%")
    print(f"Pull request: {head_covered} / {head_total} lines, {head:.2f}%")
    if drop > args.max_drop:
        print(f"Backend line coverage drops by {drop:.2f} points, more than {args.max_drop}.")
        return 1
    print(f"Change: {-drop:+.2f} points, largest drop allowed {args.max_drop}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
