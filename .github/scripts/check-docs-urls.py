#!/usr/bin/env python3
"""Fail when code or configuration links to the docs without a key of docs/links.json.

The backend and the front open the documentation by key: docs/links.json maps each key to a page
and anchor, and the docs build checks every entry against the site (docs/hooks/checks.py). A raw
URL in the code escapes that check and breaks silently when the docs move, and so does a path or an
anchor glued onto a key. This script lists the files git tracks and reports, outside the places
allowed to have them:

- docs URLs, case-insensitively: `docs.openaev.io` (and any `docs.openaev`), the regex form
  `docs\\.openaev`, `openaev.io/latest`, and `docs` followed by an escaped dot (`\\x2e`, `\\u002e`,
  `\\u{2e}`, `&#46;`, `&#x2e;`, `%2e`),
- concatenation onto a docs link: `DocsLinks.NAME +`, `+ DocsLinks.NAME`, `docsUrl(...) +` and
  `${docsUrl(` in a template literal,
- an import of links.json anywhere but openaev-front/src/utils/docsUrl.ts, the one front module
  that reads the map.

Allowed places:

- the documentation sources: docs/docs/**, docs/overrides/**, docs/hooks/**, docs/tests/**,
  docs/*.py, docs/*.md, docs/links.json, docs/mkdocs.yml and docs/requirements.*,
- .github/**: CI, templates and agent instructions,
- Markdown files outside a source tree (no `src` folder in their path): READMEs, changelogs,
- comment lines (`#` or `!` first) of openaev-api/src/main/resources/application.properties: it
  ships with the release, its comments give operators a URL they can open, and DocsLinksTest
  checks them against links.json. Its property values stay forbidden, continuation lines of a value
  included, and so do the comments of every other .properties file,
- DocsLinks.java, the hand-written constants of the map in the backend, and DocsLinksTest, which
  keeps them equal to links.json.

With --base-ref, it also checks docs/links.json against the one of that commit: a value removed
from `links` must be listed in `retired`, since released versions still open it and the docs build
keeps every retired path reachable.

Run it from anywhere in the repository: python3 .github/scripts/check-docs-urls.py [--base-ref REF]
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from collections.abc import Iterable
from pathlib import Path, PurePosixPath

URL = "docs URL"
CONCATENATION = "concatenation onto a docs link"
IMPORT = "import of links.json"

_ESCAPED_DOT = r"(?:\\x2e|\\u002e|\\u\{0*2e\}|&#0*46;|&#x0*2e;|%2e)"
PATTERNS = (
    (URL, re.compile(rf"docs(?:\.|\\\.|{_ESCAPED_DOT})", re.I), "openaev"),
    (URL, re.compile(r"openaev(?:\.|\\\.)io/latest", re.I), None),
    (URL, re.compile(rf"docs{_ESCAPED_DOT}", re.I), None),
    (CONCATENATION, re.compile(r"DocsLinks\.\w+\s*\+"), None),
    (CONCATENATION, re.compile(r"\+\s*DocsLinks\.\w+"), None),
    (CONCATENATION, re.compile(r"docsUrl\([^)]*\)\s*\+"), None),
    (CONCATENATION, re.compile(r"\$\{\s*docsUrl\("), None),
    (IMPORT, re.compile(r"""(?:\bfrom|\brequire\s*\(|\bimport\s*\(?)\s*['"`][^'"`\n]*links\.json(?:\?[^'"`\n]*)?['"`]"""), None),
)
LINKS_FILE = "docs/links.json"
# The one module of the front that imports the map; every other one goes through its docsUrl().
LINKS_IMPORTER = "openaev-front/src/utils/docsUrl.ts"
ALLOWED_PREFIXES = ("docs/docs/", "docs/overrides/", "docs/hooks/", "docs/tests/", ".github/")
ALLOWED_DOCS_FILES = re.compile(r"docs/(?:[^/]+\.(?:py|md)|links\.json|mkdocs\.yml|requirements\.[^/]+)")
# The constants of docs/links.json in the backend, and the test that keeps them equal to the map.
ALLOWED_FILES = {
    "openaev-api/src/main/java/io/openaev/docs/DocsLinks.java",
    "openaev-api/src/test/java/io/openaev/docs/DocsLinksTest.java",
}
# The one .properties file whose comments may hold URLs: they are checked by DocsLinksTest.
PROPERTIES_WITH_URL_COMMENTS = "openaev-api/src/main/resources/application.properties"
# Files that cannot hold a URL a reader would follow. SVG is text, and can hold links.
BINARY_SUFFIXES = {
    ".png", ".jpg", ".jpeg", ".gif", ".ico", ".webp", ".pdf", ".zip", ".gz", ".jar",
    ".woff", ".woff2", ".ttf", ".eot", ".otf", ".mp4", ".webm",
}
HINT = (
    "add a key to docs/links.json and use it as it is (DocsLinks constants in the backend, docsUrl() in the front),"
    " with no path or anchor glued onto it"
)


def file_allowed(path: str) -> bool:
    """True for a file that may hold docs URLs anywhere, or that cannot hold one."""
    pure = PurePosixPath(path)
    suffix = pure.suffix.lower()
    return (
        path in ALLOWED_FILES
        or path.startswith(ALLOWED_PREFIXES)
        or ALLOWED_DOCS_FILES.fullmatch(path) is not None
        or (suffix == ".md" and "src" not in pure.parts[:-1])
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


def findings(path: str, text: str) -> list[tuple[int, str, str]]:
    """(line number, reason, line) of every forbidden docs link of a file, one per line and reason."""
    if file_allowed(path):
        return []
    lines = text.splitlines()
    url_comments = properties_comments(lines) if path == PROPERTIES_WITH_URL_COMMENTS else set()
    starts = [0]
    for line in text.splitlines(keepends=True):
        starts.append(starts[-1] + len(line))
    found: dict[tuple[int, str], None] = {}
    for reason, pattern, followed_by in PATTERNS:
        if reason == IMPORT and path == LINKS_IMPORTER:
            continue
        for match in pattern.finditer(text):
            if followed_by and not text[match.end():match.end() + len(followed_by)].lower() == followed_by:
                continue
            index = _line_index(starts, match.start())
            if reason == URL and index in url_comments:
                continue
            found[(index, reason)] = None
    return [(index + 1, reason, lines[index].strip()) for index, reason in sorted(found)]


def _line_index(starts: list[int], offset: int) -> int:
    """Index of the line holding a character offset, given the offset each line starts at."""
    low, high = 0, len(starts) - 1
    while low < high - 1:
        middle = (low + high) // 2
        if starts[middle] <= offset:
            low = middle
        else:
            high = middle
    return low


def removed_not_retired(base: dict, head: dict) -> list[str]:
    """Values of the base's `links` that the head's `links` no longer has and its `retired` does not list."""
    kept = set(head.get("links", {}).values())
    retired = set(head.get("retired", []))
    return sorted({value for value in base.get("links", {}).values() if value not in kept and value not in retired})


def escape_data(text: str) -> str:
    """Escape the message of a GitHub workflow command."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text: str) -> str:
    """Escape a property of a GitHub workflow command, such as file=."""
    return escape_data(text).replace(":", "%3A").replace(",", "%2C")


def check(root: Path, paths: Iterable[str]) -> list[tuple[str, int, str, str]]:
    result = []
    for path in paths:
        file = root / path
        if file_allowed(path) or not file.is_file():
            continue  # allowed, or deleted in the working tree, or a submodule
        text = file.read_text(encoding="utf-8", errors="replace")
        result += [(path, number, reason, line) for number, reason, line in findings(path, text)]
    return result


def git(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)


def check_retired(root: Path, base_ref: str) -> int:
    """Check that every link removed since base_ref is retired. 0 when fine or nothing to compare, 1 on
    problems, 2 when base_ref is not a commit."""
    if git(root, "rev-parse", "--verify", "--quiet", f"{base_ref}^{{commit}}").returncode != 0:
        print(f"::error::--base-ref {escape_data(base_ref)} is not a commit of this clone")
        return 2
    base_file = git(root, "show", f"{base_ref}:{LINKS_FILE}")
    if base_file.returncode != 0:
        print(f"{LINKS_FILE} does not exist on {base_ref}: no removed link to check.")
        return 0
    head_file = root / LINKS_FILE
    head = json.loads(head_file.read_text(encoding="utf-8")) if head_file.is_file() else {}
    missing = removed_not_retired(json.loads(base_file.stdout), head)
    for value in missing:
        message = escape_data(
            f"'{value}' was removed from links since {base_ref}: add it to retired, released versions still open it"
        )
        print(f"::error file={escape_property(LINKS_FILE)}::{message}")
    if missing:
        print(f"{len(missing)} link(s) removed from {LINKS_FILE} without a retired entry.")
        return 1
    print(f"Every link removed from {LINKS_FILE} since {base_ref} is retired.")
    return 0


def main(argv: list[str] | None = None, cwd: Path | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument(
        "--base-ref",
        help="commit to compare docs/links.json with: every value removed from links must be retired",
    )
    args = parser.parse_args(argv)
    root = Path(
        subprocess.run(["git", "rev-parse", "--show-toplevel"], cwd=cwd, check=True, capture_output=True,
                       text=True).stdout.strip()
    )
    tracked = subprocess.run(["git", "ls-files", "-z"], cwd=root, check=True, capture_output=True, text=True).stdout
    problems = check(root, filter(None, tracked.split("\0")))
    for path, number, reason, line in problems:
        message = escape_data(f"{path}:{number}: {reason}: {line[:160]}")
        print(f"::error file={escape_property(path)},line={number}::{message}")
    status = 0
    if problems:
        print(f"{len(problems)} docs link(s) written outside docs/links.json: {HINT}.")
        status = 1
    else:
        print("No docs URL, concatenation onto a docs link or import of links.json outside the allowed places.")
    if args.base_ref:
        status = max(status, check_retired(root, args.base_ref))
    return status


if __name__ == "__main__":
    sys.exit(main())
