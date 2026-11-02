# Helpers shared by new-adr.sh and validate-adrs.sh. Sourced, never run; the caller is at the repository root.
#
# Portable to every developer setup: bash 3.2+ (macOS ships 3.2: no associative arrays, no mapfile), GNU or
# BSD tools (Linux, macOS), Git Bash or WSL on Windows (from PowerShell: bash adr/scripts/<script>.sh).
# Only options common to GNU and BSD are used, e.g. "sed -i.bak", never "sed -i" alone.

for adr_cmd in git awk sed grep sort comm find mktemp; do
  command -v "$adr_cmd" >/dev/null 2>&1 || { echo "❌ '$adr_cmd' is required by the ADR scripts but not found." >&2; exit 1; }
done

ADR_REMOTE=${ADR_REMOTE:-origin}
ADR_BASE_BRANCH=${ADR_BASE_BRANCH:-main}
ADR_NAME_REGEX='^ADR-[0-9]{3,}-[a-z0-9]+(-[a-z0-9]+)*\.md$'

# ADR file names in adr/ on disk, one per line, sorted.
adr_names_on_disk() {
  find adr -maxdepth 1 -type f -name '*.md' | sed 's#^adr/##' | LC_ALL=C sort
}

# ADR file names in adr/ at a git ref, one per line, sorted.
adr_names_at() {
  git ls-tree --name-only "$1" -- adr/ | sed 's#^adr/##' | grep -E '\.md$' | LC_ALL=C sort || true
}

# File names on stdin -> "<number> <name>" for each ADR, the number without its zero padding.
adr_index() {
  sed -nE 's/^(ADR-([0-9]+)-.*\.md)$/\2 \1/p' | awk '{ printf "%d %s\n", $1, $2 }'
}

# Highest number in adr_index output on stdin, -1 when there is none.
adr_max() {
  awk 'BEGIN { m = -1 } $1 > m { m = $1 } END { print m }'
}

adr_id() {
  printf 'ADR-%03d' "$1"
}
