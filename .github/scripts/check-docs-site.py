#!/usr/bin/env python3
"""Check that the built MkDocs site is complete, not only that the build succeeded.

`mkdocs build --strict` fails on errors and warnings, but a plugin that breaks silently still
produces a green build (selectolax 1.0.1 made mkdocs-glightbox drop every image, #8602). So,
for the site built in <site-dir> from docs/docs:

- every Markdown page has its HTML page,
- every local image a page references is an <img> of that built page,
- the search index lists every page.

Usage: check-docs-site.py [site-dir]   (default: docs/site)
"""

import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote, urlsplit

REPO = Path(__file__).resolve().parents[2]
DOCS_ROOT = REPO / "docs" / "docs"

FENCE_RE = re.compile(r"^\s*(```|~~~)")
MD_IMAGE_RE = re.compile(r"!\[[^\]]*\]\(\s*<?([^)\s>]+)")
HTML_IMAGE_RE = re.compile(r"""<img\b[^>]*\bsrc=["']([^"']+)["']""", re.I)


def page_output(site: Path, page: Path) -> Path:
    relative = page.relative_to(DOCS_ROOT).with_suffix("")
    if relative.name == "index":
        return site / relative.parent / "index.html"
    return site / relative / "index.html"


def local_image_names(page: Path) -> set[str]:
    """File names of the local images a Markdown page references, outside code fences."""
    names, in_fence = set(), False
    for line in page.read_text(encoding="utf-8").splitlines():
        if FENCE_RE.match(line):
            in_fence = not in_fence
        if in_fence:
            continue
        for src in MD_IMAGE_RE.findall(line) + HTML_IMAGE_RE.findall(line):
            parts = urlsplit(src)
            if not parts.scheme and not src.startswith(("//", "data:", "{{")):
                names.add(Path(unquote(parts.path)).name)
    return names


def main() -> int:
    site = Path(sys.argv[1]) if len(sys.argv) > 1 else REPO / "docs" / "site"
    if not (site / "index.html").is_file():
        print(f"::error::{site} is not a built MkDocs site: run `mkdocs build` in docs/ first.")
        return 2

    pages = sorted(DOCS_ROOT.rglob("*.md"))
    errors, images = [], 0
    for page in pages:
        rel = page.relative_to(REPO).as_posix()
        output = page_output(site, page)
        if not output.is_file():
            errors.append((rel, "no HTML page in the built site"))
            continue
        built = {Path(unquote(s)).name for s in HTML_IMAGE_RE.findall(output.read_text(encoding="utf-8"))}
        for name in sorted(local_image_names(page)):
            images += 1
            if name not in built:
                errors.append((rel, f"image {name} is not an <img> of the built page"))

    index = site / "search" / "search_index.json"
    if index.is_file():
        indexed = {doc["location"].split("#")[0] for doc in json.loads(index.read_text(encoding="utf-8"))["docs"]}
        if len(indexed) < len(pages):
            errors.append(("docs/mkdocs.yml", f"search index lists {len(indexed)} pages for {len(pages)} Markdown pages"))

    for rel, error in errors:
        print(f"::error file={rel}::{error}")
    print(f"Checked {len(pages)} page(s) and {images} image reference(s), {len(errors)} problem(s).")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
