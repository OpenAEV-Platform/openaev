# ADR-007 Implementation Plan - Marking Definitions (Single-Chunk Delivery)

## 1) Goal

Deliver ADR-007 Task 1 in one integrated execution chunk, behind feature flag `MARKING`, with tenant-v2-safe backend CRUD, frontend classic paginated list UX, protected default TLP seeds, and complete validation gates.

## 2) Confirmed decisions and constraints

- `marking_definition_order` is required and must be `>= 0`.
- Protected rows are immutable: no edit and no delete.
- Assignment actions remain partially stubbed in Task 1.
- In-use delete flow and unlink side-effects are deferred TODOs.
- Import/export behavior is deferred.
- Feature flag key is `MARKING`; frontend behavior is menu masking when disabled.
- Front list uses classic pagination component pattern.

## 3) Single execution chunk (end-to-end)

### 3.1 Workstream A - Architecture and baseline alignment

- Create a baseline scope note in the PR description with impacted layers: RBAC, model, migration, API, frontend, seeds, tests.
- Confirm no code is added in deprecated `openaev-framework`.
- Keep API additions in `io.openaev.api.*` (not legacy `io.openaev.rest`).
- Define rollback strategy: keep `MARKING` default off so merge is dark-deploy safe.

### 3.2 Workstream B - RBAC and feature flag (`MARKING`)

- Add capability chains:
  - Definitions: `ACCESS_MARKING_DEFINITION` -> `MANAGE_MARKING_DEFINITION` -> `DELETE_MARKING_DEFINITION`
  - Assignment: `ACCESS_MARKING_ASSIGNMENT` -> `ASSIGN_MARKING` -> `DELETE_MARKING_ASSIGNMENT`
- Register parent hierarchy and ensure BYPASS still grants effective access.
- Expose MARKING group in capability tree API.
- Add/confirm preview flag `MARKING` backend plumbing (default off).
- Frontend:
  - Parse new capabilities.
  - Render MARKING group in role editor.
  - Mask Marking Definitions menu entry when `MARKING` is disabled.
  - Keep route/action guards consistent with capabilities and backend checks.

### 3.3 Workstream C - Data model, migration, and tenant-v2 activation

- Create entity `MarkingDefinition` (tenant-scoped v2 table) with fields:
  - `marking_definition_id`
  - `marking_definition_type`
  - `marking_definition_definition`
  - `marking_definition_color`
  - `marking_definition_order`
  - `marking_definition_protected`
  - `tenant_id`
  - audit timestamps
- Add Java Flyway migration with idempotent guards.
- Add indexes/constraints:
  - unique `(marking_definition_type, marking_definition_definition, tenant_id)`
  - non-unique `(marking_definition_type, marking_definition_order, tenant_id)`
  - index on `tenant_id`
  - indexes for `type`, `definition`, `order`, `created_at`
- Activate `marking_definitions` in `openaev.tenant.active-tables` in same go-live commit.
- Add required `TxCtx` parameters on entrypoints reaching this table.
- Update `TenantScopedEntrypointsTxCtxArchTest` registration for all required entrypoints.

### 3.4 Workstream D - Backend CRUD API and business rules

- Build full stack: repository, service, DTOs, mapper, controller.
- Endpoints:
  - `POST /api/{tenant}/marking_definitions/search`
  - `POST /api/{tenant}/marking_definitions`
  - `PUT /api/{tenant}/marking_definitions/{id}`
  - `DELETE /api/{tenant}/marking_definitions/{id}`
- Validation and invariants:
  - required `type`, `definition`, `order`
  - `order >= 0`
  - `type` immutable on update
  - `protected=true` blocks update/delete
- Keep explicit TODO boundaries in code/API behavior:
  - in-use delete warning flow not implemented in Task 1
  - unlink propagation to Assets/Users not implemented in Task 1

### 3.5 Workstream E - Frontend page and forms

- Add Marking Definitions page under Security.
- Implement classic list pattern with pagination/search/filter/sort.
- Columns: Type, Definition, Color, Order, Creation date.
- Create/Edit dialog:
  - required validation
  - non-negative order validation
  - type editable on create, read-only on edit
  - order-change confirmation in edit mode
- Delete:
  - confirmation dialog
  - hide/disable actions for protected rows
  - refresh Marking Definitions list/store on success
- Feature flag behavior:
  - menu entry masked when `MARKING` disabled

### 3.6 Workstream F - Default TLP seed lifecycle

- Seed on tenant creation via datapack (idempotent):
  - `TLP:CLEAR (1)`
  - `TLP:GREEN (2)`
  - `TLP:AMBER (3)`
  - `TLP:AMBER+STRICT (4)`
  - `TLP:RED (5)`
- Set `protected=true` for seeded rows.

### 3.7 Workstream G - Telemetry and audit

- Apply classic OpenAEV audit coverage for view/search/create/update/delete attempt outcomes and RBAC denials.
- Optional counters remain future and non-blocking for Task 1.

### 3.8 Workstream H - Deferred items tracking

- Create follow-up issues for:
  - in-use delete policy
  - unlink side-effects + Assets/Users refresh
  - import/export behavior
  - assignment UX completion for Groups/Assets

## 4) All OpenAEV skills usage map

This delivery uses every listed skill either as implementation driver or as mandatory review gate.

| Skill | How it is used in this plan | Expected output |
|---|---|---|
| `create-feature-module` | Primary scaffold for entity -> repository -> service -> API -> frontend path | Base implementation skeleton aligned with layering |
| `add-migration` | Build Java Flyway migration + indexes/constraints + active-tables update | Safe, idempotent migration |
| `activate-tenant-table` | Apply tenant-v2 activation checklist + TxCtx call graph inventory | v2 isolation correctness and entrypoint coverage |
| `add-test` | Add integration/component tests for CRUD, validation, visibility | Test suite for Task 1 scope |
| `review-code` | Global review gate for architecture/convention compliance | Consolidated review findings |
| `review-security` | Verify RBAC, endpoint protection, exposure risks | Security review sign-off |
| `review-performance` | Check pagination/search/index usage and query patterns | Performance review sign-off |
| `review-multi-tenancy` | Verify tenant-v2 isolation, TxCtx propagation, cross-tenant safety | Multi-tenancy review sign-off |
| `review-migration` | Audit migration idempotency, safety, and rollout risk | Migration review sign-off |
| `review-frontend` | Validate list/form/permission/i18n patterns and flag behavior | Frontend review sign-off |
| `review-docs` | Ensure ADR/docs updates reflect functional changes | Docs coverage sign-off |
| `review-chaining-engine` | Explicitly run N/A check to confirm no chaining engine impact | Recorded N/A confirmation |
| `reduce-tx-baseline` | Apply only if any new change introduces baseline-related transaction pattern regressions | No new transaction-architecture debt |
| `add-contract-output-type` | Explicit N/A check for this feature (no new injector contract output type) | Recorded N/A confirmation |

## 5) Validation matrix (must pass before merge)

### 5.1 Backend

- Build compiles with new model/API classes.
- CRUD integration tests pass.
- Tenant-v2 isolation tests pass.
- TxCtx entrypoint arch test passes.
- Migration applies in dev profile without checksum/rerun issues.

### 5.2 Frontend

- Typecheck + lint pass on changed files.
- Component tests pass for list, forms, permissions, and feature flag behavior.
- Menu masking verified with `MARKING` disabled.

### 5.3 Review gates

- `review-code` + `review-security` + `review-performance` + `review-multi-tenancy` + `review-migration` + `review-frontend` + `review-docs` completed.
- `review-chaining-engine` recorded as N/A (no touched chaining packages/files).

## 6) Detailed Definition of Done

- RBAC MARKING chains are available and correctly inherited.
- Feature flag `MARKING` masks frontend menu entry when disabled.
- Marking Definitions CRUD works with pagination/search/filter/sort.
- Backend enforces `order >= 0`, immutable type, and protected-row guards.
- Table is tenant-v2-active with complete `TxCtx` coverage.
- Default TLP rows are seeded idempotently and protected.
- Deferred items are tracked as follow-up issues, not silently omitted.
- All quality and review gates are green.

## 7) Rollout plan

- Merge with `MARKING` off by default (dark rollout).
- Validate in controlled environment/tenant(s).
- Enable progressively after review-gate sign-off and smoke checks.
- Release note: Marking Definitions foundation (RBAC + CRUD + protected default TLP seeds).

## 8) Traceability to user stories

- US1 covered by Workstream B.
- US2 covered by Workstreams B, D, E.
- US3 covered by Workstreams D, E.
- US4 covered by Workstreams D, E.
- US5 covered by Workstreams D, E (with explicit deferred in-use/unlink TODO boundaries).
- US6 covered by Workstream F.

