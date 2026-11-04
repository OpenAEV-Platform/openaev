"""End-to-end test of hooks/checks.py: a strict build of a tiny site, with the engine check.py uses.

Run with: python3 docs/run.py test
"""

import json
import os
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

DOCS = Path(__file__).resolve().parents[1]

BUILD = """
import sys
from pathlib import Path
import check
try:
    check.mkdocs_build(Path(sys.argv[1]), Path(sys.argv[2]))
except check.DocsProblems:
    sys.exit(1)
"""


class FixtureSiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        root = Path(cls.tmp.name)
        (root / "docs").mkdir()
        (root / "mkdocs.yml").write_text(textwrap.dedent(f"""\
            site_name: Fixture
            theme:
              name: material
            plugins:
              - search
              - redirects:
                  redirect_maps:
                    'old.md': 'a.md'
                    'b.md': 'a.md'
            validation:
              links:
                anchors: warn
            hooks:
              - {json.dumps(str(DOCS / "hooks" / "checks.py"))}
            """))
        (root / "docs" / "index.md").write_text(textwrap.dedent("""\
            # Home

            [Markdown link, missing anchor](a.md#md-nope) and [fine](a.md#setup).

            <a href="a/#raw-nope">Raw link, missing anchor</a> <a href="a/#setup">raw, fine</a>

            <img src="assets/missing.png" alt="raw image, missing"> <img src="assets/ok.png" alt="raw image, fine">
            """))
        (root / "docs" / "assets").mkdir()
        (root / "docs" / "assets" / "ok.png").write_bytes(b"\x89PNG\r\n\x1a\n")
        (root / "docs" / "a.md").write_text("# A\n\n## Setup\n\nText.\n")
        (root / "docs" / "b.md").write_text("# B\n\nA page a redirect overwrites.\n")
        (root / "links.json").write_text(json.dumps({
            "base": "https://docs.openaev.io/latest/",
            "links": {"home": "", "through_redirect": "old/", "missing_anchor": "a/#key-nope", "fine": "a/#setup"},
        }))
        env = {**os.environ, "NO_MKDOCS_2_WARNING": "true", "DISABLE_MKDOCS_2_WARNING": "true"}
        result = subprocess.run(
            [sys.executable, "-c", BUILD, str(root), str(root / "site")],
            cwd=DOCS, env=env, capture_output=True, text=True, check=False,
        )
        cls.code = result.returncode
        cls.lines = [line for line in (result.stdout + result.stderr).splitlines() if line.startswith("WARNING")]

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def warned(self, *parts: str) -> bool:
        return any(all(part in line for part in parts) for line in self.lines)

    def test_strict_build_fails(self):
        self.assertEqual(self.code, 1, self.lines)

    def test_markdown_link_is_reported_once_by_the_engine(self):
        self.assertTrue(self.warned("a.md#md-nope"), self.lines)
        self.assertEqual(sum("md-nope" in line for line in self.lines), 1, self.lines)

    def test_raw_html_link_with_a_missing_anchor(self):
        self.assertTrue(self.warned("Page index.md: link a/#raw-nope", "no anchor #raw-nope"), self.lines)
        self.assertFalse(self.warned("#setup"), self.lines)

    def test_raw_html_image_missing(self):
        self.assertTrue(self.warned("image assets/missing.png written in HTML is not a file"), self.lines)
        self.assertFalse(self.warned("ok.png"), self.lines)

    def test_redirect_overwriting_a_page(self):
        self.assertTrue(self.warned("Redirect from b.md", "is also a page"), self.lines)

    def test_links_json_key_through_a_redirect(self):
        self.assertTrue(self.warned("key 'through_redirect': old/ is a redirect to a/: use a/"), self.lines)

    def test_links_json_key_with_a_missing_anchor(self):
        self.assertTrue(self.warned("key 'missing_anchor'", "no anchor #key-nope"), self.lines)
        self.assertFalse(self.warned("'fine'"), self.lines)


if __name__ == "__main__":
    unittest.main()
