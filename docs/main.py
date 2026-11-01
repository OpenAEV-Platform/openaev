"""Macros loaded by mkdocs-macros for every page of the documentation.

`feature_visible(name)` hides the documentation of a preview feature while its constant exists in the backend
`PreviewFeature` enum. Once the backend removes the constant, the content shows again and the strict build of the
pull request fails until the `{% if feature_visible(...) %}` block is removed from the pages.
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


def define_env(env):
    preview_features = set(
        re.findall(r"^\s*([A-Z][A-Z0-9_]*)\(", PREVIEW_FEATURE_ENUM.read_text(encoding="utf-8"), re.MULTILINE)
    )
    # Comma-separated preview features to show in a local build, "*" for all of them
    enabled_features = {
        feature.strip().upper() for feature in os.environ.get("DOCS_FEATURE_FLAGS", "").split(",") if feature.strip()
    }

    @env.macro
    def feature_visible(name):
        if name not in preview_features:
            log.warning(
                "%s is no longer a preview feature in PreviewFeature.java: remove its feature_visible() block from %s",
                name,
                env.page.file.src_uri,
            )
            return True
        return name in enabled_features or "*" in enabled_features
