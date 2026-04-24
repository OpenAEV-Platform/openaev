---
name: review-product
description: >-
  Product review checklist: user stories completeness, acceptance criteria quality,
  Gherkin format, edge cases, user value. Used during spec creation and post-implementation review.
---

# Product Review

## Procedure

### Step 1 — Validate User Stories

For each user story in the spec:

- [ ] Has a clear **actor** (admin, planner, player, operator — not just "user")
- [ ] Has a specific **action** (not vague verbs like "manage" or "handle")
- [ ] Has a measurable **benefit** (why this matters to the actor)
- [ ] Is **independently testable** — could be an MVP on its own
- [ ] Has a **priority** assigned (P1, P2, P3)

### ✅ Good User Story

```markdown
### US-1: Create Assessment Campaign (Priority: P1) 🎯 MVP

**As a** security planner with MANAGE_ASSESSMENT capability,
**I want** to create an assessment campaign with a name, description, and date range,
**so that** I can organize and schedule breach simulation exercises.

**Why this priority**: Core feature — without creating assessments, no other feature works.
```

### ❌ Bad User Story

```markdown
### US-1: Assessment Management (Priority: P1)

**As a** user,
**I want** to manage assessments,
**so that** I can use the system.
```

**Why bad**: "user" is too vague (which role?), "manage" is too broad (create? edit? delete? search?), "use the system" is not a benefit.

### Step 2 — Validate Acceptance Criteria

For each acceptance scenario:

- [ ] Uses proper **Gherkin format** (Given/When/Then)
- [ ] **Given** sets up a specific, reproducible state
- [ ] **When** describes exactly ONE action
- [ ] **Then** has verifiable assertions (status codes, field values, state changes)
- [ ] Covers both **happy path** and **error path**

### ✅ Good Acceptance Criteria

```gherkin
Scenario: Admin creates an assessment with valid data
  Given I am authenticated as an admin with MANAGE_ASSESSMENT capability
  And no assessment exists with name "Q4 Red Team"
  When I POST to /api/assessments with name "Q4 Red Team" and description "Quarterly exercise"
  Then the response status is 201
  And the response body contains assessment_id, assessment_name, and created_at
  And the assessment is visible in my tenant's assessment list

Scenario: Creating an assessment with duplicate name fails
  Given I am authenticated as an admin with MANAGE_ASSESSMENT capability
  And an assessment exists with name "Q4 Red Team" in my tenant
  When I POST to /api/assessments with name "Q4 Red Team"
  Then the response status is 400
  And the response body contains an error message about duplicate name
```

### ❌ Bad Acceptance Criteria

```gherkin
Scenario: Create assessment
  Given the system is ready
  When I create an assessment
  Then it should be created successfully
```

**Why bad**: No specific state, no specific action, no verifiable assertion.

### Step 3 — Validate Edge Cases

Check that the spec addresses:

- [ ] **Empty/null inputs**: What happens with blank required fields?
- [ ] **Boundary values**: Max name length? Max description length?
- [ ] **Concurrent access**: Two users creating/editing the same resource?
- [ ] **Large datasets**: What happens with 10k+ records?
- [ ] **Permission boundaries**: What can each role do vs not do?
- [ ] **Tenant boundaries**: Can tenant A see tenant B's data?
- [ ] **Deletion cascades**: What happens to related data when parent is deleted?

### Step 4 — Validate Success Criteria

- [ ] Each criterion is **measurable** (specific numbers, percentages, times)
- [ ] Criteria are **technology-agnostic** (no mention of frameworks, DBs)
- [ ] Criteria are **user-focused** (describe outcomes, not internals)

### ✅ Good Success Criteria

- "Users can create an assessment in under 30 seconds"
- "Search returns results in <500ms for datasets up to 10k assessments"
- "All P1 acceptance scenarios pass in the integration test suite"

### ❌ Bad Success Criteria

- "API responds in under 200ms" (too technical)
- "Tests pass" (too vague — which tests?)
- "It works well" (not measurable)

### Step 5 — Report

Document findings using conventional comments:

- `issue (blocking):` — missing user stories, untestable acceptance criteria, no error handling
- `suggestion (non-blocking):` — additional edge cases, better phrasing, missing priorities
- `praise:` — well-written scenarios, thorough edge case coverage
- `note:` — informational items, context for future reference

### Blocker Criteria

A finding is a **🚫 Blocker** if:
- A user story has no clear actor, action, or benefit
- All acceptance criteria use vague verbs ("works", "handles", "properly")
- No error/edge case scenarios exist for the feature
- The feature scope is unclear or contradictory
- Success criteria are unmeasurable
