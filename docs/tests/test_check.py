"""Tests of check.py: fingerprint, skip, build engine errors and exit codes. The build is faked.

Run with: python3 docs/run.py test
"""

import contextlib
import io
import os
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock

import check

MKDOCS_YML = """\
extra:
  flags: !ENV [DOCS_FEATURE_FLAGS]
plugins:
  - git-committers:
      enabled: !ENV [DOCS_GIT_PLUGINS, OTHER_FALLBACK, true]
  - x:
      token: !ENV MY_TOKEN
"""


def make_docs(root: Path) -> Path:
    for folder in ("docs", "hooks", "overrides"):
        (root / folder).mkdir()
    (root / "docs" / "index.md").write_text("# Home\n")
    (root / "hooks" / "checks.py").write_text("")
    (root / "overrides" / "main.html").write_text("")
    (root / "mkdocs.yml").write_text(MKDOCS_YML)
    for name in ("requirements.txt", "links.json", "check.py", ".python-version"):
        (root / name).write_text(name)
    return root


class EnvNamesTest(unittest.TestCase):
    def test_reads_every_env_tag_form(self):
        self.assertEqual(check.env_names(MKDOCS_YML),
                         ["DOCS_FEATURE_FLAGS", "DOCS_GIT_PLUGINS", "MY_TOKEN", "OTHER_FALLBACK", "true"])

    def test_the_real_mkdocs_yml(self):
        names = check.env_names((check.DOCS / "mkdocs.yml").read_text(encoding="utf-8"))
        self.assertIn("DOCS_GIT_PLUGINS", names)
        self.assertIn("DOCS_FEATURE_FLAGS", names)


class FingerprintTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = make_docs(Path(self.tmp.name))

    def tearDown(self):
        self.tmp.cleanup()

    def test_stable_for_the_same_inputs(self):
        self.assertEqual(check.fingerprint(self.docs, {}), check.fingerprint(self.docs, {}))

    def test_changes_with_every_input(self):
        before = check.fingerprint(self.docs, {})
        for change in ("docs/index.md", "docs/new.md", "mkdocs.yml", "overrides/main.html", "hooks/checks.py",
                       "hooks/new.py", "requirements.txt", "links.json", "check.py", ".python-version",
                       "main.py", "snippets/part.md"):
            path = self.docs / change
            path.parent.mkdir(exist_ok=True)
            path.write_text(path.read_text() + "changed" if path.exists() else "new")
            after = check.fingerprint(self.docs, {})
            self.assertNotEqual(before, after, change)
            before = after

    def test_ignores_environments_caches_output_and_tests(self):
        before = check.fingerprint(self.docs, {})
        for folder in (".venv/py3.11", ".cache/check", "site", "tests", "hooks/__pycache__"):
            (self.docs / folder).mkdir(parents=True)
            (self.docs / folder / "x").write_text("x")
        self.assertEqual(before, check.fingerprint(self.docs, {}))

    def test_changes_with_the_environment_mkdocs_yml_reads(self):
        base = check.fingerprint(self.docs, {})
        for name in ("DOCS_GIT_PLUGINS", "DOCS_FEATURE_FLAGS", "MY_TOKEN"):
            self.assertNotEqual(base, check.fingerprint(self.docs, {name: "x"}), name)
        self.assertEqual(base, check.fingerprint(self.docs, {"UNRELATED": "x"}))

    def test_changes_with_the_python_environment(self):
        base = check.fingerprint(self.docs, {})
        with mock.patch.object(check.sys, "prefix", "/elsewhere"):
            self.assertNotEqual(base, check.fingerprint(self.docs, {}))


class FakeAbort(Exception):
    pass


class FakeConfigurationError(Exception):
    pass


def fake_engine(load_config=None, build=None):
    config = SimpleNamespace(plugins=mock.Mock())
    return SimpleNamespace(
        name="mkdocs",
        load_config=load_config or (lambda *args, **kwargs: config),
        build=build or (lambda config: None),
        Abort=FakeAbort,
        ConfigurationError=FakeConfigurationError,
    )


class MkdocsBuildTest(unittest.TestCase):
    def build(self, engine):
        with contextlib.redirect_stderr(io.StringIO()):
            check.mkdocs_build(Path("/docs"), Path("/site"), engine)

    def test_success(self):
        self.build(fake_engine())

    def test_strict_abort_and_configuration_errors_are_docs_problems(self):
        for engine in (fake_engine(build=mock.Mock(side_effect=FakeAbort("2 warnings"))),
                       fake_engine(load_config=mock.Mock(side_effect=FakeAbort("config error"))),
                       fake_engine(load_config=mock.Mock(side_effect=FakeConfigurationError("bad")))):
            with self.assertRaises(check.DocsProblems):
                self.build(engine)

    def test_other_errors_go_through_and_main_exits_3(self):
        engine = fake_engine(build=mock.Mock(side_effect=RuntimeError("plugin crash")))
        with self.assertRaises(RuntimeError):
            self.build(engine)
        with mock.patch.object(check, "load_engine", return_value=engine), \
                mock.patch.object(check, "fingerprint", return_value="new"), \
                mock.patch.dict(os.environ), contextlib.redirect_stderr(io.StringIO()), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(check.main(["--force"]), check.CHECK_FAILED)

    def test_docs_problems_make_main_exit_1(self):
        engine = fake_engine(build=mock.Mock(side_effect=FakeAbort("2 warnings")))
        with tempfile.TemporaryDirectory() as directory, \
                mock.patch.object(check, "load_engine", return_value=engine), \
                mock.patch.object(check, "DOCS", make_docs(Path(directory))), \
                mock.patch.dict(os.environ), contextlib.redirect_stderr(io.StringIO()), \
                contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(check.main(["--force"]), check.DOCS_PROBLEMS)


class RunTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.docs = make_docs(Path(self.tmp.name))
        self.stamp = self.docs / ".cache" / "check" / "fingerprint"
        self.sites = []

    def tearDown(self):
        self.tmp.cleanup()

    def build_ok(self, docs, site_dir):
        self.sites.append(site_dir)
        self.assertTrue(site_dir.is_dir())

    def run_quietly(self, **kwargs):
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            return check.run(self.docs, **kwargs)

    def test_success_writes_the_fingerprint_and_removes_the_site(self):
        self.assertEqual(self.run_quietly(force=False, builder=self.build_ok), check.OK)
        self.assertEqual(self.stamp.read_text().strip(), check.fingerprint(self.docs, os.environ))
        self.assertFalse(self.sites[0].exists())
        self.assertEqual([p.name for p in self.stamp.parent.iterdir()], ["fingerprint"])

    def test_skips_when_nothing_changed_unless_forced(self):
        self.run_quietly(force=False, builder=self.build_ok)
        self.assertEqual(self.run_quietly(force=False, builder=self.build_ok), check.OK)
        self.assertEqual(len(self.sites), 1)
        self.run_quietly(force=True, builder=self.build_ok)
        self.assertEqual(len(self.sites), 2)

    def test_rebuilds_after_a_change(self):
        self.run_quietly(force=False, builder=self.build_ok)
        (self.docs / "docs" / "index.md").write_text("# Changed\n")
        self.run_quietly(force=False, builder=self.build_ok)
        self.assertEqual(len(self.sites), 2)

    def test_each_run_builds_into_its_own_directory(self):
        self.run_quietly(force=True, builder=self.build_ok)
        self.run_quietly(force=True, builder=self.build_ok)
        self.assertNotEqual(self.sites[0], self.sites[1])

    def test_docs_problems_exit_1_and_keep_no_fingerprint(self):
        def build(docs, site_dir):
            self.sites.append(site_dir)
            raise check.DocsProblems()

        self.assertEqual(self.run_quietly(force=False, builder=build), check.DOCS_PROBLEMS)
        self.assertFalse(self.stamp.exists())
        self.assertFalse(self.sites[0].exists())

    def test_a_failed_check_does_not_skip_the_next_one(self):
        self.run_quietly(force=False, builder=self.build_ok)
        (self.docs / "links.json").write_text("broken")

        def build(docs, site_dir):
            raise check.DocsProblems()

        self.assertEqual(self.run_quietly(force=False, builder=build), check.DOCS_PROBLEMS)
        self.assertEqual(self.run_quietly(force=False, builder=build), check.DOCS_PROBLEMS)


class MainTest(unittest.TestCase):
    def main(self, argv, **patch):
        with mock.patch.dict(os.environ), mock.patch.multiple(check, **patch), \
                contextlib.redirect_stderr(io.StringIO()) as err:
            return check.main(argv), err.getvalue()

    def test_a_crash_exits_3_never_1(self):
        code, err = self.main([], run=mock.Mock(side_effect=RuntimeError("boom")))
        self.assertEqual(code, check.CHECK_FAILED)
        self.assertIn("RuntimeError: boom", err)

    def test_a_missing_toolchain_exits_3(self):
        code, _ = self.main([], run=mock.Mock(side_effect=ModuleNotFoundError("No module named 'mkdocs'")))
        self.assertEqual(code, check.CHECK_FAILED)

    def test_passes_the_result_of_run_through(self):
        for code in (check.OK, check.DOCS_PROBLEMS):
            self.assertEqual(self.main(["--force"], run=mock.Mock(return_value=code))[0], code)

    def test_defaults_the_git_plugins_off_without_leaking_into_the_environment(self):
        seen = {}
        os.environ.pop("DOCS_GIT_PLUGINS", None)

        def run(docs, force):
            seen["value"] = os.environ.get("DOCS_GIT_PLUGINS")
            return check.OK

        self.main([], run=run)
        self.assertEqual(seen["value"], "false")
        self.assertNotIn("DOCS_GIT_PLUGINS", os.environ)

    def test_bad_arguments_exit_2(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as raised:
            check.main(["--bogus"])
        self.assertEqual(raised.exception.code, 2)


class WriteAtomicallyTest(unittest.TestCase):
    def test_replaces_the_file_and_leaves_no_temporary_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "sub" / "fingerprint"
            check.write_atomically(path, "a\n")
            check.write_atomically(path, "b\n")
            self.assertEqual(path.read_text(), "b\n")
            self.assertEqual([p.name for p in path.parent.iterdir()], ["fingerprint"])


if __name__ == "__main__":
    unittest.main()
