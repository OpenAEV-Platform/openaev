"""Tests of run.py: version check, environment stamp, --offline and the commands it runs.

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


def fake_venv(venv: Path, version=(3, 12)) -> None:
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
        (self.docs / "requirements.txt").write_text("mkdocs==1.6.1\n")
        self.venv = run.environment_dir(self.docs, run.sys.version_info)

    def tearDown(self):
        self.tmp.cleanup()

    def stamp(self):
        digest = run.requirements_digest(self.docs / "requirements.txt", run.sys.version_info)
        (self.venv / run.STAMP_NAME).write_text(digest + "\n")

    def test_one_environment_per_python_version(self):
        self.assertEqual(run.environment_dir(self.docs, (3, 13, 2)), self.docs / ".venv" / "py3.13")

    def test_venv_version_reads_pyvenv_cfg(self):
        fake_venv(self.venv, (3, 13))
        self.assertEqual(run.venv_version(self.venv), (3, 13))
        self.assertIsNone(run.venv_version(self.docs / "nothing"))

    def test_digest_depends_on_requirements_and_python(self):
        requirements = self.docs / "requirements.txt"
        base = run.requirements_digest(requirements, (3, 12))
        self.assertNotEqual(base, run.requirements_digest(requirements, (3, 13)))
        requirements.write_text("mkdocs==1.6.2\n")
        self.assertNotEqual(base, run.requirements_digest(requirements, (3, 12)))

    def test_up_to_date_environment_is_used_as_it_is(self):
        fake_venv(self.venv, run.sys.version_info[:2])
        self.stamp()
        with mock.patch.object(run, "create_venv") as create, mock.patch.object(run, "pip") as pip:
            self.assertEqual(run.ensure_environment(self.docs, offline=True), run.venv_python(self.venv))
        create.assert_not_called()
        pip.assert_not_called()

    def test_offline_fails_when_an_install_is_needed(self):
        fake_venv(self.venv, run.sys.version_info[:2])
        with self.assertRaises(run.SetupError) as raised:
            run.ensure_environment(self.docs, offline=True)
        self.assertIn("--offline", str(raised.exception))

    def test_missing_environment_is_created_then_installed_and_stamped(self):
        calls = []

        def create(venv):
            calls.append("create")
            fake_venv(venv, run.sys.version_info[:2])

        with mock.patch.object(run, "create_venv", side_effect=create), \
                mock.patch.object(run, "pip", side_effect=lambda venv, *args: calls.append(args[0])), \
                contextlib.redirect_stdout(io.StringIO()):
            run.ensure_environment(self.docs, offline=False)
        self.assertEqual(calls, ["create", "install", "check"])
        self.assertEqual(run.installed_digest(self.venv),
                         run.requirements_digest(self.docs / "requirements.txt", run.sys.version_info))

    def test_changed_requirements_recreate_the_environment(self):
        fake_venv(self.venv, run.sys.version_info[:2])
        self.stamp()
        (self.docs / "requirements.txt").write_text("mkdocs==1.6.2\n")
        with mock.patch.object(run, "create_venv") as create, mock.patch.object(run, "pip") as pip, \
                contextlib.redirect_stdout(io.StringIO()):
            run.ensure_environment(self.docs, offline=False)
        create.assert_called_once_with(self.venv)
        self.assertEqual([c.args[1] for c in pip.call_args_list], ["install", "check"])

    def test_create_venv_clears_the_old_environment(self):
        with mock.patch.dict(sys.modules, {"ensurepip": mock.Mock()}), \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=1)) as called, \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(run.SetupError):
            run.create_venv(self.venv)
        self.assertEqual(called.call_args.args[0][1:], ["-m", "venv", "--clear", str(self.venv)])

    def test_a_failed_install_leaves_no_stamp(self):
        fake_venv(self.venv, run.sys.version_info[:2])
        with mock.patch.object(run, "pip", side_effect=run.SetupError("pip failed")), \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(run.SetupError):
            run.ensure_environment(self.docs, offline=False)
        self.assertIsNone(run.installed_digest(self.venv))


class CommandTest(unittest.TestCase):
    def test_commands_forward_their_arguments(self):
        python, docs = Path("/venv/bin/python"), Path("/repo/docs")
        self.assertEqual(run.command_for(python, docs, "check", ["--force"]),
                         [str(python), str(docs / "check.py"), "--force"])
        self.assertEqual(run.command_for(python, docs, "serve", ["-a", "0.0.0.0:8000"]),
                         [str(python), str(docs / "toolchain.py"), "serve", "--config-file", str(docs / "mkdocs.yml"),
                          "-a", "0.0.0.0:8000"])
        self.assertEqual(run.command_for(python, docs, "test", [])[:4], [str(python), "-m", "unittest", "discover"])

    def test_main_returns_the_exit_code_of_the_command(self):
        for code in (0, 1, 3):
            with mock.patch.object(run, "ensure_environment", return_value=Path("/venv/bin/python")), \
                    mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=code)) as called:
                self.assertEqual(run.main(["--offline", "check", "--force"]), code)
            self.assertEqual(called.call_args.args[0][-1], "--force")

    def test_main_passes_offline_on(self):
        with mock.patch.object(run, "ensure_environment", return_value=Path("/venv/bin/python")) as ensure, \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=0)):
            run.main(["--offline", "check"])
        self.assertTrue(ensure.call_args.args[1])

    def test_setup_failure_exits_4(self):
        with mock.patch.object(run, "ensure_environment", side_effect=run.SetupError("no venv")), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
        self.assertIn("no venv", err.getvalue())

    def test_any_setup_error_exits_4(self):
        for error in (OSError("disk full"), ValueError("bad .python-version"), RuntimeError("bug")):
            with mock.patch.object(run, "ensure_environment", side_effect=error), \
                    contextlib.redirect_stderr(io.StringIO()) as err:
                self.assertEqual(run.main(["check"]), run.SETUP_FAILED)
            self.assertIn(type(error).__name__, err.getvalue())

    def test_unreadable_python_version_exits_4(self):
        with mock.patch.object(run, "required_version", side_effect=ValueError("not enough values")), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)

    def test_a_command_killed_by_a_signal_exits_3(self):
        with mock.patch.object(run, "ensure_environment", return_value=Path("/venv/bin/python")), \
                mock.patch.object(run.subprocess, "run", return_value=mock.Mock(returncode=-9)):
            self.assertEqual(run.main(["check"]), run.CHECK_FAILED)

    def test_too_old_python_exits_4(self):
        with mock.patch.object(run, "required_version", return_value=(99, 0)), \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(run.main(["check"]), run.SETUP_FAILED)

    def test_bad_arguments_exit_2(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            run.main(["build"])
        self.assertEqual(raised.exception.code, 2)


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

    def test_lock_can_be_taken_again_once_released(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sub" / "venv.lock"
            with run.exclusive_lock(path):
                pass
            with run.exclusive_lock(path):
                self.assertTrue(path.exists())


if __name__ == "__main__":
    unittest.main()
