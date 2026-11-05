"""Macros loaded by mkdocs-macros for every page of the documentation.

`feature_visible(name)` hides the documentation of a preview feature while its constant exists in the backend
`PreviewFeature` enum (see `preview_features.py`).
"""

from preview_features import is_visible


def define_env(env):
    @env.macro
    def feature_visible(name):
        return is_visible(name, env.page.file.src_uri)
