"""Run the docs toolchain: the check, `serve`, or the tests of the docs tooling.

    <python> docs/run.py [--offline] check [--force]
    <python> docs/run.py [--offline] serve [serve options]
    <python> docs/run.py [--offline] test [unittest options]

<python> is a Python of the version in docs/.python-version or later, with its venv module; older
ones are refused. It works from any directory, and only needs the Python standard library. On
first use, it creates a virtual environment under docs/.venv, one per Python version, and installs
the pinned requirements.txt in it with pip; when requirements.txt changes, it creates it again, so
no package removed from the lock lingers. Then it runs check.py, `serve` of the engine mike deploys
with, or the unit tests of docs/tests, with the Python of that environment, and returns its exit code.

Exit codes:
  0  the docs are fine, or nothing changed since the last successful check (check), or serve ended,
     or the tests passed (test, which otherwise exits 1)
  1  the docs have problems (check.py)
  2  bad command line arguments
  3  check.py could not run: crash of the check or of a plugin, or the command was killed
  4  the environment could not be set up: Python too old, venv or pip missing, install failed,
     --offline while an install is needed, or any other error of run.py itself
"""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import os
import shutil
import subprocess
import sys
import traceback
from pathlib import Path

from toolchain import write_atomically

DOCS = Path(__file__).resolve().parent
CHECK_FAILED, SETUP_FAILED = 3, 4
STAMP_NAME = "openaev-requirements.sha256"


class SetupError(Exception):
    """The environment cannot be set up: the message says what to do."""


def required_version(docs: Path = DOCS) -> tuple[int, int]:
    """The minimum Python version, from .python-version (`3.11`)."""
    major, minor = (docs / ".python-version").read_text(encoding="utf-8").strip().split(".")[:2]
    return int(major), int(minor)


def check_python(version: tuple[int, ...], required: tuple[int, int]) -> None:
    if tuple(version[:2]) < required:
        raise SetupError(
            f"Python {required[0]}.{required[1]} or later is needed to build the docs, this is "
            f"{version[0]}.{version[1]} ({sys.executable}). Install a newer Python and run this again with it."
        )


def environment_dir(docs: Path, version: tuple[int, ...]) -> Path:
    return docs / ".venv" / f"py{version[0]}.{version[1]}"


def venv_python(venv: Path) -> Path:
    return venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def venv_version(venv: Path) -> tuple[int, int] | None:
    """Python version of a virtual environment, from its pyvenv.cfg, or None when it is not one."""
    try:
        lines = (venv / "pyvenv.cfg").read_text(encoding="utf-8").splitlines()
    except OSError:
        return None
    for line in lines:
        key, _, value = line.partition("=")
        if key.strip() in ("version", "version_info"):
            parts = value.strip().split(".")
            if len(parts) >= 2 and parts[0].isdigit() and parts[1].isdigit():
                return int(parts[0]), int(parts[1])
    return None


def venv_usable(venv: Path, version: tuple[int, ...]) -> bool:
    """True when the environment exists, is complete, and was made by a Python of this version."""
    return venv_python(venv).is_file() and venv_version(venv) == tuple(version[:2])


def requirements_digest(requirements: Path, version: tuple[int, ...]) -> str:
    digest = hashlib.sha256(requirements.read_bytes())
    digest.update(f"\0python {version[0]}.{version[1]}".encode())
    return digest.hexdigest()


def installed_digest(venv: Path) -> str | None:
    try:
        return (venv / STAMP_NAME).read_text(encoding="utf-8").strip()
    except OSError:
        return None


@contextlib.contextmanager
def exclusive_lock(path: Path):
    """Hold an exclusive lock on a file: one process at a time sets the environment up.

    The operating system releases it when the process ends, so a crash never leaves it held.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "a+b") as file:
        if os.name == "nt":
            import msvcrt

            while True:
                try:
                    file.seek(0)
                    msvcrt.locking(file.fileno(), msvcrt.LK_LOCK, 1)  # retries for 10 s, then raises
                    break
                except OSError:
                    continue
            try:
                yield
            finally:
                file.seek(0)
                msvcrt.locking(file.fileno(), msvcrt.LK_UNLCK, 1)
        else:
            import fcntl

            fcntl.flock(file.fileno(), fcntl.LOCK_EX)
            try:
                yield
            finally:
                fcntl.flock(file.fileno(), fcntl.LOCK_UN)


def base_python() -> str:
    """The Python that creates the environment, symbolic links resolved.

    The environment records the folder of this executable as its home. A link elsewhere, such as
    ~/.local/bin/python3 to a Python installed in another folder, would point it at a folder
    without the standard library. Windows keeps sys.executable: its Store Python is an alias that
    must be run as it is.
    """
    return sys.executable if os.name == "nt" else os.path.realpath(sys.executable)


def create_venv(venv: Path) -> None:
    try:
        import ensurepip  # noqa: F401  (Debian and Ubuntu ship it apart, in python3-venv)
    except ImportError as error:
        raise SetupError(
            f"This Python cannot create a virtual environment ({error}). On Debian or Ubuntu, install it "
            f"with `sudo apt install python3-venv` (or python{sys.version_info[0]}.{sys.version_info[1]}-venv)."
        ) from error
    print(f"Docs setup: creating the virtual environment {venv}", flush=True)
    result = subprocess.run([base_python(), "-m", "venv", "--clear", str(venv)], check=False)
    if result.returncode != 0 or not venv_python(venv).is_file():
        # Half an environment would pass for a complete one next time.
        shutil.rmtree(venv, ignore_errors=True)
        raise SetupError(f"Creating the virtual environment {venv} failed, see above.")


def pip(venv: Path, *args: str) -> None:
    command = [str(venv_python(venv)), "-m", "pip", "--disable-pip-version-check", *args]
    if subprocess.run(command, cwd=DOCS, check=False).returncode != 0:
        raise SetupError(
            f"`pip {' '.join(args)}` failed, see above. Check the network access to PyPI and GitHub "
            "(mike installs from git), then run this again."
        )


def ensure_environment(docs: Path, offline: bool) -> Path:
    """Return the Python of the environment, after creating it or installing requirements.txt if needed.

    Each Python version gets its own environment, docs/.venv/py<major>.<minor>: a run with another
    Python never rebuilds the environment a running check or serve uses. Creating and installing
    happen under a lock, and the stamp written last tells a complete environment from one being set up.
    """
    venv = environment_dir(docs, sys.version_info)
    requirements = docs / "requirements.txt"
    version = tuple(sys.version_info[:2])
    wanted = requirements_digest(requirements, version)
    if venv_usable(venv, version) and installed_digest(venv) == wanted:
        return venv_python(venv)
    if offline:
        raise SetupError(
            "The docs environment must be installed or updated, which --offline does not allow. "
            "Run this once online."
        )
    with exclusive_lock(docs / ".cache" / "venv.lock"):
        # Another process may have set it up while this one waited for the lock.
        if installed_digest(venv) != wanted or not venv_usable(venv, version):
            # A new environment rather than an upgrade: a package dropped from the lock must go too.
            create_venv(venv)
            print("Docs setup: installing requirements.txt", flush=True)
            pip(venv, "install", "--quiet", "--no-deps", "-r", str(requirements))
            pip(venv, "check")
            write_atomically(venv / STAMP_NAME, wanted + "\n")
    return venv_python(venv)


def command_for(python: Path, docs: Path, command: str, args: list[str]) -> list[str]:
    if command == "check":
        return [str(python), str(docs / "check.py"), *args]
    if command == "test":
        return [str(python), "-m", "unittest", "discover", "-s", str(docs / "tests"), "-t", str(docs), *args]
    return [str(python), str(docs / "toolchain.py"), "serve", "--config-file", str(docs / "mkdocs.yml"), *args]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Run the docs check, `mkdocs serve` or the docs tests.")
    parser.add_argument("--offline", action="store_true", help="fail instead of installing anything")
    parser.add_argument("command", choices=["check", "serve", "test"])
    parser.add_argument("args", nargs=argparse.REMAINDER, help="passed to check.py, serve or unittest")
    options = parser.parse_args(argv)

    try:
        check_python(sys.version_info, required_version())
        python = ensure_environment(DOCS, options.offline)
    except SetupError as error:
        print(f"Docs setup failed: {error}", file=sys.stderr)
        return SETUP_FAILED
    except Exception:  # noqa: BLE001  any other failure of the setup is still a setup failure
        traceback.print_exc()
        print("Docs setup failed: the error above is in run.py or in the environment.", file=sys.stderr)
        return SETUP_FAILED
    try:
        code = subprocess.run(command_for(python, DOCS, options.command, options.args), cwd=DOCS, check=False).returncode
        # A negative code is the signal that killed the command, which is not a docs problem.
        return CHECK_FAILED if code < 0 else code
    except OSError as error:
        print(f"Docs setup failed: cannot run {python}: {error}. Delete {python.parents[1]} and run this again.",
              file=sys.stderr)
        return SETUP_FAILED
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
