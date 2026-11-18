#!/usr/bin/env bash
# Creates an ADR with the next number, or gives an unmerged ADR a new one.
#
#   adr/scripts/new-adr.sh "Tenant isolation"           -> adr/ADR-NNN-tenant-isolation.md
#   adr/scripts/new-adr.sh --renumber adr/ADR-017-x.md  -> adr/ADR-NNN-x.md, references updated
#
# The next number is one above the highest ADR on disk: rebase on main first to start from its latest ADRs.
# Another branch may take the same number meanwhile; the ADR Numbering CI check then fails on the pull
# request merging second, which runs --renumber after rebasing. Never type the number by hand.
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$script_dir/_common.sh"
cd "$(git rev-parse --show-toplevel)"

usage() {
  cat >&2 <<'EOF'
Usage: adr/scripts/new-adr.sh "<title or kebab-case-slug>"
       adr/scripts/new-adr.sh --renumber adr/ADR-NNN-<slug>.md

  Creates adr/ADR-NNN-<slug>.md from adr/ADR-000-template.md with the number after the highest ADR on
  disk, and prints its path. Never overwrites a file.

  --renumber <file>  give an ADR that is not merged yet the next number: renames the file, its
                     "# ADR-NNN:" title and every reference to its file name in the repository.
                     Rebase on main first, so the ADRs merged meanwhile are on disk.
EOF
  exit 2
}

renumber=""
title=""
while [ $# -gt 0 ]; do
  case "$1" in
    --renumber) [ $# -ge 2 ] || usage; renumber=$2; shift ;;
    -h | --help) usage ;;
    -*) echo "Unknown option: $1" >&2; usage ;;
    *) [ -z "$title" ] || usage; title=$1 ;;
  esac
  shift
done
if [ -n "$renumber" ]; then [ -z "$title" ] || usage; else [ -n "$title" ] || usage; fi

base="$ADR_REMOTE/$ADR_BASE_BRANCH"
has_base=0
git rev-parse --verify --quiet "$base^{commit}" >/dev/null && has_base=1
# Local check only, against the main fetched last: numbering from an old main is how collisions start.
if [ "$has_base" -eq 1 ] && ! git merge-base --is-ancestor "$base" HEAD; then
  echo "⚠️  This branch does not contain $base: rebase on it first, or the number may already be taken there." >&2
fi

if [ -n "$renumber" ]; then
  old=$(basename "$renumber")
  [ -f "adr/$old" ] || { echo "❌ adr/$old does not exist." >&2; exit 1; }
  [[ $old =~ $ADR_NAME_REGEX ]] || { echo "❌ adr/$old is not named ADR-NNN-<kebab-case-slug>.md." >&2; exit 1; }
fi

# A renumbered ADR still counts its own number: it is moving away from it.
next=$(($(adr_names_on_disk | adr_index | adr_max) + 1))
id=$(adr_id "$next")

# ── Renumber an unmerged ADR ──
if [ -n "$renumber" ]; then
  # A merged ADR keeps its number for good: links to it live outside this repository.
  if [ "$has_base" -eq 1 ] && git cat-file -e "$(git merge-base HEAD "$base"):adr/$old" 2>/dev/null; then
    echo "❌ adr/$old was already on $base when this branch started: a merged ADR is never renumbered." >&2
    exit 1
  fi
  old_id=$(echo "$old" | sed -E 's/^(ADR-[0-9]+)-.*/\1/')
  new="$id-${old#"$old_id"-}"
  old_re=$(printf '%s' "$old" | sed 's/[.]/\\./g')
  [ ! -e "adr/$new" ] || { echo "❌ adr/$new already exists." >&2; exit 1; }

  if git ls-files --error-unmatch "adr/$old" >/dev/null 2>&1; then
    git mv "adr/$old" "adr/$new"
  else
    mv "adr/$old" "adr/$new"
  fi
  sed -i.bak -E "1s/^# $old_id:/# $id:/" "adr/$new" && rm "adr/$new.bak"
  git grep -l -I --untracked -F "$old" -- ':!adr/scripts/' | while read -r file; do
    sed -i.bak "s/$old_re/$new/g" "$file" && rm "$file.bak"
    echo "   updated a reference in $file" >&2
  done || true

  echo "✅ $old_id is now $id: adr/$new" >&2
  echo "   Mentions without the file name are not rewritten; check them with: git grep -nw '$old_id'" >&2
  echo "adr/$new"
  exit 0
fi

# ── Create a new ADR ──
slug=$(printf '%s' "$title" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//')
[ -n "$slug" ] || { echo "❌ \"$title\" gives an empty file name: use letters or digits." >&2; exit 1; }
# A slug given alone becomes the title: "tenant-isolation" -> "Tenant isolation".
case "$title" in
  *" "*) ;;
  *) title=$(printf '%s' "$slug" | sed -E 's/-/ /g') && title="$(printf '%s' "${title:0:1}" | tr '[:lower:]' '[:upper:]')${title:1}" ;;
esac

file="adr/$id-$slug.md"
template=adr/ADR-000-template.md
[ -f "$template" ] || { echo "❌ $template is missing." >&2; exit 1; }
[ ! -e "$file" ] || { echo "❌ $file already exists, not overwriting it." >&2; exit 1; }

# noclobber: never overwrite, even if a file appeared since the check above.
(set -C && awk -v heading="# $id: $title" 'NR == 1 { print heading; next } { print }' "$template" >"$file")

echo "✅ Created $id: $file" >&2
echo "$file"
