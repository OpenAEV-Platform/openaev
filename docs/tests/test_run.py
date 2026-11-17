"""Tests of run.py: version check, environments, cleanup, --offline, exit codes and commands.

Run with: python3 docs/run.py test
"""

import contextlib
import io
import os
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

import run

VERSION = tuple(sys.version_info[:2])


def fake_venv(venv: Path, version=VERSION) -> None:
    python = run.venv_python(venv)
    python.parent.mkdir(parents=True)
    python.write_text("")
    (venv / "pyvenv.cfg").write_text(f"home = /usr/bin\nversion = {version[0]}.{version[1]}.3\n")


class VersionTest(unittest.TestCase):
    def test_reads_the_minimum_from_python_version(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / ".python-version").write_text("3.12\n")
            self.assertEqual(run.required_version(Path(directory)), (3, 12))

    def test_accepts_the_minimum_and_later(self):
        for version in ((3, 12, 0), (3, 13, 1), (4, 0, 0)):
            run.check_python(version, (3, 12))

    def test_rejects_an_older_python_and_says_which(self):
        with self.assertRaises(run.SetupError) as raised:
            run.check_python((3, 11, 9), (3, 12))
        self.assertIn("Python 3.12 or later", str(raised.exception))
        self.assertIn("this is 3.11", str(raised.exception))


class EnvironmentTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = Path(self.tmp.name)
        self.requirements = self.docs / "requirements.txt"
        self.requirements.write_text("mkdocs==1.6.1\n")
        self.digest = run.requirements_digest(self.requirements, VERSION)
        self.venv = run.environment_dir(self.docs, VERSION, self.digest)

    def tearDown(self):
        self.tmp.cleanup()

    def stamp(self, venv=None, digest=None):
        (venv or self.venv).joinpath(run.STAMP_NAME).write_text((digest or self.digest) + "\n")

    def ensure(self, offline=False, create=None, pip=None):
        def default_create(venv):
            fake_venv(venv)

        with mock.patch.object(run, "create_venv", side_effect=create or default_create) as created, \
                mock.patch.object(run, "pip", side_effect=pip) as piped, contextlib.redirect_stdout(io.StringIO()):
            venv = run.ensure_environment(self.docs, offline=offline)
        return venv, created, piped

    def test_one_environment_per_python_version_and_requirements(self):
        self.assertEqual(run.environment_dir(self.docs, (3, 13, 2), "ab" * 32), self.docs / ".venv" / f"py3.13-{'ab' * 6}")

    def test_venv_version_reads_pyvenv_cfg(self):
        fake_venv(self.venv, (3, 13))
        self.assertEqual(run.venv_version(self.venv), (3, 13))
        self.assertIsNone(run.venv_version(self.docs / "nothing"))

    def test_digest_depends_on_requirements_and_python(self):
        base = run.requirements_digest(self.requirements, (3, 12))
        self.assertNotEqual(base, run.requirements_digest(self.requirements, (3, 13)))
        self.requirements.write_text("mkdocs==1.6.2\n")
        self.assertNotEqual(base, run.requirements_digest(self.requirements, (3, 12)))

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
        venv, _, _ = self.ensure(create=lambda v: (calls.append("create"), fake_venv(v)),
                                 pip=lambda v, *args: calls.append(args[0]))
        self.assertEqual(calls, ["create", "install", "check"])
        self.assertEqual(run.installed_digest(venv), self.digest)

    def test_new_requirements_get_a_new_environment_and_leave_the_old_one_alone_while_in_use(self):
        fake_venv(self.venv)
        self.stamp()
        old = self.venv
        self.requirements.write_text("mkdocs==1.6.2\n")
        with run.in_use(old):
            if os.name != "nt":
                venv, created, _ = self.ensure()
                self.assertNotEqual(venv, old)
                created.assert_called_once_with(venv)
                self.assertTrue(old.is_dir(), "an environment in use must stay")
        # Once nothing uses it, the next setup deletes it.
        if os.name != "nt":
            (self.docs / "requirements.txt").write_text("mkdocs==1.6.3\n")
            self.ensure()
            self.assertFalse(old.exists())

    def test_stale_environments_are_the_ones_of_other_requirements_whatever_the_python(self):
        current = self.venv
        other_python = run.environment_dir(self.docs, (3, 99),
                                           run.requirements_digest(self.requirements, (3, 99)))
        stale = self.docs / ".venv" / "py3.99-0123456789ab"
        legacy = self.docs / ".venv" / "py3.11"
        for venv in (current, other_python, stale, legacy):
            fake_venv(venv)
        self.assertEqual(run.stale_environments(self.docs, self.requirements), [stale])

    def test_create_venv_clears_a_half_made_environment(self):
        with mock.patch.dict(sys.modules, {"ensurepip": mock.Mock()}), \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=1)) as called, \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(run.SetupError):
            run.create_venv(self.venv)
        self.assertEqual(called.call_args.args[0][1:], ["-m", "venv", "--clear", str(self.venv)])

    def test_a_failed_install_leaves_no_stamp(self):
        with self.assertRaises(run.SetupError):
            self.ensure(pip=run.SetupError("pip failed"))
        self.assertIsNone(run.installed_digest(self.venv))


class CommandTest(unittest.TestCase):
    def test_commands_forward_their_arguments(self):
        python, docs = Path("/venv/bin/python"), Path("/repo/docs")
        self.assertEqual(run.command_for(python, docs, "check", ["--force"]),
                         [str(python), "-c", run.CHECK_BOOTSTRAP, "--force"])
        self.assertEqual(run.command_for(python, docs, "serve", ["-a", "0.0.0.0:8000"]),
                         [str(python), str(docs / "toolchain.py"), "serve", "--config-file", str(docs / "mkdocs.yml"),
                          "-a", "0.0.0.0:8000"])
        self.assertEqual(run.command_for(python, docs, "test", [])[:4], [str(python), "-m", "unittest", "discover"])

    def run_main(self, argv, code=0):
        with mock.patch.object(run, "ensure_environment", return_value=Path("/venv")), \
                mock.patch.object(run, "in_use", return_value=contextlib.nullcontext()), \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=code)) as called:
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

    def test_main_passes_offline_on(self):
        with mock.patch.object(run, "ensure_environment", return_value=Path("/venv")) as ensure, \
                mock.patch.object(run, "in_use", return_value=contextlib.nullcontext()), \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=0)):
            run.main(["--offline", "check"])
        self.assertTrue(ensure.call_args.args[1])

    def test_setup_failure_exits_4(self):
        with mock.patch.object(run, "ensure_environment", side_effect=run.SetupError("no venv")), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
        self.assertIn("no venv", err.getvalue())

    def test_any_setup_error_exits_4(self):
        for error in (OSError("disk full"), ValueError("bad .python-version"), RuntimeError("bug"),
                      SyntaxError("broken toolchain.py")):
            with mock.patch.object(run, "ensure_environment", side_effect=error), \
                    contextlib.redirect_stderr(io.StringIO()) as err:
                self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
            self.assertIn(type(error).__name__, err.getvalue())

    def test_unreadable_python_version_exits_4(self):
        with mock.patch.object(run, "required_version", side_effect=ValueError("not enough values")), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)

    def test_too_old_python_exits_4(self):
        with mock.patch.object(run, "required_version", return_value=(99, 0)), \
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
    def test_another_process_waits_for_the_lock(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "venv.lock"
            holder = subprocess.Popen(
                [sys.executable, "-c",
                 "import sys, time, run\n"
                 "with run.exclusive_lock(__import__('pathlib').Path(sys.argv[1])):\n"
                 "    print('locked', flush=True)\n"
                 "    time.sleep(1)\n",
                 str(path)],
                cwd=run.DOCS, stdout=subprocess.PIPE, text=True,
            )
            try:
                self.assertEqual(holder.stdout.readline().strip(), "locked")
                start = time.monotonic()
                with run.exclusive_lock(path):
                    waited = time.monotonic() - start
            finally:
                holder.wait(timeout=10)
                holder.stdout.close()
            self.assertGreater(waited, 0.5)

    @unittest.skipIf(os.name == "nt", "no shared lock on Windows")
    def test_an_environment_in_use_in_another_process_is_not_deleted(self):
        with tempfile.TemporaryDirectory() as directory:
            docs = Path(directory)
            requirements = docs / "requirements.txt"
            requirements.write_text("a==1\n")
            stale = docs / ".venv" / "py3.99-0123456789ab"
            fake_venv(stale, (3, 99))
            holder = subprocess.Popen(
                [sys.executable, "-c",
                 "import sys, time, pathlib, run\n"
                 "with run.in_use(pathlib.Path(sys.argv[1])):\n"
                 "    print('using', flush=True)\n"
                 "    time.sleep(1)\n",
                 str(stale)],
                cwd=run.DOCS, stdout=subprocess.PIPE, text=True,
            )
            try:
                self.assertEqual(holder.stdout.readline().strip(), "using")
                run.remove_stale_environments(docs, requirements)
                self.assertTrue(stale.is_dir())
            finally:
                holder.wait(timeout=10)
                holder.stdout.close()
            run.remove_stale_environments(docs, requirements)
            self.assertFalse(stale.exists())

    def test_lock_can_be_taken_again_once_released(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sub" / "venv.lock"
            with run.exclusive_lock(path):
                pass
            with run.exclusive_lock(path):
                self.assertTrue(path.exists())


if __name__ == "__main__":
    unittest.main()
