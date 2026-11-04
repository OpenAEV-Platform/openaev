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
### Keep the docs link constants up to date (`docs-links`)
Bound to `generate-sources` in `openaev-api`, so it runs on every build.

`openaev-api/src/main/java/io/openaev/docs/DocsLinks.java` holds one compile-time constant per key of
`docs/links.json` (`configuration_imap` becomes `DocsLinks.CONFIGURATION_IMAP`, the base URL followed by the path).
Backend code links to the documentation only through these constants, so a key that is renamed or removed fails
compilation. The class is committed, so IDE builds compile without running Maven; this goal keeps it in line with
the map:
- on a developer's computer, it rewrites the class when `docs/links.json` changed: commit it with the map;
- in CI (`CI` environment variable set), it fails instead when the class is out of date.

The build fails on a missing or malformed map, a `base` other than `https://docs.openaev.io/latest/`, a duplicate
key, a key that is not snake_case or longer than 64 characters, or a value that is not a page path relative to the
base (characters outside RFC 3986, a scheme, a leading `/`, or a page part that does not end with `/` before any
`#`). These are the rules of the docs build (`docs/hooks/checks.py`), which also checks that pages and anchors exist.

The goal also checks the docs URLs written in `application.properties` (listed under `linkedFiles` in
`openaev-api/pom.xml`): each must be a link of the map, and a URL of another docs version or of the bare site is
reported too. The map path is set in the POM only, not from the command line: the goal rewrites the committed class
from it.

### Check the docs links (`docs-check`)
Bound to `validate` in `openaev-api`, so it runs on every local build, `spring-boot:run` included, once per build.

Runs `<python> docs/run.py check` (`docs/run.py --offline check` when Maven runs offline). The script sets up and
reuses its own virtual environment for the docs toolchain, checks every link of `docs/links.json` against the built
docs site, and skips the docs build when the docs did not change. Exit code 1 means the docs or the map have
problems, 4 that the Python environment could not be set up (Python too old, `python3-venv` missing on
Debian/Ubuntu, packages not downloadable, or offline while they must be installed); any other failure means the
toolchain crashed. If Maven is stopped, the check and the processes it started are stopped too.

It needs Python at the minimum version of `docs/.python-version`. An explicit choice, `-Dopenaev.docs.python=<path
or command>` then the `PYTHON` environment variable, must work: when it is not found, does not answer `--version` or
is too old, the build fails naming it. Without one, the first of `py -3` (Windows launcher), `python3`, `python`,
then `python3.20` down to `python3.<minimum>` on the `PATH` that answers `--version` with the minimum version or
later is used, and logged. A candidate that does not answer (such as the Windows Store stub) is skipped.

It is skipped when the `CI` environment variable is set (the Docs Check job covers CI). To build without it, skip
the check:
```shell
mvn package -Dopenaev.docs.check.skip=true
```

### After this plugin changes
Builds that do not include this module (`-pl openaev-api` without `-am`, the offline fast restart) take the plugin
from the local Maven repository. When it holds the same version from before a new goal was added, Maven fails with
`Could not find goal '<goal>' in plugin io.openaev:openaev-maven-plugin:<version>`. Install the plugin again once:
```shell
mvn -pl openaev-maven-plugin install
```
