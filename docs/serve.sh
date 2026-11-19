#!/bin/sh -e
# Serves the docs at http://localhost:8000. run.py sets up docs/.venv with the pinned requirements first.
# Set DOCS_GIT_PLUGINS=false to skip the GitHub API calls of the git plugins.
#
# Python, the same rule as the backend build and `yarn check-docs-links`: OPENAEV_DOCS_PYTHON when
# set, which must then be usable, otherwise the first usable of python3, python, python3.20 ...
# python3.<minimum>. Usable: it runs, has the venv and ensurepip modules, and is at least the
# version of .python-version.
docs=$(dirname "$0")
minimum=$(tr -d ' \r\n' < "$docs/.python-version")
min_major=${minimum%%.*}
min_minor=${minimum#*.}

usable() {
  version=$("$1" -c "import sys, venv, ensurepip; print('%d.%d' % sys.version_info[:2])" 2> /dev/null) || return 1
  major=${version%%.*}
  minor=${version#*.}
  case "$major$minor" in '' | *[!0-9]*) return 1 ;; esac
  [ "$major" -gt "$min_major" ] || { [ "$major" -eq "$min_major" ] && [ "$minor" -ge "$min_minor" ]; }
}

if [ -n "$OPENAEV_DOCS_PYTHON" ]; then
  if ! usable "$OPENAEV_DOCS_PYTHON"; then
    echo "OPENAEV_DOCS_PYTHON=$OPENAEV_DOCS_PYTHON is not a Python $minimum or later with the venv module." >&2
    exit 4
  fi
  python=$OPENAEV_DOCS_PYTHON
else
  python=
  n=20
  candidates="python3 python"
  while [ "$n" -ge "$min_minor" ]; do
    candidates="$candidates python$min_major.$n"
    n=$((n - 1))
  done
  for candidate in $candidates; do
    if command -v "$candidate" > /dev/null 2>&1 && usable "$candidate"; then
      python=$candidate
      break
    fi
  done
  if [ -z "$python" ]; then
    echo "No Python $minimum or later with the venv module found (python3, python, python$min_major.N)." \
      "Install one (on Debian and Ubuntu, also python3-venv), or set OPENAEV_DOCS_PYTHON." >&2
    exit 4
  fi
fi
echo "Docs: using $(command -v "$python" || echo "$python")"
exec "$python" "$docs/run.py" serve "$@"
