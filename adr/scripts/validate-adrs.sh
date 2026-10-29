#!/usr/bin/env bash
# Checks the numbering of the ADRs on disk. Run it before opening a pull request; CI runs it on every one.
#
#   - every ADR is adr/ADR-NNN-<kebab-case-slug>.md and its title starts with "# ADR-NNN:";
#   - no two ADRs share a number, on the target branch once this branch is merged;
#   - new numbers follow the highest one on the target branch, without a gap;
#   - every reference to an ADR file name points to an existing file.
#
# When another branch merged the same number first, this one fails until it is rebased and renumbered.
# Against a target branch, only problems this branch introduces are errors; the others are warnings,
# so a bad ADR on main does not block unrelated pull requests. Exits 1 on error.
set -euo pipefail

script_dir=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=_common.sh
. "$script_dir/_common.sh"
cd "$(git rev-parse --show-toplevel)"

usage() {
  cat >&2 <<'EOF'
Usage: adr/scripts/validate-adrs.sh [--base <ref> | --no-base]

  --base <ref>  what this branch merges into (default: origin/main, fetched first)
  --no-base     check the ADRs on disk alone, every problem being an error (CI on main)
EOF
  exit 2
}

base="$ADR_REMOTE/$ADR_BASE_BRANCH"
base_given=0
while [ $# -gt 0 ]; do
  case "$1" in
    --base) [ $# -ge 2 ] || usage; base=$2; base_given=1; shift ;;
    --no-base) base="" ;;
    -h | --help) usage ;;
    *) echo "Unknown option: $1" >&2; usage ;;
  esac
  shift
done
# For the messages: a CI base is a commit, not a branch.
base_branch=${GITHUB_BASE_REF:-$ADR_BASE_BRANCH}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
errors=0
warnings=0

# problem <is-error> <message> <fix>
problem() {
  if [ "$1" -eq 1 ]; then
    errors=$((errors + 1))
    printf '❌ %s\n   Fix: %s\n' "$2" "$3"
    if [ -n "${GITHUB_ACTIONS:-}" ]; then echo "::error title=ADR numbering::$2"; fi
  else
    warnings=$((warnings + 1))
    printf '⚠️  %s (already on %s: not blocking)\n' "$2" "$base_branch"
  fi
}

# ── What this branch changes ──
adr_names_on_disk >"$tmp/disk"
if [ -n "$base" ]; then
  if [ "$base_given" -eq 0 ]; then
    git fetch --quiet "$ADR_REMOTE" "$ADR_BASE_BRANCH" 2>/dev/null ||
      echo "⚠️  Could not fetch $base, using the copy fetched last."
  fi
  if ! git rev-parse --verify --quiet "$base^{commit}" >/dev/null; then
    echo "❌ Target branch $base not found. Fetch it, or pass --base <ref> or --no-base."
    exit 1
  fi
  fork=$(git merge-base HEAD "$base")
  adr_names_at "$base" >"$tmp/base"
  adr_names_at "$fork" >"$tmp/fork"
  LC_ALL=C comm -23 "$tmp/disk" "$tmp/fork" >"$tmp/added"
  LC_ALL=C comm -13 "$tmp/disk" "$tmp/fork" >"$tmp/deleted"
  # Files this branch changes anywhere, committed or not, to tell its problems from the base's.
  { git diff --name-only "$fork" && git ls-files --others --exclude-standard; } | LC_ALL=C sort -u >"$tmp/changed"
  sed -nE 's#^adr/([^/]+\.md)$#\1#p' "$tmp/changed" | LC_ALL=C comm -12 - "$tmp/disk" >"$tmp/touched"
  # The ADRs once merged: the target branch, without what this branch deletes, with what it adds.
  LC_ALL=C comm -23 "$tmp/base" "$tmp/deleted" | cat - "$tmp/added" | LC_ALL=C sort -u >"$tmp/merged"
else
  cp "$tmp/disk" "$tmp/merged"
  cp "$tmp/disk" "$tmp/touched"
  : >"$tmp/added" && : >"$tmp/deleted" && : >"$tmp/base" && : >"$tmp/fork"
  git ls-files >"$tmp/changed"
fi
touched() { grep -qxF "$1" "$tmp/touched"; }
adr_index <"$tmp/merged" >"$tmp/merged.idx"
adr_index <"$tmp/base" | awk '{ print $1 }' | sort -u >"$tmp/base.nums"

# ── File names ──
while read -r name; do
  [ "$name" = "README.md" ] && continue
  if ! [[ $name =~ $ADR_NAME_REGEX ]]; then
    t=0 && touched "$name" && t=1
    problem "$t" "adr/$name is not named ADR-NNN-<kebab-case-slug>.md" \
      "rename it in lowercase kebab-case after the ADR-NNN- prefix, and update its references."
  fi
done <"$tmp/merged"

# ── Duplicate numbers ──
awk '{ print $1 }' "$tmp/merged.idx" | sort -n | uniq -d | while read -r n; do
  files=$(awk -v n="$n" '$1 == n { print $2 }' "$tmp/merged.idx")
  t=0
  for f in $files; do touched "$f" && t=1; done
  # sed, not head: an early exit would kill the loop with SIGPIPE under pipefail.
  mine=$(for f in $files; do if touched "$f"; then echo "adr/$f"; fi; done | sed -n 1p)
  problem "$t" "$(adr_id "$n") is used by $(echo "$files" | sed 's#^#adr/#' | paste -sd ' ' - | sed 's/ /, /g')" \
    "keep the number of the ADR already on $base_branch: rebase on $base_branch, then run adr/scripts/new-adr.sh --renumber ${mine:-<the other ADR>}"
done >"$tmp/out.dup"
# The loop runs in a subshell: count its problems from its output.
cat "$tmp/out.dup"
errors=$((errors + $(grep -c '^❌' "$tmp/out.dup" || true)))
warnings=$((warnings + $(grep -c '^⚠️' "$tmp/out.dup" || true)))

# ── Title matches the file name ──
while read -r name; do
  [ -f "adr/$name" ] || continue
  n=$(echo "$name" | adr_index | awk '{ print $1 }')
  [ -n "$n" ] || continue
  # tr: an editor on Windows may have saved the file with CRLF line endings.
  heading=$(head -1 "adr/$name" | tr -d '\r')
  title_n=$(echo "$heading" | sed -nE 's/^# ADR-([0-9]+)([^0-9].*)?$/\1/p')
  if [ -n "$title_n" ] && [ "$((10#$title_n))" -ne "$n" ]; then
    t=0 && touched "$name" && t=1
    problem "$t" "adr/$name is titled \"ADR-$title_n\" but its file name says $(adr_id "$n")" \
      "make its first line start with \"# $(adr_id "$n"):\"."
  fi
done <"$tmp/merged"

# ── New numbers: right after the highest one on the target branch ──
if [ -n "$base" ]; then
  base_max=$(adr_index <"$tmp/base" | adr_max)
  adr_index <"$tmp/added" | while read -r n name; do
    grep -qx "$n" "$tmp/base.nums" || echo "$n $name"
  done >"$tmp/new"

  while read -r n name; do
    if [ "$n" -le "$base_max" ]; then
      problem 1 "adr/$name takes $(adr_id "$n"), but $base_branch already goes up to $(adr_id "$base_max")" \
        "rebase on $base_branch, then run adr/scripts/new-adr.sh --renumber adr/$name"
      continue
    fi
    # Numbers this branch adds itself fill the space between: two new ADRs take N+1 and N+2.
    gap=""
    i=$((base_max + 1))
    while [ "$i" -lt "$n" ]; do
      awk -v n="$i" '$1 == n { found = 1 } END { exit !found }' "$tmp/new" || gap="$gap $(adr_id "$i")"
      i=$((i + 1))
    done
    if [ -n "$gap" ]; then
      problem 1 "adr/$name takes $(adr_id "$n"), skipping$gap after the last ADR on $base_branch" \
        "rebase on $base_branch, then run adr/scripts/new-adr.sh --renumber adr/$name"
    fi
  done <"$tmp/new"

  # A merged ADR keeps its number for good: say so when this branch changes one, for the reviewers.
  while read -r gone; do
    slug=$(echo "$gone" | sed -E 's/^ADR-[0-9]+-//' | tr '[:upper:]' '[:lower:]')
    now=$(grep -iE "^ADR-[0-9]+-$slug\$" "$tmp/added" | sed -n 1p || true)
    if [ -n "$now" ] && [ "$(echo "$now" | adr_index | awk '{ print $1 }')" != "$(echo "$gone" | adr_index | awk '{ print $1 }')" ]; then
      echo "ℹ️  adr/$gone, already on $base_branch, is renumbered to adr/$now: only acceptable to fix a duplicate."
    fi
  done <"$tmp/deleted"
fi

# ── References to ADR file names ──
git grep -I -n -o --untracked -E 'ADR-[0-9]+-[A-Za-z0-9-]+\.md' -- ':!adr/scripts/' 2>/dev/null |
  awk -F: '{ print $1, $2, $3 }' | sort -u -k1,1 -k3,3 >"$tmp/refs" || true
while read -r file line ref; do
  [ -f "adr/$ref" ] && continue
  t=0
  if [ -z "$base" ] || grep -qxF "$file" "$tmp/changed" || grep -qxF "$ref" "$tmp/fork"; then t=1; fi
  problem "$t" "$file:$line points to $ref, which does not exist" \
    "point it to the ADR's current file name in adr/."
done <"$tmp/refs"

# ── Verdict ──
count=$(wc -l <"$tmp/merged.idx" | tr -d ' ')
if [ "$errors" -gt 0 ]; then
  echo ""
  echo "❌ ADR numbering: $errors error(s), $warnings warning(s) in $count ADRs."
  echo "   Create an ADR with adr/scripts/new-adr.sh, never by hand; see \"ADRs\" in AGENTS.md."
  exit 1
fi
echo "✅ ADR numbering: $count ADRs checked, $warnings warning(s)."
