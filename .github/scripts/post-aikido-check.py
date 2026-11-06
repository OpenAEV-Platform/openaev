#!/usr/bin/env python3
"""Post a check run that explains the fixable vulnerabilities found in the CI images.

The aikido-image-scan job scans the images the pipeline builds and stays green on findings. When a
finding at or above the threshold has a fix, this check run says so: a short title with the count
per image for the PR checks list, and in its details the findings of each image (package,
installed and fixed versions, file, description), how to fix them, and why it does not block. It
is posted with the PR checks app, like the PR title check, so it shows as its own check on the
pull request or the commit.

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


def intro(threshold: str) -> str:
    return f"""\
The Docker images built by this pipeline are scanned with Aikido's local scanner. A vulnerability \
rated `{threshold}` or above that already has a fixed version should be fixed before it ships. \
Vulnerabilities with no fix are listed too, but never fail this check.
"""


FIX = """\
### How to fix it

- **Operating system package** (file under `/var/lib/dpkg` or `/var/lib/rpm`): the fix usually \
comes with a newer base image. Bump the `app` stage image in `Dockerfile` or `Dockerfile_ubi9`, \
or upgrade the package in that stage.
- **Java library** (file in a `.jar`): bump the dependency, or the BOM that brings it, in the \
`pom.xml` to a fixed version.

The "Fixed in" column lists the fixed versions Aikido knows for every distribution, not only the \
one of the image: check that one of them applies before bumping. A false positive, such as a \
server CVE matched against a client library, goes in \
`.github/actions/aikido-image-scan/ignored-findings.txt` with its reason. The JSON findings and \
the scanner output are in the `aikido-image-scan-*` artifacts of the run.
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
    parser.add_argument("--threshold", required=True, help="lowest severity reported")
    parser.add_argument(
        "--image",
        nargs=3,
        action="append",
        required=True,
        metavar=("LABEL", "FIXABLE", "REPORT"),
        help="an image label, its number of fixable findings, and its Markdown report",
    )
    args = parser.parse_args()

    images = [(label, int(fixable or 0), report) for label, fixable, report in args.image]
    # The PR checks list shows about 100 characters of "name — title": the counts come first.
    title = " · ".join(
        f"{'❌' if fixable else '✅'} {label} {fixable} fixable" for label, fixable, _ in images
    ) + f" ({args.threshold}+)"
    summary = "\n".join(
        [
            intro(args.threshold),
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
        "conclusion": "failure" if any(fixable for _, fixable, _ in images) else "success",
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
