#!/usr/bin/env python3
"""Fail when a pull request drops backend line coverage by more than a given number of points.

The pull request's JaCoCo report is compared with the report of the last commit of the main
branch. Core CI uploads that report as the `jacoco-api` artifact on every push to main, and only
there: a branch that is not main has no reports of its own, so main is the base whatever branch the
pull request targets. The base report is the one of the tip of main or, when the run on the tip
has not uploaded it yet, of its nearest ancestor that has one, following the commit history rather
than the order the runs finished in.

A pull request that is behind main lacks the commits that main got since, and their tests. When
the drop is over the limit and the base report has commits the pull request does not, the output
says how many and that a rebase on main is the fix: the drop may come from those commits alone.
For a stacked pull request, the branch it targets needs the update first.

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
WORKFLOW = "core-ci.yml"
# How far back main is searched for a commit with a report, in commits and in Core CI runs.
HISTORY = 100


def gh_api(path: str) -> bytes:
    return subprocess.run(["gh", "api", path], check=True, capture_output=True).stdout


def line_coverage(xml: bytes) -> tuple[int, int]:
    """Covered and total lines of a JaCoCo XML report."""
    hits, partials, misses = line_totals(report_lines(xml, SRC_ROOT))
    return hits, hits + partials + misses


def commits_missing(repo: str, target_sha: str, report_sha: str) -> int:
    """Commits in the base report that the target branch does not have."""
    return json.loads(gh_api(f"repos/{repo}/compare/{target_sha}...{report_sha}"))["ahead_by"]


def main_tip(repo: str, main_branch: str) -> str:
    return json.loads(gh_api(f"repos/{repo}/commits/{main_branch}"))["sha"]


def find_base_report(repo: str, base_sha: str, main_branch: str) -> tuple[str, bytes] | None:
    """Report of base_sha, or else of its nearest ancestor that has one."""
    runs: dict[str, list[int]] = {}
    for run in json.loads(
        gh_api(
            f"repos/{repo}/actions/workflows/{WORKFLOW}/runs"
            f"?branch={main_branch}&event=push&per_page={HISTORY}"
        )
    )["workflow_runs"]:
        runs.setdefault(run["head_sha"], []).append(run["id"])
    # Newest first, following the history of base_sha: the first commit with a report is the
    # nearest one, whatever order the runs finished or were retried in.
    for commit in json.loads(gh_api(f"repos/{repo}/commits?sha={base_sha}&per_page={HISTORY}")):
        for run_id in runs.get(commit["sha"], []):
            artifacts = json.loads(
                gh_api(f"repos/{repo}/actions/runs/{run_id}/artifacts?name={ARTIFACT}")
            )["artifacts"]
            for artifact in artifacts:
                if artifact["expired"]:
                    continue
                archive = gh_api(f"repos/{repo}/actions/artifacts/{artifact['id']}/zip")
                return commit["sha"], zipfile.ZipFile(io.BytesIO(archive)).read("jacoco.xml")
    return None


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--report", required=True, help="JaCoCo XML report of the pull request")
    parser.add_argument(
        "--target-sha",
        required=True,
        help="tip of the branch the pull request targets (first parent of the merge commit)",
    )
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
    # Rounded as printed: a drop of exactly max-drop passes despite floating-point noise.
    if round(drop, 2) > args.max_drop:
        print(f"Line coverage drops by {drop:.2f} points, more than {args.max_drop}.")
        if behind := commits_missing(repo, args.target_sha, found_sha):
            print(f"This branch is {behind} commits behind main: rebase it and let CI run again.")
        return 1
    print(f"Change: {-drop:+.2f} points, largest drop allowed {args.max_drop}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
