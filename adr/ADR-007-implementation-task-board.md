# ADR-007 Task Board - Marking Definitions (Single Chunk)

This checklist is derived from `adr/ADR-007-implementation-plan.md` and is ready to use in issue/PR tracking.

## 0. Scope lock (before coding)

- [ ] Confirm Task 1 keeps naming as `MarkingDefinition` (entity/API/DTOs).
- [ ] Confirm deferred items remain out of scope:
  - [ ] in-use delete behavior
  - [ ] unlink propagation to Assets/Users
  - [ ] import/export behavior
  - [ ] full assignment UX for Groups/Assets
- [ ] Confirm feature flag is `MARKING` and default is OFF.
- [ ] Add PR scope note listing impacted layers (RBAC, model, migration, API, frontend, seeds, tests).

## 1. RBAC + feature flag

### Backend

- [ ] Add capabilities:
  - [ ] `ACCESS_MARKING_DEFINITION`
  - [ ] `MANAGE_MARKING_DEFINITION`
  - [ ] `DELETE_MARKING_DEFINITION`
  - [ ] `ACCESS_MARKING_ASSIGNMENT`
  - [ ] `ASSIGN_MARKING`
  - [ ] `DELETE_MARKING_ASSIGNMENT`
- [ ] Wire parent hierarchy for both chains.
- [ ] Ensure BYPASS behavior remains effective.
- [ ] Expose MARKING group in capability tree API.
- [ ] Add/confirm backend feature-flag plumbing for `MARKING` (default off).

### Frontend

- [ ] Add capability parsing/mapping for new MARKING capabilities.
- [ ] Render MARKING group in role editor UI.
- [ ] Gate marking-related actions by capabilities.
- [ ] Hide Marking Definitions menu entry when `MARKING` is disabled.
- [ ] Keep direct route access protected by existing route guards + backend RBAC.

### Tests

- [ ] Capability cascade tests.
- [ ] Capability independence tests.
- [ ] BYPASS tests.
- [ ] Front menu masking test with `MARKING=false`.

## 2. Data model + migration + tenant-v2 activation

### Model

- [ ] Add `MarkingDefinition` entity fields:
  - [ ] `marking_definition_id`
  - [ ] `marking_definition_type`
  - [ ] `marking_definition_definition`
  - [ ] `marking_definition_color`
  - [ ] `marking_definition_order`
  - [ ] `marking_definition_protected`
  - [ ] `tenant_id`
  - [ ] audit timestamps

### Migration (`add-migration` skill)

- [ ] Create new Java Flyway migration (do not edit existing migrations).
- [ ] Add table `marking_definitions` with idempotent guards.
- [ ] Add unique constraint: `(marking_definition_type, marking_definition_definition, tenant_id)`.
- [ ] Add non-unique index/constraint support for order: `(marking_definition_type, marking_definition_order, tenant_id)`.
- [ ] Add index on `tenant_id`.
- [ ] Add supporting indexes for `type`, `definition`, `order`, `created_at`.
- [ ] Activate `marking_definitions` in `openaev.tenant.active-tables` in same rollout commit.

### Tenant v2 (`activate-tenant-table` skill)

- [ ] Add `TxCtx` on every transactional entrypoint that reaches marking definitions.
- [ ] Update `TenantScopedEntrypointsTxCtxArchTest` with all required entrypoints.
- [ ] Validate no v1 `@Filter` is used for this v2-active table.

## 3. Backend CRUD API

### API surface

- [ ] Implement `POST /api/{tenant}/marking_definitions/search` (paginated).
- [ ] Implement `POST /api/{tenant}/marking_definitions`.
- [ ] Implement `PUT /api/{tenant}/marking_definitions/{id}`.
- [ ] Implement `DELETE /api/{tenant}/marking_definitions/{id}`.

### Business rules

- [ ] Enforce required fields: `type`, `definition`, `order`.
- [ ] Enforce `order >= 0`.
- [ ] Enforce immutable `type` on update.
- [ ] Enforce protected-row guards (`protected=true` blocks update/delete).
- [ ] Keep deferred TODO boundaries explicit:
  - [ ] no in-use warning flow in Task 1
  - [ ] no unlink propagation in Task 1

### Backend tests (`add-test` skill)

- [ ] CRUD happy-path integration tests.
- [ ] Validation tests (`order >= 0`, required fields).
- [ ] Type immutability tests.
- [ ] Protected-row update/delete rejection tests.
- [ ] Search/filter/sort tests for Type/Definition/Color/Order/Creation date.
- [ ] Tenant isolation tests (read/write).

## 4. Frontend page and forms

### UI implementation

- [ ] Add Marking Definitions page under Security.
- [ ] Use classic pagination front list component pattern.
- [ ] Render columns: Type, Definition, Color, Order, Creation date.
- [ ] Implement search/filter/sort on the same fields.
- [ ] Implement create/edit dialogs:
  - [ ] required validation
  - [ ] non-negative numeric order
  - [ ] type editable on create only
  - [ ] order-change warning confirmation on edit
- [ ] Implement delete confirmation.
- [ ] Hide/disable edit/delete for protected rows.
- [ ] Refresh Marking Definitions list/store after create/update/delete.

### Frontend tests (`add-test` skill)

- [ ] Visibility tests by permission + feature flag.
- [ ] Validation tests for form and `order >= 0`.
- [ ] List behavior tests for pagination/filter/sort/search.
- [ ] Protected-row action visibility tests.
- [ ] Success refresh tests after create/update/delete.

## 5. Default TLP seed lifecycle

- [ ] Seed defaults in tenant datapack (idempotent):
  - [ ] `TLP:CLEAR (1)`
  - [ ] `TLP:GREEN (2)`
  - [ ] `TLP:AMBER (3)`
  - [ ] `TLP:AMBER+STRICT (4)`
  - [ ] `TLP:RED (5)`
- [ ] Set `protected=true` for all seeded rows.
- [ ] Add seed idempotency tests.
- [ ] Add tests that seeded rows are immutable.
- [ ] Add coexistence test with custom types.

## 6. Skills-driven review gates (must run)

- [ ] `review-code` completed.
- [ ] `review-security` completed.
- [ ] `review-performance` completed.
- [ ] `review-multi-tenancy` completed.
- [ ] `review-migration` completed.
- [ ] `review-frontend` completed.
- [ ] `review-docs` completed.
- [ ] `review-chaining-engine` recorded as N/A (no touched chaining files).
- [ ] `reduce-tx-baseline` checked (apply only if new baseline debt appears).
- [ ] `add-contract-output-type` recorded as N/A for this feature.
- [ ] `create-feature-module` used as structure checklist to ensure complete cross-layer delivery.

## 7. Final verification before merge

- [ ] Backend formatting checks pass.
- [ ] Backend compile/tests pass.
- [ ] Frontend lint/type-check/tests pass.
- [ ] Tenant-v2 TxCtx arch checks pass.
- [ ] Migration applies cleanly.
- [ ] Feature is dark-merge safe (`MARKING` remains OFF by default).

## 8. Rollout

- [ ] Merge with `MARKING` disabled.
- [ ] Validate in controlled tenant(s).
- [ ] Progressive enablement after validation gates.
- [ ] Publish release note for Marking Definitions foundation.

## 9. Follow-up issues (deferred)

- [ ] In-use delete policy decision and implementation.
- [ ] Unlink side-effects + Assets/Users refresh.
- [ ] Import/export behavior for markings.
- [ ] Assignment UX completion for Groups/Assets.

