"""Tests of check-docs-urls.py. Run from the repository root: python3 -m unittest discover -s .github/scripts/tests"""

import contextlib
import importlib.util
import io
import subprocess
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "check-docs-urls.py"
spec = importlib.util.spec_from_file_location("check_docs_urls", SCRIPT)
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

URL = "https://docs.openaev.io/latest/administration/parameters/"


class FindingsTest(unittest.TestCase):
    def test_flags_a_url_in_code(self):
        self.assertEqual(guard.findings("openaev-front/src/a.tsx", f"const a = '{URL}';\n"), [(1, f"const a = '{URL}';")])

    def test_flags_every_line_and_numbers_them(self):
        text = f"x\n{URL}\ny\nDOCS.OPENAEV.IO\n"
        self.assertEqual([n for n, _ in guard.findings("a.java", text)], [2, 4])

    def test_allows_docs_github_and_markdown(self):
        for path in ("docs/links.json", "docs/docs/index.md", ".github/workflows/a.yml", "README.md", "x/CHANGELOG.MD"):
            self.assertEqual(guard.findings(path, URL), [], path)

    def test_allows_the_generated_backend_constants_only(self):
        self.assertEqual(guard.findings("openaev-api/src/main/java/io/openaev/docs/DocsLinks.java", URL), [])
        self.assertEqual(len(guard.findings("openaev-api/src/main/java/io/openaev/docs/Other.java", URL)), 1)

    def test_allows_the_code_that_reads_the_map(self):
        for path in (
            "openaev-maven-plugin/src/main/java/io/openaev/docs/DocsLinksGenerator.java",
            "openaev-maven-plugin/src/test/java/io/openaev/docs/GenerateDocsLinksTest.java",
            "openaev-front/src/__tests__/utils/docsUrl.test.ts",
        ):
            self.assertEqual(guard.findings(path, URL), [], path)
        self.assertEqual(len(guard.findings("openaev-maven-plugin/src/main/java/io/openaev/ocsf/A.java", URL)), 1)

    def test_ignores_binary_files(self):
        self.assertEqual(guard.findings("openaev-front/src/static/logo.png", URL), [])

    def test_reads_svg_as_text(self):
        self.assertEqual(len(guard.findings("openaev-front/src/static/logo.svg", f'<a href="{URL}"/>')), 1)

    def test_allows_comment_lines_of_properties_files(self):
        text = f"# see also: {URL}\n  ! {URL}\n"
        self.assertEqual(guard.findings("openaev-api/src/main/resources/application.properties", text), [])

    def test_flags_a_property_value(self):
        text = f"# fine: {URL}\nopenaev.docs={URL}\n"
        self.assertEqual(guard.findings("a.properties", text), [(2, f"openaev.docs={URL}")])

    def test_continuation_lines_are_part_of_the_value(self):
        text = f"a=first \\\n  # {URL}\nb=x \\\\\n# {URL}\n"
        # Line 2 continues the value of a (odd backslashes); line 4 is a comment (an even run ends b).
        self.assertEqual([n for n, _ in guard.findings("a.properties", text)], [2])

    def test_a_comment_never_continues(self):
        self.assertEqual(guard.findings("a.properties", f"# note \\\n# {URL}\n"), [])

    def test_comment_rule_is_only_for_properties_files(self):
        self.assertEqual(len(guard.findings("a.yml", f"# {URL}\n")), 1)
        self.assertEqual(len(guard.findings("a.py", f"# {URL}\n")), 1)


class EscapeTest(unittest.TestCase):
    def test_workflow_command_escapes(self):
        self.assertEqual(guard.escape_data("100% a\r\nb"), "100%25 a%0D%0Ab")
        self.assertEqual(guard.escape_property("a:b,c%"), "a%3Ab%2Cc%25")


class MainTest(unittest.TestCase):
    def git(self, root, *args):
        subprocess.run(["git", *args], cwd=root, check=True, capture_output=True)

    def test_main_lists_tracked_files_only(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.git(root, "init", "-q")
            (root / "src").mkdir()
            (root / "src" / "a.ts").write_text(f"const a = '{URL}';\n")
            (root / "src" / "untracked.ts").write_text(URL)
            (root / "docs").mkdir()
            (root / "docs" / "links.json").write_text(URL)
            self.git(root, "add", "src/a.ts", "docs/links.json")
            with contextlib.redirect_stdout(io.StringIO()) as out:
                self.assertEqual(guard.main(root / "src"), 1)
            self.assertIn("::error file=src/a.ts,line=1::", out.getvalue())
            self.assertNotIn("untracked", out.getvalue())
            (root / "src" / "a.ts").write_text("const a = DOCS_LINKS.home;\n")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(guard.main(root), 0)


class CheckTest(unittest.TestCase):
    def test_reads_files_and_skips_missing_and_allowed_ones(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "docs").mkdir()
            (root / "docs" / "a.json").write_text(URL)
            (root / "b.ts").write_text(f"ok\n'{URL}'\n")
            result = guard.check(root, ["docs/a.json", "b.ts", "deleted.ts"])
        self.assertEqual(result, [("b.ts", 2, f"'{URL}'")])


if __name__ == "__main__":
    unittest.main()
