#!/usr/bin/env python3
"""Check that the built MkDocs site is complete, not only that the build succeeded.

`mkdocs build --strict` fails on errors and warnings, but a plugin that breaks silently still
produces a green build (selectolax 1.0.1 made mkdocs-glightbox drop every image, #8602). So,
for the site built in <site-dir> from docs/docs:

- every Markdown page has its HTML page,
- every local image a page references is an <img> of that page's content,
- the search index has an entry for every page that is not excluded from search.

Usage: check-docs-site.py [site-dir]   (default: docs/site)
"""

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

import yaml

REPO = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO / "docs" / "docs"

FENCE_RE = re.compile(r"^\s*(`{3,}|~{3,})")
COMMENT_RE = re.compile(r"<!--.*?-->", re.S)
INLINE_CODE_RE = re.compile(r"`[^`]*`")
FRONT_MATTER_RE = re.compile(r"\A---\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\Z)", re.S)
MD_IMAGE_RE = re.compile(r"!\[[^\]]*\]\(\s*(?:<([^>]+)>|([^)\s]+))")
HTML_IMAGE_RE = re.compile(r"""<img\b[^>]*\bsrc=["']([^"']+)["']""", re.I)
ARTICLE_RE = re.compile(r"<article\b.*</article>", re.S)


def url_of(page: Path) -> str:
    """Location of a page in the built site, as the search index writes it."""
    relative = page.relative_to(DOCS_ROOT).with_suffix("")
    if relative.name in ("index", "README"):
        relative = relative.parent
    return "" if relative == Path(".") else f"{relative.as_posix()}/"


def search_excluded(text: str) -> bool:
    """True when the page front matter sets `search: {exclude: true}`."""
    front_matter = FRONT_MATTER_RE.match(text)
    if not front_matter:
        return False
    try:
        meta = yaml.safe_load(front_matter.group(1))
    except yaml.YAMLError:
        return False  # MkDocs reports a malformed front matter itself
    search = meta.get("search") if isinstance(meta, dict) else None
    return isinstance(search, dict) and search.get("exclude") is True


def image_name(src: str) -> str | None:
    """File name of a local image source, or None for a remote, data or templated one."""
    parts = urlsplit(src)
    if parts.scheme or src.startswith(("//", "data:", "{{")):
        return None
    return Path(unquote(parts.path)).name


def local_image_names(text: str) -> set[str]:
    """File names of the local images a Markdown page references, outside code and comments."""
    names, fence = set(), None
    for line in COMMENT_RE.sub("", text).splitlines():
        opener = FENCE_RE.match(line)
        if fence is None and opener:
            fence = opener.group(1)
            continue
        if fence is not None:
            # A fence is closed by a line of the same character, at least as long as the opener.
            if opener and opener.group(1)[0] == fence[0] and len(opener.group(1)) >= len(fence):
                fence = None
            continue
        line = INLINE_CODE_RE.sub("", line)
        for src in [a or b for a, b in MD_IMAGE_RE.findall(line)] + HTML_IMAGE_RE.findall(line):
            name = image_name(src)
            if name:
                names.add(name)
    return names


def main() -> int:
    site = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "docs" / "site"
    if not (site / "index.html").is_file():
        print(f"::error::{site} is not a built MkDocs site: run `mkdocs build` in docs/ first.")
        return 2

    pages = sorted(DOCS_ROOT.rglob("*.md"))
    errors, images, searchable = [], 0, set()
    for page in pages:
        rel = page.relative_to(REPO).as_posix()
        text = page.read_text(encoding="utf-8")
        if not search_excluded(text):
            searchable.add(url_of(page))

        output = site / url_of(page) / "index.html"
        if not output.is_file():
            errors.append((rel, "no HTML page in the built site"))
            continue
        html = output.read_text(encoding="utf-8")
        # Only the page content: the theme header and footer have images of their own.
        content = ARTICLE_RE.search(html)
        built = {image_name(s) for s in HTML_IMAGE_RE.findall(content.group(0) if content else html)}
        for name in sorted(local_image_names(text)):
            images += 1
            if name not in built:
                errors.append((rel, f"image {name} is not an <img> of the built page"))

    index = site / "search" / "search_index.json"
    if not index.is_file():
        errors.append(("docs/mkdocs.yml", "no search index in the built site"))
    else:
        indexed = {doc["location"].split("#")[0] for doc in json.loads(index.read_text(encoding="utf-8"))["docs"]}
        for url in sorted(searchable - indexed):
            errors.append(("docs/mkdocs.yml", f"page /{url} is missing from the search index"))

    for rel, error in errors:
        print(f"::error file={rel}::{error}")
    print(f"Checked {len(pages)} page(s) and {images} image reference(s), {len(errors)} problem(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
