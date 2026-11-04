#!/usr/bin/env python3
"""Fail when code or configuration writes a docs.openaev.io URL instead of a key of docs/links.json.

The backend and the front open the documentation by key: docs/links.json maps each key to a page
and anchor, and the docs build checks every entry against the site (docs/hooks/checks.py). A raw
URL in the code escapes that check and breaks silently when the docs move. This script lists the
files git tracks and reports every docs.openaev.io URL outside the places allowed to have one:

- docs/**: the documentation itself and links.json,
- *.md: READMEs, changelogs and other prose,
- .github/**: CI, templates and agent instructions,
- comment lines of .properties files (`#` or `!` first): application.properties ships with the
  release, and its comments give operators a URL they can open. Property values stay forbidden,
  continuation lines of a value included,
- DocsLinks.java: the backend constants, generated from links.json by the docs-links goal of
  openaev-maven-plugin, which also checks the URLs of the application.properties comments,
- the code that reads the map and its tests: the docs goals of openaev-maven-plugin and the test
  of the front docsUrl helper, which need the base URL and expected URLs.

Run it from anywhere in the repository: python3 .github/scripts/check-docs-urls.py
"""

from __future__ import annotations

import re
import subprocess
import sys
from collections.abc import Iterable
from pathlib import Path, PurePosixPath

URL_RE = re.compile(r"docs\.openaev\.io", re.I)
ALLOWED_DIRS = ("docs/", ".github/")
ALLOWED_SUFFIXES = {".md"}
# Generated from docs/links.json, so its URLs are the map's own.
ALLOWED_FILES = {
    "openaev-api/src/main/java/io/openaev/docs/DocsLinks.java",
    "openaev-front/src/__tests__/utils/docsUrl.test.ts",
}
# The code that reads the map and checks its URLs, and its tests.
ALLOWED_PREFIXES = (
    "openaev-maven-plugin/src/main/java/io/openaev/docs/",
    "openaev-maven-plugin/src/test/java/io/openaev/docs/",
)
# Files that cannot hold a URL a reader would follow. SVG is text, and can hold links.
BINARY_SUFFIXES = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp", ".pdf", ".zip", ".gz", ".jar",
    ".woff", ".woff2", ".ttf", ".eot", ".otf", ".mp4", ".webm",
}
HINT = "add a key to docs/links.json and use it (DocsLinks constants in the backend, the links map in the front)"


def file_allowed(path: str) -> bool:
    """True for a file that may hold docs.openaev.io URLs anywhere, or that cannot hold one."""
    suffix = PurePosixPath(path).suffix.lower()
    return (
        path in ALLOWED_FILES
        or path.startswith(ALLOWED_DIRS)
        or path.startswith(ALLOWED_PREFIXES)
        or suffix in ALLOWED_SUFFIXES
        or suffix in BINARY_SUFFIXES
    )


def continues(line: str) -> bool:
    """True when a .properties line goes on to the next one: it ends with an odd number of backslashes."""
    return (len(line) - len(line.rstrip("\\"))) % 2 == 1


def properties_comments(lines: list[str]) -> set[int]:
    """Indexes of the comment lines of a .properties file. A line that continues a value is part of
    the value, even when it starts with `#` or `!`."""
    comments, in_value = set(), False
    for index, line in enumerate(lines):
        if not in_value and line.lstrip().startswith(("#", "!")):
            comments.add(index)  # a comment never continues
        else:
            in_value = continues(line)
    return comments


def findings(path: str, text: str) -> list[tuple[int, str]]:
    """(line number, line) of every forbidden docs.openaev.io URL of a file."""
    if file_allowed(path) or not URL_RE.search(text):
        return []
    lines = text.splitlines()
    allowed = properties_comments(lines) if PurePosixPath(path).suffix.lower() == ".properties" else set()
    return [
        (index + 1, line.strip())
        for index, line in enumerate(lines)
        if URL_RE.search(line) and index not in allowed
    ]


def escape_data(text: str) -> str:
    """Escape the message of a GitHub workflow command."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text: str) -> str:
    """Escape a property of a GitHub workflow command, such as file=."""
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def check(root: Path, paths: Iterable[str]) -> list[tuple[str, int, str]]:
    result = []
    for path in paths:
        file = root / path
        if file_allowed(path) or not file.is_file():
            continue  # allowed, or deleted in the working tree, or a submodule
        text = file.read_text(encoding="utf-8", errors="replace")
        result += [(path, number, line) for number, line in findings(path, text)]
    return result


def main(cwd: Path | None = None) -> int:
    root = Path(
        subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=cwd, check=True, capture_output=True,
                       text=True).stdout.strip()
    )
    tracked = subprocess.run(["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True, text=True).stdout
    problems = check(root, filter(None, tracked.split("\0")))
    for path, number, line in problems:
        message = escape_data(f"{path}:{number}: docs.openaev.io URL: {line[:160]}")
        print(f"::error file={escape_property(path)},line={number}::{message}")
    if problems:
        print(f"{len(problems)} docs.openaev.io URL(s) outside docs/, .github/, Markdown files and .properties comments: {HINT}.")
        return 1
    print("No docs.openaev.io URL outside docs/, .github/, Markdown files and .properties comments.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
