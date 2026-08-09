# ADR-003: Simulation and Inject execution logging

|         |                                                        |
|---------|--------------------------------------------------------|
| Status  | In Progress                                            |
| Related | https://github.com/OpenAEV-Platform/openaev/issues/6602 |

## 1. Context

Today, `AuditLogger` exposes two specialized methods:
- `logAuthEvent(...)` — authentication events (login, logout, RBAC denial)
- `logAccessControlEvent(...)` — CRUD mutation events (create, update, delete)

Both follow the same internal pattern: check `isAuditLoggingEnabled()`, submit to `taskLoggerExecutor` via `CompletableFuture.supplyAsync()`, call `awaitIfHaltOnFailure()`.

New use cases (scheduled jobs, system events, data retention purges, integration syncs, simulation lifecycle) don't fit either method's signature. The simulation lifecycle audit logging (US.1a/US.1b) requires logging from Quartz jobs and executor services with no HTTP context. Each new event type shouldn't require adding yet another specialized method with its own parameter list.

## 2. Decision drivers

1. **Halt-on-failure guarantee** — audit events must block + rollback when halt-on-failure is enabled, regardless of origin (HTTP request or scheduled job). This is non-negotiable.
2. **No servlet dependency** — must work from Quartz jobs and message consumers (no `HttpServletRequest`, no `RequestContextHolder`).
3. **Backward compatibility** — `logAuthEvent` and `logAccessControlEvent` must continue working unchanged for existing callers.
4. **Type safety** — event construction must be type-safe (not raw `Map<String, Object>`), preventing malformed audit entries.
5. **Simplicity** — minimal ceremony for callers; the approach should be obvious and explicit.

## 3. Considered options

### Option A: Direct call to `AuditLogger.logEvent(AuditEvent)`

Add a single generic `logEvent(AuditEvent)` method on the existing `AuditLogger` bean. Callers build an `AuditEvent` via Lombok `@Builder` and call `auditLogger.logEvent(event)` directly. Existing methods (`logAuthEvent`, `logAccessControlEvent`) are refactored to delegate to `logEvent` internally.

**Pros**: Explicit halt-on-failure behavior visible at call site. No magic — the caller controls exactly when and where audit logging happens. Minimal infrastructure change. Easy to grep for all audit call sites.
**Cons**: Slightly more verbose than an annotation. No automatic decoupling.

### Option B: Spring `ApplicationEventPublisher` + `@EventListener`

Publish an `AuditEvent` via `applicationEventPublisher.publishEvent(event)`. A synchronous `@EventListener` in `AuditLogger` consumes it and delegates to `logGenericEvent()`. Halt-on-failure works because Spring's default `ApplicationEventPublisher` is synchronous.

**Pros**: Decouples publisher from consumer. Follows Spring event pattern.
**Cons**: Halt-on-failure works only because the publisher is synchronous — this is an implicit guarantee that could break if someone configures an async publisher. Refactoring all existing `logAuthEvent` / `logAccessControlEvent` call sites to use events is unnecessary churn. The decoupling provides no practical benefit since there will always be exactly one consumer (`AuditLogger`).

### Option C: Custom `@AuditEvent` annotation (AOP)

Annotate methods with `@AuditEvent(type = ..., scope = ...)` and let an AOP aspect extract parameters via SpEL, build the event, and log it.

**Pros**: Minimal code at call site. Declarative.
**Cons**: Halt-on-failure semantics become invisible — the caller doesn't know logging happens. SpEL expressions for extracting contextData are fragile and hard to debug. Does not work for Quartz jobs (no Spring AOP proxy on `Job.execute()`). Complex to implement for the variable contextData shapes needed across different event types.

## 4. Decision

We chose **Option A (direct call)** because it preserves explicit halt-on-failure behavior, works everywhere (controllers, services, Quartz jobs), and requires minimal infrastructure change.

### Architecture

#### `AuditEvent` — Immutable event descriptor

```java
package io.openaev.aop.audit_log;

@Getter
@Builder
public class AuditEvent {

  @NonNull
  private final EventType eventType;        // reuses existing io.openaev.database.model.EventType
  @NonNull
  private final AuditEventScope eventScope;
  @NonNull
  private final EventStatus eventStatus;    // reuses existing io.openaev.database.model.EventStatus
  private final String resourceType;    // nullable — e.g. "Team", "Scenario", null for system events
  private final String resourceId;      // nullable
  private final String message;         // human-readable description
  private final Map<String, Object> contextData;  // arbitrary key-value pairs
  private final JsonNode entityDiffs;   // nullable — field-level diffs for mutation events only
  @NonNull
  private final AuditEventOrigin origin;
}
```

#### Enums

> **Implementation note**: `AuditEventType` and `AuditEventStatus` were **not created** as separate enums.
> Instead, the existing `EventType` and `EventStatus` enums in `io.openaev.database.model` were extended:
> - `EventType` — added `EXECUTION`, `SYSTEM` values
> - `EventStatus` — added `WARNING` value
>
> This avoids duplication since these enums already existed with the same values (`MUTATION`, `AUTHENTICATION`, `SUCCESS`, `ERROR`).

```java
// AuditEventOrigin.java — NEW (in io.openaev.aop.audit_log)
public enum AuditEventOrigin {
  /** HTTP request context available (controllers, filters). */
  REQUEST,
  /** No HTTP context (scheduled jobs, message consumers, startup tasks). */
  SYSTEM
}
```

```java
// EventType.java — EXISTING (in io.openaev.database.model), extended
public enum EventType {
  MUTATION,
  AUTHENTICATION,
  EXECUTION,   // added
  SYSTEM       // added
}
```

```java
// AuditEventScope.java — NEW (in io.openaev.aop.audit_log)
public enum AuditEventScope {
  // Authentication
  LOGIN, LOGOUT, SESSION_EXPIRED, UNAUTHORIZED,

  // Mutation (CRUD)
  CREATE, UPDATE, DELETE, DUPLICATE, STATUS_CHANGE,

  // Execution (inject lifecycle)
  SCHEDULED_LAUNCH, INJECT_STATUS_TRANSITION, TARGET_RESOLUTION,
  AGENT_TRACE_STEP, COVERAGE_GAP, INJECT_FINAL_STATUS, EXPECTATION_RESULT,
  INJECT_QUEUED,  // inject dispatched to integration agent queue

  // System
  RETENTION_PURGE, JOB_EXECUTION, MIGRATION, STARTUP;

  /** Maps an {@link Action} to its corresponding {@link AuditEventScope}. */
  public static AuditEventScope from(Action action) {
    return switch (action) {
      case CREATE -> CREATE;
      case WRITE -> UPDATE;
      case DELETE -> DELETE;
      case LAUNCH -> STATUS_CHANGE;
      case DUPLICATE -> DUPLICATE;
      case LOGIN -> LOGIN;
      case LOGOUT -> LOGOUT;
      case UNAUTHORIZED -> UNAUTHORIZED;
      default -> UPDATE;
    };
  }
}
```

```java
// EventStatus.java — EXISTING (in io.openaev.database.model), extended
public enum EventStatus {
  SUCCESS,
  ERROR,
  WARNING  // added
}
```

#### `AuditLogger.logEvent(AuditEvent)` — Generic public method

```java
public void logEvent(AuditEvent event) {
  if (!isAuditLoggingEnabled()) return;

  CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(
      () -> doLogEvent(event), taskLoggerExecutor);

  awaitIfHaltOnFailure(future);
}

private boolean doLogEvent(AuditEvent event) {
  String logUUID = UUID.randomUUID().toString();
  boolean status = false;
  try {
    status = logService.logGenericEvent(event, Level.WARNING, logUUID);
  } catch (Exception e) {
    log.warn("[AUDIT] Audit logging failed: {}", e.getMessage(), e);
  }

  if (!status) {
    log.warn("[AUDIT] Failed to log event {}.{}", event.getEventType(), event.getEventScope());
    prepareLogFailure();
  }
  return status;
}
```

> **Implementation note**: the `logUUID` is generated inside `doLogEvent()` (not `logEvent()`), since it's an internal concern of the logging thread — no caller ever provides or sees it.

#### `LogService.logGenericEvent(AuditEvent)` — Single transport method

```java
public boolean logGenericEvent(AuditEvent event, Object logLevel, String logUUID) {
  if (!isEnabled()) return true;

  LogEvent doc = buildBaseAuditLog(
      event.getEventType(), event.getEventStatus(),
      resolveEventAccess(event), event.getEventScope(), logUUID);

  Map<String, Object> ctx = new LinkedHashMap<>(event.getContextData() != null ? event.getContextData() : Map.of());
  ctx.put("message", event.getMessage());

  if (event.getResourceType() != null) ctx.put("entity_type", event.getResourceType());
  if (event.getResourceId() != null) ctx.put("resource_id", event.getResourceId());

  doc.setContextData(ctx);

  if (event.getEntityDiffs() != null) {
    doc.setEntityDiffs(event.getEntityDiffs());
  }

  // For SYSTEM-origin: skip user metadata population (no servlet context)
  if (event.getOrigin() == AuditEventOrigin.SYSTEM) {
    doc.setUserId(null);
    doc.setUserMetadata(null);
  }

  return emit(doc, logLevel);
}
```

`LogService.logRequestEvent()` and `LogService.logAuthEvent()` are kept as convenience utilities — they internally delegate to `logGenericEvent()` but provide a simpler signature for their respective call sites. No deprecation, no removal.

#### Refactoring existing methods

Both `logAuthEvent` and `logAccessControlEvent` build an `AuditEvent` and delegate to `logEvent(event)`. No more `doLogAuthEvent()`, `doLogAccessControlEvent()`, or `Supplier` overloads — there is one `logEvent()`, one `doLogEvent()`.

> **Implementation note**: the signatures of `logAuthEvent`, `logAccessControlEvent`, and `logAuthEventWithRequestContext` were changed to accept enum types (`AuditEventScope`, `EventStatus`) instead of raw strings. The `AccessControlAuditLogAspect` uses `AuditEventScope.from(action)` to map `Action` enums to scopes — this mapping was moved from a private method in the aspect to a `public static` utility on `AuditEventScope` itself.

```java
public void logAuthEvent(AuditEventScope eventScope, EventStatus eventStatus, String provider, String reason) {
  Map<String, Object> ctx = new LinkedHashMap<>();
  if (provider != null) ctx.put("provider", provider);
  if (reason != null) ctx.put("reason", reason);

  logEvent(AuditEvent.builder()
      .eventType(EventType.AUTHENTICATION)
      .eventScope(eventScope)
      .eventStatus(eventStatus)
      .message(LogUtils.buildAuthLogMessage(eventScope.name().toLowerCase(), eventStatus.name().toLowerCase(), provider))
      .contextData(ctx)
      .origin(AuditEventOrigin.REQUEST)
      .build());
}
```

```java
public CompletableFuture<Boolean> logAccessControlEvent(
    AuditEventScope eventScope, EventStatus eventStatus, ResourceType resourceType,
    String resourceId, JsonNode input, JsonNode output, JsonNode signatureNode,
    Map<String, AuditLogContext.EntitySnapshot> snapshots) {

  JsonNode entityDiffsNode = ObjectDiffUtils.computeEntityDiffsNode(snapshots, objectMapper);

  // contextData populated with input/output/signature;
  // normalization, redaction, and message building handled by LogService.logGenericEvent()

  logEvent(AuditEvent.builder()
      .eventType(EventType.MUTATION)
      .eventScope(eventScope)
      .eventStatus(eventStatus)
      .resourceType(resourceType != null ? resourceType.name() : null)
      .resourceId(resourceId)
      .entityDiffs(entityDiffsNode)
      .contextData(Map.of(
          "input", input != null ? input : NullNode.getInstance(),
          "output", output != null ? output : NullNode.getInstance(),
          "signature", signatureNode != null ? signatureNode : NullNode.getInstance()))
      .origin(AuditEventOrigin.REQUEST)
      .build());
}
```

The `logUUID` parameter is removed from all public methods — it's generated inside `logEvent()`.

#### Halt-on-failure behavior by origin

| Origin | Halt-on-failure | Session invalidation | Shutdown |
|---|---|---|---|
| `REQUEST` | Block + throw + rollback + invalidate session | ✅ | ✅ |
| `SYSTEM` | Block + throw + rollback (if in `@Transactional`) | ❌ (no session) | ✅ |

Both origins respect halt-on-failure identically. The only difference is session invalidation (best-effort no-op for SYSTEM since there is no HTTP session). `awaitIfHaltOnFailure()` already handles missing servlet context gracefully.

#### Dependency injection pattern

All services receiving `AuditLogger` must use `Optional<AuditLogger>` since it's `@ConditionalOnExpression` — it may not be present if audit logging is not configured.

#### File changes summary

| File | Change |
|---|---|
| `io.openaev.aop.audit_log.AuditEvent` | **New** — immutable class + `@Builder` (Lombok), uses `EventType` and `EventStatus` from `openaev-model` |
| `io.openaev.aop.audit_log.AuditEventScope` | **New** — enum with `from(Action)` utility method |
| `io.openaev.aop.audit_log.AuditEventOrigin` | **New** — enum (`REQUEST`, `SYSTEM`) |
| `io.openaev.aop.audit_log.AuditLogger` | Add `logEvent(AuditEvent)` + `doLogEvent()`, refactor `logAuthEvent`, `logAccessControlEvent`, `logAuthEventWithRequestContext` to use enum params and delegate to `logEvent` |
| `io.openaev.aop.audit_log.AccessControlAuditLogAspect` | Use `AuditEventScope.from(action)` instead of private `toAuditScope()`, use `EventStatus` enum |
| `io.openaev.service.LogService` | Add `logGenericEvent(AuditEvent, logLevel, logUUID)` with MUTATION normalization, redaction, `formatResourceType()` for `entity_type` |
| `io.openaev.database.model.EventType` | Add `EXECUTION`, `SYSTEM` enum values |
| `io.openaev.database.model.EventStatus` | Add `WARNING` enum value |
| `io.openaev.utils.log.LogUtils` | Add `getEventType()` mappings for `EXECUTION` and `SYSTEM` |

> **Not created** (originally planned but reused existing enums instead):
> - ~~`io.openaev.aop.audit_log.AuditEventType`~~ → reused `io.openaev.database.model.EventType`
> - ~~`io.openaev.aop.audit_log.AuditEventStatus`~~ → reused `io.openaev.database.model.EventStatus`

---

### Implementation plan — Simulation lifecycle audit logging

> This plan covers **both** OpenAEV agent execution (US.1a) and integration agent execution (US.1b). ACs are tagged with their origin US for traceability. Where both US share an AC, it's marked as **shared**.

#### AC coverage analysis

| AC | US | Status | Mechanism | Notes |
|---|---|---|---|---|
| Launch logged | 1a/1b | ✅ Done | `@AccessControl` aspect | Launch is a CRUD mutation via API → already audited |
| Recurrence config logged | 1a/1b | ✅ Done | `@AccessControl` aspect | Saving recurrence is an update via API → already audited |
| Scheduled execution logged | 1a/1b | ❌ **Needs generic event** | `InjectsExecutionJob.handleAutoStartExercises()` | Single hook: SCHEDULED → RUNNING transition, covers both scenario recurrence and from-scratch, agent-agnostic |
| Inject status transitions | 1a/1b | ❌ **Needs generic event** | `InjectStatusService.updateInjectStatus()` | Called from agent callbacks (mixed context) and scheduler. `executor_type` in `contextData` distinguishes OpenAEV vs integration agents |
| Target resolution logged | 1a | ❌ **Needs generic event** | `InjectsExecutionJob.executeInject()` | Logged at inject execution time — includes resolved targets, inactive agents, and agentless assets in `contextData` (OpenAEV agents only) |
| Inject placed in queue | 1b | ❌ **Needs generic event** | `ExecutionExecutorService.launchExecutorContext()` | Last platform-controlled point before integration agent takes over. New scope: `INJECT_QUEUED` |
| Agent trace steps logged | 1a | ❌ **Needs generic event** | `InjectStatusService.createExecutionTrace()` | Each step (prerequisite, attack, cleanup) arrives as a callback (OpenAEV agents only) |
| Agent callbacks logged | 1a/1b | ✅ Done | `@AccessControl` aspect | Callback endpoints are `@AccessControl`-annotated — input/output payloads are already logged, including integration agent status values |
| Agent inactive logged | 1a | ❌ **Needs generic event** | Proactive detection needed (no existing job) | Proactive monitoring job to log as soon as agent becomes inactive. At execution time, inactive agents are included in `TARGET_RESOLUTION` event `contextData` |
| Asset agentless logged | 1a | ✅ Covered | Included in `TARGET_RESOLUTION` event `contextData` | Not a standalone event — agentless assets are part of the inject execution context alongside inactive agents |
| Cleanup failure logged | 1a | ✅ Done | `@AccessControl` aspect | Callback with `EXECUTED_WITH_CLEANUP_FAIL` → already audited |
| Rolled-up inject status logged | 1a/1b | ❌ **Needs generic event** | `InjectStatusService.updateFinalInjectStatus()` | Computed after all agent traces received. US.1b: integration status vocabulary carried in `contextData` |
| Expectation results logged | 1a/1b | ❌ **Needs generic event** | `InjectExpectationService.updateInjectExpectation()` | Collector results + manual validations, agent-agnostic |
| Manual results attributed | 1a | ✅ Done | `@AccessControl` aspect | User saves via API → already audited |
| PII filtering | 1a/1b | ✅ Done | `ObjectRedactionUtils` | Applied in `LogService` before emission |

#### ACs requiring `logEvent(AuditEvent)` — implementation details

##### Scheduled execution logging (shared)

Both launch paths (scenario recurrence and from-scratch) converge at `InjectsExecutionJob.handleAutoStartExercises()` which transitions the simulation from `SCHEDULED` to `RUNNING`. Agent type (OpenAEV or integration) is irrelevant at this stage.

**Where**: `InjectsExecutionJob.handleAutoStartExercises()` — after status transition to `RUNNING`

```java
Map<String, Object> ctx = new LinkedHashMap<>(Map.of(
    "simulation_id", exercise.getId(),
    "simulation_name", exercise.getName(),
    "scheduled_start", exercise.getStart().toString(),
    "initiator", "scheduler"));

if (exercise.getScenario() != null) {
  ctx.put("scenario_id", exercise.getScenario().getId());
  ctx.put("scenario_name", exercise.getScenario().getName());
}

auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.SYSTEM)
    .eventScope(AuditEventScope.SCHEDULED_LAUNCH)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("Simulation")
    .resourceId(exercise.getId())
    .message("Simulation '%s' started (scheduled start reached)".formatted(exercise.getName()))
    .contextData(ctx)
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

##### Inject status transitions (shared)

**Where**: `InjectStatusService.updateInjectStatus()` — after status change is persisted

```java
auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.INJECT_STATUS_TRANSITION)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("Inject")
    .resourceId(inject.getId())
    .message("Inject '%s' transitioned to %s".formatted(inject.getTitle(), newStatus))
    .contextData(Map.of(
        "inject_id", inject.getId(),
        "inject_name", inject.getTitle(),
        "previous_status", previousStatus.name(),
        "new_status", newStatus.name(),
        "executor_type", executorType))
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

**Hook points**: `InjectService.launch()` (QUEUING), `InjectsExecutionJob.executeInject()` (EXECUTING), `InjectStatusService.updateInjectStatus()` (PENDING), `InjectStatusService.updateFinalInjectStatus()` (terminal).

Integration agents use their own status vocabulary — `contextData` carries status values as strings. The `executor_type` field distinguishes agent types.

##### Target resolution logging (US.1a only)

**Where**: `InjectsExecutionJob.executeInject()` — at inject execution time, after targets have been resolved. Includes per-endpoint agent status (active, inactive, agentless).

```java
List<Map<String, Object>> endpointResolutions = endpoints.stream()
    .map(endpoint -> {
      List<Agent> agents = agentsByEndpoint.get(endpoint.getId());
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("endpoint_id", endpoint.getId());
      if (agents == null || agents.isEmpty()) {
        entry.put("status", "ASSET_AGENTLESS");
      } else {
        entry.put("agents", agents.stream()
            .map(agent -> Map.of(
                "agent_id", agent.getId(),
                "status", agent.isActive() ? "AGENT_ACTIVE" : "AGENT_INACTIVE"))
            .toList());
      }
      return entry;
    })
    .toList();

auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.TARGET_RESOLUTION)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("Inject")
    .resourceId(inject.getId())
    .message("Resolved %d endpoints for inject '%s'".formatted(endpoints.size(), inject.getTitle()))
    .contextData(Map.of(
        "inject_id", inject.getId(),
        "inject_name", inject.getTitle(),
        "asset_group_ids", assetGroupIds,
        "team_ids", teamIds,
        "player_ids", playerIds,
        "total_endpoints", endpoints.size(),
        "endpoints", endpointResolutions))
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

Example `contextData.endpoints` output:
```json
[
  { "endpoint_id": "ep-001", "status": "ASSET_AGENTLESS" },
  { "endpoint_id": "ep-002", "agents": [
      { "agent_id": "ag-001", "status": "AGENT_ACTIVE" },
      { "agent_id": "ag-002", "status": "AGENT_INACTIVE" }
  ]}
]
```

##### Inject placed in queue (US.1b only)

Last platform-controlled point before the external agent takes over. OpenAEV agents don't have this concept.

**Where**: `ExecutionExecutorService.launchExecutorContext(Inject inject)` — before routing to individual `ExecutorContextService` implementations.

```java
auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.INJECT_QUEUED)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("Inject")
    .resourceId(inject.getId())
    .message("Inject '%s' dispatched to integration agent '%s'".formatted(
        inject.getTitle(), executorType))
    .contextData(Map.of(
        "inject_id", inject.getId(),
        "inject_name", inject.getTitle(),
        "integration_agent_id", agentId,
        "executor_type", executorType,
        "simulation_id", exerciseId,
        "initiator", initiator))
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

##### Agent trace steps logged in real-time (US.1a only)

**Where**: `InjectStatusService.createExecutionTrace()` — after trace is persisted. Each step: prerequisite check, prerequisite retrieval, attack command, cleanup.

```java
auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.AGENT_TRACE_STEP)
    .eventStatus(traceStatus.isSuccess() ? AuditEventStatus.SUCCESS : AuditEventStatus.ERROR)
    .resourceType("Inject")
    .resourceId(injectStatus.getInject().getId())
    .message("Agent step '%s' completed with status '%s'".formatted(
        executionAction.name(), traceStatus.name()))
    .contextData(Map.of(
        "inject_id", injectStatus.getInject().getId(),
        "agent_id", agent != null ? agent.getId() : "unknown",
        "endpoint_id", agent != null && agent.getAsset() != null ? agent.getAsset().getId() : "unknown",
        "step_name", executionAction.name(),
        "trace_status", traceStatus.name(),
        "trace_id", executionTrace.getId()))
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

> stdout/stderr are NOT included in the audit event (PII/sensitive data). Only a `trace_id` reference is logged.

##### Agent inactive detection (US.1a only)

Today, agent inactivity is detected lazily at inject execution time via `AgentHelper.isAgentActiveFromLastSeen(lastSeen)` (1h threshold). The `isActive()` status is computed dynamically — there's no persisted status field.

At execution time, inactive agents are included in the `TARGET_RESOLUTION` event `contextData`. To log as soon as an agent becomes inactive, a proactive monitoring mechanism is needed.

**Design — Persisted `agent_status` column replaces computed `isActive()`**

| Column | Type | Nullable | Default | Description |
|---|---|---|---|---|
| `agent_status` | `VARCHAR(20)` | ❌ NOT NULL | `'ACTIVE'` | Source of truth for agent liveness. Values: `ACTIVE`, `INACTIVE` |

`Agent.isActive()` keeps its existing `@JsonProperty("agent_active")` signature and boolean return — no impact on callers. Internally, it reads the persisted `agent_status` field instead of computing from `lastSeen`. `AgentHelper` class is deleted entirely — `ACTIVE_THRESHOLD` constant moves to `AgentInactivityMonitorJob` (its sole consumer). The remaining callers (`VulnerableEndpointHandler`, `AgentRegisterInput`) are migrated to use `agent.isActive()` directly.

**New Quartz job: `AgentInactivityMonitorJob`**
- Runs every 5 minutes (configurable), `@DisallowConcurrentExecution`
- Queries: `WHERE agent_last_seen < now() - threshold AND agent_status = 'ACTIVE'`
- For each matched agent: transition `agent_status` ACTIVE → INACTIVE, emit `COVERAGE_GAP` audit event (logs only the state change)

**Reset logic**: When `lastSeen` is updated (heartbeat), if `agent_status = 'INACTIVE'`, transition back to `ACTIVE`. This happens in `EndpointService.register()` and `updateExistingAgent()`.

**Migration**: `V{X}_{YY}__AddStatusToAgents.java`
```sql
ALTER TABLE agents ADD COLUMN agent_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE agents SET agent_status = 'INACTIVE'
  WHERE agent_last_seen < NOW() - INTERVAL '1 hour'
     OR agent_last_seen IS NULL;
```

**Enum and entity change**:
```java
public enum AgentStatus {
  ACTIVE,
  INACTIVE
}
```

```java
// Agent.java
@Enumerated(EnumType.STRING)
@Column(name = "agent_status", nullable = false)
@JsonIgnore
private AgentStatus status = AgentStatus.ACTIVE;

@JsonProperty("agent_active")
public boolean isActive() {
  return this.status == AgentStatus.ACTIVE;
}
```

**Job**:
```java
@Component
@DisallowConcurrentExecution
@RequiredArgsConstructor
public class AgentInactivityMonitorJob implements Job {

  /** Threshold in milliseconds to consider an agent as inactive (1 hour). */
  public static final int ACTIVE_THRESHOLD = 3_600_000;

  private final AgentRepository agentRepository;
  private final Optional<AuditLogger> auditLogger;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void execute(JobExecutionContext context) {
    Instant threshold = Instant.now().minus(ACTIVE_THRESHOLD, ChronoUnit.MILLIS);

    List<Agent> newlyInactiveAgents = agentRepository
        .findByLastSeenBeforeAndStatus(threshold, AgentStatus.ACTIVE);

    for (Agent agent : newlyInactiveAgents) {
      agent.setStatus(AgentStatus.INACTIVE);

      auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
          .eventType(AuditEventType.EXECUTION)
          .eventScope(AuditEventScope.COVERAGE_GAP)
          .eventStatus(AuditEventStatus.WARNING)
          .resourceType("Agent")
          .resourceId(agent.getId())
          .message("Agent '%s' became inactive (no heartbeat for %d minutes)".formatted(
              agent.getName(), ACTIVE_THRESHOLD / 60_000))
          .contextData(Map.of(
              "agent_id", agent.getId(),
              "endpoint_id", agent.getAsset() != null ? agent.getAsset().getId() : "unknown",
              "last_seen", agent.getLastSeen() != null ? agent.getLastSeen().toString() : "never",
              "previous_status", "ACTIVE",
              "new_status", "INACTIVE"))
          .origin(AuditEventOrigin.SYSTEM)
          .build()));
    }

    agentRepository.saveAll(newlyInactiveAgents);
  }
}
```

**Reset in `EndpointService`**:
```java
agent.setLastSeen(Instant.now());
if (agent.getStatus() == AgentStatus.INACTIVE) {
  agent.setStatus(AgentStatus.ACTIVE);
}
```

##### Rolled-up inject status (shared)

**Where**: `InjectStatusService.updateFinalInjectStatus()` — after final status is computed.

```java
auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.INJECT_FINAL_STATUS)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("Inject")
    .resourceId(injectStatus.getInject().getId())
    .message("Inject '%s' final status: %s".formatted(
        injectStatus.getInject().getTitle(), finalStatus.name()))
    .contextData(Map.of(
        "inject_id", injectStatus.getInject().getId(),
        "inject_name", injectStatus.getInject().getTitle(),
        "final_status", finalStatus.name(),
        "executor_type", executorType,
        "contributing_statuses", perAgentStatuses))
    .origin(AuditEventOrigin.SYSTEM)
    .build()));
```

##### Expectation validation results (shared)

**Where**: `InjectExpectationService.updateInjectExpectation()` (automatic) and manual validation endpoints.

```java
auditLogger.ifPresent(logger -> logger.logEvent(AuditEvent.builder()
    .eventType(AuditEventType.EXECUTION)
    .eventScope(AuditEventScope.EXPECTATION_RESULT)
    .eventStatus(AuditEventStatus.SUCCESS)
    .resourceType("InjectExpectation")
    .resourceId(expectation.getId())
    .message("Expectation '%s' result: %s".formatted(
        expectation.getType().name(), resultMet ? "met" : "not_met"))
    .contextData(Map.of(
        "inject_id", expectation.getInject().getId(),
        "expectation_type", expectation.getType().name(),
        "result", resultMet ? "met" : "not_met",
        "source", isManual ? userId : collectorName,
        "source_type", isManual ? "manual" : "automatic",
        "execution_timestamp", executionTimestamp.toString(),
        "detection_timestamp", detectionTimestamp != null ? detectionTimestamp.toString() : null))
    .origin(isManual ? AuditEventOrigin.REQUEST : AuditEventOrigin.SYSTEM)
    .build()));
```

#### Event type taxonomy

| `eventType` | `eventScope` | Origin | US |
|---|---|---|---|
| `authentication` | `login`, `logout`, `session_expired`, `unauthorized` | REQUEST | Existing |
| `mutation` | `create`, `update`, `delete`, `duplicate`, `status_change` | REQUEST | Existing |
| `execution` | `scheduled_launch` | SYSTEM | 1a/1b |
| `execution` | `inject_status_transition` | SYSTEM | 1a/1b |
| `execution` | `target_resolution` | SYSTEM | 1a |
| `execution` | `inject_queued` | SYSTEM | 1b |
| `execution` | `agent_trace_step` | SYSTEM | 1a |
| `execution` | `coverage_gap` | SYSTEM | 1a (proactive monitoring only) |
| `execution` | `inject_final_status` | SYSTEM | 1a/1b |
| `execution` | `expectation_result` | SYSTEM/REQUEST | 1a/1b |
| `system` | `retention_purge`, `job_execution`, `migration` | SYSTEM | Future |

#### Implementation order

##### Chunk 1 — Foundation + inject status transitions + rolled-up status (shared)
1. Create `AuditEvent` class with `@Getter @Builder`, `AuditEventScope` enum (with `from(Action)` utility), `AuditEventOrigin` enum
2. Extend existing `EventType` enum with `EXECUTION`, `SYSTEM` values; extend existing `EventStatus` enum with `WARNING` value
3. Add `AuditLogger.logEvent(AuditEvent)` + `doLogEvent()` (UUID generated inside `doLogEvent`)
4. Add `LogService.logGenericEvent()` with MUTATION normalization, redaction, `formatResourceType()` for `entity_type`
5. Refactor `logAuthEvent`, `logAccessControlEvent`, and `logAuthEventWithRequestContext` to accept enum params (`AuditEventScope`, `EventStatus`) and delegate to `logEvent`
6. Move `toAuditScope(Action)` from `AccessControlAuditLogAspect` to `AuditEventScope.from(Action)` static utility
7. Inject `Optional<AuditLogger>` into `InjectStatusService`
8. Log status transitions in `updateInjectStatus()` and `updateFinalInjectStatus()`
9. Capture previous status before mutation for diff logging
10. Include `executor_type` in `contextData` to distinguish OpenAEV vs integration agents
11. **Tests**:
    - Unit test `AuditLogger.logEvent()` (extend `AuditLoggerUnitTest`) — verify async dispatch, halt-on-failure, SYSTEM origin skips user metadata
    - Unit test refactored `logAuthEvent` / `logAccessControlEvent` still produce correct audit events
    - Update `AccessControlAuditLogAspectTest` to use enum captors (`AuditEventScope`, `EventStatus`) and 8-param signature (no `logUUID`)
    - Update `HaltOnFailureRollback` test to stub `logGenericEvent` instead of `logRequestEvent`
    - Integration test: verify `updateInjectStatus()` and `updateFinalInjectStatus()` trigger audit log with correct `eventType`, `eventScope`, `previousStatus`, `newStatus`, `executor_type`

##### Chunk 2 — Agent trace steps (US.1a only)
1. Log each `ExecutionTrace` creation in `InjectStatusService.createExecutionTrace()`
2. **Tests**:
    - Verify `createExecutionTrace()` emits an `AGENT_TRACE_STEP` audit event with correct `trace_id`, `step_name`, `trace_status`

##### Chunk 3 — Scheduled execution + target resolution (shared + US.1a)
1. Inject `Optional<AuditLogger>` into `InjectsExecutionJob`
2. Log after auto-start transition `SCHEDULED → RUNNING` in `handleAutoStartExercises()` (shared)
3. Enrich context with scenario info when the simulation originates from a scenario recurrence
4. Log resolved target list in `executeInject()` at inject execution time, once targets are known — including inactive agents and agentless assets in `contextData` (US.1a)
5. **Tests**:
    - Verify `handleAutoStartExercises()` emits `SCHEDULED_LAUNCH` audit event with `simulation_id`, `initiator = "scheduler"`
    - Verify scenario recurrence enriches context with `scenario_id`, `scenario_name`
    - Verify from-scratch simulation does NOT include scenario fields in context
    - Verify `executeInject()` emits `TARGET_RESOLUTION` audit event with structured `endpoints` list containing per-endpoint agent status (`AGENT_ACTIVE`, `AGENT_INACTIVE`, or `ASSET_AGENTLESS`)

##### Chunk 4 — Inject queued for integration agent (US.1b only)
1. Inject `Optional<AuditLogger>` into `ExecutionExecutorService`
2. Log inject dispatch in `launchExecutorContext()` with `INJECT_QUEUED` scope, before routing to individual `ExecutorContextService` implementations
3. Include `executor_type` and integration agent details in `contextData`
4. **Tests**:
    - Verify `launchExecutorContext()` emits `INJECT_QUEUED` audit event with correct `executor_type`, `integration_agent_id`
    - Verify event is emitted per executor type when inject targets multiple integration agents

##### Chunk 5 — Expectation results (shared)
1. Inject `Optional<AuditLogger>` into `InjectExpectationService`
2. Log in `updateInjectExpectation()` (automatic) with collector source
3. Log in manual validation path with user attribution
4. **Tests**:
    - Verify automatic expectation result emits `EXPECTATION_RESULT` audit event with `source_type = "automatic"` and `origin = SYSTEM`
    - Verify manual expectation result emits `EXPECTATION_RESULT` audit event with `source_type = "manual"`, user ID, and `origin = REQUEST`

##### Issue 6918 — Agent inactivity monitoring (next step)
1. Migration: add `agent_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` column to `agents` table + backfill inactive agents
2. Add `AgentStatus` enum (`ACTIVE`, `INACTIVE`) and `status` field to `Agent` entity
3. Change `Agent.isActive()` implementation to read persisted `status` field (no signature change — `@JsonProperty("agent_active")` preserved, zero impact on callers)
4. Delete `AgentHelper` class entirely — move `ACTIVE_THRESHOLD` constant into `AgentInactivityMonitorJob`. Migrate `VulnerableEndpointHandler` and `AgentRegisterInput` to use `agent.isActive()` directly
5. Add `AgentRepository.findByLastSeenBeforeAndStatus(Instant threshold, AgentStatus status)` query
6. Create `AgentInactivityMonitorJob` (Quartz, every 5min, `@DisallowConcurrentExecution`)
7. Inject `Optional<AuditLogger>`, transition `status` ACTIVE → INACTIVE, emit `COVERAGE_GAP` event per state change
8. In `EndpointService.register()`: set `status = ACTIVE` on agent creation, and reset `status` INACTIVE → ACTIVE on heartbeat (existing agents). Same in `updateExistingAgent()` for external executor sync
9. Register job trigger in `PlatformTriggers`
10. **Tests**:
    - Verify job emits `COVERAGE_GAP` audit event with `gap_type = "AGENT_INACTIVE"` when agent exceeds heartbeat threshold
    - Verify no duplicate event is emitted on subsequent poll cycles for the same agent
    - Verify event is re-emitted if agent recovers (heartbeat received → status reset to ACTIVE) then becomes inactive again
    - Verify job does nothing when no agents are inactive

#### Volume & performance considerations

- **Agent traces (US.1a)**: High volume — each inject × each agent × each step (4 steps). For 100 injects × 50 agents = 20,000 events. Audit emission is already async via `taskLoggerExecutor`, so this is non-blocking. Monitor executor queue depth.
- **Status transitions (shared)**: ~4 events per inject (moderate volume).
- **Inject queued (US.1b)**: 1 event per inject per integration agent dispatch (moderate volume).
- **Expectations (shared)**: Volume proportional to injects × expectation types.
- **All events (including SYSTEM-origin)**: halt-on-failure applies uniformly — a job that cannot be audited must fail. There is no per-event override.

## 5. Consequences

### Positive

- Single `logEvent(AuditEvent)` entry point handles any event type — no more specialized methods per use case.
- Simulation lifecycle is fully auditable: from scheduled launch through inject execution to expectation results.
- Agent inactivity is now proactively detected and persisted (`agent_status` column), eliminating the computed `isActive()` logic scattered across the codebase.
- Halt-on-failure guarantee is preserved for all origins (HTTP and scheduled jobs).
- Existing callers of `logAuthEvent` / `logAccessControlEvent` are unaffected — they delegate internally.

### Negative / trade-offs

- `AgentInactivityMonitorJob` adds a new Quartz job (every 5 min) and a DB migration — operational complexity.
- High-volume events (agent traces: up to 20k events per simulation run) may stress the async executor pool — requires monitoring.
- `logRequestEvent()` and `logAuthEvent()` in `LogService` are now wrappers around `logGenericEvent()` — slight indirection, but preserves caller simplicity.

### Neutral

- The REST API contract is unchanged — `@JsonProperty("agent_active")` on `isActive()` continues to serialize the same boolean field.
- PII filtering (`ObjectRedactionUtils`) applies identically to generic events — no change needed.
- `AuditLogger` remains `@ConditionalOnExpression` — all consumers use `Optional<AuditLogger>` as before.
