"""Build the docs strictly and check them: pages, images, links between pages and links.json.

The checks themselves run in hooks/checks.py and in the `validation` of mkdocs.yml, so plain
`properdocs build` and `properdocs serve` run them too. This script is the single check the backend build,
the front `yarn check-docs-links` and the CI run, through run.py, which sets up the Python
environment first:

    python3 docs/run.py check [--force] [--git-plugins]

It builds with ProperDocs, the engine mike deploys with, with `--strict`, into
.cache/check/site-<pid>, so that several runs at the same time do not clash. The git plugins are
off unless --git-plugins is given: DOCS_GIT_PLUGINS of the environment does not count. It skips
the build when none of its inputs changed since the last successful check, whose fingerprint it
keeps in .cache/check/fingerprint. --force builds anyway.

Exit codes of check.py itself:
  0  the docs are fine, or nothing changed since the last successful check
  1  the docs have problems: the warnings and errors the build printed above, an invalid mkdocs.yml
     included (a plugin, extension or theme name that is not installed: run.py installs exactly
     the lock, so the name is wrong)
  2  bad command line arguments
  3  the check itself failed: crash of check.py or of a plugin
The Maven goal, `yarn check-docs-links` and the CI see the exit code of run.py, which passes these
through and adds 3 for a killed check and 4 for an environment it could not set up.
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
import time
import traceback
from collections.abc import Callable, Mapping
from pathlib import Path
from types import SimpleNamespace

from run import write_atomically

DOCS = Path(__file__).resolve().parent

# Everything under docs/ is an input of the check, except these folders at the top of docs/:
# environments, caches of the check and of tools, editor settings, build output, and the tests,
# which do not change what the build produces. Python caches and the folder metadata files of
# macOS and Windows are left out wherever they are.
EXCLUDED_TOP_DIRS = {".venv", ".cache", "site", "tests", ".git", ".dev_venv", "venv", ".venv-build", ".idea",
                     ".vscode", ".mypy_cache", ".ruff_cache", ".pytest_cache"}
EXCLUDED_DIRS_ANYWHERE = {"__pycache__"}
EXCLUDED_FILES_ANYWHERE = {".DS_Store", "Thumbs.db"}
SITE_DIR_RE = re.compile(r"site-(\d+)")
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
        top = Path(root) == docs
        dirs[:] = sorted(d for d in dirs if d not in EXCLUDED_DIRS_ANYWHERE and not (top and d in EXCLUDED_TOP_DIRS))
        files += (Path(root) / name for name in names if name not in EXCLUDED_FILES_ANYWHERE)
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
        try:
            digest.update(path.read_bytes())
        except OSError:
            digest.update(b"<unreadable>")  # removed while listed: a change the next fingerprint sees
    for relative in OUTSIDE_INPUTS:
        path = docs.parent / relative
        digest.update(f"\0outside:{relative}\0".encode())
        digest.update(path.read_bytes() if path.is_file() else b"<missing>")
    return digest.hexdigest()


def load_engine() -> SimpleNamespace:
    """The modules of ProperDocs, which also answers the `mkdocs.*` imports of plugins and hooks."""
    importlib.import_module("properdocs.replacement")
    exceptions = importlib.import_module("properdocs.exceptions")
    return SimpleNamespace(
        build=importlib.import_module("properdocs.commands.build").build,
        load_config=importlib.import_module("properdocs.config").load_config,
        Abort=exceptions.Abort,
        ConfigurationError=exceptions.ConfigurationError,
    )


def mkdocs_build(docs: Path, site_dir: Path, engine: SimpleNamespace | None = None) -> None:
    """Build strictly, as `properdocs build --strict` does. Raise DocsProblems when the docs have problems."""
    engine = engine or load_engine()
    # The output of the engine's command line: its `--quiet` would drop the warnings `--strict` counts.
    logger = logging.getLogger("properdocs")
    logger.setLevel(logging.INFO)
    logger.propagate = False
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
            # The engine printed what is wrong in mkdocs.yml. A plugin, theme or extension it cannot
            # load is a wrong name there too: run.py installs exactly requirements.txt.
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


def process_alive(pid: int) -> bool:
    """True while a process of this pid runs, or when that cannot be told."""
    if os.name == "nt":
        import ctypes

        kernel32 = ctypes.windll.kernel32
        handle = kernel32.OpenProcess(0x1000, False, pid)  # PROCESS_QUERY_LIMITED_INFORMATION
        if not handle:
            return ctypes.GetLastError() == 5  # access denied: it exists
        try:
            code = ctypes.c_ulong()
            return not kernel32.GetExitCodeProcess(handle, ctypes.byref(code)) or code.value == 259  # STILL_ACTIVE
        finally:
            kernel32.CloseHandle(handle)
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except OSError:
        return True  # it exists, owned by another user
    return True


def sweep_site_dirs(cache: Path) -> None:
    """Delete the build folders of checks that died without removing theirs (killed by SIGKILL, for example)."""
    for entry in cache.glob("site-*") if cache.is_dir() else []:
        match = SITE_DIR_RE.fullmatch(entry.name)
        if match and (int(match.group(1)) == os.getpid() or not process_alive(int(match.group(1)))):
            shutil.rmtree(entry, ignore_errors=True)


def run(docs: Path, force: bool, builder: Callable[[Path, Path], None] = mkdocs_build,
        cache: Path | None = None) -> int:
    cache = cache or docs / ".cache" / "check"
    fingerprint_file = cache / "fingerprint"
    before = fingerprint(docs, os.environ)
    if not force and fingerprint_file.is_file() and fingerprint_file.read_text(encoding="utf-8").strip() == before:
        print("Docs check: nothing changed since the last successful check, skipped (--force to run it).")
        return OK

    start = time.monotonic()
    sweep_site_dirs(cache)
    site_dir = cache / f"site-{os.getpid()}"
    site_dir.mkdir(parents=True)
    try:
        builder(docs, site_dir)
    except DocsProblems:
        print("Docs check failed: fix the problems above (docs/README.md explains the checks).", file=sys.stderr)
        return DOCS_PROBLEMS
    finally:
        shutil.rmtree(site_dir, ignore_errors=True)
    print(f"Docs check passed in {time.monotonic() - start:.1f}s: pages, images, links and links.json are fine.")
    # A file changed during the build: what passed is not what is on disk now, so the next run builds again.
    if fingerprint(docs, os.environ) != before:
        print("Docs check: a file changed during the build, the next check builds again.")
        return OK
    try:
        write_atomically(fingerprint_file, before + "\n")
    except OSError as error:
        print(f"Docs check: cannot record the result in {fingerprint_file} ({error}), the next check builds again.",
              file=sys.stderr)
    return OK


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="check.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("--force", action="store_true", help="build even when nothing changed since the last check")
    parser.add_argument("--git-plugins", action="store_true",
                        help="build with the git plugins, as the deployed site does (calls the GitHub API)")
    args = parser.parse_args(argv)

    # The git plugins are off unless asked for: git-committers calls the GitHub API for every page,
    # and a DOCS_GIT_PLUGINS exported in a shell must not change what the check builds.
    os.environ["DOCS_GIT_PLUGINS"] = "true" if args.git_plugins else "false"
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
