"""MkDocs hook: check the site every `properdocs build` and `properdocs serve` produces.

`build --strict` fails on warnings, and the `validation` of mkdocs.yml already checks the Markdown
links between pages: target file, anchor, no root or unrecognized links. A plugin that breaks
silently still produces a green build, though (selectolax 1.0.1 made mkdocs-glightbox drop every
image, #8602), and MkDocs never looks at links written in raw HTML, at the redirects of
mkdocs-redirects, or at the map of docs links the application uses. So this hook checks:

- page URIs: no underscore in the path of a page (`on_files`),
- every page the engine builds has its HTML file, and the search index lists every page not
  excluded from search (`on_post_build`),
- every local image the Markdown of a page renders is still an `<img>` of the final page, after
  every plugin ran (`on_page_content` first, `on_post_page` last),
- the relative links written in raw HTML (`<a href>`): each must lead to a page or file of the
  site, to an id of that page when it has an #anchor, and not through a redirect (link the page
  itself). A link from the server root (`/...`) warns: mike serves the site under a version.
  Templated links (`{{ ... }}`) are skipped,
- the images written in raw HTML (`<img src>`) are files of the site,
- no link or image of a page uses an absolute docs.openaev.io URL: a relative link is checked by
  MkDocs, an absolute one by nothing,
- every redirect of mkdocs-redirects: it starts from no page of the site, which it would
  overwrite, and its target is a page of the built site (a page hidden by a preview feature is
  not) and, with an #anchor, an id of that page,
- `links.json`, the map of the docs links the backend and the front use: its shape, its keys and
  values, and that every link goes straight to a page of the site, not through a redirect, and
  to an anchor of that page. Its `retired` paths, the ones released versions of the application
  open, must still lead to a page and anchor of this site, redirects allowed (`on_post_build`).

Not checked: `<video>`, `<source>`, `<iframe>` and `srcset`, and links to other sites.

Every problem is a warning of the `mkdocs.hooks.checks` logger: `--strict` fails the build on it,
and `serve` shows it. A `--dirty` build renders only the changed pages, so the checks that need
the whole site are skipped there.
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
# The base of the paths of links.json: mike serves the latest build of the site there.
LINKS_BASE = f"https://{DOCS_HOST}/latest/"
LINKS_FILE = "links.json"
# Keys are snake_case, and values are relative to the base, made of URI characters (RFC 3986
# unreserved, sub-delims, ":@/?#" and %XX escapes), whose path, before any #anchor, is empty or
# ends with '/'.
LINK_KEY_RE = re.compile(r"[a-z][a-z0-9]*(_[a-z0-9]+)*")
LINK_VALUE_RE = re.compile(r"(?:[A-Za-z0-9\-._~!$&'()*+,;=:@/?#]|%[0-9A-Fa-f]{2})*")
# Relative links are resolved against the URL of their page under this placeholder origin.
_ORIGIN = "https://site.invalid/"
ABSOLUTE_DOCS_MESSAGE = f"is an absolute {DOCS_HOST} URL: link it relatively (MkDocs checks relative links)"


def validate_key(key: object) -> str | None:
    """Error message for a key of links.json, or None when it is valid."""
    if not isinstance(key, str) or not LINK_KEY_RE.fullmatch(key):
        return f"must be snake_case, matching {LINK_KEY_RE.pattern}"
    return None


def validate_path(value: object) -> str | None:
    """Error message for a path of links.json relative to the base, or None when it is one."""
    if not isinstance(value, str):
        return "must be a string"
    if not LINK_VALUE_RE.fullmatch(value):
        return f"{json.dumps(value)} must only hold URI characters: no space or control character, % only in %XX escapes"
    parts = urlsplit(value)
    if parts.scheme or parts.netloc or value.startswith("/"):
        return f"{value} must be a path relative to the base"
    return None


def validate_value(value: object) -> str | None:
    """Error message for a value of the links of links.json, or None when it is valid."""
    if error := validate_path(value):
        return error
    path = value.split("#", 1)[0]
    if path and not path.endswith("/"):
        return f"{value} must be the URL of a page, ending with '/' (a.md is a/)"
    return None


def is_templated(url: str) -> bool:
    return "{{" in url or "%7B%7B" in url.upper()


def kind(url: str) -> str:
    """'docs' for an absolute URL of the docs site, 'other' for another site or scheme, else 'local'."""
    parts = urlsplit(url)
    if parts.scheme or parts.netloc:
        return "docs" if parts.hostname == DOCS_HOST else "other"
    return "local"


def resolve(page_url: str, href: str) -> tuple[str, str]:
    """(path from the site root, #anchor), both decoded, of a local link of the page at page_url."""
    parts = urlsplit(urljoin(_ORIGIN + page_url, href))
    return unquote(parts.path).removeprefix("/"), unquote(parts.fragment)


def local_image(page_url: str, src: str) -> str | None:
    """Path of a local image from the site root, or None for a remote, data or templated one."""
    if not src or is_templated(src) or kind(src) != "local":
        return None
    return resolve(page_url, src)[0]


@dataclass
class PageInfo:
    src_uri: str
    url: str = ""  # as the engine writes it, percent-encoded
    searchable: bool = True
    ids: set[str] = field(default_factory=set)
    # Links of the content: from Markdown, which MkDocs validates, and the others (raw HTML).
    markdown_hrefs: list[str] = field(default_factory=list)
    other_hrefs: list[str] = field(default_factory=list)


@dataclass
class Redirect:
    """A redirect of mkdocs-redirects: to the page `page` (decoded URL) and its `fragment`, or to
    another site (`external`). `page` is None when the target page is not built."""

    source: str
    target: str
    page: str | None = None
    fragment: str = ""
    external: bool = False


@dataclass
class Site:
    """The built site, as decoded URLs relative to its root: pages, redirects and static files."""

    pages: dict[str, PageInfo] = field(default_factory=dict)
    redirects: dict[str, Redirect] = field(default_factory=dict)
    static_files: set[str] = field(default_factory=set)

    def target(self, path: str) -> str | None:
        """URL of the page, redirect or file a path is served from, or None."""
        if path == "index.html":
            page_path = ""
        elif path.endswith("/index.html"):
            page_path = path.removesuffix("index.html")
        else:
            page_path = path
        candidates = [page_path]
        if page_path and not page_path.endswith("/"):
            candidates.append(f"{page_path}/")  # GitHub Pages redirects a folder without its slash
        for candidate in candidates:
            if candidate in self.pages or candidate in self.redirects:
                return candidate
        return path if path in self.static_files else None

    def follow(self, path: str, fragment: str = "") -> tuple[str | None, str | None]:
        """Follow a decoded path and anchor: (error message or None, where it lands when it went
        through a redirect, as a path with its #anchor, or None)."""
        target = self.target(path)
        if target is None:
            return f"no page {path or '(home)'} in the site", None
        redirected = target in self.redirects
        if redirected:
            redirect = self.redirects[target]
            if redirect.external:
                return f"{target} redirects to another site, {redirect.target}", None
            if redirect.page is None:
                return f"{target} redirects to {redirect.target}, which is not a page of the site", None
            if fragment and redirect.fragment:
                # The redirect page appends the incoming anchor to its target: `target#a#b` resolves nowhere.
                return f"{target} redirects to #{redirect.fragment}, a link cannot add #{fragment}", None
            fragment = fragment or redirect.fragment
            target = redirect.page
        page = self.pages.get(target)
        if fragment:
            if page is None:
                return f"{target} is a file, it has no anchor #{fragment}", None
            if fragment not in page.ids:
                return f"no anchor #{fragment} on {target or '(home)'} ({page.src_uri})", None
        if not redirected:
            return None, None
        return None, target + (f"#{fragment}" if fragment else "")

    def link_errors(self, page_key: str) -> list[tuple[str, str]]:
        """(href, error) for each link of a page that MkDocs does not validate and that is broken."""
        info = self.pages[page_key]
        errors = [(href, ABSOLUTE_DOCS_MESSAGE) for href in info.markdown_hrefs if kind(href) == "docs"]
        for href in info.other_hrefs:
            if is_templated(href) or kind(href) == "other":
                continue
            if kind(href) == "docs":
                errors.append((href, ABSOLUTE_DOCS_MESSAGE))
                continue
            if href.startswith("/"):
                errors.append((href, "starts at the root of the server, outside the version mike serves: link it "
                                     "relatively"))
                continue
            error, redirected = self.follow(*resolve(info.url, href))
            if error:
                errors.append((href, error))
            elif redirected is not None:
                errors.append((href, f"is a redirect to {redirected}: link it directly"))
        return errors

    def redirect_errors(self) -> list[str]:
        """Errors of the redirects whose target page is built: an anchor that is not an id of it."""
        errors = []
        for redirect in self.redirects.values():
            page = self.pages.get(redirect.page) if redirect.page is not None else None
            if page is not None and redirect.fragment and redirect.fragment not in page.ids:
                errors.append(f"Redirect from {redirect.source} to {redirect.target}: no anchor "
                              f"#{redirect.fragment} on {page.src_uri}")
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


# State of the current build. MkDocs loads a hook module once per process, so `serve` keeps it
# across rebuilds: on_pre_build resets what belongs to one build.
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
    before on_env. Runs after preview_features.py removed the hidden pages.
    """
    global _files
    _files = files
    for file in files.documentation_pages():
        if "_" in file.src_uri:
            log.warning("Page %s has an underscore in its path: use '-' instead of '_' in page URIs.", file.src_uri)
    _site.static_files = {unquote(f.url) for f in files if not f.is_documentation_page()}
    # The redirect pages mkdocs-redirects writes, as URLs: the same mapping its on_post_build applies.
    plugin = config.plugins.get("redirects")
    maps = plugin.config.get("redirect_maps", {}) if plugin is not None else {}
    page_urls = {unquote(f.url): f.src_uri for f in files.documentation_pages()}
    for old, new in maps.items():
        old_url = unquote(File(old, "", "", config.use_directory_urls).url)
        if old_url in page_urls:
            log.warning("Redirect from %s: %s is also a page of the site, which the redirect page overwrites.",
                        old, page_urls[old_url])
        target, _, fragment = str(new).partition("#")
        redirect = Redirect(old, str(new), fragment=unquote(fragment))
        if target.lower().startswith(("http://", "https://")):
            redirect.external = True
        elif (file := files.get_file_from_path(target)) is not None and file.is_documentation_page():
            redirect.page = unquote(file.url)
        else:
            log.warning("Redirect from %s: %s is not a page of the built site (missing, or hidden by a preview "
                        "feature), so the redirect leads nowhere.", old, target)
        _site.redirects[old_url] = redirect
    return files


@event_priority(100)
def on_page_content(html, page, config, files):
    """Record what the Markdown of the page renders, before any plugin rewrites it.

    The local images, to find them in the final page. The links, split into the ones of the
    Markdown tree, which MkDocs validates, and the others, written in raw HTML. The images written
    in raw HTML, which MkDocs does not validate either, must be files of the site. No image uses
    an absolute URL of the docs site.
    """
    parser = ContentParser(article_only=False)
    parser.feed(html)
    _markdown_images[page.url] = {p for src in parser.images if (p := local_image(page.url, src)) is not None}
    markdown_hrefs, markdown_images = _markdown_links.take()
    _page_hrefs[page.url] = split_by_origin(parser.hrefs, markdown_hrefs)
    for src in parser.images:
        if kind(src) == "docs":
            log.warning("Page %s: image %s %s.", page.file.src_uri, src, ABSOLUTE_DOCS_MESSAGE)
    for src in split_by_origin(parser.images, markdown_images)[1]:
        path = local_image(page.url, src)
        if path is not None and path not in _site.static_files:
            log.warning("Page %s: image %s written in HTML is not a file of the site.", page.file.src_uri, src)
    return html


@event_priority(-100)
def on_post_page(output, page, config):
    parser = ContentParser(article_only=True)
    parser.feed(output)
    search = page.meta.get("search")
    excluded = isinstance(search, dict) and search.get("exclude") is True
    markdown_hrefs, other_hrefs = _page_hrefs.get(page.url, ([], []))
    _site.pages[unquote(page.url)] = PageInfo(page.file.src_uri, page.url, not excluded, parser.ids,
                                              markdown_hrefs, other_hrefs)
    if not parser.found:
        log.warning("Page %s has no <article> in its HTML: the theme changed, update hooks/checks.py.", page.file.src_uri)
        return output
    # Images of the theme are outside the <article>, only the content counts.
    built = {p for src in parser.images if (p := local_image(page.url, src)) is not None}
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
    docs = json.loads(index.read_text(encoding="utf-8"))["docs"]
    indexed = {unquote(doc["location"].split("#")[0]) for doc in docs}
    for file in expected:
        url = unquote(file.page.url)
        info = _site.pages.get(url)
        if info is not None and info.searchable and url not in indexed:
            log.warning("Page %s is missing from the search index.", file.src_uri)


def load_links(text: str) -> tuple[dict | None, list[str]]:
    """Parse links.json: ({"links": ..., "retired": [...]}, errors). The first is None when the file
    cannot be used at all."""
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
    if not isinstance(data, dict) or not {"base", "links"} <= set(data) <= {"base", "links", "retired"}:
        return None, errors + ['must be an object with the members "base", "links" and, optionally, "retired"']
    if data["base"] != LINKS_BASE:
        return None, errors + [f'"base" must be "{LINKS_BASE}", not {json.dumps(data["base"])}']
    if not isinstance(data["links"], dict):
        return None, errors + ['"links" must be an object of key to path']
    retired = data.get("retired", [])
    if not isinstance(retired, list):
        return None, errors + ['"retired" must be a list of paths']
    for path in sorted({p for p in retired if isinstance(p, str) and retired.count(p) > 1}):
        errors.append(f"retired path '{path}' is listed more than once")
    return {"links": data["links"], "retired": retired}, errors


def follow_path(site: Site, value: str) -> tuple[str | None, str | None]:
    """Follow a path of links.json, relative to the root of the site."""
    parts = urlsplit(value)
    return site.follow(unquote(parts.path), unquote(parts.fragment))


def links_errors(site: Site, links: dict) -> list[str]:
    """Error messages, naming the key, for the entries of links.json that are invalid or do not resolve."""
    errors = []
    for key, value in links.items():
        if error := validate_key(key):
            errors.append(f"key '{key}' {error}")
        if error := validate_value(value):
            errors.append(f"key '{key}': {error}")
            continue
        error, redirected = follow_path(site, value)
        if error:
            errors.append(f"key '{key}': {value}: {error}")
        elif redirected is not None:
            errors.append(f"key '{key}': {value} is a redirect to {redirected}: use {redirected}")
    return errors


def retired_errors(site: Site, retired: list) -> list[str]:
    """Error messages, naming the path, for the retired paths that are invalid or do not resolve.

    Released versions of the application keep opening them, so they must lead to a page of this
    site, through a redirect or not: unlike a key, a retired path may go through a redirect.
    """
    errors = []
    for path in retired:
        if error := validate_path(path):
            errors.append(f"retired path {json.dumps(path)}: {error}")
        elif error := follow_path(site, path)[0]:
            errors.append(f"retired path '{path}': {error}")
    return errors


@event_priority(-100)
def on_post_build(config) -> None:
    if _dirty or _files is None:
        log.info("Dirty build: the site, link and %s checks are skipped.", LINKS_FILE)
        return
    _check_site(config)
    for error in _site.redirect_errors():
        log.warning("%s.", error)
    for page_key, info in _site.pages.items():
        for href, error in _site.link_errors(page_key):
            log.warning("Page %s: link %s %s.", info.src_uri, href,
                        error if error.startswith("is ") else f"leads nowhere: {error}")

    path = Path(config.config_file_path).parent / LINKS_FILE
    try:
        document, errors = load_links(path.read_text(encoding="utf-8"))
    except OSError as error:
        document, errors = None, [f"cannot be read: {error}"]
    if document is not None:
        errors += links_errors(_site, document["links"])
        errors += retired_errors(_site, document["retired"])
    for error in errors:
        log.warning("%s: %s.", LINKS_FILE, error)
    document = document or {"links": {}, "retired": []}
    log.info("Checked %d page(s), %d redirect(s), %d %s link(s) and %d retired path(s).", len(_site.pages),
             len(_site.redirects), len(document["links"]), LINKS_FILE, len(document["retired"]))
