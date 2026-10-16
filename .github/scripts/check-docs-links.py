#!/usr/bin/env python3
"""Verify every docs.openaev.io link in the application code resolves in docs/.

Each https://docs.openaev.io/latest/<path>[#anchor] link found in the API, model
and front sources must point to a Markdown page under docs/docs (directly or
through a mkdocs-redirects entry), and its anchor must match a heading or an
explicit id on that page. Runs offline against the checkout, so a docs move that
breaks an in-app link fails here instead of on the published site.
"""

import re
import subprocess
import sys
import unicodedata
from pathlib import Path
from urllib.parse import urlsplit

REPO = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO / "docs" / "docs"
MKDOCS_CONFIG = REPO / "docs" / "mkdocs.yml"
SOURCE_DIRS = ["openaev-api/src", "openaev-model/src", "openaev-front/src"]
SOURCE_SUFFIXES = {".java", ".ts", ".tsx", ".js", ".jsx"}

URL_RE = re.compile(r"https://docs\.openaev\.io[^\s\"'`<>)\\]*")
REDIRECT_RE = re.compile(r"^\s+'([^']+\.md)':\s*'([^']+\.md)'\s*$")
FENCE_RE = re.compile(r"^\s*(```|~~~)")
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")
EXPLICIT_ID_RE = re.compile(r"\{[^}]*#([\w-]+)[^}]*\}\s*$")
HTML_ID_RE = re.compile(r"<[^>]+\b(?:id|name)=[\"']([^\"']+)[\"']")


def load_redirects() -> dict[str, str]:
    redirects = {}
    for line in MKDOCS_CONFIG.read_text(encoding="utf-8").splitlines():
        match = REDIRECT_RE.match(line)
        if match:
            redirects[match.group(1)] = match.group(2)
    return redirects


def slugify(text: str) -> str:
    # Same rules as Python-Markdown's toc extension, which MkDocs uses for heading ids.
    text = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode("ascii")
    text = re.sub(r"[^\w\s-]", "", text).strip().lower()
    return re.sub(r"[-\s]+", "-", text)


def heading_text(raw: str) -> str:
    text = re.sub(r"\{[^}]*\}\s*$", "", raw)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    text = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text)
    text = re.sub(r"\*+|(?<!\w)_+|_+(?!\w)|`", "", text)
    return text.strip()


def page_anchors(page: Path) -> set[str]:
    anchors, in_fence = set(), False
    for line in page.read_text(encoding="utf-8").splitlines():
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        anchors.update(HTML_ID_RE.findall(line))
        match = HEADING_RE.match(line)
        if not match:
            continue
        explicit = EXPLICIT_ID_RE.search(match.group(2))
        slug = explicit.group(1) if explicit else slugify(heading_text(match.group(2)))
        # Repeated headings get _1, _2... suffixes, as in Python-Markdown.
        unique, n = slug, 0
        while unique in anchors:
            n += 1
            unique = f"{slug}_{n}"
        anchors.add(unique)
    return anchors


def resolve_page(path: str, redirects: dict[str, str]) -> Path | None:
    for candidate in (f"{path}.md", f"{path}/index.md"):
        if (DOCS_ROOT / candidate).is_file():
            return DOCS_ROOT / candidate
        if candidate in redirects:
            target = DOCS_ROOT / redirects[candidate]
            return target if target.is_file() else None
    return None


def check_url(url: str, redirects: dict[str, str]) -> str | None:
    parts = urlsplit(url)
    segments = [s for s in parts.path.split("/") if s]
    if not segments:
        return None
    if segments[0] != "latest":
        return "link must start with /latest/"
    path = "/".join(segments[1:])
    page = DOCS_ROOT / "index.md" if not path else resolve_page(path, redirects)
    if page is None:
        return f"no page docs/docs/{path}.md or redirect for it in docs/mkdocs.yml"
    if parts.fragment and parts.fragment not in page_anchors(page):
        return f"no anchor #{parts.fragment} in {page.relative_to(REPO).as_posix()}"
    return None


def source_files() -> list[Path]:
    listed = subprocess.run(
        ["git", "ls-files", "--", *SOURCE_DIRS],
        cwd=REPO, check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    return [REPO / f for f in listed if Path(f).suffix in SOURCE_SUFFIXES]


def main() -> int:
    redirects = load_redirects()
    checked, errors = 0, []
    for file in source_files():
        rel = file.relative_to(REPO).as_posix()
        for line_no, line in enumerate(file.read_text(encoding="utf-8").splitlines(), 1):
            for url in URL_RE.findall(line):
                checked += 1
                error = check_url(url, redirects)
                if error:
                    errors.append((rel, line_no, url, error))

    for rel, line_no, url, error in errors:
        print(f"::error file={rel},line={line_no}::{url}: {error}")
    print(f"Checked {checked} docs link(s), {len(errors)} broken.")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
