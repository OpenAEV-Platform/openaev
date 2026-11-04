"""Helpers shared by run.py (any Python, standard library only) and check.py (the docs environment).

    python toolchain.py serve [options]   runs `serve` of the build engine mike deploys with
"""

from __future__ import annotations

import os
import runpy
import sys
import tempfile
from importlib import metadata
from pathlib import Path


def write_atomically(path: Path, text: str) -> None:
    """Write through a temporary file and a rename, so a concurrent run never reads half a file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            file.write(text)
        os.replace(temporary, path)
    except BaseException:
        Path(temporary).unlink(missing_ok=True)
        raise


def engine_name() -> str:
    """The build engine mike deploys with, chosen as mike does (mike/mkdocs_utils.py).

    mike runs ProperDocs, the continuation of MkDocs 1.x, whenever it is installed: mkdocs-redirects
    depends on it. The check and serve use the same engine, so CI checks what is deployed.
    """
    try:
        metadata.version("properdocs")
        return "properdocs"
    except metadata.PackageNotFoundError:
        return "mkdocs"


def main(argv: list[str]) -> int:
    if not argv or argv[0] != "serve":
        print("usage: toolchain.py serve [options]", file=sys.stderr)
        return 2
    name = engine_name()
    sys.argv = [name, *argv]
    try:
        runpy.run_module(name, run_name="__main__", alter_sys=True)
    except SystemExit as exit_:
        return exit_.code if isinstance(exit_.code, int) else (0 if exit_.code is None else 1)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
