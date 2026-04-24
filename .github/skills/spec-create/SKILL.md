---
name: spec-create
description: >-
  Creates a new feature specification through a multi-agent pipeline: interview → product agent →
  staff agent → security agent. Blockers halt the pipeline for user consultation.
  Use when asked to create a new feature spec or when user says "/spec create".
---

# Create Feature Specification

## Prerequisites

- Feature description (natural language from user)
- Access to `.github/specs/constitution.md` for project principles
- Access to `.github/templates/spec-template.md` for spec format

## Procedure

### Step 1 — Initialize the Spec

1. **Find next spec number**:
   ```bash
   ls .github/specs/ | grep "^SPEC-" | sort | tail -1
   ```
   If no specs exist, start at `SPEC-001`.

2. **Generate short name** from description (2-4 words, kebab-case):
   - "I want user authentication" → `user-auth`
   - "Add assessment import" → `assessment-import`

3. **Create spec directory and file**:
   ```bash
   mkdir -p .github/specs/SPEC-{NNN}-{short-name}
   cp .github/templates/spec-template.md .github/specs/SPEC-{NNN}-{short-name}/spec.md
   ```

4. **Fill metadata**: Spec ID, date, status = "Interview"

### Step 2 — Interview Mode 🎤

Conduct a focused interview with the user to understand the feature. Ask questions ONE AT A TIME using `ask_user`.

**Round 1 — Scope**:
- What is the core problem this feature solves?
- Who are the target users (admin, planner, player, operator)?
- What are the must-have vs nice-to-have capabilities?

**Round 2 — Behavior**:
- What does the happy path look like step by step?
- What should happen on errors or edge cases?
- Are there limits or caps (e.g., max items, rate limits)?

**Round 3 — Context**:
- Does this feature relate to existing entities/features?
- Is this tenant-scoped or platform-level?
- Are there external integrations involved?

**IMPORTANT**: Make informed guesses for standard patterns. Only ask about decisions that significantly impact scope or architecture. Maximum 5-7 questions total.

After the interview, update spec status to "Product Review" and fill:
- §1 Summary
- §3 Functional Requirements
- §8 Assumptions & Constraints

### Step 3 — Product Definer 📋

Launch the **Product Definer** agent to enrich the spec:

**Agent task**: "Review and enrich the spec at `.github/specs/SPEC-{NNN}/spec.md`. Add Gherkin user stories, acceptance criteria, priorities, edge cases, and success criteria."

The Product Definer will:
1. Create user stories in Gherkin format (Given/When/Then)
2. Assign priorities (P1 = MVP, P2, P3)
3. Define edge cases
4. Write measurable success criteria
5. Update §2, §6 (test plan based on acceptance criteria), §7

**Blocker check**: If the Product Definer raises a 🚫 Blocker:
- **STOP the pipeline**
- Present the blocker to the user with `ask_user`
- Wait for resolution before proceeding
- Update the spec with the resolution

If no blocker, update spec status to "Staff Review".

### Step 4 — Staff Definer 🏗️

Launch the **Staff Definer** agent to add technical context:

**Agent task**: "Enrich the spec at `.github/specs/SPEC-{NNN}/spec.md` with technical context. Map to OpenAEV modules, define entities, database schema, API endpoints. Check for anti-patterns."

The Staff Definer will:
1. Map the feature to OpenAEV modules (openaev-model, openaev-api, openaev-front)
2. Define entity schema following database.instructions.md conventions
3. Design API endpoints following backend.instructions.md conventions
4. Validate architecture against constitution principles II, IV, VIII
5. Update §5 (Technical Context)

**Blocker check**: If the Staff Definer raises a 🚫 Blocker:
- **STOP the pipeline**
- Present the blocker to the user with `ask_user`
- Wait for resolution before proceeding

If no blocker, update spec status to "Security Review".

### Step 5 — Security Definer 🔒

Launch the **Security Definer** agent to add security requirements:

**Agent task**: "Review the spec at `.github/specs/SPEC-{NNN}/spec.md`. Add threat model, access control plan, tenant isolation requirements. Check for security gaps."

The Security Definer will:
1. Define access control model (ResourceType, Capabilities)
2. Assess tenant isolation requirements
3. Build a threat model table
4. Identify data exposure risks
5. Update §4 (Security Requirements)

**Blocker check**: If the Security Definer raises a 🚫 Blocker:
- **STOP the pipeline**
- Present the blocker to the user with `ask_user`
- Wait for resolution before proceeding

If no blocker, update spec status to "Ready".

### Step 6 — Summary & Finalization

Present a summary to the user:

```markdown
## Spec Summary: SPEC-{NNN} — {Feature Name}

### User Stories
- US-1 (P1): {title} — {# acceptance scenarios}
- US-2 (P2): {title} — {# acceptance scenarios}

### Architecture
- Modules: {list of affected modules}
- New entities: {list}
- New endpoints: {count}

### Security
- Access model: {type}
- Tenant-scoped: {yes/no}
- Threats identified: {count}

### Agent Review Log
- Product: {status}
- Staff: {status}
- Security: {status}

### Open Questions (if any)
1. {question from any agent}
```

If there are open questions, ask the user and update the spec.

### Step 7 — Commit the Spec

```bash
git add .github/specs/SPEC-{NNN}-{short-name}/
git commit -m "[agent] feat(spec): create SPEC-{NNN} — {feature name}"
```

The spec is now ready for `/spec plan`.
