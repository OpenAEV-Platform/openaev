---
name: review-code
description: >-
  Step-by-step general code review procedure for OpenAEV pull requests.
  Covers architecture, conventions, code quality, and delegation to specialized agents.
---

# Review Code

## Step 1 — Assess PR scope

```bash
# Count changed files and lines
git diff --stat HEAD~1
```

- If >500 lines changed: flag for splitting before detailed review
- If >20 files changed: flag for splitting before detailed review

## Step 2 — Check PR metadata

Verify:
- ☐ PR title follows conventional commits (`type(scope?): description (#issue)` — NO `[context]` prefix; `[context]` is for commit messages only)
- ☐ PR description explains WHAT and WHY
- ☐ Linked issue/ticket exists

## Step 3 — Check build

```bash
mvn spotless:check -pl openaev-api
mvn compile -pl openaev-api -q
```

If build fails: stop review, report build failure.

For frontend changes:
```bash
cd openaev-front && yarn check-ts && yarn lint
```

## Step 4 — Review architecture alignment

```bash
# Check for entity exposure in API layer (should use DTOs)
grep -rn "import io.openaev.database.model" openaev-api/src/main/java/io/openaev/api/ openaev-api/src/main/java/io/openaev/rest/ --include="*.java" | grep -v "Action\|ResourceType\|Capability\|Filters\|Grant"
```

Flag any direct entity usage in controllers or API responses (should use Output records + Mapper).

```bash
# Check for repository injection in controllers (should go through service)
grep -rn "Repository" openaev-api/src/main/java/io/openaev/api/ openaev-api/src/main/java/io/openaev/rest/ --include="*.java" | grep -v "test\|Test"
```

## Step 5 — Review code quality

```bash
# System.out.println (should use @Slf4j)
grep -rn "System.out\|System.err\|printStackTrace" --include="*.java" openaev-api/src/main/java/

# jakarta.transaction.Transactional (should use Spring's)
grep -rn "jakarta.transaction.Transactional" --include="*.java" openaev-api/src/main/java/

# New code in deprecated module
git diff --name-only HEAD~1 | grep "openaev-framework"

# New code in legacy rest/ package (should be in api/)
git diff --name-only HEAD~1 | grep "io/openaev/rest/" | grep -v "test"
```

## Step 6 — Check test coverage

```bash
# Are there test files for the changed production files?
for f in $(git diff --name-only HEAD~1 | grep "src/main/java" | grep -v "migration"); do
  testfile=$(echo "$f" | sed 's|src/main/java|src/test/java|' | sed 's|\.java$|Test.java|')
  if [ ! -f "$testfile" ]; then
    echo "⚠️ Missing test: $testfile"
  fi
done
```

## Step 7 — Determine delegation

Based on changed files, determine if specialized agents should run:

```bash
# Security signals
grep -rn "AccessControl\|@Filter\|Capability\|Permission\|nativeQuery" --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null | head -10

# Performance signals
grep -rn "OneToMany\|ManyToMany\|FetchType\|findAll\|Pageable" --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null | head -10

# Tenancy signals (v1 @Filter + v2 TxCtx/active-tables)
grep -rn "TenantBase\|tenant_id\|TenantContext\|TxCtx\|active-tables\|TenantScopedTransaction\|RequireTenantSelector\|can_access_tenant" --include="*.java" $(git diff --name-only HEAD~1) 2>/dev/null | head -10

# Frontend signals
git diff --name-only HEAD~1 | grep -E "\.tsx$|\.ts$" | head -10
```

## Step 8 — Check common anti-patterns (learned from reviews)

Apply these checks based on past review feedback:

- **Root cause vs workaround**: If a bug is backend-originated, don't add frontend workarounds (onError handlers, fallback states). Fix the root cause in the correct layer.
- **String/value duplication**: When introducing new methods that share data with existing methods (e.g., logo filenames, config keys), extract the shared value to a private method or constant — never compute the same formatted string in multiple places.
- **Non-critical operations**: Startup operations that interact with external services (MinIO, S3, etc.) for non-critical assets (logos, thumbnails) should be best-effort: wrap in try-catch with `log.warn` so failures don't block application startup.
- **Test proportionality**: Don't require tests for small mechanical changes (1-line additions, parameter threading). Tests should be proportionate to the risk and complexity of the change.
- **Pre-existing issues**: Don't fix unrelated pre-existing issues (e.g., alt text, parameter naming) in a bug fix PR. Track them as follow-up.

## Step 9 — Compile review

Generate the Code Review Summary following the output format
defined in `code-reviewer.agent.md`.

