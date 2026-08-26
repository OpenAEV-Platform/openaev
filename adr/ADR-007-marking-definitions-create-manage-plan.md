# ADR-007: Marking definitions (create/manage) - brainstorm and implementation plan

|         |                                                        |
|---------|--------------------------------------------------------|
| Status  | Proposed                                               |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7512 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7513 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7514 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7515 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7516 |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/7517 |

## 1. Context

Task 1 introduces the foundation for marking-based visibility controls in OpenAEV.
Scope from the provided user stories:

- US1: split RBAC into two independent capability chains:
  - Marking definitions: Access -> Manage -> Delete
  - Assign marking: Access -> Assign -> Delete
- US2: add a dedicated Marking Definitions entry/page under Settings -> Security
- US3: create marking definitions (type, definition, color, order)
- US4: edit existing definitions
- US5: delete definitions with in-use warning
- US6: preload default TLP definitions on platform initialization and protect them from deletion

Product constraints confirmed in the source document:

- Type is user-defined, not restricted to TLP.
- Type is immutable after creation (not editable from UI or API update).
- TLP is a built-in default type only.
- Order comparison is within a given type only.
- Menu/actions are hidden when unauthorized (not disabled).
- BYPASS capability grants access.

## 2. Decision drivers

- Security and segregation of duties (definition management vs assignment operations).
- Multi-tenant safety with v2 isolation (no cross-tenant visibility or modifications).
- Consistency with existing OpenAEV RBAC hierarchy and UI behavior.
- Backward-safe rollout (defaults preloaded, no forced migrations for users).
- Search/filter UX parity with existing admin list screens.

## 3. Brainstorm outcomes

### 3.1 Domain model proposal

Introduce a new entity `MarkingDefinition` (tenant-scoped, v2 table) with fields:

- `marking_definition_id` (UUID string)
- `marking_definition_type` (string, required)
- `marking_definition_definition` (string, required) - human-visible label like `TLP:AMBER`
- `marking_definition_color` (string, optional hex with validation)
- `marking_definition_order` (integer, required, `>= 0`)
- `marking_definition_protected` (boolean, default false) - true for built-in or locked rows
- audit fields (`created_at`, `updated_at`)
- `tenant_id` (not null)

Multi-tenancy mode for this feature:

- implement as tenant **v2** (`TenantStatementInspector` + `TxCtx`), not v1 Hibernate `@Filter`
- once activated in `openaev.tenant.active-tables`, do not add `@Filter("tenantFilter")` on this table
- every transactional entrypoint that can reach `marking_definitions` must declare `TxCtx`

Uniqueness and ordering rules:

- unique by tenant on (`marking_definition_type`, `marking_definition_definition`)
- order must be a non-negative integer (0 or greater)
- duplicate order values are allowed within the same type
- order has meaning only inside one type (sorting/precedence), but it is not unique

### 3.2 RBAC model proposal

Add a new top-level capability group `MARKING` with two independent chains:

- definitions chain:
  - `ACCESS_MARKING_DEFINITION`
  - `MANAGE_MARKING_DEFINITION`
  - `DELETE_MARKING_DEFINITION`
- assignment chain:
  - `ACCESS_MARKING_ASSIGNMENT`
  - `ASSIGN_MARKING`
  - `DELETE_MARKING_ASSIGNMENT`

Behavior:

- parent auto-enable cascade follows current capability tree behavior
- BYPASS continues to override both chains

### 3.3 API and UX proposal

- Add page route under Security menu: Marking Definitions
- Add CRUD endpoints for Marking Definitions with DTOs and pagination/search
- Add list columns required by US2: Type, Definition, Color, Order, Creation date
- List must support sorting on Type, Definition, Color, Order, and Creation date
- List must support filtering on Type, Definition, Color, Order, and Creation date
- Search must cover Type, Definition, Color, Order, and Creation date
- Add create/edit modal with required validation for type/definition/order (order `>= 0`)
- Create modal fields are: Type, Definition, Color, Order
- Type input supports selecting an existing type or entering a new custom type (not restricted to TLP)
- Color uses a color picker UI and stores a normalized color value (hex)
- Order is required and numeric
- After successful creation, the new marking appears immediately in the list
- In edit mode, `type` is read-only/disabled on frontend and ignored/rejected if sent to update API
- Add delete confirmation dialog
- For protected markings, hide/disable delete action in the UI
- If marking is in use, return warning payload and confirm deletion explicitly (or block based on product final choice)
- After a successful delete, refresh impacted frontend stores so Assets and Users no longer display the removed marking

### 3.4 Seed/default proposal (US6)

On tenant creation, seed the 5 TLP defaults through the tenant datapack:

- TLP:CLEAR (1)
- TLP:GREEN (2)
- TLP:AMBER (3)
- TLP:AMBER+STRICT (4)
- TLP:RED (5)

Mark these rows as `protected=true` so update and delete are forbidden.
Protected rows are immutable from the UI and API (no edit, no delete).
Seeding must be idempotent in case datapack application is retried.

## 4. Considered options

### Option A: hardcode only TLP enum

Pros:
- simple validation

Cons:
- conflicts with requirement that type is user-defined
- blocks PAP/custom future types

### Option B: user-defined type string + per-type ordering (selected)

Pros:
- matches all user stories
- extensible without schema changes
- keeps TLP as defaults, not restriction

Cons:
- needs stronger validation while keeping order non-unique within a type

### Option C: separate tables for types and definitions

Pros:
- normalized type management

Cons:
- larger scope for Task 1
- not required by current acceptance criteria

## 5. Decision

Choose Option B.

Implement a tenant-scoped `MarkingDefinition` with user-defined `type`, per-type `order`, and default non-deletable TLP rows seeded by the tenant datapack at tenant creation.
Implement RBAC split with two independent capability chains under a new `MARKING` group.

## 6. Implementation plan (single feature plan, chunked delivery)

### Chunk 1 - RBAC foundation (US1)

Backend:

- update capability catalog and parent hierarchy
- expose new group in capability tree API
- ensure permission checks include BYPASS behavior

Frontend:

- map capability strings in permission parser
- ensure role editor renders `MARKING` group and both chains
- gate Security menu entry and Group/Asset marking actions by new capabilities

Tests:

- role capability cascade and independence tests
- hidden vs visible menu/action behavior
- BYPASS override tests

### Chunk 2 - Marking Definitions backend CRUD (US2/US3/US4/US5 backend side)

Backend model/repository/service/API:

- create `MarkingDefinition` entity, repository, service
- create search endpoint with pagination/filter/sort
- expose searchable/filterable/sortable fields for Type, Definition, Color, Order, and Creation date
- create create/update/delete endpoints and DTOs/mappers
- enforce uniqueness and validation rules
- enforce create validation: required `type`, `definition`, and non-negative numeric `order` (`>= 0`)
- enforce immutable type on update (`marking_definition_type` cannot change after creation)
- enforce no delete for `protected=true` rows (backend guard)
- enforce no update for `protected=true` rows (backend guard)
- TODO (future): implement in-use check API contract for delete warning flow
- TODO (future): define and implement deletion side-effects for linked entities (Assets and Users): remove/unlink the deleted marking and keep data consistent
- wire `TxCtx` on API/service transactional entrypoints that read/write marking definitions
- register required entrypoints in `TenantScopedEntrypointsTxCtxArchTest`

Migration:

- add table and indexes
- add unique constraint `(type, definition, tenant_id)`
- keep `(type, order, tenant_id)` non-unique to allow same order in a type
- include FK/index on `tenant_id`
- activate `marking_definitions` in `openaev.tenant.active-tables` in the same rollout commit

Tests:

- integration tests for CRUD, validation, uniqueness, and non-deletable defaults
- integration tests for type immutability on update (backend rejects/ignores type changes)
- integration tests for search/filter/sort on Type, Definition, Color, Order, and Creation date
- integration tests for delete propagation: deleted marking is no longer linked from Assets and Users
- tenant isolation tests for search and mutations

### Chunk 3 - Marking Definitions frontend CRUD (US2/US3/US4/US5 frontend side)

Frontend:

- add Marking Definitions page in Settings -> Security
- add paginated table, search, filters, and sorting
- render columns Type, Definition, Color, Order, and Creation date
- add create/edit dialog with field validation
- make `type` non-editable in edit form (readonly/disabled field)
- when `order` changes in edit mode, show a warning confirmation dialog before saving (reuse delete confirmation dialog pattern)
- add delete confirmation
- TODO (future): add in-use warning path
- disable/hide edit action for `marking_definition_protected=true`
- disable/hide delete action for `marking_definition_protected=true`
- on delete success, refresh Marking Definitions store/query
- TODO (future): refresh Assets and Users stores/queries if unlink-on-delete is implemented
- hide entire feature when unauthorized

Tests:

- component tests for visibility, validations, and CRUD interactions
- component tests for edit form type field locked in update mode
- component tests for order-change warning dialog and confirmed save path
- component tests for column rendering and search/filter/sort behavior on Type, Definition, Color, Order, and Creation date
- component tests for create modal: field presence, required-field validation, non-negative numeric order validation (`>= 0`), and immediate list refresh after create
- component tests for delete refresh behavior on Marking Definitions, Assets, and Users views
- permission-based rendering tests

### Chunk 4 - Default seed lifecycle (US6)

Backend:

- seed TLP defaults idempotently in the tenant datapack (applied at tenant creation)
- set `protected=true` for defaults

Tests:

- datapack idempotency test on tenant provisioning (no duplicates)
- update/delete forbidden for protected rows (backend) and edit/delete actions hidden/disabled (frontend)
- coexistence test with custom types

## 7. API contract sketch

Base path (tenant API style):

- `POST /api/{tenant}/marking-definitions/search` -> `Page<MarkingDefinitionOutput>`
- `POST /api/{tenant}/marking-definitions` -> create
- `PUT /api/{tenant}/marking-definitions/{id}` -> update
- `DELETE /api/{tenant}/marking-definitions/{id}` -> delete

Output fields:

- `marking_definition_id`
- `marking_definition_type`
- `marking_definition_definition`
- `marking_definition_color`
- `marking_definition_order`
- `marking_definition_protected`
- `marking_definition_created_at`

## 8. Data safety, tenancy, and performance notes

- Entity must be tenant-scoped with `tenant_id NOT NULL` and never expose tenant relation in output.
- Tenant isolation must use v2 (`TxCtx` + statement inspector), not v1 `@Filter` for this table.
- Any native query touching this table must use a SQL shape accepted by `TenantStatementInspector`.
- Search endpoint must be paginated; avoid unbounded lists.
- Add DB indexes for all FK and common filter/sort fields (`type`, `definition`, `order`, `created_at`).
- Keep delete flow transactionally safe with explicit in-use checks.

## 9. Telemetry plan (minimum)

Use classic OpenAEV audit expectations for:

- marking definition viewed/searched
- create success/failure
- update success/failure
- delete attempted/blocked/success
- RBAC-denied access attempts

Optional counters (future, if needed):

- `marking_definition_created_total`
- `marking_definition_updated_total`
- `marking_definition_deleted_total`
- `marking_definition_delete_blocked_in_use_total`

## 10. Risks and open questions

- TODO (future): Delete semantics for in-use markings (block hard vs force detach after explicit confirmation).
- TODO (future): Existing unlink/refresh behavior for Assets and Users after marking deletion must be verified; implement if missing.
- Decision (option 1): protected markings are immutable (no edit, no delete).
- Decision (option 2): assignment actions for Groups and Assets remain partially stubbed behind capabilities in Task 1.
- Deferred: import/export behavior is out of current scope and will be defined later.

## 11. Acceptance mapping checklist

- US1 AC1-AC5: RBAC tree, independence, hidden behavior, BYPASS, parent cascade.
- US2 AC1-AC6: menu + list columns (Type, Definition, Color, Order, Creation date) + search/filter/sort on those columns + auth denial.
- US3 AC1-AC4: create modal fields (Type/Definition/Color/Order), existing-or-new Type input, required Type/Definition/non-negative numeric Order validation (`>= 0`), immediate list refresh after create, and per-type order semantics.
- US4 AC1-AC3: edit action and immediate list refresh, with `type` non-editable and warning confirmation when `order` is changed.
- US5 AC1-AC3: delete action, confirmation, in-use warning behavior.
- US6 AC1-AC2: default TLP preloaded and non-deletable.

## 12. Rollout

- Feature branch with sequential chunks above.
- Keep migrations idempotent and forward-only.
- Validate RBAC and tenant v2 isolation first, then UI exposure, then seed behavior.
- Prepare release note: "Marking Definitions foundation (RBAC + CRUD + default TLP seeds)".

