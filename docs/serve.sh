#!/bin/sh
# Serves the docs at http://localhost:8000 and rebuilds them on every change. Options go to
# `properdocs serve`. Set DOCS_GIT_PLUGINS=false to skip the GitHub API calls of the git plugins.
#
# This script only starts a Python: OPENAEV_DOCS_PYTHON when set, otherwise the first of python3
# and python that answers --version. docs/run.py then checks it and, when it is too old or has no
# venv module, finds a newer python3.N itself (docs/README.md, "Which Python runs the docs toolchain").
set -e
docs=$(dirname "$0")

# PYTHONPATH and friends could make a working Python fail, for the probe and for run.py itself.
unset PYTHONPATH PYTHONHOME PYTHONSAFEPATH

answers() {
  # Python 2 answers --version too, then fails on run.py: only a Python 3 counts. `timeout` is
  # missing on some systems.
  if command -v timeout > /dev/null 2>&1; then
    version=$(timeout 10 "$1" --version 2>&1) || return 1
  else
    version=$("$1" --version 2>&1) || return 1
  fi
  case $version in
    "Python 3."*) return 0 ;;
    *) return 1 ;;
  esac
}

if [ -n "$OPENAEV_DOCS_PYTHON" ]; then
  python=$OPENAEV_DOCS_PYTHON
  case $python in
    /*) ;;
    */*) python=$(pwd)/$python ;;  # relative to the current folder, which run.py does not keep
  esac
  OPENAEV_DOCS_PYTHON=$python
  export OPENAEV_DOCS_PYTHON
else
  python=
  for candidate in python3 python; do
    if command -v "$candidate" > /dev/null 2>&1 && answers "$candidate"; then
      python=$candidate
      break
    fi
  done
  if [ -z "$python" ]; then
    echo "No Python found (python3, python). Install Python $(cat "$docs/.python-version") or later" \
      "(on Debian and Ubuntu, also python3-venv), or set OPENAEV_DOCS_PYTHON." >&2
    exit 4
  fi
fi
exec "$python" "$docs/run.py" serve "$@"
