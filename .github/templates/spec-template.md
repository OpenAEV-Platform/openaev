# Feature Specification: [FEATURE NAME]

**Spec ID**: SPEC-[NNN]
**Feature Branch**: `feature/[short-name]`
**Created**: [DATE]
**Status**: Draft | Interview | Product Review | Staff Review | Security Review | Ready | Implementing | Complete
**Author**: [name]

---

## 1. Summary

[1-2 paragraph description of the feature: what it does, who it's for, and why it matters]

## 2. User Stories & Acceptance Criteria

<!--
  Stories are PRIORITIZED as user journeys ordered by importance.
  Each story must be INDEPENDENTLY TESTABLE.
  Use Gherkin format (Given/When/Then) for acceptance scenarios.
-->

### US-1: [Brief Title] (Priority: P1) 🎯 MVP

**As a** [actor], **I want** [action], **so that** [benefit].

**Why this priority**: [Value explanation]

**Acceptance Scenarios**:

```gherkin
Scenario: [Scenario name]
  Given [initial state]
  When [action performed]
  Then [expected outcome]
  And [additional assertion]

Scenario: [Error scenario name]
  Given [initial state]
  When [invalid action]
  Then [error handling]
```

---

### US-2: [Brief Title] (Priority: P2)

**As a** [actor], **I want** [action], **so that** [benefit].

**Why this priority**: [Value explanation]

**Acceptance Scenarios**:

```gherkin
Scenario: [Scenario name]
  Given [initial state]
  When [action performed]
  Then [expected outcome]
```

---

### US-3: [Brief Title] (Priority: P3)

[Same format as above]

---

### Edge Cases

- What happens when [boundary condition]?
- How does the system handle [error scenario]?
- What if [concurrent access / race condition]?

## 3. Functional Requirements

- **FR-001**: System MUST [specific capability]
- **FR-002**: System MUST [specific capability]
- **FR-003**: Users MUST be able to [key interaction]

## 4. Security Requirements

<!--
  Mandatory section — filled by the Security Agent during spec creation.
  Maps to OpenAEV security.instructions.md rules.
-->

### Access Control

- **Resource Type**: [ResourceType enum value, e.g. ASSESSMENT, SCENARIO]
- **Capabilities needed**: ACCESS_X, MANAGE_X, DELETE_X
- **Access model**: [capability-based | grant-managed | open-read | sub-resource]
- **`@AccessControl` on every endpoint**: Yes

### Tenant Isolation

- **Tenant-scoped entity**: [Yes/No]
- **`@Filter("tenantFilter")`**: [Required/N/A]
- **Native queries**: [None expected / Will include `WHERE tenant_id`]
- **`tenant_id` in API response**: Never

### Data Exposure

- **DTO-only responses**: [Confirm — no JPA entities returned]
- **Sensitive fields excluded**: [List fields hidden from response]
- **Error messages**: [Generic — no stack traces to client]

### Threat Model

| Threat | Impact | Mitigation |
|--------|--------|------------|
| [e.g., IDOR via direct ID access] | [High] | [@AccessControl + ownership check] |
| [e.g., tenant data leak] | [Critical] | [Hibernate filter + native query guard] |

## 5. Technical Context

<!--
  Filled by the Staff Agent — maps the feature to OpenAEV modules.
-->

### Affected Modules

| Module | Changes |
|--------|---------|
| `openaev-model` | [New entity, repository] |
| `openaev-api` | [Service, controller, DTOs, mapper, migration] |
| `openaev-front` | [Actions, pages, components] |

### Key Entities

- **[Entity]**: [What it represents, key attributes, relationships]
- **[Entity]**: [What it represents, key attributes, relationships]

### Database Schema

```sql
-- Table: [table_name]
CREATE TABLE [table_name] (
  [entity]_id VARCHAR(255) NOT NULL,
  [entity]_name VARCHAR(255) NOT NULL,
  tenant_id VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
  CONSTRAINT pk_[table_name] PRIMARY KEY ([entity]_id),
  CONSTRAINT fk_[table_name]_tenant FOREIGN KEY (tenant_id)
    REFERENCES tenants(tenant_id) ON DELETE CASCADE
);
CREATE INDEX idx_[table_name]_tenant ON [table_name](tenant_id);
```

### API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| POST | `/api/{entities}/search` | Search with pagination | ACCESS_X |
| POST | `/api/{entities}` | Create | MANAGE_X |
| GET | `/api/{entities}/{id}` | Get by ID | ACCESS_X |
| PUT | `/api/{entities}/{id}` | Update | MANAGE_X |
| DELETE | `/api/{entities}/{id}` | Delete | DELETE_X |

## 6. Test Plan

<!--
  Defines what tests are required for this feature.
-->

### Backend Tests

- [ ] Integration test: CRUD operations (`{Feature}ApiTest`)
- [ ] Integration test: Permission checks (with/without capabilities)
- [ ] Integration test: Tenant isolation (cross-tenant access denied)
- [ ] Unit test: [Complex business logic, if any]

### Frontend Tests

- [ ] Vitest: [Component behavior, if applicable]
- [ ] E2E (Playwright): [User journey, if applicable]

### Security Tests

- [ ] RBAC: endpoint access without required capability → 403
- [ ] Tenant isolation: access other tenant's data → 404/403
- [ ] Input validation: malformed input → 400 (not 500)

## 7. Success Criteria

- **SC-001**: [Measurable outcome, e.g. "Users can complete X in under Y seconds"]
- **SC-002**: [Coverage target, e.g. "All acceptance scenarios pass"]
- **SC-003**: [Performance, e.g. "Search returns results in <500ms for 10k records"]

## 8. Assumptions & Constraints

- [Assumption about scope, e.g. "Mobile support is out of scope for v1"]
- [Dependency, e.g. "Requires existing User entity and authentication"]
- [Constraint, e.g. "Must not break existing API contracts"]

## 9. Agent Review Log

<!--
  Automatically populated during the spec creation pipeline.
  Each agent appends their findings here.
-->

### Product Agent Review

- **Date**: [DATE]
- **Status**: [✅ Approved | ⚠️ Approved with notes | 🚫 Blocker]
- **Findings**: [Summary of review]

### Staff Agent Review

- **Date**: [DATE]
- **Status**: [✅ Approved | ⚠️ Approved with notes | 🚫 Blocker]
- **Findings**: [Summary of review]

### Security Agent Review

- **Date**: [DATE]
- **Status**: [✅ Approved | ⚠️ Approved with notes | 🚫 Blocker]
- **Findings**: [Summary of review]
