"""MkDocs hook: check the site every `mkdocs build` and `mkdocs serve` produces.

`mkdocs build --strict` fails on warnings, and MkDocs' own `validation` (see mkdocs.yml) already
checks the Markdown links between pages: target file, anchor, no root or unrecognized links. A
plugin that breaks silently still produces a green build, though (selectolax 1.0.1 made
mkdocs-glightbox drop every image, #8602), and MkDocs never looks at links written in raw HTML,
at absolute links to the docs site, or at the map of docs links the application uses. So this
hook checks:

- page URIs: no underscore in the path of a page (`on_files`),
- every page MkDocs builds has its HTML file, and the search index lists every page not excluded
  from search (`on_post_build`),
- every local image the Markdown of a page renders is still an `<img>` of the final page, after
  every plugin ran (`on_page_content` first, `on_post_page` last),
- the links of a page MkDocs does not validate: absolute links to docs.openaev.io in Markdown, and
  every link written in raw HTML. Each is resolved as a browser does: dot segments included, it
  must stay under /latest/, redirects of mkdocs-redirects are followed, and its anchor must be an
  id of the target page. A link through a redirect warns too: it should go to the page itself. A
  link to a fixed version (docs.openaev.io/<version>/...) fails on purpose: the docs links must
  follow the latest docs,
- the images written in raw HTML, which MkDocs does not validate either, are files of the site,
- no redirect of mkdocs-redirects starts from a page of the site, which it would overwrite,
- `links.json`, the map of the docs links the backend and the front use: its shape, its keys and
  values (the same rules as the Java generator of the backend), and that every link goes straight
  to a page of the site, not through a redirect, and to an anchor of that page (`on_post_build`).

Every problem is a warning of the `mkdocs.hooks.checks` logger: `--strict` fails the build on it,
and `mkdocs serve` shows it. A `--dirty` build renders only the changed pages, so the checks that
need the whole site are skipped there.
"""

from __future__ import annotations

import json
import logging
import re
from collections import Counter
from dataclasses import dataclass, field
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urljoin, urlsplit

from markdown import Extension
from markdown.treeprocessors import Treeprocessor
from mkdocs.plugins import event_priority
from mkdocs.structure.files import File

log = logging.getLogger("mkdocs.hooks.checks")

DOCS_HOST = "docs.openaev.io"
# The site is deployed by mike under a version: a page of the build is served at /latest/<url>.
VERSION_ROOT = f"https://{DOCS_HOST}/latest/"
LINKS_FILE = "links.json"
# The same rules as DocsLinksGenerator of openaev-maven-plugin: snake_case keys, and values relative
# to the base, made of URI characters (RFC 3986 unreserved, sub-delims, ":@/?#" and %XX escapes),
# whose path, before any #anchor, is empty or ends with '/'.
LINK_KEY_RE = re.compile(r"[a-z][a-z0-9]*(_[a-z0-9]+)*")
LINK_VALUE_RE = re.compile(r"(?:[A-Za-z0-9\-._~!$&'()*+,;=:@/?#]|%[0-9A-Fa-f]{2})*")
# The first segment of a path to a version deployed by mike: 3.2.0, 2.260717.0...
VERSION_SEGMENT_RE = re.compile(r"v?\d+(\.\d+)*")


def validate_key(key: object) -> str | None:
    """Error message for a key of links.json, or None when it is valid."""
    if not isinstance(key, str) or not LINK_KEY_RE.fullmatch(key):
        return f"must be snake_case, matching {LINK_KEY_RE.pattern}"
    return None


def validate_value(value: object) -> str | None:
    """Error message for a value of links.json, or None when it is valid."""
    if not isinstance(value, str):
        return "must map to a string"
    if not LINK_VALUE_RE.fullmatch(value):
        return f"{json.dumps(value)} must only hold URI characters: no space or control character, % only in %XX escapes"
    parts = urlsplit(value)
    if parts.scheme or parts.netloc or value.startswith("/"):
        return f"{value} must be a path relative to the base"
    path = value.split("#", 1)[0]
    if path and not path.endswith("/"):
        return f"{value} must be the URL of a page, ending with '/' (a.md is a/)"
    return None


def stays_on_site(href: str) -> bool:
    """True for a relative or root link, or one to the docs site; false for another site or scheme."""
    parts = urlsplit(href)
    return parts.hostname == DOCS_HOST or not (parts.scheme or parts.netloc)


def site_path(url: str) -> str:
    """Decoded path of a URL without its dot segments, as a browser requests it: /latest/../a is /a."""
    path, segments = unquote(urlsplit(url).path), []
    for segment in path.split("/")[1:]:
        if segment == "..":
            segments = segments[:-1]
        elif segment != ".":
            segments.append(segment)
    if path.endswith(("/.", "/..")):
        segments.append("")  # `a/b/..` is the folder a/: keep its trailing slash
    return "/" + "/".join(segments)


def version_path(url: str) -> str | None:
    """Path of a docs site URL relative to /latest/, or None when it is outside /latest/."""
    path = site_path(url)
    if path in ("/latest", "/latest/"):
        return ""
    return path.removeprefix("/latest/") if path.startswith("/latest/") else None


def outside_message(url: str) -> str:
    segment = site_path(url).split("/")[1]
    if VERSION_SEGMENT_RE.fullmatch(segment):
        return (f"links to the fixed version {segment}: link the latest docs, {VERSION_ROOT}..., which "
                "follow the docs as they move")
    return f"is outside {VERSION_ROOT}"


def local_image(page_url: str, src: str) -> str | None:
    """Path of a local image in the site, resolved against its page, or None for a remote or data one."""
    if not src or src.startswith("{{") or not stays_on_site(src) or urlsplit(src).hostname:
        return None
    return unquote(urlsplit(urljoin(VERSION_ROOT + page_url, src)).path)


@dataclass
class PageInfo:
    src_uri: str
    searchable: bool = True
    ids: set[str] = field(default_factory=set)
    # Links of the content: from Markdown, which MkDocs validates, and the others (raw HTML).
    markdown_hrefs: list[str] = field(default_factory=list)
    other_hrefs: list[str] = field(default_factory=list)


@dataclass
class Site:
    """The built site, as URLs relative to /latest/: pages, redirects and static files."""

    pages: dict[str, PageInfo] = field(default_factory=dict)
    # URL of a redirect page -> absolute URL it redirects to, #anchor included.
    redirects: dict[str, str] = field(default_factory=dict)
    static_files: set[str] = field(default_factory=set)

    def target(self, path: str) -> str | None:
        """URL of the page, redirect or file a path under /latest/ is served from, or None."""
        page_path = path.removesuffix("index.html")
        candidates = [page_path]
        if page_path and not page_path.endswith("/"):
            candidates.append(f"{page_path}/")  # GitHub Pages redirects a folder without its slash
        for candidate in candidates:
            if candidate in self.pages or candidate in self.redirects:
                return candidate
        return path if path in self.static_files else None

    def follow(self, url: str) -> tuple[str | None, str | None]:
        """Follow an absolute docs site URL: (error message or None, where it lands when it went
        through a redirect of the site, as a path under /latest/ with its #anchor, or None)."""
        path = version_path(url)
        if path is None:
            return outside_message(url), None
        fragment = unquote(urlsplit(url).fragment)
        seen: set[str] = set()
        while True:
            target = self.target(path)
            if target is None:
                return f"no page /latest/{path} in the site", None
            if target not in self.redirects:
                break
            if target in seen:
                return f"redirect loop on /latest/{target}", None
            seen.add(target)
            redirect = self.redirects[target]
            if not stays_on_site(redirect):
                return None, None  # a redirect to another site: there is nothing to check
            redirect_fragment = unquote(urlsplit(redirect).fragment)
            if fragment and redirect_fragment:
                # The redirect page appends the incoming fragment to its target: `target#a#b` resolves nowhere.
                return f"/latest/{target} redirects to #{redirect_fragment}, a link cannot add #{fragment}", None
            fragment = fragment or redirect_fragment
            path = version_path(redirect)
            if path is None:
                return f"/latest/{target} redirects out of {VERSION_ROOT}", None
        page = self.pages.get(target)
        if fragment:
            if page is None:
                return f"/latest/{target} is a file, it has no anchor #{fragment}", None
            if fragment not in page.ids:
                return f"no anchor #{fragment} on /latest/{target} ({page.src_uri})", None
        landed = target + (f"#{fragment}" if fragment else "")
        return None, (landed if seen else None)

    def resolve(self, url: str) -> str | None:
        """Check an absolute docs site URL: an error message, or None when it resolves, redirects included."""
        return self.follow(url)[0]

    def link_errors(self, page_url: str) -> list[tuple[str, str]]:
        """(href, error) for each link of a page that MkDocs does not validate and that is broken."""
        info = self.pages[page_url]
        # MkDocs validates every Markdown link but the absolute ones.
        hrefs = [h for h in info.markdown_hrefs if urlsplit(h).hostname] + info.other_hrefs
        errors = []
        for href in hrefs:
            if not stays_on_site(href):
                continue
            url = urljoin(VERSION_ROOT + page_url, href)
            if site_path(url) == "/":
                continue  # the docs root, which mike redirects to the latest version
            error, redirected = self.follow(url)
            if error:
                errors.append((href, error))
            elif redirected is not None:
                errors.append((href, f"is a redirect to /latest/{redirected}: link it directly"))
        return errors


class ContentParser(HTMLParser):
    """Collect the ids, link targets and image sources of an HTML fragment, or of its <article> only."""

    def __init__(self, article_only: bool) -> None:
        super().__init__(convert_charrefs=True)
        self.article_only = article_only
        self.depth = 0
        self.found = False
        self.ids: set[str] = set()
        self.hrefs: list[str] = []
        self.images: list[str] = []

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag == "article":
            self.depth += 1
            self.found = True
        if self.article_only and not self.depth:
            return
        values = {name: value for name, value in attrs if value}
        if "id" in values:
            self.ids.add(values["id"])
        if tag == "a" and "name" in values:
            self.ids.add(values["name"])
        if tag == "a" and "href" in values:
            self.hrefs.append(values["href"])
        if tag == "img" and "src" in values:
            self.images.append(values["src"])

    def handle_endtag(self, tag: str) -> None:
        if tag == "article" and self.depth:
            self.depth -= 1


class _MarkdownLinks(Treeprocessor):
    """Record the link targets and image sources of the Markdown tree, after MkDocs rewrote them
    (its relpath step, priority 0).

    Raw HTML is not part of the tree: the links and images left out are the ones MkDocs never
    validates.
    """

    def __init__(self, md, hrefs: list[str], images: list[str]) -> None:
        super().__init__(md)
        self.hrefs, self.images = hrefs, images

    def run(self, root):
        self.hrefs.extend(el.get("href") for el in root.iter("a") if el.get("href"))
        self.images.extend(el.get("src") for el in root.iter("img") if el.get("src"))


class MarkdownLinksExtension(Extension):
    def __init__(self) -> None:
        super().__init__()
        self.hrefs: list[str] = []
        self.images: list[str] = []

    def extendMarkdown(self, md) -> None:
        md.treeprocessors.register(_MarkdownLinks(md, self.hrefs, self.images), "openaev_markdown_links", -10)

    def take(self) -> tuple[list[str], list[str]]:
        taken = (list(self.hrefs), list(self.images))
        self.hrefs.clear()
        self.images.clear()
        return taken


def split_by_origin(found: list[str], from_markdown: list[str]) -> tuple[list[str], list[str]]:
    """Split what a rendered page holds into what the Markdown tree made and the rest (raw HTML)."""
    remaining = Counter(from_markdown)
    markdown, others = [], []
    for item in found:
        if remaining[item] > 0:
            remaining[item] -= 1
            markdown.append(item)
        else:
            others.append(item)
    return markdown, others


# State of the current build. MkDocs loads a hook module once per process, so `mkdocs serve`
# keeps it across rebuilds: on_pre_build resets what belongs to one build.
_dirty = False
_files = None
_site = Site()
_markdown_links = MarkdownLinksExtension()
_markdown_images: dict[str, set[str]] = {}
_page_hrefs: dict[str, tuple[list[str], list[str]]] = {}


def on_startup(command: str, dirty: bool) -> None:
    global _dirty
    _dirty = dirty


def on_config(config):
    # Pages are rendered one after the other, each by a new Markdown instance built from this list.
    config.markdown_extensions.append(_markdown_links)
    return config


def on_serve(server, config, builder):
    server.watch(str(Path(config.config_file_path).parent / LINKS_FILE))
    return server


def on_pre_build(config) -> None:
    global _site
    _site = Site()
    _markdown_images.clear()
    _page_hrefs.clear()
    _markdown_links.take()


@event_priority(-100)
def on_files(files, config):
    """Page URIs use hyphens: `scenarios-and-simulations/`, never `scenarios_and_simulations/`.

    Also records the files and redirects of the site: pages render, on_page_content included,
    before on_env.
    """
    global _files
    _files = files
    for file in files.documentation_pages():
        if "_" in file.src_uri:
            log.warning("Page %s has an underscore in its path: use '-' instead of '_' in page URIs.", file.src_uri)
    _site.static_files = {unquote(f.url) for f in files if not f.is_documentation_page()}
    # The redirect pages mkdocs-redirects writes, as URLs: the same mapping its on_post_build applies.
    redirects = config.plugins.get("redirects")
    maps = redirects.config.get("redirect_maps", {}) if redirects is not None else {}
    page_urls = {f.url: f.src_uri for f in files.documentation_pages()}
    for old, new in maps.items():
        old_url = File(old, "", "", config.use_directory_urls).url
        if old_url in page_urls:
            log.warning("Redirect from %s: %s is also a page of the site, which the redirect page overwrites.",
                        old, page_urls[old_url])
        target, _, fragment = str(new).partition("#")
        if target.lower().startswith(("http://", "https://")):
            _site.redirects[old_url] = str(new)
        elif (file := files.get_file_from_path(target)) is not None:
            _site.redirects[old_url] = VERSION_ROOT + file.url + (f"#{fragment}" if fragment else "")
        # mkdocs-redirects warns itself about a target that does not exist
    return files


@event_priority(100)
def on_page_content(html, page, config, files):
    """Record what the Markdown of the page renders, before any plugin rewrites it.

    The local images, to find them in the final page. The links, split into the ones of the
    Markdown tree, which MkDocs validates, and the others, written in raw HTML. The images written
    in raw HTML, which MkDocs does not validate either, must be files of the site.
    """
    parser = ContentParser(article_only=False)
    parser.feed(html)
    _markdown_images[page.url] = {p for src in parser.images if (p := local_image(page.url, src))}
    markdown_hrefs, markdown_images = _markdown_links.take()
    _page_hrefs[page.url] = split_by_origin(parser.hrefs, markdown_hrefs)
    for src in split_by_origin(parser.images, markdown_images)[1]:
        path = local_image(page.url, src)
        if path is not None and path.removeprefix("/latest/") not in _site.static_files:
            log.warning("Page %s: image %s written in HTML is not a file of the site.", page.file.src_uri, src)
    return html


@event_priority(-100)
def on_post_page(output, page, config):
    parser = ContentParser(article_only=True)
    parser.feed(output)
    search = page.meta.get("search")
    excluded = isinstance(search, dict) and search.get("exclude") is True
    markdown_hrefs, other_hrefs = _page_hrefs.get(page.url, ([], []))
    _site.pages[page.url] = PageInfo(page.file.src_uri, not excluded, parser.ids, markdown_hrefs, other_hrefs)
    if not parser.found:
        log.warning("Page %s has no <article> in its HTML: the theme changed, update hooks/checks.py.", page.file.src_uri)
        return output
    # Images of the theme are outside the <article>, only the content counts.
    built = {p for src in parser.images if (p := local_image(page.url, src))}
    for missing in sorted(_markdown_images.get(page.url, set()) - built):
        log.warning("Page %s: image %s is not an <img> of the built page.", page.file.src_uri, missing)
    return output


def _check_site(config) -> None:
    site = Path(config.site_dir)
    expected = [f for f in _files.documentation_pages() if f.page is not None]
    for file in expected:
        if not (site / file.dest_uri).is_file():
            log.warning("Page %s has no HTML file %s in the built site.", file.src_uri, file.dest_uri)

    index = site / "search" / "search_index.json"
    if not index.is_file():
        log.warning("The built site has no search index search/search_index.json.")
        return
    indexed = {doc["location"].split("#")[0] for doc in json.loads(index.read_text(encoding="utf-8"))["docs"]}
    for file in expected:
        info = _site.pages.get(file.page.url)
        if info is not None and info.searchable and file.page.url not in indexed:
            log.warning("Page %s is missing from the search index.", file.src_uri)


def load_links(text: str) -> tuple[dict | None, list[str]]:
    """Parse links.json: (links, errors). links is None when the file cannot be used at all."""
    errors: list[str] = []

    def no_duplicates(pairs):
        keys = [key for key, _ in pairs]
        for key in sorted({k for k in keys if keys.count(k) > 1}):
            errors.append(f"key '{key}' is defined more than once")
        return dict(pairs)

    try:
        data = json.loads(text, object_pairs_hook=no_duplicates)
    except ValueError as error:
        return None, [f"cannot be read as JSON: {error}"]
    if not isinstance(data, dict) or set(data) != {"base", "links"}:
        return None, errors + ['must be an object with exactly two members, "base" and "links"']
    if data["base"] != VERSION_ROOT:
        return None, errors + [f'"base" must be "{VERSION_ROOT}", not {json.dumps(data["base"])}']
    if not isinstance(data["links"], dict):
        return None, errors + ['"links" must be an object of key to path']
    return data["links"], errors


def links_errors(site: Site, links: dict) -> list[str]:
    """Error messages, naming the key, for the entries of links.json that are invalid or do not resolve."""
    errors = []
    for key, value in links.items():
        if error := validate_key(key):
            errors.append(f"key '{key}' {error}")
        if error := validate_value(value):
            errors.append(f"key '{key}': {error}")
            continue
        error, redirected = site.follow(urljoin(VERSION_ROOT, value))
        if error:
            errors.append(f"key '{key}': {value}: {error}")
        elif redirected is not None:
            errors.append(f"key '{key}': {value} is a redirect to {redirected}: use {redirected}")
    return errors


@event_priority(-100)
def on_post_build(config) -> None:
    if _dirty or _files is None:
        log.info("Dirty build: the site, link and %s checks are skipped.", LINKS_FILE)
        return
    _check_site(config)
    for page_url, info in _site.pages.items():
        for href, error in _site.link_errors(page_url):
            log.warning("Page %s: link %s: %s.", info.src_uri, href, error)

    path = Path(config.config_file_path).parent / LINKS_FILE
    try:
        links, errors = load_links(path.read_text(encoding="utf-8"))
    except OSError as error:
        links, errors = None, [f"cannot be read: {error}"]
    if links is not None:
        errors += links_errors(_site, links)
    for error in errors:
        log.warning("%s: %s.", LINKS_FILE, error)
    log.info("Checked %d page(s) and %d %s link(s).", len(_site.pages), len(links or {}), LINKS_FILE)
