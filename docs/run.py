"""Run the docs toolchain: the check, `serve`, or the tests of the docs tooling.

    <python> docs/run.py [--offline] check [--force]
    <python> docs/run.py [--offline] serve [serve options]
    <python> docs/run.py [--offline] test [unittest options]

<python> is a Python of the version in docs/.python-version or later, with its venv module; older
ones are refused. It works from any directory, and only needs the Python standard library. It
runs check.py, `serve` of the engine mike deploys with, or the unit tests of docs/tests, with the
Python of a virtual environment that holds the pinned requirements.txt, and returns its exit code.

The environment is docs/.venv/py<major>.<minor>-<digest>, named after the Python version and the
digest of requirements.txt. When either changes, run.py creates a new one beside the others, under
a lock, and marks it complete last, so a running check or serve never loses the environment it
uses, and no package removed from the lock lingers. On Linux and macOS, it then deletes the
environments of older digests that no running command uses. On Windows, where a running Python
keeps its files, older environments stay: delete docs/.venv to reclaim the space.

Exit codes:
  0  the docs are fine, or nothing changed since the last successful check (check), or serve ended,
     or the tests passed (test, which otherwise exits 1)
  1  the docs have problems (check.py)
  2  bad command line arguments
  3  check.py could not run: crash of check.py (import included), of the toolchain or of a plugin,
     or the command was killed
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
import re
import traceback
from pathlib import Path

DOCS = Path(__file__).resolve().parent
DOCS_PROBLEMS, CHECK_FAILED, SETUP_FAILED = 1, 3, 4
STAMP_NAME = "openaev-requirements.sha256"
ENVIRONMENT_RE = re.compile(r"py(\d+)\.(\d+)-([0-9a-f]{12})")
# check.py runs through this bootstrap, so that only its own result can mean "docs problems": a
# crash while importing it, or an interpreter that fails to start, exits 1 too, which run.py then
# reports as 3. The bootstrap hands docs problems over as 10.
CHECK_DOCS_PROBLEMS = 10
CHECK_BOOTSTRAP = (
    "import sys, traceback\n"
    "try:\n"
    "    import check\n"
    "except BaseException:\n"
    "    traceback.print_exc()\n"
    "    sys.exit(3)\n"
    "code = check.main(sys.argv[1:])\n"
    f"sys.exit({CHECK_DOCS_PROBLEMS} if code == 1 else code)\n"
)


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


def environment_dir(docs: Path, version: tuple[int, ...], digest: str) -> Path:
    return docs / ".venv" / f"py{version[0]}.{version[1]}-{digest[:12]}"


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
def in_use(venv: Path):
    """Hold a shared lock while a command runs with an environment, so no cleanup deletes it.

    Windows has no shared lock in its standard library; there, run.py never deletes environments.
    """
    if os.name == "nt":
        yield
        return
    import fcntl

    with open(venv.parent / f"{venv.name}.inuse", "a+b") as file:
        fcntl.flock(file.fileno(), fcntl.LOCK_SH)
        try:
            yield
        finally:
            fcntl.flock(file.fileno(), fcntl.LOCK_UN)


def stale_environments(docs: Path, requirements: Path) -> list[Path]:
    """Environments built from another requirements.txt than the current one, whatever their Python."""
    root = docs / ".venv"
    stale = []
    for entry in sorted(root.iterdir()) if root.is_dir() else []:
        match = ENVIRONMENT_RE.fullmatch(entry.name)
        if entry.is_dir() and match:
            version = (int(match.group(1)), int(match.group(2)))
            if requirements_digest(requirements, version)[:12] != match.group(3):
                stale.append(entry)
    return stale


def remove_stale_environments(docs: Path, requirements: Path) -> None:
    """Delete the stale environments no command uses: one is in use while a process holds its shared lock."""
    if os.name == "nt":
        return
    import fcntl

    for venv in stale_environments(docs, requirements):
        marker = venv.parent / f"{venv.name}.inuse"
        with open(marker, "a+b") as file:
            try:
                fcntl.flock(file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                continue  # a running command uses it: a later run deletes it
            shutil.rmtree(venv, ignore_errors=True)
            marker.unlink(missing_ok=True)


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
    """Return the environment for this Python and requirements.txt, after creating it if needed.

    Its folder is named after both, so a new requirements.txt or another Python gets a new folder:
    a running check or serve never sees its environment change. Creating and installing happen
    under a lock, and the stamp written last tells a complete environment from one being set up.
    """
    from toolchain import write_atomically

    requirements = docs / "requirements.txt"
    version = tuple(sys.version_info[:2])
    wanted = requirements_digest(requirements, version)
    venv = environment_dir(docs, version, wanted)
    if venv_usable(venv, version) and installed_digest(venv) == wanted:
        return venv
    if offline:
        raise SetupError(
            "The docs environment must be installed or updated, which --offline does not allow. "
            "Run this once online."
        )
    with exclusive_lock(docs / ".cache" / "venv.lock"):
        # Another process may have set it up while this one waited for the lock.
        if installed_digest(venv) != wanted or not venv_usable(venv, version):
            # Nobody uses an environment without its stamp: start it over.
            create_venv(venv)
            print("Docs setup: installing requirements.txt", flush=True)
            pip(venv, "install", "--quiet", "--no-deps", "-r", str(requirements))
            pip(venv, "check")
            write_atomically(venv / STAMP_NAME, wanted + "\n")
        remove_stale_environments(docs, requirements)
    return venv


def command_for(python: Path, docs: Path, command: str, args: list[str]) -> list[str]:
    if command == "check":
        return [str(python), "-c", CHECK_BOOTSTRAP, *args]
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
        venv = ensure_environment(DOCS, options.offline)
        python = venv_python(venv)
    except SetupError as error:
        print(f"Docs setup failed: {error}", file=sys.stderr)
        return SETUP_FAILED
    except Exception:  # noqa: BLE001  any other failure of the setup is still a setup failure
        traceback.print_exc()
        print("Docs setup failed: the error above is in run.py or in the environment.", file=sys.stderr)
        return SETUP_FAILED
    try:
        with in_use(venv):
            code = subprocess.run(command_for(python, DOCS, options.command, options.args), cwd=DOCS,
                                  check=False).returncode
    except OSError as error:
        print(f"Docs setup failed: cannot run {python}: {error}. Delete {venv} and run this again.", file=sys.stderr)
        return SETUP_FAILED
    except KeyboardInterrupt:
        return 130
    return exit_code(options.command, code)


def exit_code(command: str, code: int) -> int:
    """The exit code of run.py for the code of its command."""
    if code < 0:
        return CHECK_FAILED  # killed by a signal, which is not a result of the docs
    if command != "check":
        return code
    if code == CHECK_DOCS_PROBLEMS:
        return DOCS_PROBLEMS
    # 1 here comes from the interpreter, not from check.py, which reports docs problems as 10.
    return code if code in (0, 2, CHECK_FAILED) else CHECK_FAILED


if __name__ == "__main__":
    sys.exit(main())
