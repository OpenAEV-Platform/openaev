#!/usr/bin/env python3
"""Fail when a pull request drops backend line coverage by more than a given number of points.

The pull request's JaCoCo report is compared with the report of the last commit of the main
branch. Core CI uploads that report as the `jacoco-api` artifact on every push to main, and only
there: a branch that is not main has no reports of its own, so main is the base whatever branch the
pull request targets. The base report is the newest artifact whose commit is the tip of main or
one of its ancestors: when the run on the tip itself has not finished yet, the closest earlier one
stands in for it.

Codecov compares with the commit the pull request branched from, not the tip of main. The two are
the same once the branch is up to date with main, so a pull request that is behind and shows
another drop in Codecov is fixed by a rebase.

Line coverage is counted the way Codecov counts it (see jacoco_lines.py): a line where only some
branches ran is partial, and partial lines are not covered.

No base report (artifacts expired, or no green run on main for a while) is not a
failure: the check cannot measure a drop, says so in its output and passes. Codecov reports the
numbers on the pull request; this script only passes or fails.

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


def main_tip(repo: str, main_branch: str) -> str:
    return json.loads(gh_api(f"repos/{repo}/commits/{main_branch}"))["sha"]


def find_base_report(repo: str, base_sha: str, main_branch: str) -> tuple[str, bytes] | None:
    for page in range(1, MAX_PAGES + 1):
        artifacts = json.loads(
            gh_api(f"repos/{repo}/actions/artifacts?name={ARTIFACT}&per_page=100&page={page}")
        )["artifacts"]
        if not artifacts:
            return None
        for artifact in artifacts:
            run = artifact.get("workflow_run") or {}
            if artifact["expired"] or run.get("head_branch") != main_branch:
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
    parser.add_argument("--main-branch", default="main", help="branch that has the reports")
    parser.add_argument(
        "--max-drop", type=float, required=True, help="largest drop allowed, in points"
    )
    args = parser.parse_args()
    repo = os.environ["GITHUB_REPOSITORY"]

    with open(args.report, "rb") as report:
        head_covered, head_total = line_coverage(report.read())
    head = 100 * head_covered / head_total

    base_sha = main_tip(repo, args.main_branch)
    base = find_base_report(repo, base_sha, args.main_branch)
    if base is None:
        print(
            f"No {ARTIFACT} report found for {args.main_branch} at or before "
            f"{base_sha[:10]}: the drop cannot be measured. Pull request: {head:.2f}%."
        )
        return 0

    found_sha, base_xml = base
    base_covered, base_total = line_coverage(base_xml)
    base_cov = 100 * base_covered / base_total
    drop = base_cov - head
    compared = found_sha[:10]
    if found_sha != base_sha:
        compared += f", closest report before {base_sha[:10]}"
    print(f"Base ({compared}): {base_covered} / {base_total} lines, {base_cov:.2f}%")
    print(f"Pull request: {head_covered} / {head_total} lines, {head:.2f}%")
    if drop > args.max_drop:
        print(f"Backend line coverage drops by {drop:.2f} points, more than {args.max_drop}.")
        return 1
    print(f"Change: {-drop:+.2f} points, largest drop allowed {args.max_drop}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
