"""Tests of the pure functions of hooks/checks.py.

Run with: python3 docs/run.py test
"""

import json
import unittest

from hooks import checks
from hooks.checks import PageInfo, Redirect, Site

BASE = checks.LINKS_BASE


def site() -> Site:
    return Site(
        pages={
            "": PageInfo("index.md", "", ids={"welcome"}),
            "a/": PageInfo("a.md", "a/", ids={"intro", "setup"}),
            "b/c/": PageInfo("b/c.md", "b/c/", ids={"deep"}),
            "café/": PageInfo("café.md", "caf%C3%A9/", ids={"menu"}),
        },
        redirects={
            "old/": Redirect("old.md", "a.md", page="a/"),
            "old-anchor/": Redirect("old-anchor.md", "a.md#setup", page="a/", fragment="setup"),
            "old-bad-anchor/": Redirect("old-bad-anchor.md", "a.md#nope", page="a/", fragment="nope"),
            "hidden/": Redirect("hidden.md", "preview.md"),
            "away/": Redirect("away.md", "https://example.com/elsewhere", external=True),
        },
        static_files={"assets/x.pdf", "assets/demo/index.html", "index.html.bak"},
    )


class TargetTest(unittest.TestCase):
    def test_page_and_anchor(self):
        self.assertEqual(site().follow("a/", "setup"), (None, None))
        self.assertEqual(site().follow(""), (None, None))
        self.assertEqual(site().follow("b/c/index.html", "deep"), (None, None))
        self.assertEqual(site().follow("index.html", "welcome"), (None, None))

    def test_index_html_is_stripped_only_as_a_whole_segment(self):
        self.assertIsNone(site().target("a/xindex.html"))
        self.assertEqual(site().target("b/c/index.html"), "b/c/")

    def test_folder_without_its_slash(self):
        self.assertEqual(site().follow("a"), (None, None))

    def test_missing_page_and_anchor(self):
        self.assertIn("no page nope/", site().follow("nope/")[0])
        self.assertIn("no anchor #nope on a/ (a.md)", site().follow("a/", "nope")[0])

    def test_static_file(self):
        self.assertEqual(site().follow("assets/x.pdf"), (None, None))
        self.assertIn("is a file", site().follow("assets/x.pdf", "p")[0])

    def test_a_static_index_html_keeps_its_name(self):
        self.assertEqual(site().follow("assets/demo/index.html"), (None, None))
        self.assertIsNotNone(site().follow("assets/x.pdf/index.html")[0])

    def test_redirects_are_followed_with_their_anchor(self):
        self.assertEqual(site().follow("old/", "intro"), (None, "a/#intro"))
        self.assertIn("no anchor #nope", site().follow("old/", "nope")[0])
        self.assertEqual(site().follow("old-anchor/"), (None, "a/#setup"))

    def test_a_link_cannot_add_an_anchor_to_a_redirect_that_has_one(self):
        self.assertIn("redirects to #setup, a link cannot add #intro", site().follow("old-anchor/", "intro")[0])

    def test_a_redirect_to_another_site_or_to_a_missing_page_is_an_error(self):
        self.assertIn("redirects to another site, https://example.com/elsewhere", site().follow("away/")[0])
        self.assertIn("not a page of the site", site().follow("hidden/")[0])


class RedirectErrorsTest(unittest.TestCase):
    def test_every_redirect_anchor_is_checked(self):
        self.assertEqual(site().redirect_errors(),
                         ["Redirect from old-bad-anchor.md to a.md#nope: no anchor #nope on a.md"])


class LinkErrorsTest(unittest.TestCase):
    def test_checks_raw_html_links_and_flags_absolute_docs_urls(self):
        s = site()
        s.pages["a/"].markdown_hrefs = ["../nope/", "#nope", BASE + "b/c/#deep", "https://example.com/"]
        s.pages["a/"].other_hrefs = ["../b/c/#deep", "../b/c/#nope", "#intro", "#nope", "../nope.md", "mailto:x@y.z",
                                     "{{ config.site_url }}", "%7B%7B%20x%20%7D%7D/", "https://docs.openaev.io/3.2.0/a/",
                                     "/latest/a/", "../caf%C3%A9/#menu"]
        errors = dict(s.link_errors("a/"))
        self.assertEqual(sorted(errors), sorted([BASE + "b/c/#deep", "../b/c/#nope", "#nope", "../nope.md",
                                                 "https://docs.openaev.io/3.2.0/a/", "/latest/a/"]))
        self.assertIn("link it relatively", errors[BASE + "b/c/#deep"])
        self.assertIn("link it relatively", errors["https://docs.openaev.io/3.2.0/a/"])

    def test_relative_links_resolve_against_the_encoded_page_url(self):
        s = site()
        s.pages["café/"].other_hrefs = ["#menu", "../a/#setup", "#nope"]
        self.assertEqual([href for href, _ in s.link_errors("café/")], ["#nope"])

    def test_a_link_through_a_redirect_warns(self):
        s = site()
        s.pages["b/c/"].other_hrefs = ["../../old/#intro"]
        self.assertEqual(s.link_errors("b/c/"), [("../../old/#intro", "is a redirect to a/#intro: link it directly")])


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
        document, errors = checks.load_links(json.dumps({"base": BASE, "links": {"a": "a/"}}))
        self.assertEqual((document, errors), ({"links": {"a": "a/"}, "retired": []}, []))

    def test_valid_file_with_retired_paths(self):
        document, errors = checks.load_links(json.dumps({"base": BASE, "links": {}, "retired": ["old/", "x"]}))
        self.assertEqual((document["retired"], errors), (["old/", "x"], []))

    def test_shape_errors(self):
        for text in ("{", "[]", json.dumps({"base": BASE}), json.dumps({"base": BASE, "links": [], "x": 1}),
                     json.dumps({"base": "https://docs.openaev.io/3.2.0/", "links": {}}),
                     json.dumps({"base": BASE, "links": []}),
                     json.dumps({"base": BASE, "links": {}, "retired": "old/"}),
                     json.dumps({"base": BASE, "links": {}, "other": []})):
            links, errors = checks.load_links(text)
            self.assertIsNone(links, text)
            self.assertTrue(errors, text)

    def test_duplicate_keys(self):
        links, errors = checks.load_links('{"base": "%s", "links": {"a": "a/", "a": "b/"}}' % BASE)
        self.assertEqual(errors, ["key 'a' is defined more than once"])

    def test_errors_name_the_key(self):
        errors = checks.links_errors(site(), {"ok": "a/#setup", "Bad": "a/", "page": "nope/", "anchor": "a/#nope",
                                              "slash": "a", "encoded": "caf%C3%A9/#menu"})
        self.assertEqual(len(errors), 4, errors)
        for key in ("Bad", "page", "anchor", "slash"):
            self.assertTrue(any(f"'{key}'" in e for e in errors), key)

    def test_duplicate_retired_paths(self):
        _, errors = checks.load_links(json.dumps({"base": BASE, "links": {}, "retired": ["a/", "b/", "a/"]}))
        self.assertEqual(errors, ["retired path 'a/' is listed more than once"])

    def test_retired_paths_may_go_through_redirects_but_must_resolve(self):
        retired = ["old/#intro", "old-anchor/", "a", "a/#setup", "caf%C3%A9/"]
        self.assertEqual(checks.retired_errors(site(), retired), [])

    def test_retired_path_errors_name_the_path(self):
        errors = checks.retired_errors(site(), ["nope/", "old/#nope", "/a/", "a b/", 3,
                                                "https://docs.openaev.io/latest/a/", "away/"])
        self.assertEqual(len(errors), 7, errors)
        self.assertIn("retired path 'nope/': no page nope/", errors[0])
        self.assertIn("retired path 'old/#nope': no anchor #nope on a/", errors[1])
        for error, path in zip(errors[2:6], ('"/a/"', '"a b/"', "3", '"https://docs.openaev.io/latest/a/"'), strict=True):
            self.assertIn(f"retired path {path}", error)
        self.assertIn("retired path 'away/': away/ redirects to another site", errors[6])

    def test_a_key_through_a_redirect_fails_and_names_the_page(self):
        self.assertEqual(checks.links_errors(site(), {"old": "old/#intro"}),
                         ["key 'old': old/#intro is a redirect to a/#intro: use a/#intro"])

    def test_a_key_through_a_redirect_to_another_site_fails(self):
        self.assertIn("redirects to another site", checks.links_errors(site(), {"away": "away/"})[0])


class ImagesTest(unittest.TestCase):
    def test_local_image(self):
        self.assertEqual(checks.local_image("a/b/", "../img/x.png"), "a/img/x.png")
        self.assertEqual(checks.local_image("caf%C3%A9/", "x%20y.png"), "café/x y.png")
        for src in ("https://example.com/x.png", "data:image/png;base64,AA", "{{ x }}", "",
                    "https://docs.openaev.io/latest/x.png"):
            self.assertIsNone(checks.local_image("a/", src), src)


if __name__ == "__main__":
    unittest.main()
