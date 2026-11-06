"""Tests of the pure functions of hooks/checks.py.

Run with: python3 docs/run.py test
"""

import json
import unittest

from hooks import checks
from hooks.checks import PageInfo, Site

ROOT = checks.VERSION_ROOT


def site() -> Site:
    return Site(
        pages={
            "": PageInfo("index.md", ids={"welcome"}),
            "a/": PageInfo("a.md", ids={"intro", "setup"}),
            "b/c/": PageInfo("b/c.md", ids={"deep"}),
        },
        redirects={
            "old/": ROOT + "a/",
            "old-anchor/": ROOT + "a/#setup",
            "chain/": ROOT + "old/",
            "loop-1/": ROOT + "loop-2/",
            "loop-2/": ROOT + "loop-1/",
            "away/": "https://example.com/elsewhere",
            "out/": "https://docs.openaev.io/3.2.0/a/",
        },
        static_files={"assets/x.pdf", "assets/demo/index.html"},
    )


class SitePathTest(unittest.TestCase):
    def test_removes_dot_segments_as_a_browser_does(self):
        self.assertEqual(checks.site_path("https://docs.openaev.io/latest/a/../b/./c/"), "/latest/b/c/")
        self.assertEqual(checks.site_path("https://docs.openaev.io/latest/../../x"), "/x")

    def test_keeps_the_trailing_slash_of_a_folder(self):
        self.assertEqual(checks.site_path("https://docs.openaev.io/latest/a/b/.."), "/latest/a/")

    def test_decodes_the_path(self):
        self.assertEqual(checks.site_path("https://docs.openaev.io/latest/a%20b/"), "/latest/a b/")


class VersionPathTest(unittest.TestCase):
    def test_path_under_latest(self):
        self.assertEqual(checks.version_path(ROOT + "a/b/"), "a/b/")
        self.assertEqual(checks.version_path("https://docs.openaev.io/latest"), "")
        self.assertEqual(checks.version_path(ROOT), "")

    def test_outside_latest(self):
        self.assertIsNone(checks.version_path("https://docs.openaev.io/3.2.0/a/"))
        self.assertIsNone(checks.version_path(ROOT + "../a/"))
        self.assertIsNone(checks.version_path("https://docs.openaev.io/latestx/a/"))


class OutsideMessageTest(unittest.TestCase):
    def test_names_the_fixed_version(self):
        for url in ("https://docs.openaev.io/3.2.0/a/", "https://docs.openaev.io/2.260717.0/", "https://docs.openaev.io/v3/"):
            self.assertIn("fixed version", checks.outside_message(url), url)

    def test_other_paths_are_only_outside(self):
        for url in ("https://docs.openaev.io/stable/a/", "https://docs.openaev.io/a/", "https://docs.openaev.io/"):
            message = checks.outside_message(url)
            self.assertNotIn("fixed version", message, url)
            self.assertIn("is outside " + ROOT, message, url)


class ResolveTest(unittest.TestCase):
    def test_page_and_anchor(self):
        self.assertIsNone(site().resolve(ROOT + "a/#setup"))
        self.assertIsNone(site().resolve(ROOT))
        self.assertIsNone(site().resolve(ROOT + "b/c/index.html#deep"))

    def test_folder_without_its_slash(self):
        self.assertIsNone(site().resolve(ROOT + "a"))

    def test_missing_page_and_anchor(self):
        self.assertIn("no page /latest/nope/", site().resolve(ROOT + "nope/"))
        self.assertIn("no anchor #nope on /latest/a/ (a.md)", site().resolve(ROOT + "a/#nope"))

    def test_fixed_version_fails_and_says_why(self):
        self.assertIn("fixed version 3.2.0", site().resolve("https://docs.openaev.io/3.2.0/a/"))
        self.assertIn("is outside " + ROOT, site().resolve(ROOT + "../a/"))

    def test_static_file(self):
        self.assertIsNone(site().resolve(ROOT + "assets/x.pdf"))
        self.assertIn("is a file", site().resolve(ROOT + "assets/x.pdf#p"))

    def test_a_static_index_html_keeps_its_name(self):
        self.assertIsNone(site().resolve(ROOT + "assets/demo/index.html"))
        self.assertIsNotNone(site().resolve(ROOT + "assets/x.pdf/index.html"))

    def test_redirects_are_followed_with_their_anchor(self):
        self.assertIsNone(site().resolve(ROOT + "old/#intro"))
        self.assertIn("no anchor #nope", site().resolve(ROOT + "old/#nope"))
        self.assertIsNone(site().resolve(ROOT + "old-anchor/"))
        self.assertIsNone(site().resolve(ROOT + "chain/#setup"))

    def test_follow_tells_where_a_redirect_lands(self):
        self.assertEqual(site().follow(ROOT + "a/#intro"), (None, None))
        self.assertEqual(site().follow(ROOT + "old/#intro"), (None, "a/#intro"))
        self.assertEqual(site().follow(ROOT + "old-anchor/"), (None, "a/#setup"))
        self.assertEqual(site().follow(ROOT + "chain/"), (None, "a/"))

    def test_a_link_cannot_add_an_anchor_to_a_redirect_that_has_one(self):
        self.assertIn("redirects to #setup, a link cannot add #intro", site().resolve(ROOT + "old-anchor/#intro"))

    def test_redirect_loop_another_site_and_out_of_latest(self):
        self.assertIn("redirect loop", site().resolve(ROOT + "loop-1/"))
        self.assertIsNone(site().resolve(ROOT + "away/"))
        self.assertIn("redirects out of", site().resolve(ROOT + "out/"))


class LinkErrorsTest(unittest.TestCase):
    def test_checks_raw_html_links_and_absolute_markdown_links_only(self):
        s = site()
        s.pages["a/"].markdown_hrefs = ["../nope/", "#nope", ROOT + "b/c/#nope", "https://example.com/"]
        s.pages["a/"].other_hrefs = ["../b/c/#deep", "../b/c/#nope", "#intro", "#nope", "../nope.md", "/", "mailto:x@y.z"]
        errors = dict(s.link_errors("a/"))
        self.assertEqual(sorted(errors), sorted([ROOT + "b/c/#nope", "../b/c/#nope", "#nope", "../nope.md"]))

    def test_a_link_through_a_redirect_warns(self):
        s = site()
        s.pages["b/c/"].other_hrefs = ["../../old/#intro"]
        self.assertEqual(s.link_errors("b/c/"), [("../../old/#intro", "is a redirect to /latest/a/#intro: link it directly")])


class SplitByOriginTest(unittest.TestCase):
    def test_counts_duplicates(self):
        self.assertEqual(checks.split_by_origin(["a", "b", "a", "c"], ["a", "c"]), (["a", "c"], ["b", "a"]))


class KeyAndValueTest(unittest.TestCase):
    def test_keys(self):
        for key in ("home", "a", "teams_v2", "a1_b2_c3"):
            self.assertIsNone(checks.validate_key(key), key)
        for key in ("", "Home", "1a", "_a", "a_", "a__b", "a-b", "a.b", "home\n", " home", 3):
            self.assertIsNotNone(checks.validate_key(key), repr(key))

    def test_values(self):
        for value in ("", "#x", "a/", "a/b/#x", "a/#", "a%20b/", "a/#x-y_z.1~", "a/?q=1&r=2/"):
            self.assertIsNone(checks.validate_value(value), value)
        for value in ("a", "a/b#x", "/a/", "https://docs.openaev.io/latest/a/", "//docs.openaev.io/a/", "mailto:a", 1, None):
            self.assertIsNotNone(checks.validate_value(value), value)

    def test_values_hold_only_uri_characters(self):
        for value in ("a b/", "a/\n", "a/\t", "a/#x y", "é/", "a/%2", "a/%zz/", "a/<b>/", 'a"/', "a\\b/"):
            self.assertIsNotNone(checks.validate_value(value), repr(value))

    def test_query_is_part_of_the_path_rule(self):
        self.assertIsNotNone(checks.validate_value("a/?x=1"))


class LinksFileTest(unittest.TestCase):
    def test_valid_file(self):
        links, errors = checks.load_links(json.dumps({"base": ROOT, "links": {"a": "a/"}}))
        self.assertEqual((links, errors), ({"a": "a/"}, []))

    def test_shape_errors(self):
        for text in ("{", "[]", json.dumps({"base": ROOT}), json.dumps({"base": ROOT, "links": [], "x": 1}),
                     json.dumps({"base": "https://docs.openaev.io/3.2.0/", "links": {}}),
                     json.dumps({"base": ROOT, "links": []})):
            links, errors = checks.load_links(text)
            self.assertIsNone(links, text)
            self.assertTrue(errors, text)

    def test_duplicate_keys(self):
        links, errors = checks.load_links('{"base": "%s", "links": {"a": "a/", "a": "b/"}}' % ROOT)
        self.assertEqual(errors, ["key 'a' is defined more than once"])

    def test_errors_name_the_key(self):
        errors = checks.links_errors(site(), {"ok": "a/#setup", "Bad": "a/", "page": "nope/", "anchor": "a/#nope",
                                              "slash": "a"})
        self.assertEqual(len(errors), 4)
        for key in ("Bad", "page", "anchor", "slash"):
            self.assertTrue(any(f"'{key}'" in e for e in errors), key)

    def test_a_key_through_a_redirect_fails_and_names_the_page(self):
        self.assertEqual(checks.links_errors(site(), {"old": "old/#intro"}),
                         ["key 'old': old/#intro is a redirect to a/#intro: use a/#intro"])


class ImagesTest(unittest.TestCase):
    def test_local_image(self):
        self.assertEqual(checks.local_image("a/b/", "../img/x.png"), "/latest/a/img/x.png")
        for src in ("https://example.com/x.png", "data:image/png;base64,AA", "{{ x }}", ""):
            self.assertIsNone(checks.local_image("a/", src), src)


if __name__ == "__main__":
    unittest.main()
