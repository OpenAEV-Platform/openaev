"""Preview features of the documentation, shared by the `main.py` macros and the MkDocs hook below.

A preview feature is a constant of the backend `PreviewFeature` enum. Its documentation stays hidden while the constant
exists, and shows once the backend removes it:

- `{% if feature_visible("NAME") %}` (macro in `main.py`) hides content inside a page.
- `preview_feature: NAME` in the front matter of a page removes the page from the navigation (`on_config` hook below).

Once the backend removes the constant, both warn, so the strict build of the pull request fails until the
`feature_visible()` block and the `preview_feature` front matter are removed.
"""

import logging
import os
import re
from pathlib import Path

from mkdocs.utils.meta import get_data

log = logging.getLogger("mkdocs.plugins.preview_features")

PREVIEW_FEATURE_ENUM = (
    Path(__file__).resolve().parent.parent
    / "openaev-api/src/main/java/io/openaev/rest/settings/PreviewFeature.java"
)

PREVIEW_FEATURES = set(
    re.findall(r"^\s*([A-Z][A-Z0-9_]*)\(", PREVIEW_FEATURE_ENUM.read_text(encoding="utf-8"), re.MULTILINE)
)
# Comma-separated preview features to show in a local build, "*" for all of them
ENABLED_FEATURES = {
    feature.strip().upper() for feature in os.environ.get("DOCS_FEATURE_FLAGS", "").split(",") if feature.strip()
}


def is_visible(name, source):
    """Tells whether the documentation of the preview feature `name`, used in the page `source`, is shown."""
    if name not in PREVIEW_FEATURES:
        log.warning(
            "%s is no longer a preview feature in PreviewFeature.java: remove its feature_visible() block and its "
            "preview_feature front matter from %s",
            name,
            source,
        )
        return True
    return name in ENABLED_FEATURES or "*" in ENABLED_FEATURES


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
