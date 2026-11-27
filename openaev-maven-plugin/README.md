# openaev-maven-plugin

Plugin for managing common tasks for the development of OpenAEV

## Installation (mandatory)
Within the maven plugin root directory, run the install command.
This installs the plugin in the local maven repository.
```shell
mvn clean install
```

## Usage

### Create a new empty Flyway migration file
**Note: this is only intended for being run within the OpenAEV API module.**

This will create an empty migration file titled `V6_{timestamp}__migration`.
```shell
mvn openaev:migration
```
```shell
V6_20260619103476928__migration.java
```

It is possible to specify a "reason" for giving a more explicit name to the migration.
```shell
mvn openaev:migration -Dreason="add more columns to table"
```
```shell
V6_20260619103476928__add_more_columns_to_table.java
```

### Generate an OCSF parser
**Note: run this from the repository root.**

This will download the OCSF schema specification from schema.ocsf.io and generate Java source code implementing
a parser for the requested schema version.
```shell
mvn openaev:generate-ocsf-parser -Dversion={version}
```
All source code is created in the `openaev-ocsf` module.
### Check the docs links (`docs-check`)
Bound to `validate` in `openaev-api`, so it runs on every local build, `spring-boot:run` included, once per build.

Runs `<python> docs/run.py check` (`docs/run.py --offline check` when Maven runs offline). The script sets up and
reuses its own virtual environment for the docs toolchain, checks every link of `docs/links.json` against the built
docs site, and skips the docs build when the docs did not change. If Maven is stopped, the check and the processes
it started are stopped too.

Only docs problems (exit code 1: a key of `docs/links.json` pointing to a missing page, anchor or redirect, a broken
link between pages...) fail the build. Anything else logs a warning saying why and what to do, and the build goes
on; the Docs Check CI job still checks the docs. That covers no Python to start `run.py` with, an environment that
cannot be set up (exit code 4: no usable Python, packages not downloadable, or offline while they must be
installed), any other failure of the toolchain, and a check that runs longer than `-Dopenaev.docs.check.timeout`
seconds (900 by default), which is then stopped. The warning ends with the lines of the check's output that say why,
so it still explains the failure with `mvn -q`.

The goal only starts a Python for `run.py`; `run.py` chooses the interpreter of the check: it needs the version of
`docs/.python-version` or later with the `venv` module (`python3-venv` on Debian/Ubuntu), and starts a newer
`python3.N` itself when the first one is too old. The full rule is in [docs/README.md](../docs/README.md#which-python-runs-the-docs-toolchain). The
Python that starts `run.py` is:

- `-Dopenaev.docs.python=<path or name>`, then the `OPENAEV_DOCS_PYTHON` environment variable: one executable,
  as a path or a command name on the `PATH`, without arguments (`py -3` cannot be given this way). A relative path is
  resolved against the directory Maven runs from. `run.py` gets it in `OPENAEV_DOCS_PYTHON` and uses only that one;
- otherwise the first of `py -3` (Windows launcher), `python3` and `python` on the `PATH` that answers `--version`
  as Python 3 within 10 seconds, which skips the Windows Store stub.

It is skipped when the `CI` environment variable is set (the Docs Check job covers CI). To skip it:
```shell
mvn package -Dopenaev.docs.check.skip=true
```

### After this plugin changes
Builds that do not include this module (`-pl openaev-api` without `-am`, the offline fast restart) take the plugin
from the local Maven repository. When it holds the same version from before a new goal was added, Maven fails with
`Could not find goal '<goal>' in plugin io.openaev:openaev-maven-plugin:<version>` (for example
`Could not find goal 'docs-check'`), and a changed goal keeps its old behaviour. Install the plugin again once
(`-Dopenaev.docs.check.skip=true` also gets past the missing `docs-check` goal, since it turns off the
profile that binds it):
```shell
mvn -pl openaev-maven-plugin install
```
