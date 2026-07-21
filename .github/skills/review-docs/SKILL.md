---
name: review-docs
description: >-
  Step-by-step documentation gap detection for OpenAEV pull requests.
  Identifies functional code changes that are not reflected in docs/.
---

# Review Docs

## Step 1 — Identify all changed files in the PR

```bash
git diff --name-only HEAD~1
```

Separate the changed files into two categories:
- **Functional files**: anything in `openaev-api/`, `openaev-model/`, `openaev-front/src/`, `openaev-framework/`, configuration files
- **Doc files**: anything in `docs/`

If there are zero functional files changed (doc-only PR): output `PASS` and stop.

## Step 2 — Filter out non-user-facing changes

Remove from the functional files list any files that match these patterns (they don't require doc updates):

```bash
# Test files
git diff --name-only HEAD~1 | grep -E "Test\.java$|\.test\.(ts|tsx)$|tests_e2e/"

# CI/Build files
git diff --name-only HEAD~1 | grep -E "\.github/workflows/|Dockerfile|docker-compose|pom\.xml$|package\.json$|yarn\.lock$"

# Code style / formatting
git diff --name-only HEAD~1 | grep -E "\.eslintrc|\.prettierrc|spotless|\.editorconfig"

# Internal dev docs (not user-facing)
git diff --name-only HEAD~1 | grep -E "\.github/instructions/|\.github/agents/|\.github/skills/|AGENTS\.md|CLAUDE\.md|CONTRIBUTING\.md|copilot-instructions"

# Annotation processor / Maven plugin (build tooling)
git diff --name-only HEAD~1 | grep -E "openaev-annotation-processor/|openaev-maven-plugin/"
```

If all functional files are filtered out: output `PASS` and stop.

## Step 3 — Classify remaining functional changes by impact

For each remaining functional file, determine the type of change:

```bash
# New files (likely new features)
git diff --name-only --diff-filter=A HEAD~1 | grep -E "openaev-api/|openaev-model/|openaev-front/src/"

# Deleted files (likely removed features)
git diff --name-only --diff-filter=D HEAD~1 | grep -E "openaev-api/|openaev-model/|openaev-front/src/"

# Modified files — check if changes are behavioral
git diff --name-only --diff-filter=M HEAD~1 | grep -E "openaev-api/|openaev-model/|openaev-front/src/"
```

For modified files, inspect the diff to determine if changes are:
- **Behavioral**: new parameters, changed defaults, new endpoints, modified workflows, new UI components
- **Internal**: refactoring, renaming, code cleanup, performance optimization with same external behavior

```bash
# Look for new REST endpoints
git diff HEAD~1 -- "*.java" | grep -E "^\+.*@(Get|Post|Put|Delete|Patch)Mapping"

# Look for new configuration properties
git diff HEAD~1 -- "*.java" "*.properties" "*.yml" | grep -E "^\+.*@Value|^\+.*openaev\."

# Look for new frontend routes/pages
git diff HEAD~1 -- "*.tsx" "*.ts" | grep -E "^\+.*Route|^\+.*path:"

# Look for changed/new API input/output DTOs
git diff HEAD~1 -- "*Input.java" "*Output.java" | grep -E "^\+|^\-" | head -30
```

## Step 4 — Map functional changes to expected doc pages

Using the **Code-to-Doc Mapping** table in `docs-reviewer.agent.md`, determine which doc pages should be impacted.

For each functional file with behavioral changes:
1. Match it against the mapping table
2. Record the expected doc page(s)
3. Check if those doc page(s) appear in the list of changed files from Step 1

```bash
# Check if any doc files were changed in this PR
git diff --name-only HEAD~1 | grep "^docs/"
```

## Step 5 — Cross-reference and identify gaps

For each expected doc page from Step 4:
- If the doc page IS in the changed files list: mark as **covered**
- If the doc page is NOT in the changed files list: mark as **gap**

For gaps, determine severity:
- **New feature** (new file added, new endpoint, new UI page) with no doc: 🔴 CRITICAL
- **Behavioral change** (changed defaults, renamed fields, modified workflow) with no doc: 🟠 HIGH
- **New config/env var** with no doc: 🟡 MEDIUM
- **Internal change** referenced in dev docs with no doc update: 🟢 LOW

## Step 6 — Check for linked documentation issues

```bash
# Read the PR description for linked issues mentioning "doc" or "documentation"
gh pr view --json body,title,labels 2>/dev/null || echo "Not in a PR context"
```

If the PR description or a linked issue mentions a follow-up documentation task:
- Acknowledge it in the review
- Downgrade gap severity by one level (but never below 🟢)

## Step 7 — Compile findings

Generate the Documentation Review Summary following the output format
defined in `docs-reviewer.agent.md`.

For each gap, provide:
- The specific code file and what changed
- The specific doc page that should be updated
- A brief explanation of what users need to know

If documentation WAS updated alongside code, acknowledge it with 👏 praise.
