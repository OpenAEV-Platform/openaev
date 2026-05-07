# Feature Specification: Attack Path Visualization

**Spec ID**: SPEC-002
**Feature Branch**: `poc/attack-path`
**Created**: 2026-05-06
**Status**: Ready
**Author**: Copilot + User
**Mockup**: `docs/design/mockups/Attack Path v6.html`

---

## 1. Summary

Redesign the Attack Path view in simulation results to display a BloodHound-inspired interactive graph showing the attacker's progression through the infrastructure. The graph uses a topology where **assets are group-like large nodes**, **actions (injects) are smaller nodes connected to their target asset**, and **events are circle markers on the chain-flow edges between actions**.

This replaces the current basic ReactFlow implementation with a professional, readable graph that helps SOC operators, red teamers, and purple team coordinators quickly identify: where defenses failed, what the lateral movement path was, and where to invest in detection.

The view includes a live execution feed (left panel), stats banner (top), and real-time updates via SSE during active simulations.

## 2. User Stories & Acceptance Criteria

### US-1: View Attack Path Graph (Priority: P1) 🎯 MVP

**As a** simulation operator, **I want** to see the attack path as an interactive graph, **so that** I can quickly identify which assets were compromised and where defenses succeeded or failed.

**Why this priority**: Core value proposition — without the graph, the attack path page is useless.

**Acceptance Scenarios**:

```gherkin
Scenario: Display attack path graph after simulation execution
  Given a simulation has been executed with chaining workflow
  And expectations exist for the executed injects
  When I navigate to the attack path tab
  Then I see asset nodes (hostname, IP, OS, role) as large rectangles
  And I see action nodes (payload name, status color) as smaller circles connected to their asset
  And I see event markers (circles) on the edges between actions
  And chain-flow edges have animated particles showing attack direction

Scenario: Status colors reflect expectation results
  Given an inject has PREVENTION expectation with status SUCCESS
  Then the action node is colored green (prevented)
  Given an inject has DETECTION expectation with status SUCCESS but PREVENTION FAILED
  Then the action node is colored orange (detected)
  Given both PREVENTION and DETECTION expectations are FAILED
  Then the action node is colored red (undetected)
  Given expectations are still PENDING
  Then the action node is colored grey (pending)

Scenario: Empty state when no workflow configured
  Given a simulation has no chaining workflow
  When I navigate to the attack path tab
  Then I see an informational message indicating no workflow is configured
```

---

### US-2: Interactive Node Selection & Inline Details (Priority: P1) 🎯 MVP

**As a** SOC analyst, **I want** to click a node and see its details expanded inline in the feed + highlighted path in the graph, **so that** I can drill into specific attack steps without losing graph context.

**Why this priority**: Interactivity is essential for analysis workflows.

**Acceptance Scenarios**:

```gherkin
Scenario: Click action node expands details inline in feed
  Given the attack path graph is displayed
  When I click on an action node (in graph or in feed)
  Then the corresponding feed entry expands below to show:
    | Field | Content |
    | Status badge | Prevented/Detected/Undetected/Pending with color |
    | Target | Hostname |
    | IP | Asset IP address |
    | Executed | Timestamp of execution |
    | Expectations | List of DETECTION/PREVENTION with their statuses |

Scenario: Click action node highlights chain path
  Given the attack path graph is displayed
  When I click on an action node
  Then all upstream and downstream connected nodes are highlighted
  And non-connected nodes are dimmed (opacity reduced to ~0.15)
  And chain edges are emphasized

Scenario: Click asset node highlights all its actions
  Given the attack path graph is displayed
  When I click on an asset node
  Then all action nodes connected to this asset are highlighted
  And the asset's worst status is shown

Scenario: Deselect node
  Given a node is selected
  When I click the same node again or click empty graph space
  Then the selection is cleared and all nodes return to normal opacity
  And the feed entry collapses back
```

---

### US-3: Live Execution Feed (Priority: P1) 🎯 MVP

**As a** simulation operator, **I want** a real-time feed showing execution progress, **so that** I can follow the attack chain as it unfolds.

**Why this priority**: During active simulations, operators need to see what's happening live.

**Acceptance Scenarios**:

```gherkin
Scenario: Feed shows executed actions chronologically
  Given a simulation is running or completed
  When I view the attack path
  Then the left panel shows actions in reverse chronological order
  And each entry shows: timestamp, payload name, status dot, target asset

Scenario: Feed updates in real-time via WebSocket
  Given a simulation is currently running
  When a new inject is executed
  Then a new entry appears at the top of the feed within 2 seconds
  And the graph updates the corresponding node status

Scenario: Feed entry syncs with graph selection
  Given the attack path is displayed
  When I click a feed entry
  Then the corresponding action node is selected in the graph
  And the path is highlighted
```

---

### US-4: Stats Banner (Priority: P2)

**As a** team lead, **I want** to see aggregate statistics at a glance, **so that** I can quickly assess the overall simulation outcome.

**Why this priority**: Quick summary without deep analysis.

**Acceptance Scenarios**:

```gherkin
Scenario: Stats banner shows expectation breakdown
  Given a simulation has expectations
  When I view the attack path
  Then the stats banner shows counts for: Prevented, Detected, Undetected, Pending
  And shows total actions executed (e.g., "6 of 6 actions executed")

Scenario: Stats update in real-time
  Given a simulation is running
  When an expectation status changes
  Then the stats banner updates within 2 seconds
```

---

### US-5: Real-time Updates via SSE (Priority: P2)

**As a** simulation operator, **I want** the attack path to update live without refreshing, **so that** I can monitor execution as it happens.

**Why this priority**: Enables operational use during live campaigns.

**Acceptance Scenarios**:

```gherkin
Scenario: SSE connection established on page load
  Given a simulation is in RUNNING state
  When I open the attack path tab
  Then an SSE connection is established to receive updates

Scenario: Expectation status update received
  Given I am viewing the attack path of a running simulation
  When an inject expectation status changes on the backend
  Then the corresponding action node color updates within 2 seconds
  And the feed adds/updates the entry
  And the stats banner recalculates

Scenario: Graceful degradation
  Given the SSE connection fails
  Then the system falls back to polling every 10 seconds
  And a subtle indicator shows "Live updates unavailable"
```

---

### Edge Cases

- What happens when an action targets multiple assets? → Show the action node connected to each asset
- What happens with 20+ actions? → Graph should support zoom/pan with controls
- What happens when a workflow has cycles? → Layout algorithm handles cycles gracefully (display as-is, no infinite loops)
- What happens when simulation has no expectations yet? → Show graph structure with all nodes in "pending" state
- What happens when event has no matching upstream provider? → Show event label text in grey/dimmed

## 3. Functional Requirements

- **FR-001**: System MUST display an interactive graph with assets, actions, and events from the simulation's workflow
- **FR-002**: System MUST color action nodes based on their expectation results (prevented/detected/undetected/pending)
- **FR-003**: System MUST show animated particle flow on chain edges indicating attack direction
- **FR-004**: System MUST display event labels (relation name + context, e.g. "Credentials Found · CORP\j.martinez") as text on chain-flow edges between actions
- **FR-005**: System MUST expand inline details in the feed panel when clicking any action node (no separate drawer)
- **FR-006**: System MUST highlight upstream/downstream chain on node selection
- **FR-007**: System MUST show a live execution feed in the left panel with chronological entries
- **FR-008**: System MUST show a stats banner with prevented/detected/undetected/pending counts
- **FR-009**: System MUST support real-time updates via SSE during running simulations (P2)
- **FR-010**: System MUST gracefully degrade to polling if SSE connection is unavailable
- **FR-011**: Backend MUST provide a dedicated endpoint returning the attack path graph data (steps + expectations + assets resolved)
- **FR-012**: System MUST support zoom/pan for large attack graphs
- **FR-013**: System MUST be gated behind the `CHAINING_ATTACK_PATH` feature flag

## 4. Security Requirements

### Access Control

- **Resource Type**: EXERCISE (simulation)
- **Capabilities needed**: ACCESS_EXERCISE
- **Access model**: sub-resource of Exercise — chain of trust: Exercise (tenant-validated) → Scenario → Workflow → Steps/Expectations
- **`@AccessControl` on every endpoint**: Yes
- **ID exposure**: UUIDs exposed in DTO (stepId, injectId, assetId) are acceptable — user already has ACCESS_EXERCISE granting access to all sub-resources; UUIDs are non-sequential

### Tenant Isolation

- **Tenant-scoped entity**: Yes (via parent Exercise/Scenario)
- **`@Filter("tenantFilter")`**: Applied on Exercise/Workflow queries
- **Native queries**: Will include `WHERE tenant_id` if any
- **`tenant_id` in API response**: Never

### Data Exposure

- **DTO-only responses**: Yes — never expose raw entities
- **Sensitive fields excluded**: Internal step_data JSON not exposed raw (only derived `label`, `status`)
- **Error messages**: Generic — no stack traces to client

### SSE Security (P2)

- **Authentication**: SSE endpoint uses same `@AccessControl` as GET endpoint
- **Session expiry**: SseEmitter MUST be closed when session is invalidated
- **Connection limits**: Max 1 active SSE connection per user per exercise
- **Timeout**: SseEmitter timeout = 60 seconds; client reconnects via `EventSource` retry
- **Heartbeat**: Server sends comment-only keepalive every 15s to detect dead connections

### Threat Model

| Threat | Impact | Mitigation |
|--------|--------|------------|
| Accessing another tenant's simulation attack path | Critical | Tenant filter on exercise lookup via `@AccessControl` |
| SSE connection hijacking | Medium | Authenticated SSE with session token |
| SSE connection flooding (many open streams per user) | Medium | Max 1 connection per user/exercise; SseEmitter 60s timeout |
| Orphaned SseEmitter (memory leak) | Low | `onTimeout()` + `onCompletion()` cleanup; 15s heartbeat |
| Invalid exerciseId injection | Low | UUID format validation; 404 for non-existent; no stack trace leak |
| Feature flag bypass (direct URL) | Low | Backend `CHAINING_ATTACK_PATH` check in controller; returns 404 if disabled |
| Large graph DoS (rendering too many nodes) | Low | Client-side zoom/pan; workflows are author-constrained (<30 steps) |

## 5. Technical Context

### Affected Modules

| Module | Changes |
|--------|---------|
| `openaev-api` | New `AttackPathApi` controller in `io.openaev.api.attack_path`, `AttackPathService`, DTOs, SSE endpoint for live updates |
| `openaev-model` | **No new entities** (uses existing `Workflow`, `Step`, `InjectExpectation`, `Inject`, `Asset`, `Endpoint`, `Condition`) |
| `openaev-front` | Rewrite `attack_path/` components: SVG graph, feed, details panel, SSE hook |

### Key Entities (existing, no new ones needed)

- **`Workflow`** (`workflows` table): Contains chain structure via `workflow_status`, linked to steps
- **`Step`** (`steps` table): `step_action_class` (INJECT_EXECUTION / EVENT), `step_data` (jsonb with inject config), `step_status`, linked to `Workflow` via `step_workflow_id`
- **`Condition`** (`conditions` table): Links steps together (DEPEND_ON type creates the chain edges)
- **`InjectExpectation`** (`injects_expectations` table): `type` (PREVENTION/DETECTION), `score` → computed `status`, linked to `inject_id`, `asset_id`, `exercise_id`
- **`Inject`** (`injects` table): Executed payload with title, content
- **`Asset`** / **`Endpoint`**: Target machines resolved from workflow scope rules

### API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| GET | `/api/exercises/{exerciseId}/attack-path` | Returns full pre-assembled attack path graph | `@AccessControl(resourceId = "#exerciseId", actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)` |
| GET | `/api/exercises/{exerciseId}/attack-path/stream` | SSE stream for live expectation updates (P2) | `@AccessControl(resourceId = "#exerciseId", actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)` |

### Response DTO Structure

```java
// Package: io.openaev.api.attack_path
public record AttackPathOutput(
    String workflowId, String workflowStatus,
    List<AttackPathNodeOutput> nodes,
    List<AttackPathEdgeOutput> edges,
    AttackPathStatsOutput stats
)

public record AttackPathNodeOutput(
    String id, String type,          // "ACTION" | "EVENT" | "ASSET"
    String label,                    // Payload name or asset hostname
    String status,                   // "prevented" | "detected" | "undetected" | "pending"
    String stepId, String injectId, String assetId,
    String assetHostname, String assetIp,
    Instant executedAt,
    List<AttackPathExpectationOutput> expectations
)

public record AttackPathEdgeOutput(
    String id, String sourceNodeId, String targetNodeId,
    String type,                     // "chain_flow" | "asset_link"
    List<String> eventIds
)

public record AttackPathExpectationOutput(
    String id, String type, String status, Double score
)

public record AttackPathStatsOutput(
    int totalActions, int executedActions,
    int prevented, int detected, int undetected, int pending
)
```

### Service Design — Query Strategy

1. Load exercise → get scenario → get workflow
2. Load all steps for that workflow in **one query** (`stepRepository.findAllByWorkflowId`)
3. Load all expectations for that exercise in **one query** (`injectExpectationRepository.findAllForExercise`)
4. Load referenced assets in **one batch** (`assetRepository.findAllById`)
5. Assemble the graph DTO in-memory (no N+1)

### Architectural Decisions

| Decision | Rationale | Constitution Principle |
|----------|-----------|----------------------|
| SSE over WebSocket | No WS infra exists; SSE simpler for unidirectional updates | VIII (YAGNI) |
| Single aggregate endpoint | Eliminates 2+ round-trips; avoids N+1 on client side | IV (Performance) |
| New controller in `io.openaev.api.attack_path` | Never add to legacy `io.openaev.rest.*` | II (Layered + Package Rule) |
| DTO records, not entities | JPA entities never leave service layer | II (Layered) |
| No new DB entities/tables | Workflow + Step + Condition + InjectExpectation already model the graph | VIII (YAGNI) |

### Frontend Components

| Component | Responsibility |
|-----------|---------------|
| `SimulationAttackPath.tsx` | Container: fetches data via single GET, manages SSE, orchestrates layout |
| `AttackPathGraph.tsx` | **SVG-based** graph replacing ReactFlow (removes `@xyflow/react` dependency for this view) |
| `AttackPathFeed.tsx` | Left panel (320px): chronological execution feed + inline details expansion on selection |
| `AttackPathStats.tsx` | Top banner: aggregate stats from `attack_path_stats` |
| `useAttackPathStream.ts` | Custom hook: SSE connection + fallback to 10s polling |

## 6. Test Plan

### Backend Tests

- [ ] Integration test: GET attack-path endpoint returns correct graph structure
- [ ] Integration test: Attack path respects tenant isolation
- [ ] Integration test: Attack path requires ACCESS_EXERCISE capability
- [ ] Integration test: SSE sends updates when expectations change (P2)

### Frontend Tests

- [ ] Vitest: AttackPathGraph renders nodes for each action step
- [ ] Vitest: Status colors resolve correctly from expectations
- [ ] Vitest: Node selection triggers highlight of connected chain
- [ ] Vitest: Feed updates when new SSE message arrives
- [ ] E2E: Navigate to attack path → graph displays → click node → feed expands details

### Security Tests

- [ ] RBAC: GET endpoint without ACCESS_EXERCISE → 403
- [ ] RBAC: SSE endpoint without ACCESS_EXERCISE → 403
- [ ] Tenant isolation: access other tenant's attack path → 404
- [ ] Tenant isolation: SSE stream for other tenant's exercise → 404
- [ ] Input validation: non-UUID exerciseId → 400
- [ ] Input validation: non-existent exerciseId → 404
- [ ] Feature flag: endpoint returns 404 when CHAINING_ATTACK_PATH disabled
- [ ] SSE: unauthenticated connection → rejected
- [ ] SSE: no data leaks after session invalidation (P2)

## 7. Success Criteria

- **SC-001**: Operators can identify undetected attack steps within 5 seconds of viewing the graph
- **SC-002**: The graph correctly displays all action nodes with their resolved status colors
- **SC-003**: Real-time updates arrive within 2 seconds of backend expectation change
- **SC-004**: Graph renders without overlap for scenarios with up to 20 action steps
- **SC-005**: All acceptance scenarios from US-1 through US-5 pass

## 8. Assumptions & Constraints

- The chaining execution pipeline (SPEC-001) is functional and produces expectations
- Assets are resolved at execution time from workflow scope rules (already implemented)
- The `CHAINING_ATTACK_PATH` feature flag already exists in the codebase
- The existing ReactFlow-based implementation will be replaced entirely with SVG-based graph
- SSE (`SseEmitter`) chosen over WebSocket — no WS infrastructure exists, SSE is simpler for unidirectional updates
- Mobile support is out of scope — minimum viewport 1200px
- The approved mockup is at `docs/design/mockups/Attack Path v6.html`

## 9. Agent Review Log

### Product Agent Review

- **Date**: 2026-05-06
- **Status**: ✅ Approved (0 blockers, 7 non-blocking suggestions)
- **Findings**:
  1. Suggested Scenario Outline for status colors
  2. Clarify US-3 feed scope (P1 static vs P2 live)
  3. Add error/failure scenarios
  4. Add keyboard accessibility
  5. Add edge cases (feature flag off, reconnection, tab inactive)
  6. Strengthen SC-001 (render time metric)
  7. Add feature flag tests

### Staff Agent Review

- **Date**: 2026-05-06
- **Status**: ✅ Approved (0 blockers)
- **Findings**:
  1. SSE recommended over WebSocket — no WS infrastructure exists (Constitution VIII / YAGNI)
  2. No new entities needed — confirmed existing model is sufficient
  3. Single aggregate endpoint replaces current 2-fetch pattern
  4. New code in `io.openaev.api.attack_path` (not legacy `io.openaev.rest.*`)
  5. ReactFlow removal scoped to attack path view only
  6. Unbounded list acceptable (workflows are author-constrained, typically <30 steps)
  7. Noted pre-existing tech debt: `ExerciseExpectationApi` returns raw JPA entities (not introduced by this spec)

### Security Agent Review

- **Date**: 2026-05-06
- **Status**: ⚠️ Approved with Warnings (0 blockers, 4 warnings — all concern SSE/P2)
- **Findings**:
  1. SSE connection flooding risk → mitigated by max 1 conn per user/exercise + 60s timeout
  2. UUID exposure in DTO acceptable — user already has ACCESS_EXERCISE
  3. SSE session expiry handling needed (SseEmitter cleanup on session invalidation)
  4. Additional security test cases added (input validation, feature flag bypass, SSE-specific)
