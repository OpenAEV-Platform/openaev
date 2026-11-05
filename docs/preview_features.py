"""MkDocs hook that removes the pages of hidden preview features from the navigation.

A page with `preview_feature: NAME` in its front matter is left out of the `nav` while `NAME` is hidden, following the
same rule as the `feature_visible()` macro (see `main.py`). The page stays reachable by its URL.
"""

from pathlib import Path

from mkdocs.utils.meta import get_data

# MkDocs adds the folder of the hook to sys.path while it loads it
from main import is_visible


def _page_visible(docs_dir, path):
    page = Path(docs_dir) / path
    if not page.is_file():
        return True
    _, meta = get_data(page.read_text(encoding="utf-8"))
    feature = meta.get("preview_feature")
    return feature is None or is_visible(feature, path)


def _filter_nav(docs_dir, items):
    kept = []
    for item in items:
        title, value = next(iter(item.items())) if isinstance(item, dict) else (None, item)
        if isinstance(value, list):
            value = _filter_nav(docs_dir, value)
            if not value:
                continue
        elif not _page_visible(docs_dir, value):
            continue
        kept.append({title: value} if title is not None else value)
    return kept


def on_config(config):
    if config.nav:
        config.nav = _filter_nav(config.docs_dir, config.nav)
    return config
