# Implementation Plan: Attack Path Visualization

**Spec**: [SPEC-002](./spec.md)
**Branch**: `poc/attack-path`
**Created**: 2026-05-07
**Status**: Ready

---

## 1. Summary

Redesign the Attack Path view to display a BloodHound-inspired SVG graph showing the attacker's progression through the infrastructure. Assets = large group nodes, Actions = smaller nodes, Events = edge markers. Includes live feed, stats banner, details panel, and SSE real-time updates (P2).

**Approach**: No new database entities needed. Backend provides a single aggregate endpoint that assembles graph data from existing Workflow/Step/InjectExpectation/Asset entities. Frontend replaces ReactFlow with custom SVG-based graph.

## 2. Technical Context

**Stack**: Java 21 / Spring Boot / PostgreSQL / Elasticsearch / React / TypeScript / Vite
**Modules**: openaev-model (entities), openaev-api (service + controller + DTOs), openaev-front (SVG graph)
**Testing**: JUnit 5 + MockMvc (backend), Vitest + Playwright (frontend)
**Migrations**: None needed (no new tables)

## 3. Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Security-First | ✅ | `@AccessControl` on both endpoints, tenant isolation via Exercise parent, DTOs only |
| II. Layered Architecture | ✅ | `AttackPathApi` → `AttackPathService` → repositories. New code in `io.openaev.api.attack_path` |
| III. Test-First Quality | ✅ | Integration tests + security tests defined in spec §6. Fixtures/composers pattern |
| IV. Performance by Design | ✅ | Single aggregate endpoint, 3 batch queries (no N+1), `@Transactional(readOnly=true)` |
| V. Spec-Driven Development | ✅ | SPEC-002 validated by Product + Staff + Security agents |
| VI. Conventional Commits | ✅ | Branch `poc/attack-path` (existing), commits follow format |
| VII. Frontend Discipline | ✅ | Auto-generated types, no MUI for layout, native SVG, `t()` for i18n |
| VIII. Simplicity | ✅ | Reuses existing entities, no over-engineering, SSE is simplest real-time approach |

## 4. Architecture

### Module Mapping

```
openaev-api/src/main/java/io/openaev/
├── api/attack_path/
│   ├── AttackPathApi.java              # REST controller (GET + SSE)
│   ├── AttackPathOutput.java           # Root response DTO (record)
│   ├── AttackPathNodeOutput.java       # Node DTO (record)
│   ├── AttackPathEdgeOutput.java       # Edge DTO (record)
│   ├── AttackPathExpectationOutput.java # Expectation DTO (record)
│   └── AttackPathStatsOutput.java      # Stats DTO (record)
├── service/
│   └── AttackPathService.java          # Graph assembly logic

openaev-api/src/test/java/io/openaev/
├── api/attack_path/
│   └── AttackPathApiTest.java          # Integration test

openaev-front/src/admin/components/simulations/simulation/attack_path/
├── SimulationAttackPath.tsx            # Container (rewrite)
├── AttackPathGraph.tsx                 # SVG graph (new, replaces AttackPathFlow.tsx)
├── AttackPathFeed.tsx                  # Feed panel 320px + inline details expand (enhance)
├── AttackPathStats.tsx                 # Stats banner (new)
├── useAttackPathStream.ts             # SSE hook (new, P2)
└── attackPathUtils.ts                 # Status resolution, layout helpers
```

### Database Schema

**No new tables or migrations**. Uses existing:
- `workflows` → chain structure
- `steps` → actions/events with `step_data` JSONB
- `conditions` → step dependencies (chain edges)
- `injects_expectations` → DETECTION/PREVENTION results
- `injects` → executed payloads
- `assets` / `endpoints` → target machines

### API Contract

| Method | Path | Response | Auth |
|--------|------|----------|------|
| GET | `/api/exercises/{exerciseId}/attack-path` | `AttackPathOutput` | `@AccessControl(READ, SIMULATION)` |
| GET | `/api/exercises/{exerciseId}/attack-path/stream` | SSE `SseEmitter` (P2) | `@AccessControl(READ, SIMULATION)` |

## 5. Implementation Phases

### Phase 1: Backend DTOs & Service (No DB changes)

1. Create DTO records in `io.openaev.api.attack_path`
2. Create `AttackPathService` with graph assembly logic
3. Create `AttackPathApi` controller with `@AccessControl`
4. Feature flag check (`CHAINING_ATTACK_PATH`)

### Phase 2: Backend Tests

5. Create integration test `AttackPathApiTest` (RBAC, tenant isolation, graph structure)
6. Create security-specific test scenarios (input validation, feature flag)

### Phase 3: Frontend — Graph & Layout

7. Create `attackPathUtils.ts` (status resolution, layout algorithm)
8. Create `AttackPathGraph.tsx` (SVG-based, replaces ReactFlow)
9. Rewrite `SimulationAttackPath.tsx` (single fetch, orchestration)
10. Create `AttackPathStats.tsx` (stats banner)

### Phase 4: Frontend — Interaction & Feed

11. Enhance `AttackPathFeed.tsx` (320px, inline detail expansion on click, expectations display)
12. Add node selection + chain highlighting to graph (upstream/downstream, opacity dimming)
13. Add zoom/pan controls to graph
14. Sync feed ↔ graph selection (click feed → select graph node, click graph → expand feed entry)

### Phase 5: SSE Real-time (P2)

15. Create `useAttackPathStream.ts` (SSE hook with polling fallback)
16. Add SSE endpoint in `AttackPathApi` with `SseEmitter`
17. Add SSE integration test (optional — P2)

### Phase 6: Validation & Polish

18. `mvn spotless:apply && mvn test`
19. `yarn lint && yarn check-ts && yarn test`
20. E2E test (navigate → graph displays → click node → details)
21. Visual QA against mockup v5

## 6. Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| SVG graph performance with many nodes | Medium | Workflows are author-constrained (<30 steps); add virtualization only if needed |
| Complex layout algorithm (overlap avoidance) | Medium | Start with simple force-directed/hierarchical layout; iterate |
| Existing InjectExpectation data format varies | Low | Handle edge cases in status resolution logic |
| SSE adds new infrastructure pattern | Low | Defer to P2; use polling initially |

## 7. Dependencies

- Chaining execution pipeline (SPEC-001) MUST be functional — **already done** ✅
- `CHAINING_ATTACK_PATH` feature flag MUST exist — **already done** ✅
- Existing entities (`Workflow`, `Step`, `Condition`, `InjectExpectation`, `Asset`) — **exist** ✅
- `StepService`, `InjectExpectationService`, `AssetService` — **exist** ✅
- No migration needed — no dependency on migration ordering

## 8. Commit Strategy

| Phase | Commit Message |
|-------|---------------|
| Phase 1 | `[backend] feat(attack-path): add attack path aggregate endpoint` |
| Phase 2 | `[backend] test(attack-path): add integration tests for attack path API` |
| Phase 3 | `[frontend] feat(attack-path): implement SVG-based attack path graph` |
| Phase 4 | `[frontend] feat(attack-path): add interaction, details panel, and feed` |
| Phase 5 | `[backend] feat(attack-path): add SSE endpoint for live updates` |
| Phase 6 | `[frontend] feat(attack-path): add SSE hook with polling fallback` |
