"""Run the docs toolchain: the check, `properdocs serve`, or the tests of the docs tooling.

    <python> docs/run.py [--offline] check [--force] [--git-plugins]
    <python> docs/run.py [--offline] serve [serve options]
    <python> docs/run.py [--offline] test [unittest options]

It works from any directory and only needs the Python standard library. <python> can be any
Python 3.6 or later: run.py first chooses the interpreter (docs/README.md, "Which Python runs the
docs toolchain"). It uses the one running it when that one is at least the version of
docs/.python-version and has the venv and ensurepip modules. Otherwise it runs itself again with
the first usable python3.N, from the minimum up to python3.20 (`py -3.N` too on Windows), unless
OPENAEV_DOCS_PYTHON is set: the Python chosen explicitly is never replaced, it must be usable.

It then runs check.py, `properdocs serve` or the unit tests of docs/tests with the Python of a
virtual environment that holds the pinned requirements.txt, and returns their exit code.

The environment is docs/.venv/py<major>.<minor>-<digest>, named after the Python version and the
digest of requirements.txt. When either changes, run.py creates a new one beside the others, under
a lock, and stamps it complete last, so a running check or serve never loses the environment it
uses, and no package removed from the lock lingers. It then deletes the environments of other
digests that no running command uses: on Linux and macOS a command holds a shared lock on
<environment>.inuse while it runs; on Windows, an environment whose Python runs cannot be renamed,
and run.py deletes only the ones it could rename.

Exit codes:
  0  the docs are fine, or nothing changed since the last successful check (check), or serve ended,
     or the tests passed (test, which otherwise exits 1)
  1  the docs have problems (check.py)
  2  bad command line arguments
  3  check.py could not run: crash of check.py (import included) or of a plugin, or the command was
     killed or interrupted (Ctrl+C)
  4  the environment could not be set up: no usable Python, venv or pip missing, install failed,
     --offline while an install is needed, or any other error of run.py itself
"""

# This file must parse and start on Python 3.6, so that an old interpreter gets as far as the
# version check and the search for a newer one: no syntax newer than 3.6 (no `from __future__
# import annotations`, no walrus, no subscripted built-in types in annotations).

import argparse
import contextlib
import hashlib
import os
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time
import traceback
from pathlib import Path

DOCS = Path(__file__).resolve().parent
if sys.path[:1] != [str(DOCS)]:
    # The script folder is missing from sys.path under PYTHONSAFEPATH or -P.
    sys.path.insert(0, str(DOCS))

DOCS_PROBLEMS, CHECK_FAILED, SETUP_FAILED = 1, 3, 4
STAMP_NAME = "openaev-requirements.sha256"
SNAPSHOT_NAME = "openaev-requirements.txt"
ENVIRONMENT_RE = re.compile(r"py(\d+)\.(\d+)-([0-9a-f]{12})")
EXPLICIT_VARIABLE = "OPENAEV_DOCS_PYTHON"
# Set in the environment of run.py when it runs itself again with another Python, so that a
# second search never starts from there.
REEXEC_VARIABLE = "OPENAEV_DOCS_RUN_PY_REEXEC"
# Variables that change which modules a child Python imports, or whether it adds its script folder.
SCRUBBED_VARIABLES = ("PYTHONPATH", "PYTHONHOME", "PYTHONSAFEPATH", REEXEC_VARIABLE)
HIGHEST_MINOR = 20
PROBE_TIMEOUT = 10
PROBE = "import sys, venv, ensurepip; print('%d.%d' % sys.version_info[:2])"
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
    "sys.exit(%d if code == 1 else code)\n" % CHECK_DOCS_PROBLEMS
)


class SetupError(Exception):
    """The environment cannot be set up: the message says what to do."""


def child_env(extra=None):
    """The environment of every process run.py starts, without the variables that redirect Python imports."""
    env = {name: value for name, value in os.environ.items() if name not in SCRUBBED_VARIABLES}
    env.update(extra or {})
    return env


# Choosing the Python. Everything up to the end of this section runs on Python 3.6.

def required_version(docs=DOCS):
    """The minimum Python version, from .python-version: `3.11`, or `3.11.9` whose patch is ignored."""
    major, minor = (docs / ".python-version").read_text(encoding="utf-8").strip().split(".")[:2]
    return int(major), int(minor)


def interpreter_problem(version, required):
    """Why the Python running run.py cannot set the docs up, or None when it can."""
    if tuple(version[:2]) < required:
        return "it is Python %d.%d, older than the %d.%d of docs/.python-version" % (
            version[0], version[1], required[0], required[1])
    try:
        import venv  # noqa: F401
        import ensurepip  # noqa: F401  (Debian and Ubuntu ship it apart, in python3-venv)
    except ImportError as error:
        return "it cannot create a virtual environment (%s)" % error
    return None


def candidates(required, windows=None):
    """The commands to try, from the minimum up: python3.N, and `py -3.N` on Windows. The minimum comes first
    because CI checks the lock on it; a newer Python may lack wheels for its pinned packages."""
    windows = os.name == "nt" if windows is None else windows
    found = []
    for minor in range(required[1], HIGHEST_MINOR + 1):
        name = "%d.%d" % (required[0], minor)
        if windows:
            found.append(["py", "-" + name])
        found.append(["python" + name])
    return found


def probe(command):
    """The version of a usable Python run by this command, or None. Missing commands are skipped fast."""
    executable = shutil.which(command[0])
    if executable is None:
        return None
    try:
        result = subprocess.run([executable] + command[1:] + ["-c", PROBE], stdin=subprocess.DEVNULL,
                                stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, env=child_env(),
                                universal_newlines=True, timeout=PROBE_TIMEOUT)
    except (OSError, subprocess.SubprocessError):
        return None
    match = re.fullmatch(r"\s*(\d+)\.(\d+)\s*", result.stdout or "")
    if result.returncode != 0 or match is None:
        return None
    return int(match.group(1)), int(match.group(2))


def find_python(required):
    """(command, version) of the first usable python3.N, or (None, commands tried)."""
    tried = []
    for command in candidates(required):
        version = probe(command)
        if version is not None and version >= required:
            return command, version
        tried.append(" ".join(command))
    return None, tried


def reexec(command, argv):
    """Run run.py again with another Python, its arguments, standard streams and exit code."""
    executable = shutil.which(command[0]) or command[0]
    args = [executable] + command[1:] + [str(DOCS / "run.py")] + list(argv)
    env = child_env({REEXEC_VARIABLE: sys.executable or "python"})
    sys.stdout.flush()
    sys.stderr.flush()
    if os.name != "nt":
        os.execve(executable, args, env)  # does not return
    return run_child(args, env=env)


def choose_python(argv):
    """None when this Python is usable, otherwise the exit code of run.py, after running itself again
    with a usable python3.N when there is one."""
    required = required_version()
    problem = interpreter_problem(sys.version_info, required)
    if problem is None:
        return None
    this = "%s (%s)" % (sys.executable, ".".join(str(part) for part in sys.version_info[:3]))
    explicit = os.environ.get(EXPLICIT_VARIABLE)
    hint = ("Install Python %d.%d or later from https://www.python.org/downloads/ (on Debian and Ubuntu, "
            "also python3-venv, or python3.N-venv for the python3.N you use)" % required)
    if explicit:
        print("Docs setup failed: %s=%s chose %s, which cannot set the docs up: %s. %s, and set %s to it, or "
              "unset it to let run.py look for one." % (EXPLICIT_VARIABLE, explicit, this, problem, hint,
                                                       EXPLICIT_VARIABLE), file=sys.stderr)
        return SETUP_FAILED
    if os.environ.get(REEXEC_VARIABLE):
        print("Docs setup failed: %s, started by run.py, cannot set the docs up: %s." % (this, problem),
              file=sys.stderr)
        return SETUP_FAILED
    command, found = find_python(required)
    if command is None:
        print("Docs setup failed: %s cannot set the docs up: %s, and none of %s is usable either. %s, or set "
              "%s to one." % (this, problem, ", ".join(found), hint, EXPLICIT_VARIABLE), file=sys.stderr)
        return SETUP_FAILED
    print("Docs setup: %s cannot set the docs up (%s), using %s (Python %d.%d) instead."
          % (this, problem, " ".join(command), found[0], found[1]), flush=True)
    return reexec(command, argv)


# Environments. From here on, the Python is at least the version of .python-version.

def environment_dir(docs, version, digest):
    return docs / ".venv" / ("py%d.%d-%s" % (version[0], version[1], digest[:12]))


def venv_python(venv):
    return venv / ("Scripts/python.exe" if os.name == "nt" else "bin/python")


def venv_version(venv):
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


def venv_usable(venv, version):
    """True when the environment exists and was made by a Python of this version."""
    return venv_python(venv).is_file() and venv_version(venv) == tuple(version[:2])


def requirements_digest(requirements, version):
    """Digest of the bytes of requirements.txt for a Python version."""
    digest = hashlib.sha256(requirements)
    digest.update(("\0python %d.%d" % (version[0], version[1])).encode())
    return digest.hexdigest()


def installed_digest(venv):
    try:
        return (venv / STAMP_NAME).read_text(encoding="utf-8").strip()
    except OSError:
        return None


def is_complete(venv, version, digest):
    return venv_usable(venv, version) and installed_digest(venv) == digest


def write_atomically(path, text):
    """Write through a temporary file and a rename, so a concurrent run never reads half a file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(prefix="." + path.name + ".", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as file:
            file.write(text)
        os.replace(temporary, str(path))
    except BaseException:
        with contextlib.suppress(OSError):
            os.unlink(temporary)
        raise


def marker(venv):
    return venv.parent / (venv.name + ".inuse")


class FileLock:
    """A lock on a file, released when the process and the children it passed the file to end.

    POSIX: flock, shared or exclusive. After the lock is taken, the file must still be the one at
    the path, since a cleanup deletes the marker of an environment it deleted. Windows: msvcrt,
    exclusive only, used for the setup lock.
    """

    def __init__(self, path):
        self.path = path
        self.file = None

    def fileno(self):
        return self.file.fileno()

    def acquire(self, shared=False, blocking=True, waiting_message=None):
        """Take the lock; False when it is busy and `blocking` is false."""
        self.path.parent.mkdir(parents=True, exist_ok=True)
        announced = False
        while True:
            file = open(str(self.path), "a+b")
            try:
                if self._try(file, shared):
                    if os.name == "nt" or self._same_file(file):
                        self.file = file
                        return True
                    file.close()  # deleted meanwhile: lock the new file at the path
                    continue
                if not blocking:
                    file.close()
                    return False
                if waiting_message and not announced:
                    print(waiting_message, flush=True)
                    announced = True
                self._wait(file, shared)
                if os.name == "nt" or self._same_file(file):
                    self.file = file
                    return True
                file.close()
            except BaseException:
                file.close()
                raise

    def _same_file(self, file):
        try:
            return os.path.samestat(os.fstat(file.fileno()), os.stat(str(self.path)))
        except OSError:
            return False

    @staticmethod
    def _try(file, shared):
        if os.name == "nt":
            import msvcrt

            file.seek(0)
            try:
                msvcrt.locking(file.fileno(), msvcrt.LK_NBLCK, 1)
                return True
            except OSError as error:
                if error.errno in (13, 36):  # EACCES, EDEADLOCK: another process holds it
                    return False
                raise
        import fcntl

        try:
            fcntl.flock(file.fileno(), (fcntl.LOCK_SH if shared else fcntl.LOCK_EX) | fcntl.LOCK_NB)
            return True
        except BlockingIOError:
            return False

    @classmethod
    def _wait(cls, file, shared):
        if os.name == "nt":
            while not cls._try(file, shared):
                time.sleep(0.5)
            return
        import fcntl

        fcntl.flock(file.fileno(), fcntl.LOCK_SH if shared else fcntl.LOCK_EX)

    def release(self):
        if self.file is not None:
            self.file.close()  # closing releases flock and msvcrt locks
            self.file = None


@contextlib.contextmanager
def setup_lock(docs):
    """One process at a time creates, installs or deletes environments."""
    lock = FileLock(docs / ".cache" / "venv.lock")
    lock.acquire(waiting_message="Docs setup: waiting for another docs setup to finish (%s)" % lock.path)
    try:
        yield lock
    finally:
        lock.release()


def stale_environments(docs, requirements, keep):
    """Environments built from another requirements.txt than `requirements` (bytes), whatever their
    Python, except `keep`, the one this run uses."""
    root = docs / ".venv"
    stale = []
    for entry in sorted(root.iterdir()) if root.is_dir() else []:
        match = ENVIRONMENT_RE.fullmatch(entry.name)
        if entry.is_dir() and match and entry != keep:
            version = (int(match.group(1)), int(match.group(2)))
            if requirements_digest(requirements, version)[:12] != match.group(3):
                stale.append(entry)
    return stale


def delete_unused(venv):
    """Delete an environment no running command uses; False when one does. Under the setup lock only."""
    if os.name == "nt":
        # Windows keeps the files of a running Python open: the folder cannot be renamed then.
        aside = venv.parent / (venv.name + ".deleting-%d" % os.getpid())
        try:
            os.rename(str(venv), str(aside))
        except OSError:
            return not venv.exists()
        shutil.rmtree(str(aside), ignore_errors=True)
        return True
    lock = FileLock(marker(venv))
    if not lock.acquire(blocking=False):
        return False
    try:
        shutil.rmtree(str(venv), ignore_errors=True)
        with contextlib.suppress(OSError):
            os.unlink(str(lock.path))
    finally:
        lock.release()
    return True


def remove_stale_environments(docs, requirements, keep):
    """Delete the stale environments no command uses, and what an interrupted deletion left. Under the setup lock."""
    root = docs / ".venv"
    for leftover in sorted(root.glob("*.deleting-*")) if root.is_dir() else []:
        shutil.rmtree(str(leftover), ignore_errors=True)
    for venv in stale_environments(docs, requirements, keep):
        delete_unused(venv)  # an environment in use stays: a later run deletes it


def run_child(command, keep=(), **kwargs):
    """Run a command and return its exit code, negative when a signal killed it.

    The child inherits the lock files of `keep`, so the locks last as long as it runs, even when
    run.py itself is killed. SIGTERM and SIGHUP sent to run.py go to the child, and run.py waits for
    it. Ctrl+C reaches both: run.py waits for the child, then raises KeyboardInterrupt.
    """
    kwargs.setdefault("env", child_env())
    if os.name != "nt":
        kwargs["pass_fds"] = tuple(lock.fileno() for lock in keep)
    process = subprocess.Popen(command, **kwargs)
    previous = {}

    def forward(signum, frame):
        with contextlib.suppress(OSError):
            process.send_signal(signum)

    for name in ("SIGTERM", "SIGHUP"):
        signum = getattr(signal, name, None)
        if signum is not None:
            with contextlib.suppress(ValueError, OSError):  # ValueError: not the main thread
                previous[signum] = signal.signal(signum, forward)
    try:
        try:
            return process.wait()
        except KeyboardInterrupt:
            process.wait()
            raise
    finally:
        for signum, handler in previous.items():
            signal.signal(signum, handler)


def create_venv(venv, keep=()):
    print("Docs setup: creating the virtual environment %s" % venv, flush=True)
    base_python = sys.executable if os.name == "nt" else os.path.realpath(sys.executable)
    # The environment records the folder of this executable as its home: a symbolic link elsewhere,
    # such as ~/.local/bin/python3, would point it at a folder without the standard library. Windows
    # keeps sys.executable: its Store Python is an alias that must be run as it is.
    code = run_child([base_python, "-m", "venv", "--clear", str(venv)], keep=keep)
    if code != 0 or not venv_python(venv).is_file():
        # Half an environment would pass for a complete one next time.
        shutil.rmtree(str(venv), ignore_errors=True)
        raise SetupError("Creating the virtual environment %s failed, see above." % venv)


def pip(venv, *args, keep=()):
    command = [str(venv_python(venv)), "-m", "pip", "--disable-pip-version-check"] + list(args)
    # PIP_USER=1 (a user setting) would install outside the environment.
    if run_child(command, keep=keep, cwd=str(DOCS), env=child_env({"PIP_USER": "0"})) != 0:
        raise SetupError(
            "`pip %s` failed, see above. Check the network access to PyPI and GitHub (mike installs "
            "from git), then run this again." % " ".join(args))


def install(venv, requirements, digest, keep):
    """Create the environment and install the snapshot of requirements.txt read by this run."""
    if venv.exists() and not delete_unused(venv):
        raise SetupError(
            "The environment %s is incomplete but a running docs command uses it. Stop the other "
            "docs commands, then run this again." % venv)
    create_venv(venv, keep)
    # pip installs the bytes the digest was computed from, even if requirements.txt changes meanwhile.
    snapshot = venv / SNAPSHOT_NAME
    snapshot.write_bytes(requirements)
    print("Docs setup: installing requirements.txt", flush=True)
    pip(venv, "install", "--quiet", "--no-deps", "-r", str(snapshot), keep=keep)
    pip(venv, "check", keep=keep)
    write_atomically(venv / STAMP_NAME, digest + "\n")


def hold_in_use(venv, version, digest):
    """Take the shared lock of a complete environment, or return None when it is not complete."""
    if os.name == "nt":
        return FileLock(marker(venv)) if is_complete(venv, version, digest) else None
    lock = FileLock(marker(venv))
    lock.acquire(shared=True)
    # A cleanup may have deleted it before the lock was taken: it cannot any more.
    if is_complete(venv, version, digest):
        return lock
    lock.release()
    return None


def ensure_environment(docs, offline):
    """Return (environment, lock): the environment for this Python and requirements.txt, created if
    needed, and the lock that keeps it from deletion while the command runs (no file on Windows).

    Its folder is named after both, so a new requirements.txt or another Python gets a new folder:
    a running check or serve never sees its environment change. Creating and installing happen
    under the setup lock, and the stamp written last tells a complete environment from one being
    set up.
    """
    requirements = (docs / "requirements.txt").read_bytes()
    version = tuple(sys.version_info[:2])
    wanted = requirements_digest(requirements, version)
    venv = environment_dir(docs, version, wanted)
    in_use = hold_in_use(venv, version, wanted)
    if in_use is not None:
        # Clean up while nobody sets up, without waiting for the one who does.
        lock = FileLock(docs / ".cache" / "venv.lock")
        if lock.acquire(blocking=False):
            try:
                remove_stale_environments(docs, requirements, venv)
            finally:
                lock.release()
        return venv, in_use
    if offline:
        raise SetupError("The docs environment must be installed or updated, which --offline does not allow. "
                         "Run this once online.")
    with setup_lock(docs) as lock:
        # Another process may have set it up while this one waited for the lock.
        if not is_complete(venv, version, wanted):
            install(venv, requirements, wanted, keep=(lock,))
        in_use = hold_in_use(venv, version, wanted)
        if in_use is None:
            raise SetupError("The environment %s is not complete after its setup. Delete it and run this again." % venv)
        remove_stale_environments(docs, requirements, venv)
    return venv, in_use


def command_for(python, docs, command, args):
    if command == "check":
        return [str(python), "-c", CHECK_BOOTSTRAP] + list(args)
    if command == "test":
        return [str(python), "-m", "unittest", "discover", "-s", str(docs / "tests"), "-t", str(docs)] + list(args)
    return [str(python), "-m", "properdocs", "serve", "--config-file", str(docs / "mkdocs.yml")] + list(args)


def exit_code(command, code):
    """The exit code of run.py for the code of its command."""
    if code < 0:
        return CHECK_FAILED  # killed by a signal, which is not a result of the docs
    if command != "check":
        return code
    if code == CHECK_DOCS_PROBLEMS:
        return DOCS_PROBLEMS
    # 1 here comes from the interpreter, not from check.py, which reports docs problems as 10.
    return code if code in (0, 2, CHECK_FAILED) else CHECK_FAILED


def main(argv=None):
    argv = sys.argv[1:] if argv is None else list(argv)
    parser = argparse.ArgumentParser(description="Run the docs check, `properdocs serve` or the docs tests.")
    parser.add_argument("--offline", action="store_true", help="fail instead of installing anything")
    parser.add_argument("command", choices=["check", "serve", "test"])
    parser.add_argument("args", nargs=argparse.REMAINDER, help="passed to check.py, serve or unittest")
    options = parser.parse_args(argv)

    try:
        code = choose_python(argv)
        if code is not None:
            return CHECK_FAILED if code < 0 else code
        venv, in_use = ensure_environment(DOCS, options.offline)
        python = venv_python(venv)
    except KeyboardInterrupt:
        print("Docs setup interrupted.", file=sys.stderr)
        return CHECK_FAILED
    except SetupError as error:
        print("Docs setup failed: %s" % error, file=sys.stderr)
        return SETUP_FAILED
    except Exception:  # noqa: BLE001  any other failure of the setup is still a setup failure
        traceback.print_exc()
        print("Docs setup failed: the error above is in run.py or in the environment.", file=sys.stderr)
        return SETUP_FAILED
    try:
        code = run_child(command_for(python, DOCS, options.command, options.args), keep=(in_use,), cwd=str(DOCS))
    except OSError as error:
        print("Docs setup failed: cannot run %s: %s. Delete %s and run this again." % (python, error, venv),
              file=sys.stderr)
        return SETUP_FAILED
    except KeyboardInterrupt:
        return CHECK_FAILED
    finally:
        in_use.release()
    return exit_code(options.command, code)


if __name__ == "__main__":
    sys.exit(main())
