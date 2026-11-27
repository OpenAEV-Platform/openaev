"""Tests of check-docs-urls.py. Run from the repository root: python3 -m unittest discover -s .github/scripts/tests"""

import contextlib
import importlib.util
import io
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-docs-urls.py"
spec = importlib.util.spec_from_file_location("check_docs_urls", SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

URL = "https://docs.openaev.io/latest/administration/parameters/"
APPLICATION_PROPERTIES = "openaev-api/src/main/resources/application.properties"


def lines(path, text):
    return [number for number, _, _ in guard.findings(path, text)]


def reasons(path, text):
    return {reason for _, reason, _ in guard.findings(path, text)}


class FindingsTest(unittest.TestCase):
    def test_flags_a_url_in_code(self):
        self.assertEqual(
            guard.findings("openaev-front/src/a.tsx", f"const a = '{URL}';\n"),
            [(1, guard.URL, f"const a = '{URL}';")],
        )

    def test_flags_every_line_and_numbers_them(self):
        self.assertEqual(lines("a.java", f"x\n{URL}\ny\nDOCS.OPENAEV.IO\n"), [2, 4])

    def test_flags_every_written_form_of_the_docs_host(self):
        for text in (
            "Docs.OpenAEV.io",
            "docs.openaev.com",
            r"/https:\/\/docs\.openaev\.io/",
            r"DOCS\.OPENAEV",
            "https://openaev.io/latest/x",
            r"openaev\.io/latest",
            r"'docs\x2eopenaev\x2eio'",
            r"'docs.openaev'",
            r"'docs\u{2e}openaev'",
            "docs&#46;openaev",
            "docs&#x2E;openaev",
            "https%3A%2F%2Fdocs%2eopenaev%2eio",
            "DOCS%2E",
        ):
            self.assertEqual(reasons("openaev-front/src/a.ts", text), {guard.URL}, text)

    def test_ignores_other_docs_hosts(self):
        for text in ("https://docs.python.org/3/", "import io.openaev.docs.DocsLinks;", "openaev.io/docs"):
            self.assertEqual(guard.findings("a.java", text), [], text)

    def test_flags_concatenation_onto_a_docs_link(self):
        for text in (
            'String a = DocsLinks.PARAMETERS + "#ai";',
            'String a = "#x"+DocsLinks.PARAMETERS;',
            "const a = docsUrl('teams') + '#players';",
            "const a = `${docsUrl('teams')}#players`;",
        ):
            self.assertEqual(reasons("openaev-front/src/a.ts", text), {guard.CONCATENATION}, text)

    def test_flags_concatenation_split_over_two_lines(self):
        text = 'String a =\n    DocsLinks.PARAMETERS\n    + "#ai";\n'
        self.assertEqual(lines("openaev-api/src/main/java/A.java", text), [2])

    def test_allows_a_docs_link_used_as_it_is(self):
        for text in (
            "String a = DocsLinks.PARAMETERS;",
            "link(DocsLinks.PARAMETERS, count + 1)",
            "const a = docsUrl('teams');",
            "const a = `${t('Read')} ${label}`;",
        ):
            self.assertEqual(guard.findings("a.ts", text), [], text)

    def test_flags_an_import_of_links_json_outside_the_front_helper(self):
        for text in (
            "import docsLinks from '../../../docs/links.json';",
            "import { links } from \"../docs/links.json\";",
            "import json from '../docs/links.json?raw';",
            "const m = await import('../docs/links.json');",
            "const m = require('../../docs/links.json');",
            "import '../docs/links.json';",
        ):
            self.assertEqual(reasons("openaev-front/src/a.ts", text), {guard.IMPORT}, text)

    def test_allows_the_import_in_the_front_helper_only(self):
        text = "import { base, links } from '../../../docs/links.json';\n"
        self.assertEqual(guard.findings(guard.LINKS_IMPORTER, text), [])
        self.assertEqual(lines(guard.LINKS_IMPORTER, f"const a = '{URL}';\n"), [1])

    def test_a_path_to_links_json_is_not_an_import(self):
        text = "fileURLToPath(new URL('../docs/links.json', import.meta.url))"
        self.assertEqual(guard.findings("openaev-front/vite.config.ts", text), [])

    def test_allows_the_documentation_sources(self):
        for path in (
            "docs/links.json", "docs/mkdocs.yml", "docs/requirements.in", "docs/requirements.txt",
            "docs/check.py", "docs/README.md", "docs/docs/index.md", "docs/docs/a/b.png",
            "docs/overrides/main.html", "docs/hooks/checks.py", "docs/tests/test_check.py",
        ):
            self.assertEqual(guard.findings(path, URL), [], path)

    def test_flags_other_files_of_docs(self):
        for path in ("docs/serve.sh", "docs/serve.ps1", "docs/.ideconf/a.xml", "docs/a/b.py", "docs/other.yml"):
            self.assertEqual(lines(path, URL), [1], path)

    def test_allows_github_and_markdown_outside_source_trees(self):
        for path in (".github/workflows/a.yml", "README.md", "x/CHANGELOG.MD", "openaev-maven-plugin/README.md"):
            self.assertEqual(guard.findings(path, URL), [], path)

    def test_flags_markdown_inside_a_source_tree(self):
        for path in ("openaev-api/src/main/java/io/openaev/collectors/README.md", "openaev-front/src/CLAUDE.md",
                     "src/a.md"):
            self.assertEqual(lines(path, URL), [1], path)

    def test_allows_the_backend_constants_of_the_map_and_their_test_only(self):
        for path in (
            "openaev-api/src/main/java/io/openaev/docs/DocsLinks.java",
            "openaev-api/src/test/java/io/openaev/docs/DocsLinksTest.java",
        ):
            self.assertEqual(guard.findings(path, f'{URL}\nDocsLinks.HOME + "x"\n'), [], path)
        for path in (
            "openaev-api/src/main/java/io/openaev/docs/Other.java",
            "openaev-front/src/__tests__/utils/docsUrl.test.ts",
            "openaev-maven-plugin/src/main/java/io/openaev/docs/CheckDocs.java",
        ):
            self.assertEqual(lines(path, URL), [1], path)

    def test_ignores_binary_files(self):
        self.assertEqual(guard.findings("openaev-front/src/static/logo.png", URL), [])

    def test_reads_svg_as_text(self):
        self.assertEqual(lines("openaev-front/src/static/logo.svg", f'<a href="{URL}"/>'), [1])

    def test_allows_comment_lines_of_application_properties(self):
        self.assertEqual(guard.findings(APPLICATION_PROPERTIES, f"# see also: {URL}\n  ! {URL}\n"), [])

    def test_flags_a_property_value(self):
        text = f"# fine: {URL}\nopenaev.docs={URL}\n"
        self.assertEqual(guard.findings(APPLICATION_PROPERTIES, text), [(2, guard.URL, f"openaev.docs={URL}")])

    def test_continuation_lines_are_part_of_the_value(self):
        text = f"a=first \\\n  # {URL}\nb=x \\\\\n# {URL}\n"
        # Line 2 continues the value of a (odd backslashes); line 4 is a comment (an even run ends b).
        self.assertEqual(lines(APPLICATION_PROPERTIES, text), [2])

    def test_a_comment_never_continues(self):
        self.assertEqual(guard.findings(APPLICATION_PROPERTIES, f"# note \\\n# {URL}\n"), [])

    def test_comments_of_other_properties_files_are_flagged(self):
        for path in ("a.properties", "openaev-api/src/main/resources/application-dev.properties.example",
                     "openaev-api/src/test/resources/application.properties"):
            self.assertEqual(lines(path, f"# {URL}\n"), [1], path)

    def test_comment_rule_is_only_for_properties_files(self):
        self.assertEqual(lines("a.yml", f"# {URL}\n"), [1])
        self.assertEqual(lines("a.py", f"# {URL}\n"), [1])

    def test_one_finding_per_line_and_reason(self):
        self.assertEqual(guard.findings("a.ts", f"'{URL}' + '{URL}'\n"), [(1, guard.URL, f"'{URL}' + '{URL}'")])


class RemovedNotRetiredTest(unittest.TestCase):
    BASE = {"links": {"a": "x/", "b": "y/#z", "c": "w/"}, "retired": []}

    def test_a_removed_value_must_be_retired(self):
        self.assertEqual(guard.removed_not_retired(self.BASE, {"links": {"a": "x/", "c": "w/"}}), ["y/#z"])

    def test_a_retired_value_passes(self):
        head = {"links": {"a": "x/", "c": "w/"}, "retired": ["y/#z"]}
        self.assertEqual(guard.removed_not_retired(self.BASE, head), [])

    def test_a_value_still_used_by_another_key_passes(self):
        head = {"links": {"a": "x/", "renamed": "y/#z", "c": "w/"}}
        self.assertEqual(guard.removed_not_retired(self.BASE, head), [])

    def test_a_moved_value_must_be_retired(self):
        head = {"links": {"a": "x/", "b": "new/#z", "c": "w/"}}
        self.assertEqual(guard.removed_not_retired(self.BASE, head), ["y/#z"])

    def test_a_deleted_map_retires_nothing(self):
        self.assertEqual(guard.removed_not_retired(self.BASE, {}), ["w/", "x/", "y/#z"])


class EscapeTest(unittest.TestCase):
    def test_workflow_command_escapes(self):
        self.assertEqual(guard.escape_data("100% a\r\nb"), "100%25 a%0D%0Ab")
        self.assertEqual(guard.escape_property("a:b,c%"), "a%3Ab%2Cc%25")


class GitRepositoryTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "test")
        self.git("config", "commit.gpgsign", "false")

    def git(self, *args):
        subprocess.run(["git", *args], cwd=self.root, check=True, capture_output=True)

    def write(self, path, text):
        file = self.root / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text)

    def links(self, links, retired=()):
        self.write("docs/links.json", json.dumps({"base": "https://docs.openaev.io/latest/", "links": links,
                                                  "retired": list(retired)}))

    def commit(self):
        self.git("add", "-A")
        self.git("commit", "-q", "-m", "c")

    def run_main(self, *argv, cwd=None):
        with contextlib.redirect_stdout(io.StringIO()) as out:
            status = guard.main(list(argv), cwd or self.root)
        return status, out.getvalue()


class MainTest(GitRepositoryTest):
    def test_main_lists_tracked_files_only(self):
        self.write("src/a.ts", f"const a = '{URL}';\n")
        self.write("src/untracked.ts", URL)
        self.write("docs/links.json", URL)
        self.git("add", "src/a.ts", "docs/links.json")
        status, out = self.run_main(cwd=self.root / "src")
        self.assertEqual(status, 1)
        self.assertIn("::error file=src/a.ts,line=1::", out)
        self.assertNotIn("untracked", out)
        self.write("src/a.ts", "const a = docsUrl('home');\n")
        self.assertEqual(self.run_main()[0], 0)


class BaseRefTest(GitRepositoryTest):
    def setUp(self):
        super().setUp()
        self.links({"a": "x/", "b": "y/"})
        self.commit()

    def test_passes_when_every_removed_link_is_retired(self):
        self.links({"a": "x/"}, retired=["y/"])
        status, out = self.run_main("--base-ref", "HEAD")
        self.assertEqual(status, 0, out)

    def test_fails_on_a_removed_link_that_is_not_retired(self):
        self.links({"a": "x/", "b": "moved/"})
        status, out = self.run_main("--base-ref", "HEAD")
        self.assertEqual(status, 1)
        self.assertIn("::error file=docs/links.json::'y/' was removed from links since HEAD", out)

    def test_reads_the_base_from_the_given_commit(self):
        self.links({"a": "x/"})
        self.commit()
        self.assertEqual(self.run_main("--base-ref", "HEAD")[0], 0)
        self.assertEqual(self.run_main("--base-ref", "HEAD^1")[0], 1)

    def test_skips_a_base_without_links_json(self):
        self.git("rm", "-q", "docs/links.json")
        self.commit()
        self.links({"a": "new/"})
        status, out = self.run_main("--base-ref", "HEAD")
        self.assertEqual(status, 0)
        self.assertIn("does not exist on HEAD", out)

    def test_an_unknown_base_is_an_error(self):
        self.assertEqual(self.run_main("--base-ref", "no-such-ref")[0], 2)

    def test_without_base_ref_removed_links_are_not_checked(self):
        self.links({})
        self.assertEqual(self.run_main()[0], 0)


class CheckTest(unittest.TestCase):
    def test_reads_files_and_skips_missing_and_allowed_ones(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs" / "links.json").write_text(URL)
            (root / "b.ts").write_text(f"ok\n'{URL}'\n")
            result = guard.check(root, ["docs/links.json", "b.ts", "deleted.ts"])
        self.assertEqual(result, [("b.ts", 2, guard.URL, f"'{URL}'")])


if __name__ == "__main__":
    unittest.main()
