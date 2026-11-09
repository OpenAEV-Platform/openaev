#!/usr/bin/env python3
"""Post a check run that explains the vulnerabilities Aikido found in the CI images.

The aikido-image-scan job scans the images the pipeline builds with Aikido's release gate on
`main` and other pushes, and its PR gate on pull requests, and stays green on findings. When an
image has issues at or above the threshold (open issues of the image, or the ones a pull request
adds), this check run says so: a short title with the count per image for the PR checks list, and
in its details the issues of each image, how to fix them, and why it does not block. It is posted
with the PR checks app, like the PR title check, so it shows as its own check on the pull request
or the commit.

Needs `gh`, GH_TOKEN with checks:write, and GITHUB_REPOSITORY.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess

NAME = "Aikido Image Scan (advisory)"
# GitHub caps a check run summary at 65535 characters; each image report is cut well before that.
MAX_REPORT = 25000


def intro(mode: str, threshold: str) -> str:
    which = (
        "the open issues of each image"
        if mode == "release"
        else "the issues this pull request adds to each image, compared with its base branch"
    )
    return f"""\
The Docker images built by this pipeline are scanned with Aikido's local scanner. This check lists \
{which}, rated `{threshold}` or above. Issues ignored in Aikido are left out.
"""


FIX = """\
### How to fix it

On a pull request the table gives the installed and fixed versions and the file of each package. \
On `main` it gives one row per package: each CVE links to its osv.dev page, which lists the \
affected and fixed versions, and the Aikido link shows them with the file, for members of the \
Aikido workspace.

- **Operating system package** (`curl`, `libssl3`…): the fix usually comes with a newer base \
image. Bump the `app` stage image in `Dockerfile` or `Dockerfile_ubi9`, or upgrade the package in \
that stage.
- **Java library** (`spring-core`, `FasterXML jackson-databind`…): bump the dependency, or the BOM \
that brings it, in the `pom.xml` to a fixed version.
- **End of life**: move to a supported version of the framework.
- **False positive** (the image does not ship the vulnerable code): ask a member of the Aikido \
workspace to ignore it there, with the reason. It is left out from the next run.

The JSON result and the scanner output are in the `aikido-image-scan-*` artifacts of the run.
"""


def read(path: str) -> str:
    try:
        with open(path, encoding="utf-8") as report:
            text = report.read().strip()
    except FileNotFoundError:
        return "(no report: the scan did not finish)"
    if len(text) > MAX_REPORT:
        text = text[:MAX_REPORT] + "\n\n…"
    return text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--sha", required=True, help="commit the check is posted on")
    parser.add_argument("--mode", required=True, choices=("release", "pr"), help="gating mode of the scans")
    parser.add_argument("--threshold", required=True, help="lowest severity reported")
    parser.add_argument(
        "--image",
        nargs=3,
        action="append",
        required=True,
        metavar=("LABEL", "ISSUES", "REPORT"),
        help="an image label, its number of issues, and its Markdown report",
    )
    args = parser.parse_args()

    images = [(label, int(issues or 0), report) for label, issues, report in args.image]
    kind = "open" if args.mode == "release" else "new"
    # The PR checks list shows about 100 characters of "name — title": the counts come first.
    title = " · ".join(
        f"❌ {label} {issues} {kind}" if issues else f"✅ {label}" for label, issues, _ in images
    ) + f" ({args.threshold}+)"
    summary = "\n".join(
        [
            intro(args.mode, args.threshold),
            "## Result\n",
            *(read(report) + "\n" for _, _, report in images),
            FIX,
            "This check is advisory for now: it does not block the merge.",
        ]
    )
    payload = {
        "name": NAME,
        "head_sha": args.sha,
        "status": "completed",
        "conclusion": "failure" if any(issues for _, issues, _ in images) else "success",
        "output": {"title": title, "summary": summary},
    }
    subprocess.run(
        ["gh", "api", f"repos/{os.environ['GITHUB_REPOSITORY']}/check-runs", "--input", "-"],
        input=json.dumps(payload).encode(),
        check=True,
        stdout=subprocess.DEVNULL,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
