# OpenAEV Documentation Space

[![Website](https://img.shields.io/badge/website-openaev.io-blue.svg)](https://openaev.io)
[![Slack Status](https://img.shields.io/badge/slack-3K%2B%20members-4A154B)](https://community.filigran.io)

## Introduction

This is the main repository of the OpenAEV Documentation space. The online version is available directly on [docs.openaev.io](https://docs.openaev.io).


## Development (local setup)

### Prerequisites

* Python, at least the version of `.python-version`, with its `venv` module. On Debian and Ubuntu,
  install it with `sudo apt install python3-venv` (or `python3.N-venv` for the `python3.N` you use).
* git: pip installs mike from its git repository, and the git plugins read the history of the pages.

`run.py` does the rest, from any folder and on any platform: `python3 docs/run.py check`,
`python3 docs/run.py serve` or `python3 docs/run.py test` on Linux and macOS, `py -3 docs\run.py ...`
or `python docs\run.py ...` on Windows. `--offline`, before the command, fails instead of
installing anything.

### Which Python runs the docs toolchain

This is the one rule for the Python of the docs. The backend build, `yarn check-docs-links` in
`openaev-front`, `serve.sh` and `serve.ps1` only start a Python, and `run.py` chooses the one that
runs the toolchain.

The callers start the Python chosen explicitly, if any: `-Dopenaev.docs.python=<path or name>` for
the backend build, otherwise the environment variable `OPENAEV_DOCS_PYTHON` (a relative path is
taken from the folder the command runs in). Otherwise they start the first of `py -3` (Windows
only), `python3` and `python` that answers `--version`, which skips missing commands and the
Microsoft Store stub of Windows. They run `<python> docs/run.py [--offline] <command> [args]`.

`run.py` starts on any Python 3.6 or later, then checks it. A Python is usable when it is at least
the version of `.python-version` and has the `venv` and `ensurepip` modules.

1. When the Python running `run.py` is usable, it runs the command.
2. Otherwise, when `OPENAEV_DOCS_PYTHON` is set, it stops with exit code 4 and says why: the
   Python chosen explicitly is never replaced.
3. Otherwise it looks for `python3.N`, from the minor version of `.python-version` up to
   `python3.20` (and `py -3.N` on Windows), the version CI checks the lock on first, runs `run.py` again with the first usable one,
   with the same arguments, and returns its exit code. With none, it stops with exit code 4 and
   lists what it tried.

So `python3 docs/run.py check` works even when `python3` is too old or has no `venv` module, as long
as a usable `python3.N` is installed. `run.py` prints the Python it switched to.

### Python environments

`run.py` runs every command in a virtual environment under `.venv`, named after the Python version
and the digest of `requirements.txt` (`.venv/py3.N-<digest>`), and creates it on first use with
pip, under a lock: a second run waits and says so. When `requirements.txt` changes, it creates a new
environment beside the old one, so a running check or `serve` keeps its own. It then deletes the
environments of other digests that no running command uses: on Linux and macOS, a running command
holds a lock on `<environment>.inuse`; on Windows, the folder of an environment whose Python runs
cannot be renamed, and `run.py` deletes only the ones it could rename.

Before `run.py`, the docs used a single virtual environment in `docs/.venv` itself (`bin/` or
`Scripts/`, `lib/`, `pyvenv.cfg` directly in it). If you still have one, delete `docs/.venv`:
`run.py` creates what it needs again.

### Serve locally

From the root of the repository:

```shell
docs/serve.sh          # Linux, macOS
docs\serve.ps1         # Windows (PowerShell)
```

They start a Python as described in [Which Python runs the docs toolchain](#which-python-runs-the-docs-toolchain),
then run `<python> docs/run.py serve`, which serves the docs at http://localhost:8000 and rebuilds
them on every change. Options go to `properdocs serve`.

`serve` asks the GitHub API for the contributors of every page. To skip that, and the git dates,
set `DOCS_GIT_PLUGINS=false`: `DOCS_GIT_PLUGINS=false docs/serve.sh`, or
`$env:DOCS_GIT_PLUGINS = "false"` before `docs\serve.ps1` in PowerShell.

The site is built with ProperDocs, the continuation of MkDocs 1.x and the engine mike deploys
with. It reads the same `mkdocs.yml` and runs the same plugins.

### Check the docs

Every build and `serve` checks the site and prints each problem as a warning:

* the Markdown links between pages and their anchors, through the `validation` of `mkdocs.yml`,
* the rest through `hooks/checks.py`: every page is built and in the search index (unless its front
  matter excludes it from search), every image of a page is still in the built page, images
  written in raw HTML exist, relative links written in raw HTML resolve to a page and anchor
  without going through a redirect, no link or image uses an absolute `docs.openaev.io` URL (link
  it relatively, so that MkDocs checks it), every redirect of `mkdocs.yml` leads to a built page and
  anchor and overwrites no page, page paths use `-` and never `_`,
* `links.json`: every link of the application to the docs, and the paths released versions open.

The hook does not check `<video>`, `<source>`, `<iframe>` or `srcset`, nor links to other sites.

`check.py` builds with `--strict`, so any of these warnings fails it. Run it before pushing a docs
change:

```shell
python3 docs/run.py check
```

The backend build (Maven) and `yarn check-docs-links` in `openaev-front` run this same command, and
so does the Docs Check of the CI, which blocks the merge of a pull request. It builds without the
git plugins, whatever `DOCS_GIT_PLUGINS` says: `--git-plugins` turns them on, as the nightly Docs
Check does. It builds into `.cache/check/site-<process id>`, so several runs can go at the same
time, and deletes the folders that checks which died left behind. It skips the build when nothing
changed since its last success, which it records in `.cache/check/fingerprint`, and records nothing
when a file changed while it built. The inputs are:

* every file under `docs/`, except `.DS_Store` and `Thumbs.db` files, `__pycache__/` folders
  anywhere, and these folders at the top of `docs/`: `.venv/`, `.cache/`, `site/`, `tests/`,
  `.git/`, `.dev_venv/`, `venv/`, `.venv-build/`, `.idea/`, `.vscode/`, `.mypy_cache/`,
  `.ruff_cache/` and `.pytest_cache/`,
* `openaev-api/src/main/java/io/openaev/rest/settings/PreviewFeature.java`, where `main.py` reads
  the preview features,
* the environment variables `mkdocs.yml` reads through `!ENV`, and `DOCS_FEATURE_FLAGS`, which
  `main.py` reads,
* the Python it runs with, and its environment.

`--force` builds anyway. `run.py check` exits with:

* 0 when the docs are fine or nothing changed,
* 1 when the docs have problems: the warnings above its last line, an invalid `mkdocs.yml`
  included, such as the name of a plugin, extension or theme that is not installed (`run.py`
  installs exactly `requirements.txt`, so the name is wrong),
* 2 on a bad argument,
* 3 when the check itself could not run: crash of `check.py` or of a plugin, or the command was
  killed or interrupted (Ctrl+C),
* 4 when `run.py` could not set the environment up: no usable Python, `venv` missing, install
  failed, or `--offline` while an install is needed.

The backend build fails only on 1: on any other failure it prints a warning and goes on. Maven
skips it with `-Dopenaev.docs.check.skip=true`, and in CI, where the Docs Check runs it.

The tests of `run.py`, `check.py` and the hooks run with `python3 docs/run.py test`.

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
and `%XX` escapes: no space), and their path, before any `#anchor`, is empty or ends with `/`. A
value must point to the page itself, not to a redirect.

`retired` lists the paths released versions of the application open: the values of the keys at
the time, and the raw URLs of the releases from before `links.json`. Released versions keep opening
them, so each must still lead to a page and anchor of this site, through a redirect of
`mkdocs.yml` if needed, but not through a redirect to another site. Keep the retired paths: remove
one only when no supported release opens it any more. The CI checks that every path a pull request
removes from `links` is in `retired`.

When a page a key points to moves or is renamed:

1. add a redirect from the old page to the new one in `mkdocs.yml`,
2. move the old path of every key that points to it to `retired`, if it is not there yet,
3. point the key at the new path,
4. update the matching `DocsLinks` constant of the backend, which `DocsLinksTest` compares with the
   map.

When a heading a key or a retired path points to is renamed, a redirect cannot help: redirects of
`mkdocs.yml` are per page. Keep the old anchor on the page, next to the new heading:

```markdown
<a id="old-anchor"></a>
## New heading
```

then move the old path to `retired`, point the key at the new anchor and update its `DocsLinks`
constant. The check fails until every key and every retired path resolves.

A CI guard rejects any `docs.openaev.io` URL, in any spelling, in tracked files outside the docs
sources (`docs/docs/`, `docs/overrides/`, `docs/hooks/`, `docs/tests/`, `docs/*.py`, `docs/*.md`,
`links.json`, `mkdocs.yml` and the requirements), `.github/`, Markdown files outside source trees,
the comment lines of `openaev-api/src/main/resources/application.properties`, `DocsLinks.java` and
`DocsLinksTest.java`. It also rejects text concatenated onto a docs link and any import of
`links.json` other than the frontend `docsUrl` helper.

### Dependencies

`requirements.in` lists the direct dependencies, the build engine ProperDocs included, and
`requirements.txt` pins them with everything they pull in, for every platform and for the Python of
`.python-version` and later. Renovate bumps the pins of `requirements.in` and regenerates
`requirements.txt`, except mike, which installs from a commit of its git repository: bump that
commit by hand. To try a new version, change its pin in `requirements.in` and regenerate
`requirements.txt` from `docs/` with the `uv pip compile` command written at its top, which needs
[uv](https://docs.astral.sh/uv/getting-started/installation/) and keeps the other pins. The next
`run.py` command installs it. Never edit `requirements.txt` by hand.

The lock has no hashes: mike installs from a commit of its git repository, which pip cannot hash,
and pip refuses a partly hashed file.

The minimum Python lives in two places, which change together, by hand: `.python-version` and the
`--python-version` of the command at the top of `requirements.txt`. Everything else reads
`.python-version`.

## Deploy the documentation

### Update the source

Committing on the main branch does not impact (for now) the deployed documentation. Commits must follow the repository's Conventional Commits convention (e.g. `docs: update simulation page (#1234)`).

### Deploy and update the current version

mike runs the engine by name, so run it in the environment `run.py` creates. From `docs/`, run
`python3 run.py check` once, which prints the Python it uses when it switches to a `python3.N`,
then activate the environment of that Python version: `.venv/py3.N-<digest>`, the only
`.venv/py3.N-*` folder for that version once `run.py` cleaned the old ones. To pin the version,
run the check with that Python directly, such as `python3.12 run.py check`. On Windows, activate it
with `.venv\py3.N-<digest>\Scripts\Activate.ps1`.

```shell
$ . .venv/py3.N-*/bin/activate     # N: the minor version of the Python run.py used
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
