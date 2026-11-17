# OpenAEV Documentation Space

[![Website](https://img.shields.io/badge/website-openaev.io-blue.svg)](https://openaev.io)
[![Slack Status](https://img.shields.io/badge/slack-3K%2B%20members-4A154B)](https://community.filigran.io)

## Introduction

This is the main repository of the OpenAEV Documentation space. The online version is available directly on [docs.openaev.io](https://docs.openaev.io).

## Development (local setup)

### Prerequisites

* Python, at least the version of `.python-version` (3.11), with its `venv` module. On Debian and
  Ubuntu, install it with `sudo apt install python3-venv`.

`run.py` does the rest, from any folder and on any platform. It runs every command in a virtual
environment under `.venv`, named after the Python version and the digest of `requirements.txt`
(`.venv/py3.11-<digest>`), and creates it on first use with pip. When `requirements.txt` changes, it
creates a new environment beside the old one, so a running check or `serve` keeps its own. On
Linux and macOS, it then deletes the old environments that no running command uses; on Windows
they stay, so delete `.venv` from time to time. Run it with `python3` on Linux and macOS, and
`py -3` or `python` on Windows. `--offline`, before the command, fails instead of installing.

### Serve locally

From the root of the repository:

```shell
docs/serve.sh          # Linux, macOS
docs\serve.ps1         # Windows (PowerShell)
```

They pick a Python as the backend build and `yarn check-docs-links` do. `OPENAEV_DOCS_PYTHON`, when
it is set, must be usable, or they stop and name it. Otherwise they take the first usable of
`py -3` (Windows), `python3`, `python` and `python3.N`, from `python3.20` down to the version of
`.python-version`. Usable means it runs, has the `venv` and `ensurepip` modules, and is at least the
version of `.python-version`. They print the one they chose, then run `<python> docs/run.py serve`,
which serves the docs at http://localhost:8000 and rebuilds them on every change. Options go to
`serve`.

`serve` asks the GitHub API for the contributors of every page. To skip that, and the git dates,
set `DOCS_GIT_PLUGINS=false`: `DOCS_GIT_PLUGINS=false docs/serve.sh`, or
`$env:DOCS_GIT_PLUGINS = "false"` before `docs\serve.ps1` in PowerShell.

The site is built with the engine mike deploys with: ProperDocs, the continuation of MkDocs 1.x,
which mkdocs-redirects installs. It reads the same `mkdocs.yml` and runs the same plugins.

### Check the docs

Every build and `serve` checks the site and prints each problem as a warning:

* the Markdown links between pages and their anchors, through the `validation` of `mkdocs.yml`,
* the rest through `hooks/checks.py`: every page is built and in the search index, every image of
  a page is still in the built page, images written in raw HTML exist, links to `docs.openaev.io`
  and links written in raw HTML resolve to a page and anchor without going through a redirect, no
  redirect overwrites a page, page paths use `-` and never `_`,
* `links.json`: every link of the application to the docs, and the paths released versions open.

Links must point to the latest docs, `https://docs.openaev.io/latest/...` or a relative link. A link
to a fixed version, such as `https://docs.openaev.io/3.2.0/...`, fails on purpose: it never follows
the docs, and the check cannot see the page it points to.

`check.py` builds with `--strict`, so any of these warnings fails it. Run it before pushing a docs
change:

```shell
python3 docs/run.py check
```

The backend build (Maven) and `yarn check-docs-links` in `openaev-front` run this same command, and
so does the Docs Check of the CI, which blocks the merge of a pull request. It builds without the
git plugins into a temporary directory of its own, so several runs can go at the same time. It
skips the build when nothing changed since its last success, which it records in
`.cache/check/fingerprint`. The inputs are:

* every file under `docs/`, except the `.venv/`, `.cache/`, `site/` and `tests/` folders at its top
  and `__pycache__/` folders anywhere,
* `openaev-api/src/main/java/io/openaev/rest/settings/PreviewFeature.java`, where `main.py` reads
  the preview features,
* the environment variables `mkdocs.yml` reads through `!ENV`, and `DOCS_FEATURE_FLAGS`, which
  `main.py` reads,
* the Python it runs with, and its environment.

`--force` builds anyway. It exits with 0 when the docs are fine or nothing changed, 1 when the docs
have problems (the warnings above its last line), 2 on a bad argument, 3 when the check itself
could not run (crash of `check.py` or of a plugin, a plugin missing from the environment, or the
command was killed), and 4 when `run.py` could not set the environment up (Python too old, `venv`
missing, install failed, or `--offline` while an install is needed). The backend build fails only
on 1: on any other failure it prints a warning and goes on. Maven skips it with
`-Dopenaev.docs.check.skip=true`, and in CI, where the Docs Check runs it.

The tests of `run.py`, `check.py` and the hook run with `python3 docs/run.py test`.

### Links from the application: `links.json`

The backend and the frontend never write a `docs.openaev.io` URL: they open the docs by a key of
`links.json`, the backend through the `DocsLinks` constants, which `DocsLinksTest` keeps equal to
the map, and the frontend through its typed import of the file. Each key maps to a path relative to
`base`, the URL of a page (ending with `/`) and optionally an anchor of that page:

```json
{
  "base": "https://docs.openaev.io/latest/",
  "links": {
    "teams": "usage/build/people/#teams"
  },
  "retired": [
    "usage/teams_and_players_and_organizations/#teams"
  ]
}
```

Keys are snake_case words of lowercase letters and digits, `[a-z][a-z0-9]*(_[a-z0-9]+)*`. Values
are relative, never start with `/`, hold only URI characters (letters, digits, `-._~!$&'()*+,;=:@/?#`
and `%XX` escapes: no space), and their path, before any `#anchor`, is empty or ends with `/`.
`DocsLinksTest` applies the same rules. A value must point to the page itself, not to a redirect.

`retired` lists the paths released versions of the application open: the values of the keys at
the time, and the raw URLs of the releases from before `links.json`. Released versions keep opening
them, so each must still lead to a page and anchor of the site, through a redirect of `mkdocs.yml`
if needed. When you move or rename a page or a heading a key points to:

1. add a redirect from the old page in `mkdocs.yml`, when the page moved,
2. move the old path of the key to `retired`, if it is not there yet,
3. point the key at the new path.

The check fails until every key and every retired path resolves. Keep the retired paths: remove
one only when no supported release opens it any more.

A CI guard rejects any `docs.openaev.io` URL in tracked files outside `docs/`, Markdown files,
`.github/` and the comment lines of `openaev-api/src/main/resources/application.properties`, which
ships with the release and which `DocsLinksTest` checks. It also allows `DocsLinks.java` and the
tests that hold the URLs they expect (`DocsLinksTest` and the test of the front `docsUrl` helper).

### Dependencies

`requirements.in` lists the direct dependencies, and `requirements.txt` pins them with everything
they pull in, for every platform and for the Python of `.python-version` and later. Renovate keeps
both up to date. To try a new version, change its pin in `requirements.in` and regenerate
`requirements.txt` with the `uv pip compile` command written at its top, which needs
[uv](https://docs.astral.sh/uv/getting-started/installation/). The next `run.py` command installs
it. Never edit `requirements.txt` by hand.

The lock has no hashes: mike installs from a commit of its git repository, which pip cannot hash,
and pip refuses a partly hashed file.

The minimum Python lives in two places, which change together, by hand: `.python-version` and the
`--python-version` of the command at the top of `requirements.txt`. Everything else reads
`.python-version`: `run.py`, the serve scripts, the backend build, `yarn check-docs-links` and the
CI.

## Deploy the documentation

### Update the source

Committing on the main branch does not impact (for now) the deployed documentation. Commits must follow the repository's Conventional Commits convention (e.g. `docs: update simulation page (#1234)`).

### Deploy and update the current version

From `docs/`, in the environment `run.py` creates: run `python3 run.py check` once, then activate
it, so that mike finds the engine it runs by name. Its folder is `.venv/py<version>-<digest>`; with
the Python 3.11 of `.python-version`, it is the only `.venv/py3.11-*` folder once `run.py` cleaned
the old ones. On Windows, activate it with `.venv\py3.11-<digest>\Scripts\Activate.ps1`.

```shell
$ . .venv/py3.11-*/bin/activate
```

With the right version number (e.g. 3.3.X):
```shell
$ mike deploy --push [version]
```

### Deploy a new stable version

With the right version number (e.g. 3.3.X), update the `latest` tag:
```shell
$ mike deploy --push --update-aliases [version] latest
```

## Useful commands

List known versions, in the activated environment:
```shell
$ mike list
```
