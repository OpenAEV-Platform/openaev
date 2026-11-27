"""End-to-end test of hooks/checks.py and preview_features.py: a strict build of a tiny site, with the
engine check.py uses. Each check has a case that only it reports, so removing a check fails a test.

Run with: python3 docs/run.py test
"""

import json
import os
import re
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


# A hook of the fixture that breaks the site the way a faulty plugin would, after the engine and
# the search plugin, before hooks/checks.py looks at it.
BREAKING_HOOK = """
import json, re
from pathlib import Path
from mkdocs.plugins import event_priority

def on_post_page(output, page, config):
    if page.file.src_uri == "img.md":
        output = re.sub(r'<img[^>]*dropped[.]png[^>]*>', '', output)
    return output

@event_priority(-50)
def on_post_build(config):
    site = Path(config.site_dir)
    (site / "no-html" / "index.html").unlink()
    index = site / "search" / "search_index.json"
    data = json.loads(index.read_text(encoding="utf-8"))
    data["docs"] = [doc for doc in data["docs"] if not doc["location"].startswith("unindexed/")]
    index.write_text(json.dumps(data), encoding="utf-8")
"""


def a_preview_feature() -> str:
    """A constant of the backend PreviewFeature enum, which main.py hides unless DOCS_FEATURE_FLAGS shows it."""
    enum = DOCS.parent / "openaev-api/src/main/java/io/openaev/rest/settings/PreviewFeature.java"
    return sorted(re.findall(r"^\s*([A-Z][A-Z0-9_]*)\(", enum.read_text(encoding="utf-8"), re.MULTILINE))[0]


class FixtureSiteTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        root = Path(cls.tmp.name)
        docs = root / "docs"
        docs.mkdir()
        (root / "breaking.py").write_text(BREAKING_HOOK)
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
                    'old-preview.md': 'preview.md'
                    'bad-anchor.md': 'a.md#redirect-nope'
                    'away.md': 'https://example.com/'
            validation:
              links:
                anchors: warn
            hooks:
              - {json.dumps(str(DOCS / "preview_features.py"))}
              - breaking.py
              - {json.dumps(str(DOCS / "hooks" / "checks.py"))}
            """))
        (docs / "index.md").write_text(textwrap.dedent("""\
            # Home

            [Markdown link, missing anchor](a.md#md-nope) and [fine](a.md#setup).

            <a href="a/#raw-nope">Raw link, missing anchor</a> <a href="a/#setup">raw, fine</a>
            <a href="caf%C3%A9/#menu">encoded, fine</a> <a href="{{ templated }}/">templated</a>

            <img src="assets/missing.png" alt="raw image, missing"> <img src="assets/ok.png" alt="raw image, fine">

            [Absolute link](https://docs.openaev.io/latest/a/)
            """))
        (docs / "assets").mkdir()
        for name in ("ok.png", "kept.png", "dropped.png"):
            (docs / "assets" / name).write_bytes(b"\x89PNG\r\n\x1a\n")
        (docs / "a.md").write_text("# A\n\n## Setup\n\nText.\n")
        (docs / "b.md").write_text("# B\n\nA page a redirect overwrites.\n")
        (docs / "café.md").write_text("# Café\n\n## Menu\n\nText.\n")
        (docs / "some_page.md").write_text("# Underscore\n")
        (docs / "img.md").write_text("# Images\n\n![kept](assets/kept.png)\n\n![dropped](assets/dropped.png)\n")
        (docs / "no-html.md").write_text("# No HTML\n")
        (docs / "unindexed.md").write_text("# Unindexed\n")
        (docs / "excluded.md").write_text("---\nsearch:\n  exclude: true\n---\n# Excluded from search\n")
        (docs / "preview.md").write_text(f"---\npreview_feature: {a_preview_feature()}\n---\n# Preview\n")
        (root / "links.json").write_text(json.dumps({
            "base": "https://docs.openaev.io/latest/",
            "links": {"home": "", "through_redirect": "old/", "missing_anchor": "a/#key-nope", "fine": "a/#setup",
                      "encoded": "caf%C3%A9/#menu", "away": "away/"},
            "retired": ["old/#setup", "a/", "gone/", "old/#retired-nope", "away/"],
        }))
        env = {**os.environ, "NO_MKDOCS_2_WARNING": "true", "DISABLE_MKDOCS_2_WARNING": "true",
               "DOCS_FEATURE_FLAGS": ""}
        result = subprocess.run(
            [sys.executable, "-c", BUILD, str(root), str(root / "site")],
            cwd=DOCS, env=env, capture_output=True, text=True, check=False,
        )
        cls.code = result.returncode
        cls.output = result.stdout + result.stderr
        cls.lines = [line for line in cls.output.splitlines() if line.startswith("WARNING")]

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def warned(self, *parts: str) -> bool:
        return any(all(part in line for part in parts) for line in self.lines)

    def test_strict_build_fails(self):
        self.assertEqual(self.code, 1, self.output)

    def test_markdown_link_is_reported_once_by_the_engine(self):
        self.assertTrue(self.warned("a.md#md-nope"), self.lines)
        self.assertEqual(sum("md-nope" in line for line in self.lines), 1, self.lines)

    def test_raw_html_link_with_a_missing_anchor(self):
        self.assertTrue(self.warned("Page index.md: link a/#raw-nope", "no anchor #raw-nope"), self.lines)
        self.assertFalse(self.warned("#setup."), self.lines)

    def test_encoded_and_templated_links_pass(self):
        self.assertFalse(self.warned("caf"), self.lines)
        self.assertFalse(self.warned("templated"), self.lines)

    def test_absolute_docs_link(self):
        self.assertTrue(self.warned("Page index.md: link https://docs.openaev.io/latest/a/", "link it relatively"),
                        self.lines)

    def test_raw_html_image_missing(self):
        self.assertTrue(self.warned("image assets/missing.png written in HTML is not a file"), self.lines)
        self.assertFalse(self.warned("ok.png"), self.lines)

    def test_image_dropped_from_the_built_page(self):
        self.assertTrue(self.warned("Page img.md: image assets/dropped.png is not an <img> of the built page"),
                        self.lines)
        self.assertFalse(self.warned("kept.png"), self.lines)

    def test_underscore_in_a_page_path(self):
        self.assertTrue(self.warned("Page some_page.md has an underscore"), self.lines)

    def test_page_without_its_html_file(self):
        self.assertTrue(self.warned("Page no-html.md has no HTML file"), self.lines)

    def test_search_index_lists_every_page_not_excluded(self):
        self.assertTrue(self.warned("Page unindexed.md is missing from the search index"), self.lines)
        self.assertFalse(self.warned("excluded.md"), self.lines)

    def test_redirect_overwriting_a_page(self):
        self.assertTrue(self.warned("Redirect from b.md", "is also a page"), self.lines)

    def test_redirect_to_a_page_a_preview_feature_hides(self):
        self.assertTrue(self.warned("Redirect from old-preview.md: preview.md is not a page of the built site"),
                        self.lines)
        # preview_features.py runs before mkdocs-redirects lists the pages, which then warns too.
        self.assertTrue(self.warned("Redirect target 'preview.md' does not exist"), self.lines)

    def test_redirect_with_a_missing_anchor(self):
        self.assertTrue(self.warned("Redirect from bad-anchor.md to a.md#redirect-nope: no anchor #redirect-nope"),
                        self.lines)

    def test_links_json_key_through_a_redirect(self):
        self.assertTrue(self.warned("key 'through_redirect': old/ is a redirect to a/: use a/"), self.lines)

    def test_links_json_through_a_redirect_to_another_site(self):
        self.assertTrue(self.warned("key 'away'", "redirects to another site"), self.lines)
        self.assertTrue(self.warned("retired path 'away/'", "redirects to another site"), self.lines)

    def test_retired_paths_may_redirect_but_must_resolve(self):
        self.assertTrue(self.warned("retired path 'gone/': no page gone/"), self.lines)
        self.assertTrue(self.warned("retired path 'old/#retired-nope'", "no anchor #retired-nope"), self.lines)
        self.assertFalse(self.warned("'old/#setup'"), self.lines)
        self.assertFalse(self.warned("retired path 'a/'"), self.lines)

    def test_links_json_key_with_a_missing_anchor(self):
        self.assertTrue(self.warned("key 'missing_anchor'", "no anchor #key-nope"), self.lines)
        self.assertFalse(self.warned("'fine'"), self.lines)
        self.assertFalse(self.warned("'encoded'"), self.lines)


if __name__ == "__main__":
    unittest.main()
