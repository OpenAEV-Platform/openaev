# Tasks: Attack Path Visualization

**Spec**: [SPEC-002](./spec.md)
**Plan**: [plan.md](./plan.md)
**Created**: 2026-05-07

## Format

`[ID] [P?] [Story?] Description — file path`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps to user story (US-1, US-2, etc.)

---

## Phase 1: Backend DTOs & Service (P1 MVP)

> No database changes needed. Existing entities provide all data.

- [ ] T001 [P] [US-1] Create DTO `AttackPathOutput.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
- [ ] T002 [P] [US-1] Create DTO `AttackPathNodeOutput.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
- [ ] T003 [P] [US-1] Create DTO `AttackPathEdgeOutput.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
- [ ] T004 [P] [US-1] Create DTO `AttackPathExpectationOutput.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
- [ ] T005 [P] [US-4] Create DTO `AttackPathStatsOutput.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
- [ ] T006 [US-1] Create `AttackPathService.java` — `openaev-api/src/main/java/io/openaev/service/`
  - Load workflow → steps → conditions → expectations → assets (3 batch queries)
  - Assemble graph nodes (ASSET + ACTION + EVENT types)
  - Assemble edges (chain_flow + asset_link)
  - Compute stats (prevented/detected/undetected/pending)
  - Resolve status from expectations (priority: prevented > detected > undetected > pending)
- [ ] T007 [US-1] Create `AttackPathApi.java` — `openaev-api/src/main/java/io/openaev/api/attack_path/`
  - `GET /api/exercises/{exerciseId}/attack-path`
  - `@AccessControl(resourceId = "#exerciseId", actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)`
  - Feature flag check: `CHAINING_ATTACK_PATH` → 404 if disabled

**Checkpoint**: `mvn spotless:apply` passes, endpoint returns data

---

## Phase 2: Backend Tests

> Depends on Phase 1 completion.

- [ ] T008 [P] Create integration test `AttackPathApiTest.java` — `openaev-api/src/test/java/io/openaev/api/attack_path/`
  - [ ] T008a [US-1] `given_simulationWithWorkflow_should_returnAttackPathGraph` — verifies nodes/edges structure
  - [ ] T008b [US-1] `given_simulationWithExpectations_should_resolveStatusColors` — verifies status resolution
  - [ ] T008c [Security] `given_noAccessExercise_should_return403` — RBAC check
  - [ ] T008d [Security] `given_otherTenant_should_return404` — tenant isolation
  - [ ] T008e [Security] `given_nonUuidExerciseId_should_return400` — input validation
  - [ ] T008f [Security] `given_nonExistentExercise_should_return404` — not found
  - [ ] T008g [Security] `given_featureFlagDisabled_should_return404` — feature flag
  - [ ] T008h [US-1] `given_simulationWithNoWorkflow_should_returnEmptyGraph` — empty state

**Checkpoint**: `mvn test -Dtest="AttackPathApiTest"` passes

---

## Phase 3: Frontend — Graph & Layout (P1 MVP)

> Can start after Phase 1 checkpoint (API available). Independent of Phase 2.

- [ ] T009 [P] [US-1] Create `attackPathUtils.ts` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - `resolveNodeStatus(expectations)` → prevented | detected | undetected | pending
  - `STATUS_COLORS` constant map
  - `computeLayout(nodes, edges)` → x/y positions (hierarchical left-to-right)
  - `getUpstreamNodes(nodeId, edges)` / `getDownstreamNodes(nodeId, edges)`
- [ ] T010 [US-1] Create `AttackPathGraph.tsx` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - SVG-based graph (replaces `AttackPathFlow.tsx` + `NodeAttackStep.tsx`)
  - Asset nodes: large rectangles (hostname, IP, OS, role)
  - Action nodes: smaller circles (payload name, status color)
  - Event markers: circles on chain-flow edges
  - Animated particles on chain-flow edges (CSS animation)
  - Zoom/pan via SVG viewBox manipulation
- [ ] T011 [US-4] Create `AttackPathStats.tsx` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - Top banner with prevented/detected/undetected/pending counts
  - "X of Y actions executed" summary
- [ ] T012 [US-1] Rewrite `SimulationAttackPath.tsx` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - Single fetch from `/api/exercises/{id}/attack-path`
  - Feature flag check (`CHAINING_ATTACK_PATH`)
  - Orchestrates: Stats (top) + Feed (left) + Graph (center) + Details (right)
  - Empty state when no workflow

**Checkpoint**: Graph renders with correct nodes/edges from API data

---

## Phase 4: Frontend — Interaction & Feed (P1 MVP)

> Depends on Phase 3 (graph exists to interact with).

- [ ] T013 [US-2] Create `AttackPathDetails.tsx` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - Action node details: payload name, target asset, status, expectations list, execution time
  - Asset node details: hostname, actions count, worst status, actions list
  - Slide-in panel (right side)
- [ ] T014 [US-2] Add node selection + chain highlighting to `AttackPathGraph.tsx`
  - Click node → highlight upstream/downstream via `getUpstreamNodes`/`getDownstreamNodes`
  - Non-connected nodes dimmed (opacity 0.3)
  - Click empty space or same node → deselect
- [ ] T015 [US-3] Enhance `AttackPathFeed.tsx` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - Reverse chronological order
  - Each entry: timestamp, payload name, status dot, target asset
  - Click feed entry → select corresponding graph node
- [ ] T016 [US-1] Add zoom/pan controls to `AttackPathGraph.tsx`
  - Zoom +/- buttons
  - Pan via mouse drag on SVG background
  - Fit-to-screen button

**Checkpoint**: Full interaction works — click node → details + highlight + feed sync

---

## Phase 5: SSE Real-time (P2 — deferred)

> Can be implemented after P1 MVP is validated.

- [ ] T017 [US-5] Create `useAttackPathStream.ts` — `openaev-front/src/admin/components/simulations/simulation/attack_path/`
  - `EventSource` connection to `/api/exercises/{id}/attack-path/stream`
  - On message: update node status, add feed entry, recalculate stats
  - Fallback to 10s polling if SSE fails
  - Reconnect with exponential backoff
  - "Live updates unavailable" indicator on failure
- [ ] T018 [US-5] Add SSE endpoint to `AttackPathApi.java`
  - `GET /api/exercises/{exerciseId}/attack-path/stream` → `SseEmitter`
  - `@AccessControl` same as GET
  - 60s timeout, 15s heartbeat
  - Max 1 connection per user/exercise
  - Emit on `InjectExpectation` status change
- [ ] T019 [US-5] SSE integration test (optional)
  - Verify emitter sends update when expectation changes
  - Verify unauthenticated connection rejected

**Checkpoint**: Live updates work during running simulation

---

## Phase 6: Validation & Polish

> Depends on all previous phases (at minimum Phase 1-4 for P1).

- [ ] T020 Backend validation: `mvn spotless:apply && mvn test`
- [ ] T021 Frontend validation: `yarn lint && yarn check-ts && yarn test`
- [ ] T022 Visual QA: Compare rendered graph against mockup v5
- [ ] T023 [P] Remove old files: `AttackPathFlow.tsx`, `NodeAttackStep.tsx` (if fully replaced)
- [ ] T024 [P] Run `yarn generate-types-from-api` to sync frontend types
- [ ] T025 E2E test: navigate to attack path → graph displays → click node → details show

**Checkpoint**: All validations pass, mockup parity achieved

---

## Dependencies & Execution Order

```
Phase 1 (Backend DTOs + Service + API) ──→ Phase 2 (Backend Tests)
                                        ──→ Phase 3 (Frontend Graph)
                                                    ↓
                                             Phase 4 (Interaction + Feed)
                                                    ↓
                                             Phase 5 (SSE — P2, deferred)
                                                    ↓
                                             Phase 6 (Validation)
```

### Parallel Opportunities

- T001-T005 within Phase 1 (all DTO files are independent)
- Phase 2 and Phase 3 can run in parallel (after Phase 1)
- T009 and T011 within Phase 3 (independent files)
- Phase 5 is independent of Phase 4 (but both need Phase 3)

---

## Notes

- Mark tasks `[x]` as completed
- Commit after each phase checkpoint
- Phase 5 (SSE) is P2 — implement after P1 MVP is validated and shipped
- No migration needed — all data already exists
- Reference mockup: `docs/design/mockups/Attack Path v5.html`
