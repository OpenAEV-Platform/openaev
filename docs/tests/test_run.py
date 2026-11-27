"""Tests of run.py: choice of the Python, environments, locks, cleanup, --offline, exit codes and commands.

Run with: python3 docs/run.py test
"""

import ast
import contextlib
import io
import os
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import run

VERSION = tuple(sys.version_info[:2])
POSIX_ONLY = unittest.skipIf(os.name == "nt", "flock and signals of POSIX")


def fake_venv(venv: Path, version=VERSION) -> None:
    python = run.venv_python(venv)
    python.parent.mkdir(parents=True)
    python.write_text("")
    (venv / "pyvenv.cfg").write_text(f"home = /usr/bin\nversion = {version[0]}.{version[1]}.3\n")


def start_holder(code: str, *args: str) -> subprocess.Popen:
    """A Python process that runs `code` with run imported and prints a line once it holds what it must."""
    process = subprocess.Popen([sys.executable, "-c", "import sys, time, pathlib, run\n" + code, *args],
                               cwd=run.DOCS, stdout=subprocess.PIPE, text=True)
    process.stdout.readline()
    return process


def stop(process: subprocess.Popen) -> None:
    process.wait(timeout=20)
    process.stdout.close()


class SourceTest(unittest.TestCase):
    def test_run_py_parses_on_python_3_6(self):
        # An old python3 must get as far as the version check and the search for a newer Python.
        ast.parse((run.DOCS / "run.py").read_text(encoding="utf-8"), feature_version=(3, 6))


class VersionTest(unittest.TestCase):
    def test_reads_the_minimum_from_python_version_with_or_without_a_patch(self):
        with tempfile.TemporaryDirectory() as directory:
            for text, expected in (("3.12\n", (3, 12)), ("3.11.9\n", (3, 11)), (" 3.13 \r\n", (3, 13))):
                (Path(directory) / ".python-version").write_text(text)
                self.assertEqual(run.required_version(Path(directory)), expected, text)

    def test_the_minimum_and_later_are_usable(self):
        for version in ((3, 12, 0), (3, 13, 1), (4, 0, 0)):
            self.assertIsNone(run.interpreter_problem(version, (3, 12)))

    def test_an_older_python_says_which(self):
        problem = run.interpreter_problem((3, 11, 9), (3, 12))
        self.assertIn("Python 3.11, older than the 3.12", problem)

    def test_a_python_without_ensurepip_is_not_usable(self):
        with mock.patch.dict(sys.modules, {"ensurepip": None}):
            self.assertIn("cannot create a virtual environment", run.interpreter_problem(VERSION, VERSION))

    def test_candidates_go_from_the_minimum_up_to_the_newest(self):
        self.assertEqual(run.candidates((3, 18), windows=False), [["python3.18"], ["python3.19"], ["python3.20"]])
        self.assertEqual(run.candidates((3, 20), windows=True), [["py", "-3.20"], ["python3.20"]])


class ChoosePythonTest(unittest.TestCase):
    def choose(self, problem, env=None, found=(None, ["python3.20", "python3.11"])):
        with mock.patch.object(run, "interpreter_problem", return_value=problem), \
                mock.patch.object(run, "find_python", return_value=found) as finder, \
                mock.patch.object(run, "reexec", return_value=0) as reexec, \
                mock.patch.dict(os.environ, env or {}), \
                contextlib.redirect_stderr(io.StringIO()) as err, contextlib.redirect_stdout(io.StringIO()):
            for name in (run.EXPLICIT_VARIABLE, run.REEXEC_VARIABLE):
                if name not in (env or {}):
                    os.environ.pop(name, None)
            code = run.choose_python(["check", "--force"])
        return code, err.getvalue(), finder, reexec

    def test_a_usable_python_runs_the_command_itself(self):
        code, _, finder, _ = self.choose(None)
        self.assertIsNone(code)
        finder.assert_not_called()

    def test_an_explicit_python_is_never_replaced(self):
        code, err, finder, _ = self.choose("it is Python 3.9", {run.EXPLICIT_VARIABLE: "/usr/bin/python3"})
        self.assertEqual(code, run.SETUP_FAILED)
        self.assertIn("OPENAEV_DOCS_PYTHON=/usr/bin/python3", err)
        self.assertIn("it is Python 3.9", err)
        finder.assert_not_called()

    def test_runs_again_with_the_first_usable_python3_n(self):
        code, _, _, reexec = self.choose("it is Python 3.9", found=(["python3.14"], (3, 14)))
        self.assertEqual(code, 0)
        reexec.assert_called_once_with(["python3.14"], ["check", "--force"])

    def test_nothing_usable_exits_4_and_lists_what_was_tried(self):
        code, err, _, _ = self.choose("it is Python 3.9")
        self.assertEqual(code, run.SETUP_FAILED)
        self.assertIn("python3.20, python3.11", err)
        self.assertIn("python3-venv", err)

    def test_never_searches_twice(self):
        code, _, finder, _ = self.choose("it is Python 3.9", {run.REEXEC_VARIABLE: "/usr/bin/python3"})
        self.assertEqual(code, run.SETUP_FAILED)
        finder.assert_not_called()

    def test_probe_skips_missing_commands_and_reads_the_version(self):
        self.assertIsNone(run.probe(["no-such-python-command"]))
        self.assertEqual(run.probe([sys.executable]), VERSION)

    def test_child_environments_drop_the_variables_that_redirect_imports(self):
        with mock.patch.dict(os.environ, {"PYTHONPATH": "/x", "PYTHONHOME": "/y", "PYTHONSAFEPATH": "1",
                                          run.REEXEC_VARIABLE: "1", "KEPT": "1"}):
            env = run.child_env({"PIP_USER": "0"})
        self.assertEqual({name for name in run.SCRUBBED_VARIABLES if name in env}, set())
        self.assertEqual((env["KEPT"], env["PIP_USER"]), ("1", "0"))

    @POSIX_ONLY
    def test_an_old_python_runs_run_py_again_with_a_newer_one(self):
        # This interpreter, as python3.N first on the PATH, and a run.py that sees it as too old.
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            minimum = run.required_version()[1]
            (folder / f"python3.{minimum}").symlink_to(sys.executable)
            env = {**os.environ, "PATH": f"{folder}{os.pathsep}{os.environ['PATH']}"}
            env.pop(run.EXPLICIT_VARIABLE, None)
            env.pop(run.REEXEC_VARIABLE, None)
            code = ("import sys, run\n"
                    "run.interpreter_problem = lambda *args: 'it is too old'\n"
                    "sys.exit(run.choose_python(['--no-such-option']))\n")
            result = subprocess.run([sys.executable, "-c", code], cwd=run.DOCS, env=env, capture_output=True,
                                    text=True, check=False, timeout=60)
        self.assertIn(f"using python3.{minimum}", result.stdout, result.stderr)
        # The real run.py ran again with the arguments, and its exit code came back: argparse rejects them.
        self.assertEqual(result.returncode, 2, result.stderr)
        self.assertIn("run.py: error:", result.stderr)


class EnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name)
        self.requirements = self.docs / "requirements.txt"
        self.requirements.write_bytes(b"mkdocs==1.6.1\n")
        self.digest = run.requirements_digest(b"mkdocs==1.6.1\n", VERSION)
        self.venv = run.environment_dir(self.docs, VERSION, self.digest)

    def tearDown(self):
        self.tmp.cleanup()

    def stamp(self, venv=None, digest=None):
        (venv or self.venv).joinpath(run.STAMP_NAME).write_text((digest or self.digest) + "\n")

    def ensure(self, offline=False, create=None, pip=None):
        def default_create(venv, keep=()):
            fake_venv(venv)

        with mock.patch.object(run, "create_venv", side_effect=create or default_create) as created, \
                mock.patch.object(run, "pip", side_effect=pip) as piped, contextlib.redirect_stdout(io.StringIO()):
            venv, lock = run.ensure_environment(self.docs, offline=offline)
        self.addCleanup(lock.release)
        return venv, created, piped

    def test_one_environment_per_python_version_and_requirements(self):
        self.assertEqual(run.environment_dir(self.docs, (3, 13, 2), "ab" * 32), self.docs / ".venv" / f"py3.13-{'ab' * 6}")

    def test_venv_version_reads_pyvenv_cfg(self):
        fake_venv(self.venv, (3, 13))
        self.assertEqual(run.venv_version(self.venv), (3, 13))
        self.assertIsNone(run.venv_version(self.docs / "nothing"))

    def test_digest_depends_on_requirements_and_python(self):
        base = run.requirements_digest(b"a==1\n", (3, 12))
        self.assertNotEqual(base, run.requirements_digest(b"a==1\n", (3, 13)))
        self.assertNotEqual(base, run.requirements_digest(b"a==2\n", (3, 12)))

    def test_up_to_date_environment_is_used_as_it_is(self):
        fake_venv(self.venv)
        self.stamp()
        venv, created, piped = self.ensure(offline=True)
        self.assertEqual(venv, self.venv)
        created.assert_not_called()
        piped.assert_not_called()

    def test_offline_fails_when_an_install_is_needed(self):
        with self.assertRaises(run.SetupError) as raised:
            self.ensure(offline=True)
        self.assertIn("--offline", str(raised.exception))

    def test_missing_environment_is_created_then_installed_and_stamped(self):
        calls = []
        venv, _, _ = self.ensure(create=lambda v, keep=(): (calls.append("create"), fake_venv(v)),
                                 pip=lambda v, *args, keep=(): calls.append(args[0]))
        self.assertEqual(calls, ["create", "install", "check"])
        self.assertEqual(run.installed_digest(venv), self.digest)

    def test_installs_the_requirements_it_read_even_when_the_file_changes_meanwhile(self):
        def create(venv, keep=()):
            fake_venv(venv)
            self.requirements.write_bytes(b"mkdocs==9.9.9\n")  # a branch switch during the setup

        installed = {}

        def pip(venv, *args, keep=()):
            if args[0] == "install":
                installed["text"] = Path(args[-1]).read_bytes()

        venv, _, _ = self.ensure(create=create, pip=pip)
        self.assertEqual(installed["text"], b"mkdocs==1.6.1\n")
        self.assertEqual(venv, self.venv)
        self.assertEqual(run.installed_digest(venv), self.digest)

    def test_the_environment_of_this_run_is_never_stale(self):
        fake_venv(self.venv)
        self.assertEqual(run.stale_environments(self.docs, b"other\n", keep=self.venv), [])
        self.assertEqual(run.stale_environments(self.docs, b"other\n", keep=None), [self.venv])

    def test_stale_environments_are_the_ones_of_other_requirements_whatever_the_python(self):
        current = self.venv
        other_python = run.environment_dir(self.docs, (3, 99), run.requirements_digest(b"mkdocs==1.6.1\n", (3, 99)))
        stale = self.docs / ".venv" / "py3.99-0123456789ab"
        legacy = self.docs / ".venv" / "py3.11"
        for venv in (current, other_python, stale, legacy):
            fake_venv(venv)
        self.assertEqual(run.stale_environments(self.docs, b"mkdocs==1.6.1\n", keep=None), [stale])

    @POSIX_ONLY
    def test_new_requirements_get_a_new_environment_and_leave_the_old_one_alone_while_in_use(self):
        fake_venv(self.venv)
        self.stamp()
        old = self.venv
        holder = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire(shared=True)\n"
                              "print('using', flush=True); time.sleep(1.5)\n", str(run.marker(old)))
        try:
            self.requirements.write_bytes(b"mkdocs==1.6.2\n")
            venv, created, _ = self.ensure()
            self.assertNotEqual(venv, old)
            self.assertTrue(old.is_dir(), "an environment in use must stay")
        finally:
            stop(holder)
        # Once nothing uses it, the next run deletes it, on the fast path too.
        self.ensure(offline=True)
        self.assertFalse(old.exists())
        self.assertFalse(run.marker(old).exists())

    @POSIX_ONLY
    def test_the_fast_path_skips_the_cleanup_while_another_setup_runs(self):
        fake_venv(self.venv)
        self.stamp()
        stale = self.docs / ".venv" / "py3.99-0123456789ab"
        fake_venv(stale, (3, 99))
        holder = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire()\n"
                              "print('locked', flush=True); time.sleep(1.5)\n",
                              str(self.docs / ".cache" / "venv.lock"))
        try:
            start = time.monotonic()
            venv, _, _ = self.ensure(offline=True)
            self.assertLess(time.monotonic() - start, 1, "the fast path must not wait for the setup lock")
            self.assertTrue(stale.is_dir())
        finally:
            stop(holder)

    @POSIX_ONLY
    def test_the_environment_is_held_once_returned(self):
        venv, _, _ = self.ensure()
        self.assertFalse(run.FileLock(run.marker(venv)).acquire(blocking=False), "a cleanup could delete it")
        other = run.FileLock(run.marker(venv))
        self.assertTrue(other.acquire(shared=True, blocking=False))
        other.release()

    @POSIX_ONLY
    def test_a_broken_environment_in_use_is_not_cleared(self):
        fake_venv(self.venv, (3, 99))  # made by another Python version: not usable
        holder = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire(shared=True)\n"
                              "print('using', flush=True); time.sleep(1.5)\n", str(run.marker(self.venv)))
        try:
            with self.assertRaises(run.SetupError) as raised:
                self.ensure()
            self.assertIn("uses it", str(raised.exception))
            self.assertTrue(self.venv.is_dir())
        finally:
            stop(holder)

    def test_a_failed_install_leaves_no_stamp(self):
        with self.assertRaises(run.SetupError):
            self.ensure(pip=run.SetupError("pip failed"))
        self.assertIsNone(run.installed_digest(self.venv))

    def test_create_venv_clears_a_half_made_environment(self):
        with mock.patch.object(run, "run_child", return_value=1) as called, \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(run.SetupError):
            run.create_venv(self.venv)
        self.assertEqual(called.call_args.args[0][1:], ["-m", "venv", "--clear", str(self.venv)])
        self.assertFalse(self.venv.exists())

    def test_pip_never_installs_into_the_user_site(self):
        with mock.patch.object(run, "run_child", return_value=0) as called, \
                mock.patch.dict(os.environ, {"PIP_USER": "1"}):
            run.pip(self.venv, "check")
        self.assertEqual(called.call_args.kwargs["env"]["PIP_USER"], "0")


class CommandTest(unittest.TestCase):
    def test_commands_forward_their_arguments(self):
        python, docs = Path("/venv/bin/python"), Path("/repo/docs")
        self.assertEqual(run.command_for(python, docs, "check", ["--force"]),
                         [str(python), "-c", run.CHECK_BOOTSTRAP, "--force"])
        self.assertEqual(run.command_for(python, docs, "serve", ["-a", "0.0.0.0:8000"]),
                         [str(python), "-m", "properdocs", "serve", "--config-file", str(docs / "mkdocs.yml"),
                          "-a", "0.0.0.0:8000"])
        self.assertEqual(run.command_for(python, docs, "test", [])[:4], [str(python), "-m", "unittest", "discover"])

    def run_main(self, argv, code=0, child=None):
        with mock.patch.object(run, "choose_python", return_value=None), \
                mock.patch.object(run, "ensure_environment", return_value=(Path("/venv"), run.FileLock(Path("/x")))), \
                mock.patch.object(run, "run_child", return_value=code, side_effect=child) as called:
            return run.main(argv), called

    def test_check_results_go_through_and_docs_problems_come_only_from_check(self):
        for child, expected in ((0, 0), (run.CHECK_DOCS_PROBLEMS, 1), (2, 2), (3, 3), (1, 3), (7, 3), (-9, 3)):
            code, called = self.run_main(["--offline", "check", "--force"], child)
            self.assertEqual(code, expected, child)
            self.assertEqual(called.call_args.args[0][-1], "--force")

    def test_other_commands_return_their_code_but_a_signal_is_3(self):
        self.assertEqual(self.run_main(["test"], 1)[0], 1)
        self.assertEqual(self.run_main(["serve"], 0)[0], 0)
        self.assertEqual(self.run_main(["serve"], -15)[0], run.CHECK_FAILED)

    def test_ctrl_c_exits_3(self):
        self.assertEqual(self.run_main(["check"], child=KeyboardInterrupt)[0], run.CHECK_FAILED)
        with mock.patch.object(run, "choose_python", return_value=None), \
                mock.patch.object(run, "ensure_environment", side_effect=KeyboardInterrupt), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["check"]), run.CHECK_FAILED)

    def test_the_command_keeps_the_environment_lock(self):
        lock = run.FileLock(Path("/x"))
        with mock.patch.object(run, "choose_python", return_value=None), \
                mock.patch.object(run, "ensure_environment", return_value=(Path("/venv"), lock)), \
                mock.patch.object(run, "run_child", return_value=0) as called:
            run.main(["check"])
        self.assertEqual(called.call_args.kwargs["keep"], (lock,))

    def test_main_passes_offline_on(self):
        with mock.patch.object(run, "choose_python", return_value=None), \
                mock.patch.object(run, "ensure_environment", return_value=(Path("/venv"), run.FileLock(Path("/x")))) \
                as ensure, mock.patch.object(run, "run_child", return_value=0):
            run.main(["--offline", "check"])
        self.assertTrue(ensure.call_args.args[1])

    def test_the_exit_code_of_run_py_again_goes_through(self):
        with mock.patch.object(run, "choose_python", return_value=run.DOCS_PROBLEMS), \
                mock.patch.object(run, "ensure_environment") as ensure:
            self.assertEqual(run.main(["check"]), run.DOCS_PROBLEMS)
        ensure.assert_not_called()

    def test_setup_failure_exits_4(self):
        with mock.patch.object(run, "choose_python", return_value=None), \
                mock.patch.object(run, "ensure_environment", side_effect=run.SetupError("no venv")), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
        self.assertIn("no venv", err.getvalue())

    def test_any_setup_error_exits_4(self):
        for error in (OSError("disk full"), ValueError("bad .python-version"), RuntimeError("bug")):
            with mock.patch.object(run, "choose_python", return_value=None), \
                    mock.patch.object(run, "ensure_environment", side_effect=error), \
                    contextlib.redirect_stderr(io.StringIO()) as err:
                self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
            self.assertIn(type(error).__name__, err.getvalue())

    def test_unreadable_python_version_exits_4(self):
        with mock.patch.object(run, "required_version", side_effect=ValueError("not enough values")), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)

    def test_bad_arguments_exit_2(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            run.main(["build"])
        self.assertEqual(raised.exception.code, 2)


class BootstrapTest(unittest.TestCase):
    """The bootstrap that runs check.py, run in a real interpreter against stand-in check modules."""

    def bootstrap(self, check_source):
        with tempfile.TemporaryDirectory() as directory:
            Path(directory, "check.py").write_text(check_source)
            result = subprocess.run([sys.executable, "-c", run.CHECK_BOOTSTRAP, "--force"], cwd=directory,
                                    capture_output=True, text=True, check=False)
        return run.exit_code("check", result.returncode)

    def test_docs_problems(self):
        self.assertEqual(self.bootstrap("def main(argv):\n    return 1\n"), run.DOCS_PROBLEMS)

    def test_success_and_crash(self):
        self.assertEqual(self.bootstrap("def main(argv):\n    return 0 if argv == ['--force'] else 5\n"), 0)
        self.assertEqual(self.bootstrap("def main(argv):\n    return 3\n"), run.CHECK_FAILED)

    def test_an_import_error_of_check_is_not_a_docs_problem(self):
        self.assertEqual(self.bootstrap("def main(argv:\n"), run.CHECK_FAILED)
        self.assertEqual(self.bootstrap("import missing_module\n"), run.CHECK_FAILED)
        self.assertEqual(self.bootstrap("raise SystemExit(1)\n"), run.CHECK_FAILED)


class LockTest(unittest.TestCase):
    def test_another_process_waits_for_the_setup_lock_and_says_so(self):
        with tempfile.TemporaryDirectory() as directory:
            docs = Path(directory)
            holder = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire()\n"
                                  "print('locked', flush=True); time.sleep(1)\n", str(docs / ".cache" / "venv.lock"))
            try:
                start = time.monotonic()
                with contextlib.redirect_stdout(io.StringIO()) as out, run.setup_lock(docs):
                    waited = time.monotonic() - start
            finally:
                stop(holder)
            self.assertGreater(waited, 0.5)
            self.assertIn("waiting for another docs setup", out.getvalue())

    def test_lock_can_be_taken_again_once_released(self):
        with tempfile.TemporaryDirectory() as directory, contextlib.redirect_stdout(io.StringIO()) as out:
            with run.setup_lock(Path(directory)):
                pass
            with run.setup_lock(Path(directory)) as lock:
                self.assertTrue(lock.path.exists())
        self.assertEqual(out.getvalue(), "")

    @POSIX_ONLY
    def test_an_environment_in_use_in_another_process_is_not_deleted(self):
        with tempfile.TemporaryDirectory() as directory:
            docs = Path(directory)
            stale = docs / ".venv" / "py3.99-0123456789ab"
            fake_venv(stale, (3, 99))
            holder = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire(shared=True)\n"
                                  "print('using', flush=True); time.sleep(1)\n", str(run.marker(stale)))
            try:
                run.remove_stale_environments(docs, b"a==1\n", keep=None)
                self.assertTrue(stale.is_dir())
            finally:
                stop(holder)
            run.remove_stale_environments(docs, b"a==1\n", keep=None)
            self.assertFalse(stale.exists())

    @POSIX_ONLY
    def test_a_lock_taken_on_a_deleted_marker_is_taken_again_on_the_new_one(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "x.inuse"
            first = run.FileLock(path)
            first.acquire()
            waiter = start_holder("lock = run.FileLock(pathlib.Path(sys.argv[1])); print('start', flush=True)\n"
                                  "lock.acquire(shared=True); print(lock._same_file(lock.file), flush=True)\n"
                                  "time.sleep(1)\n", str(path))
            try:
                time.sleep(0.3)
                path.unlink()  # as a cleanup does, while it holds the lock
                first.release()
                self.assertEqual(waiter.stdout.readline().strip(), "True")
                self.assertFalse(run.FileLock(path).acquire(blocking=False))
            finally:
                stop(waiter)

    @POSIX_ONLY
    def test_a_child_keeps_the_lock_after_run_py_is_killed(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "x.inuse"
            parent = start_holder(
                "lock = run.FileLock(pathlib.Path(sys.argv[1])); lock.acquire(shared=True)\n"
                "print('ready', flush=True)\n"
                "run.run_child([sys.executable, '-c', 'import time; time.sleep(3)'], keep=(lock,))\n", str(path))
            try:
                time.sleep(0.5)
                parent.kill()
                parent.wait(timeout=10)
                self.assertFalse(run.FileLock(path).acquire(blocking=False), "the child still runs in the environment")
            finally:
                stop(parent)
            deadline, lock = time.monotonic() + 10, run.FileLock(path)
            while not lock.acquire(blocking=False):
                self.assertLess(time.monotonic(), deadline)
                time.sleep(0.2)
            lock.release()

    @POSIX_ONLY
    def test_sigterm_reaches_the_child_and_run_py_waits_for_it(self):
        parent = start_holder(
            "print('ready', flush=True)\n"
            "code = run.run_child([sys.executable, '-c', 'import time; time.sleep(30)'])\n"
            "print(code, flush=True)\n")
        try:
            time.sleep(0.5)
            parent.send_signal(signal.SIGTERM)
            self.assertEqual(parent.stdout.readline().strip(), str(-signal.SIGTERM))
        finally:
            stop(parent)


if __name__ == "__main__":
    unittest.main()
