package io.openaev.service.attackpath.ingestion;

import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Command;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.ResolutionInput.Endpoint;
import io.openaev.service.attackpath.ingestion.ResolutionInput.PayloadKind;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Attack-path ingestion — Phase A (issue 5048, #203). At RUN, create one EXECUTION row per resolved
 * edge from the run's source/target resolution, on the store columns the read already consumes.
 * #204/#202 update these rows later (Phase B), found by the queryable {@code (inject_id, agent_id)}
 * written here.
 *
 * <p><b>Why the write is attributed by hand.</b> This runs from the cross-tenant inject executor,
 * which sets no tenant scope. Relying on the ambient one would not fail loudly: {@code
 * TenantContext.getCurrentTenant()} falls back to the default tenant, so {@code TenantBaseListener}
 * would stamp every tenant's rows with that one and the real owner would see nothing. So the write
 * takes the inject's own tenant, opens its own scoped transaction through the tenant primitive, and
 * has the attribution validated by {@code TenantWriteScopeResolver} rather than assumed.
 */
@Service
@RequiredArgsConstructor
public class AttackPathExecutionIngestionService {

  private final EntityManager entityManager;
  private final AttackPathSourceTargetResolver resolver;
  private final io.openaev.service.AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;
  private final TenantWriteScopeResolver writeScopeResolver;

  /** The run context shared by all of a run's execution rows. */
  public record ExecutionContext(
      String simulationId,
      String stepId,
      String stepTemplateId,
      String injectExecId,
      Instant executedAt,
      String payloadName,
      String contractExternalId,
      String payloadId,
      String injectorType) {}

  /**
   * Row building, exposed to this package's tests only. Production must go through {@link #onRun},
   * which opens the tenant scope: {@code saveAll} on an assigned id does a read before writing, and
   * an unscoped read of an active table is fail-closed, so calling this without a scope would turn
   * a second run into a primary-key violation instead of the intended update.
   *
   * <p>Package-private on purpose, and with no {@code @Transactional}: Spring's proxying ignores
   * the annotation on non-public methods, so carrying it here would only be decorative.
   */
  void createRows(String tenantId, ExecutionContext ctx, List<ResolvedExecutionEdge> edges) {
    writeRows(tenantId, ctx, edges);
  }

  /**
   * One multi-row {@code INSERT ... ON CONFLICT DO NOTHING} for the whole run.
   *
   * <p><b>Why not {@code saveAll}.</b> Two reasons, one of correctness and one of cost. The
   * correctness one is decisive on its own: the row id is assigned (deterministic), so Spring Data
   * routes every row through {@code merge}, and merge overwrites. A replayed inject run would blank
   * the columns #204/#202 filled in between, silently erasing their work. {@code DO NOTHING} makes
   * the create genuinely create-once, so a re-run never touches an existing row. The cost reason is
   * secondary: merge issues a read per row before writing, which the single insert avoids; measured
   * at roughly a 10x lower per-row write cost, though both paths are linear in the number of rows.
   *
   * <p>Native, but through Hibernate on purpose: the statement inspector sees it and can scope it.
   * It is fail-closed, so a shape it cannot rewrite breaks the path rather than leaking, which is
   * why {@code AttackPathIngestionTenantAttributionTest} exercises this statement on every build.
   * {@code tenant_id} is an explicit column here: the inspector never attributes an insert.
   */
  private void writeRows(String tenantId, ExecutionContext ctx, List<ResolvedExecutionEdge> edges) {
    if (edges.isEmpty()) {
      return;
    }
    List<AttackPathExecution> all = edges.stream().map(edge -> toRow(tenantId, ctx, edge)).toList();
    // Postgres caps a prepared statement at 65535 parameters, and an inject's fan-out is a product
    // of its sources and targets, so the row count grows fast: 100 endpoints already means 10000
    // rows. Batched rather than assumed small, which is how the first version broke.
    for (int from = 0; from < all.size(); from += BATCH_ROWS) {
      insertBatch(all.subList(from, Math.min(from + BATCH_ROWS, all.size())));
    }
  }

  /** 27 columns per row, so this stays an order of magnitude under the parameter ceiling. */
  private static final int BATCH_ROWS = 500;

  private void insertBatch(List<AttackPathExecution> rows) {
    StringBuilder sql = new StringBuilder("INSERT INTO attackpath_execution (");
    sql.append(String.join(", ", COLUMNS)).append(") VALUES ");
    for (int row = 0; row < rows.size(); row++) {
      sql.append(row == 0 ? "" : ", ").append("(");
      for (int column = 0; column < COLUMNS.length; column++) {
        sql.append(column == 0 ? "" : ", ").append("?").append(row * COLUMNS.length + column + 1);
      }
      sql.append(")");
    }
    // The primary key is the deterministic id, so the conflict target is the row we would replace.
    sql.append(" ON CONFLICT (attackpath_execution_id) DO NOTHING");

    Query insert = entityManager.createNativeQuery(sql.toString());
    int parameter = 1;
    for (AttackPathExecution row : rows) {
      for (Object value : values(row)) {
        insert.setParameter(parameter++, value);
      }
    }
    insert.executeUpdate();
  }

  /** Column order shared by the insert and {@link #values}; the two must stay aligned. */
  private static final String[] COLUMNS = {
    "attackpath_execution_id",
    "tenant_id",
    "attackpath_execution_simulation_id",
    "attackpath_execution_inject_id",
    "attackpath_execution_step_id",
    "attackpath_execution_step_template_id",
    "attackpath_execution_executed_at",
    "attackpath_execution_payload_name",
    "attackpath_execution_payload_id",
    "attackpath_execution_contract_external_id",
    "attackpath_execution_injector_type",
    "attackpath_execution_source_kind",
    "attackpath_execution_source_injector",
    "attackpath_execution_source_asset_id",
    "attackpath_execution_source_hostname",
    "attackpath_execution_source_ip",
    "attackpath_execution_source_platform",
    "attackpath_execution_agent_id",
    "attackpath_execution_agent_name",
    "attackpath_execution_agent_privilege",
    "attackpath_execution_target_kind",
    "attackpath_execution_target_asset_id",
    "attackpath_execution_target_raw_value",
    "attackpath_execution_target_key",
    "attackpath_execution_target_hostname",
    "attackpath_execution_target_ip",
    "attackpath_execution_target_platform"
  };

  private static Object[] values(AttackPathExecution row) {
    return new Object[] {
      row.getId(),
      row.getTenant().getId(),
      row.getSimulationId(),
      row.getInjectId(),
      row.getStepId(),
      row.getStepTemplateId(),
      // Explicitly UTC: the column has no time zone, and Timestamp.from() would bake in the
      // server's offset, which the ORM path used to handle for us.
      row.getExecutedAt() == null
          ? null
          : Timestamp.valueOf(LocalDateTime.ofInstant(row.getExecutedAt(), ZoneOffset.UTC)),
      row.getPayloadName(),
      row.getPayloadId(),
      row.getContractExternalId(),
      row.getInjectorType(),
      row.getSourceKind(),
      row.getSourceInjector(),
      row.getSourceAssetId(),
      row.getSourceHostname(),
      row.getSourceIp(),
      row.getSourcePlatform(),
      row.getAgentId(),
      row.getAgentName(),
      row.getAgentPrivilege(),
      row.getTargetKind(),
      row.getTargetAssetId(),
      row.getTargetRawValue(),
      row.getTargetKey(),
      row.getTargetHostname(),
      row.getTargetIp(),
      row.getTargetPlatform()
    };
  }

  /**
   * One frozen EXECUTION row (source → target) for a resolved edge, keyed by the deterministic id.
   */
  private AttackPathExecution toRow(
      String tenantId, ExecutionContext ctx, ResolvedExecutionEdge edge) {
    AttackPathExecution row = new AttackPathExecution();
    // Explicit attribution, so TenantBaseListener never fires for this row: it only fills a null
    // tenant, and the value set here wins. Measured: a managed reference via getReference makes no
    // difference to the write cost, so the runbook's plain `new Tenant(id)` idiom stands.
    row.setTenant(new Tenant(tenantId));
    // Deterministic natural key so #204/#202 (Phase B) and retries converge on this same row.
    row.setId(AttackPathIds.executionNode(ctx.injectExecId(), edge.targetKey(), edge.agentId()));
    row.setSimulationId(ctx.simulationId());
    // Queryable lookup key so #204's per-output update finds the run's rows by (inject id, agent
    // id).
    row.setInjectId(ctx.injectExecId());
    row.setStepId(ctx.stepId());
    row.setStepTemplateId(ctx.stepTemplateId());
    row.setExecutedAt(ctx.executedAt());
    row.setPayloadName(ctx.payloadName());
    // The run's injector contract, so the read can resolve its ATT&CK techniques.
    row.setContractExternalId(ctx.contractExternalId());
    // The run's payload (so the read can resolve its detection remediations) and injector type (so
    // the graph can label the injector node with its real type).
    row.setPayloadId(ctx.payloadId());
    row.setInjectorType(ctx.injectorType());
    row.setSourceKind(edge.sourceKind());
    row.setSourceInjector(edge.sourceInjector());
    row.setSourceAssetId(edge.sourceAssetId());
    row.setSourceHostname(edge.sourceHostname());
    row.setSourceIp(edge.sourceIp());
    row.setSourcePlatform(edge.sourcePlatform());
    row.setAgentId(edge.agentId());
    row.setAgentName(edge.agentName());
    row.setAgentPrivilege(edge.agentPrivilege());
    row.setTargetKind(edge.targetKind());
    row.setTargetAssetId(edge.targetAssetId());
    row.setTargetRawValue(edge.targetRawValue());
    row.setTargetKey(edge.targetKey());
    row.setTargetHostname(edge.targetHostname());
    row.setTargetIp(edge.targetIp());
    row.setTargetPlatform(edge.targetPlatform());
    return row;
  }

  /**
   * Phase A entry point, called (guarded) at RUN: extract the run context + resolution input from
   * the executed inject, resolve the edges, create the rows.
   *
   * <p><b>Transaction semantics, changed deliberately.</b> This no longer joins the run's
   * transaction: the primitive opens a new one ({@code REQUIRES_NEW}) so the write can carry its
   * own tenant scope. Consequence to know about: the rows commit on their own, so a run that later
   * rolls back leaves them behind. That is the accepted trade for correct attribution, and it is
   * also what makes the caller's guard genuinely safe, since a failure here can no longer poison
   * the run's transaction.
   *
   * <p>Scope and assumptions (agent-based; injector path is TBD): the sources are the inject's
   * endpoints and their installed agents, resolved as direct assets plus every asset group's
   * members (mirroring the engine's own target resolution); the agent label is the endpoint
   * hostname. Documented, not final.
   */
  public void onRun(Step step, Inject inject, InjectorContract contract) {
    // The attack path is simulation-scoped, so an inject with no simulation records nothing.
    if (inject.getExercise() == null) {
      return;
    }
    // The write carries the inject's own tenant, not the executor's ambient state, and opens its
    // own scoped transaction through the primitive rather than joining the run's. The resolver
    // validates the attribution instead of us trusting it.
    TxCtx scope = TxCtx.forTenant(inject.getTenant().getId());
    tenantTx.executeNew(
        scope,
        () ->
            writeRows(
                writeScopeResolver.tenantForWrite(scope, null),
                context(step, inject, contract),
                resolver.resolve(resolutionInput(inject, contract))));
  }

  private ExecutionContext context(Step step, Inject inject, InjectorContract contract) {
    Payload payload = contract.getPayload();
    return new ExecutionContext(
        inject.getExercise() != null ? inject.getExercise().getId() : null,
        step.getId(),
        step.getStepTemplate() != null ? step.getStepTemplate().getId() : null,
        inject.getId(),
        Instant.now(),
        payload != null ? payload.getName() : null,
        // Prefer the contract's external id, but fall back to its id: many built-in contracts carry a
        // blank external id, and the reads resolve the contract by id OR external id
        // (findByIdOrExternalId), so storing the id keeps the contract name + ATT&CK resolvable.
        contract.getExternalId() != null && !contract.getExternalId().isBlank()
            ? contract.getExternalId()
            : contract.getId(),
        payload != null ? payload.getId() : null,
        inject.getInjector() != null ? inject.getInjector().getType() : null);
  }

  private ResolutionInput resolutionInput(Inject inject, InjectorContract contract) {
    Payload payload = contract.getPayload();
    // Resolve the inject's endpoints once (direct + groups) and share the list, so the group
    // expansion's per-group read does not run twice per inject.
    List<io.openaev.database.model.Endpoint> endpoints = resolvedEndpoints(inject);
    return new ResolutionInput(
        contract.getNeedsExecutorEffective(),
        inject.getInjector() != null ? inject.getInjector().getName() : null,
        agentEndpoints(endpoints),
        payloadKind(payload),
        payload instanceof DnsResolution dns ? dns.getHostname() : null,
        List.of(), // command args — TBD (command retrieval under investigation)
        null, // content selector — injector path TBD
        List.of(), // manual targets — injector path TBD
        assetEndpoints(endpoints));
  }

  /**
   * The inject's endpoints, mirroring the engine's target resolution: direct assets plus every
   * asset group's members (static and dynamic), unproxied and deduplicated by id. The dedup is
   * efficiency, not correctness: an endpoint in both the direct list and a group would otherwise be
   * resolved twice and build duplicate edges, but the deterministic row id plus {@code ON CONFLICT
   * DO NOTHING} collapse them to one row regardless. Resolved once per run and shared by the target
   * and agent-source views.
   */
  private List<io.openaev.database.model.Endpoint> resolvedEndpoints(Inject inject) {
    Map<String, io.openaev.database.model.Endpoint> byId = new LinkedHashMap<>();
    for (Asset asset : inject.getAssets()) {
      // Unproxy before the type check: a lazy Asset proxy fails instanceof even for a real
      // endpoint.
      if (Hibernate.unproxy(asset) instanceof io.openaev.database.model.Endpoint e) {
        byId.putIfAbsent(e.getId(), e);
      }
    }
    assetGroupService.assetsFromAssetGroupMap(inject.getAssetGroups()).values().stream()
        .flatMap(List::stream)
        .forEach(e -> byId.putIfAbsent(e.getId(), e));
    return new ArrayList<>(byId.values());
  }

  /** The resolved endpoints as targets (no agent). */
  private List<Endpoint> assetEndpoints(List<io.openaev.database.model.Endpoint> endpoints) {
    List<Endpoint> out = new ArrayList<>();
    for (io.openaev.database.model.Endpoint e : endpoints) {
      out.add(new Endpoint(e.getId(), e.getHostname(), firstIp(e), platform(e), null, null, null));
    }
    return out;
  }

  /** The agents on the resolved endpoints, as the agent-based sources. */
  private List<Endpoint> agentEndpoints(List<io.openaev.database.model.Endpoint> endpoints) {
    List<Endpoint> out = new ArrayList<>();
    for (io.openaev.database.model.Endpoint e : endpoints) {
      for (Agent agent : e.getAgents()) {
        out.add(
            new Endpoint(
                e.getId(),
                e.getHostname(),
                firstIp(e),
                platform(e),
                agent.getId(),
                e.getHostname(), // assumed agent label = its endpoint hostname (to confirm)
                agent.getPrivilege() != null ? agent.getPrivilege().name() : null));
      }
    }
    return out;
  }

  private static String firstIp(io.openaev.database.model.Endpoint e) {
    return e.getIps() != null && e.getIps().length > 0 ? e.getIps()[0] : null;
  }

  private static String platform(io.openaev.database.model.Endpoint e) {
    return e.getPlatform() != null ? e.getPlatform().name() : null;
  }

  private static PayloadKind payloadKind(Payload payload) {
    if (payload instanceof Command) {
      return PayloadKind.COMMAND;
    }
    if (payload instanceof DnsResolution) {
      return PayloadKind.DNS_RESOLUTION;
    }
    return PayloadKind
        .OTHER; // FileDrop/Executable → OTHER (same target rule as FILE in the resolver)
  }
}
