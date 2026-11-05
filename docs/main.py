"""Macros loaded by mkdocs-macros for every page of the documentation.

A preview feature is a constant of the backend `PreviewFeature` enum. Its documentation stays hidden while the constant
exists, and shows once the backend removes it:

- `{% if feature_visible("NAME") %}` hides content inside a page.
- `preview_feature: NAME` in the front matter of a page removes the page from the navigation (`preview_features.py`
  hook, which imports `is_visible()` from this module).

Once the backend removes the constant, both warn, so the strict build of the pull request fails until the
`feature_visible()` block and the `preview_feature` front matter are removed.
"""

import logging
import os
import re
from pathlib import Path

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


def define_env(env):
    @env.macro
    def feature_visible(name):
        return is_visible(name, env.page.file.src_uri)
