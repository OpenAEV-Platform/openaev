---
name: spec-test
description: >-
  Test and security scan pipeline: runs all tests (unit, integration, E2E, security),
  performs security scanning (gitleaks, semgrep), scores findings with CVSS v3.1,
  auto-fixes vulnerabilities below CVSS 7.0. Use after post-implementation review.
---

# Test & Security Scan Pipeline

## Prerequisites

- Implementation reviewed (spec status: "Reviewed")
- Docker services running (PostgreSQL, MinIO, Elasticsearch, RabbitMQ)
- Feature branch with all implementation committed

## Procedure

### Step 1 — Run Base Tests

#### Backend Tests

```bash
# Format check
mvn spotless:check

# Build
mvn clean install -DskipTests -Pdev

# Run tests
mvn test

# Coverage check
mvn jacoco:check
```

**If tests fail**: Analyze the failure, fix, and re-run. Commit fixes:
`[backend] fix({feature}): fix failing test`

#### Frontend Tests

```bash
cd openaev-front

# Lint + type check
yarn lint
yarn check-ts

# Unit tests
yarn test
```

**If tests fail**: Fix and commit: `[frontend] fix({feature}): fix failing test`

### Step 2 — Run Spec-Defined Tests

Check the spec's Test Plan (§6) for additional test requirements:

- [ ] **Security tests**: RBAC, tenant isolation, input validation
- [ ] **E2E tests** (if defined): Playwright scenarios
- [ ] **Performance tests** (if defined): Response time, pagination

#### E2E Tests (if applicable)

```bash
# Requires the full application running
cd openaev-front && yarn test:e2e
```

#### Security-Specific Tests

Verify security tests from the spec are present and passing:

```bash
# Check for security test methods
grep -rn "given_.*unauthorized\|given_.*forbidden\|given_.*tenant\|given_.*without.*capability" \
  openaev-api/src/test/java/ --include="*.java"
```

### Step 3 — Security Scanning

#### 3a — Secret Detection (Gitleaks)

```bash
# Scan for secrets in the codebase
# If gitleaks is installed:
gitleaks detect --source . --no-git --verbose 2>&1 || true

# Fallback: manual search for common patterns
grep -rn "password\s*=\|secret\s*=\|api_key\s*=\|apiKey\s*=\|token\s*=" \
  --include="*.java" --include="*.ts" --include="*.properties" \
  --exclude-dir=node_modules --exclude-dir=target \
  openaev-api/ openaev-model/ openaev-front/src/ || true
```

#### 3b — Static Analysis (Semgrep)

```bash
# If semgrep is installed:
semgrep scan --config auto --json openaev-api/src/main/java/ 2>&1 || true

# Fallback: manual checks for common Java vulnerabilities
# SQL Injection
grep -rn "createNativeQuery\|createQuery.*\".*+.*\"" \
  openaev-api/src/main/java/ --include="*.java" || true

# Path Traversal
grep -rn "new File(\|Paths.get(" \
  openaev-api/src/main/java/ --include="*.java" | grep -v "test\|Test" || true

# Insecure Deserialization
grep -rn "ObjectInputStream\|readObject\|fromJson\|ObjectMapper.*readValue" \
  openaev-api/src/main/java/ --include="*.java" | grep -v "test\|Test" || true
```

#### 3c — OpenAEV-Specific Security Checks

```bash
# Missing @AccessControl
grep -rn "@PostMapping\|@GetMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping" \
  openaev-api/src/main/java/io/openaev/api/ --include="*.java" | \
  while read line; do
    file=$(echo "$line" | cut -d: -f1)
    linenum=$(echo "$line" | cut -d: -f2)
    # Check if @AccessControl exists within 5 lines before the mapping
    if ! sed -n "$((linenum-5)),$((linenum))p" "$file" | grep -q "AccessControl"; then
      echo "MISSING @AccessControl: $line"
    fi
  done || true

# Native queries without tenant_id
grep -rn "nativeQuery\s*=\s*true" openaev-api/ openaev-model/ --include="*.java" | \
  while read line; do
    file=$(echo "$line" | cut -d: -f1)
    if ! grep -A5 "$(echo "$line" | cut -d: -f2)" "$file" | grep -q "tenant_id"; then
      echo "MISSING tenant_id in native query: $line"
    fi
  done || true

# JPA entities in controllers
grep -rn "public.*\(Assessment\|Scenario\|Exercise\|Inject\) " \
  openaev-api/src/main/java/io/openaev/api/ --include="*.java" | \
  grep -v "Input\|Output\|Mapper\|import" || true
```

### Step 4 — CVSS v3.1 Scoring

For each finding from Step 3, compute a CVSS v3.1 score:

**CVSS v3.1 Vector String Components**:
- AV (Attack Vector): N=Network, A=Adjacent, L=Local, P=Physical
- AC (Attack Complexity): L=Low, H=High
- PR (Privileges Required): N=None, L=Low, H=High
- UI (User Interaction): N=None, R=Required
- S (Scope): U=Unchanged, C=Changed
- C (Confidentiality): N=None, L=Low, H=High
- I (Integrity): N=None, L=Low, H=High
- A (Availability): N=None, L=Low, H=High

**Common OpenAEV Vulnerability Scores**:

| Finding | Typical CVSS | Vector |
|---------|-------------|--------|
| Hardcoded secret | 7.5 (High) | AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:N/A:N |
| Missing @AccessControl | 8.1 (High) | AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:H/A:N |
| Cross-tenant data leak | 8.7 (High) | AV:N/AC:L/PR:L/UI:N/S:C/C:H/I:N/A:N |
| SQL injection (native query) | 9.8 (Critical) | AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H |
| Missing input validation | 5.3 (Medium) | AV:N/AC:L/PR:N/UI:N/S:U/C:N/I:L/A:N |
| Stack trace in error response | 5.3 (Medium) | AV:N/AC:L/PR:N/UI:N/S:U/C:L/I:N/A:N |
| JPA entity in API response | 4.3 (Medium) | AV:N/AC:L/PR:L/UI:N/S:U/C:L/I:N/A:N |
| Missing tenant_id in native query | 6.5 (Medium) | AV:N/AC:L/PR:L/UI:N/S:U/C:H/I:N/A:N |

### Step 5 — Triage & Fix

For each scored finding:

| CVSS Score | Severity | Action |
|-----------|----------|--------|
| 0.0 - 3.9 | Low | ✅ Auto-fix immediately |
| 4.0 - 6.9 | Medium | ✅ Auto-fix immediately |
| 7.0 - 8.9 | High | ⚠️ **STOP** — consult user before fixing |
| 9.0 - 10.0 | Critical | 🚫 **BLOCK** — consult user immediately, propose fix |

**Auto-fix procedure** (CVSS < 7.0):
1. Apply the fix
2. Re-run affected tests
3. Re-run the security scan to confirm fix
4. Commit: `[backend] fix({feature}): fix {vulnerability} (CVSS {score})`

**High/Critical procedure** (CVSS ≥ 7.0):
1. Document the finding with full CVSS vector
2. Propose a fix with explanation
3. Consult user via `ask_user`
4. Apply fix only after user approval
5. Commit: `[backend] fix({feature}): fix {vulnerability} (CVSS {score})`

### Step 6 — CI Hook Validation

Verify the feature doesn't break CI:

```bash
# Backend CI pipeline simulation
mvn spotless:check
mvn clean install -DskipTests
mvn test
mvn jacoco:check

# Frontend CI pipeline simulation
cd openaev-front
yarn install
yarn build
yarn check-ts
yarn lint
yarn test
```

If any CI step fails, fix and re-run.

### Step 7 — Report

Generate a test/security report:

```markdown
## Test & Security Report: SPEC-{NNN}

### Test Results
| Suite | Status | Details |
|-------|--------|---------|
| Backend (mvn test) | ✅/❌ | {X} tests passed, {Y} failed |
| Coverage (jacoco) | ✅/❌ | {X}% line, {Y}% branch |
| Frontend (yarn test) | ✅/❌ | {X} tests passed |
| E2E (playwright) | ✅/❌/⏭️ | {status} |

### Security Scan Results
| Finding | CVSS | Severity | Status |
|---------|------|----------|--------|
| {description} | {score} | {level} | ✅ Fixed / ⚠️ Pending |

### CI Validation
| Step | Status |
|------|--------|
| spotless:check | ✅/❌ |
| mvn install | ✅/❌ |
| mvn test | ✅/❌ |
| jacoco:check | ✅/❌ |
| yarn lint | ✅/❌ |
| yarn check-ts | ✅/❌ |
| yarn test | ✅/❌ |
```

Update spec status to "Tested" and commit the report.
