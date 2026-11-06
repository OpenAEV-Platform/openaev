"""Build the docs strictly and check them: pages, images, links between pages and links.json.

The checks themselves run in hooks/checks.py and in the `validation` of mkdocs.yml, so plain
`mkdocs build` and `mkdocs serve` run them too. This script is the single check the backend build,
the front `yarn check-docs-links` and the CI run, through run.py, which sets up the Python
environment first:

    python3 docs/run.py check [--force]

It builds with the engine mike deploys with (toolchain.engine_name), with `--strict`, into a
temporary directory of its own, so that several runs at the same time do not clash, and without
the git plugins unless DOCS_GIT_PLUGINS is set. It skips the build when none of its inputs changed
since the last successful check, whose fingerprint it keeps in .cache/check/. --force builds anyway.

Exit codes, which the Maven goal of the backend maps to its messages:
  0  the docs are fine, or nothing changed since the last successful check
  1  the docs have problems: the warnings and errors the build printed above
  2  bad command line arguments
  3  the check itself failed: missing toolchain, crash of check.py or of a plugin
"""

from __future__ import annotations

import argparse
import hashlib
import importlib
import logging
import os
import re
import shutil
import sys
import tempfile
import time
import traceback
from collections.abc import Callable, Mapping
from pathlib import Path
from types import SimpleNamespace

from toolchain import engine_name, write_atomically

DOCS = Path(__file__).resolve().parent

# Everything under docs/ is an input of the check, except these folders: environments, caches,
# build output, and the tests, which do not change what the build produces.
EXCLUDED_DIRS = {".venv", ".cache", "site", "tests", "__pycache__", ".git", ".dev_venv", "venv", ".venv-build",
                 ".idea", ".vscode", ".mypy_cache"}
# `!ENV NAME` and `!ENV [NAME, OTHER, default]`: every name of the list, the default included,
# which costs nothing when no variable has that name.
ENV_TAG_RE = re.compile(r"!ENV\s+(\[[^\]]*\]|[A-Za-z_]\w*)")
NAME_RE = re.compile(r"[A-Za-z_]\w*")
# What the build reads besides docs/ and the !ENV of mkdocs.yml: the preview features of main.py
# come from the backend enum and from DOCS_FEATURE_FLAGS.
OUTSIDE_INPUTS = ("openaev-api/src/main/java/io/openaev/rest/settings/PreviewFeature.java",)
OTHER_ENV = ("DOCS_FEATURE_FLAGS",)

OK, DOCS_PROBLEMS, CHECK_FAILED = 0, 1, 3


class DocsProblems(Exception):
    """The build reported problems in the docs: the build already printed them."""


def input_files(docs: Path) -> list[Path]:
    files = []
    for root, dirs, names in os.walk(docs):
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS)
        files += (Path(root) / name for name in names)
    return sorted(files)


def env_names(mkdocs_yml: str) -> list[str]:
    """The environment variables mkdocs.yml reads through !ENV."""
    names = set()
    for value in ENV_TAG_RE.findall(mkdocs_yml):
        names.update(NAME_RE.findall(value))
    return sorted(names)


def fingerprint(docs: Path, env: Mapping[str, str]) -> str:
    digest = hashlib.sha256()
    digest.update(f"{sys.version}\0{sys.prefix}".encode())
    config = docs / "mkdocs.yml"
    names = env_names(config.read_text(encoding="utf-8") if config.is_file() else "")
    for name in sorted(set(names) | set(OTHER_ENV)):
        digest.update(f"\0env:{name}={env.get(name)}".encode())
    for path in input_files(docs):
        digest.update(f"\0file:{path.relative_to(docs).as_posix()}\0".encode())
        digest.update(path.read_bytes())
    for relative in OUTSIDE_INPUTS:
        path = docs.parent / relative
        digest.update(f"\0outside:{relative}\0".encode())
        digest.update(path.read_bytes() if path.is_file() else b"<missing>")
    return digest.hexdigest()


def load_engine() -> SimpleNamespace:
    """The modules of the build engine. ProperDocs redirects the `mkdocs.*` imports of plugins to itself."""
    name = engine_name()
    if name == "properdocs":
        importlib.import_module("properdocs.replacement")
    exceptions = importlib.import_module(f"{name}.exceptions")
    return SimpleNamespace(
        name=name,
        build=importlib.import_module(f"{name}.commands.build").build,
        load_config=importlib.import_module(f"{name}.config").load_config,
        Abort=exceptions.Abort,
        ConfigurationError=exceptions.ConfigurationError,
    )


def mkdocs_build(docs: Path, site_dir: Path, engine: SimpleNamespace | None = None) -> None:
    """Build strictly, as `<engine> build --strict` does. Raise DocsProblems when the docs have problems."""
    engine = engine or load_engine()
    # The output of the engine's command line: its `--quiet` would drop the warnings `--strict` counts.
    logger = logging.getLogger(engine.name)
    logger.setLevel(logging.INFO)
    logger.propagate = False
    if engine.name != "mkdocs":
        # As the ProperDocs command line does: plugins and hooks log under `mkdocs.`.
        legacy = logging.getLogger("mkdocs")
        legacy.parent, legacy.propagate = logger, True
        legacy.setLevel(logging.NOTSET)
    handler = logging.StreamHandler()
    handler.setFormatter(logging.Formatter("%(levelname)-8s-  %(message)s"))
    logger.addHandler(handler)
    try:
        try:
            config = engine.load_config(str(docs / "mkdocs.yml"), strict=True, site_dir=str(site_dir))
        except (engine.Abort, engine.ConfigurationError) as error:
            # The engine printed what is wrong in mkdocs.yml, a file of the docs.
            logger.error("%s", error)
            raise DocsProblems() from error
        config.plugins.on_startup(command="build", dirty=False)
        try:
            engine.build(config)
        except engine.Abort as error:
            logger.error("%s", error)
            raise DocsProblems() from error
        finally:
            config.plugins.on_shutdown()
    finally:
        logger.removeHandler(handler)


def run(docs: Path, force: bool, builder: Callable[[Path, Path], None] = mkdocs_build,
        fingerprint_file: Path | None = None) -> int:
    fingerprint_file = fingerprint_file or docs / ".cache" / "check" / "fingerprint"
    current = fingerprint(docs, os.environ)
    if not force and fingerprint_file.is_file() and fingerprint_file.read_text(encoding="utf-8").strip() == current:
        print("Docs check: nothing changed since the last successful check, skipped (--force to run it).")
        return OK

    start = time.monotonic()
    site_dir = Path(tempfile.mkdtemp(prefix="openaev-docs-check-"))
    try:
        builder(docs, site_dir)
    except DocsProblems:
        print("Docs check failed: fix the problems above (docs/README.md explains the checks).", file=sys.stderr)
        return DOCS_PROBLEMS
    finally:
        shutil.rmtree(site_dir, ignore_errors=True)
    write_atomically(fingerprint_file, current + "\n")
    print(f"Docs check passed in {time.monotonic() - start:.1f}s: pages, images, links and links.json are fine.")
    return OK


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--force", action="store_true", help="build even when nothing changed since the last check")
    args = parser.parse_args(argv)

    # Pull requests and local checks skip the git plugins: git-committers calls the GitHub API for
    # every page. Nightly sets DOCS_GIT_PLUGINS=true to build as the deployed site does.
    os.environ.setdefault("DOCS_GIT_PLUGINS", "false")
    # Material and ProperDocs print a banner about MkDocs 2.0 on every build.
    os.environ.setdefault("NO_MKDOCS_2_WARNING", "true")
    os.environ.setdefault("DISABLE_MKDOCS_2_WARNING", "true")
    try:
        return run(DOCS, args.force)
    except Exception:
        traceback.print_exc()
        print("Docs check could not run: the error above is in the toolchain or in check.py, not in the docs.",
              file=sys.stderr)
        return CHECK_FAILED


if __name__ == "__main__":
    sys.exit(main())
