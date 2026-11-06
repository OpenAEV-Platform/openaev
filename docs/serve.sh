#!/bin/sh -e
# Serves the docs at http://localhost:8000. run.py sets up docs/.venv with the pinned requirements first.
# Set DOCS_GIT_PLUGINS=false to skip the GitHub API calls of the git plugins.
#
# Python: PYTHON when set (it must work and be recent enough), otherwise the first of python3,
# python, python3.20 ... python3.<minimum> that is at least the version of .python-version.
docs=$(dirname "$0")
minimum=$(tr -d ' \r\n' < "$docs/.python-version")
min_major=${minimum%%.*}
min_minor=${minimum#*.}

# Prints the interpreter when its `--version` reports the minimum or later.
usable() {
  version=$("$1" --version 2>&1) || return 1
  version=${version#Python }
  major=${version%%.*}
  rest=${version#*.}
  minor=${rest%%.*}
  case "$major$minor" in '' | *[!0-9]*) return 1 ;; esac
  [ "$major" -gt "$min_major" ] || { [ "$major" -eq "$min_major" ] && [ "$minor" -ge "$min_minor" ]; }
}

if [ -n "$PYTHON" ]; then
  if ! usable "$PYTHON"; then
    echo "PYTHON=$PYTHON is not a Python $minimum or later." >&2
    exit 4
  fi
  python=$PYTHON
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
    echo "No Python $minimum or later found (python3, python, python$min_major.N). Install one, or set PYTHON." >&2
    exit 4
  fi
fi
echo "Docs: using $(command -v "$python" || echo "$python")"
exec "$python" "$docs/run.py" serve "$@"
