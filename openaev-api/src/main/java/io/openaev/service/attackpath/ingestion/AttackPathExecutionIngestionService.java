package io.openaev.service.attackpath.ingestion;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Command;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Step;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.ingestion.ResolutionInput.Endpoint;
import io.openaev.service.attackpath.ingestion.ResolutionInput.PayloadKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attack-path ingestion — Phase A (issue 5048, #203). At RUN, create one EXECUTION row per resolved
 * edge from the run's source/target resolution, on the store columns the read already consumes. The
 * tenant is set by {@code TenantBaseListener} from the current tenant context. #204/#202 update
 * these rows later (Phase B), found by the queryable {@code (inject_id, agent_id)} written here.
 */
@Service
@RequiredArgsConstructor
public class AttackPathExecutionIngestionService {

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathSourceTargetResolver resolver;

  /** The run context shared by all of a run's execution rows. */
  public record ExecutionContext(
      String simulationId,
      String stepId,
      String stepTemplateId,
      String injectExecId,
      Instant executedAt,
      String payloadName,
      String contractExternalId) {}

  @Transactional
  public void createRows(ExecutionContext ctx, List<ResolvedExecutionEdge> edges) {
    // saveAll batches the run's N rows in one round-trip (an inject can hit N targets).
    executionRepository.saveAll(edges.stream().map(edge -> toRow(ctx, edge)).toList());
  }

  /**
   * One frozen EXECUTION row (source → target) for a resolved edge, keyed by the deterministic id.
   */
  private AttackPathExecution toRow(ExecutionContext ctx, ResolvedExecutionEdge edge) {
    AttackPathExecution row = new AttackPathExecution();
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
   * the executed inject, resolve the edges, create the rows. Joins the caller's RUN transaction, so
   * the rows commit with the inject and a rolled-back run leaves none.
   *
   * <p>Scope and assumptions (agent-based; injector path is TBD): the sources are the inject's
   * direct asset endpoints and their installed agents (asset-group expansion is a follow-up); the
   * agent label is the endpoint hostname. Documented, not final.
   */
  @Transactional
  public void onRun(Step step, Inject inject, InjectorContract contract) {
    // The attack path is simulation-scoped, so an inject with no simulation records nothing.
    if (inject.getExercise() == null) {
      return;
    }
    createRows(
        context(step, inject, contract), resolver.resolve(resolutionInput(inject, contract)));
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
        contract.getExternalId());
  }

  private ResolutionInput resolutionInput(Inject inject, InjectorContract contract) {
    Payload payload = contract.getPayload();
    return new ResolutionInput(
        contract.getNeedsExecutorEffective(),
        inject.getInjector() != null ? inject.getInjector().getName() : null,
        agentEndpoints(inject),
        payloadKind(payload),
        payload instanceof DnsResolution dns ? dns.getHostname() : null,
        List.of(), // command args — TBD (command retrieval under investigation)
        null, // content selector — injector path TBD
        List.of(), // manual targets — injector path TBD
        assetEndpoints(inject));
  }

  /** The inject's asset endpoints as targets (no agent). */
  private List<Endpoint> assetEndpoints(Inject inject) {
    List<Endpoint> out = new ArrayList<>();
    for (Asset asset : inject.getAssets()) {
      if (asset instanceof io.openaev.database.model.Endpoint e) {
        out.add(
            new Endpoint(e.getId(), e.getHostname(), firstIp(e), platform(e), null, null, null));
      }
    }
    return out;
  }

  /** The agents on the inject's asset endpoints, as the agent-based sources. */
  private List<Endpoint> agentEndpoints(Inject inject) {
    List<Endpoint> out = new ArrayList<>();
    for (Asset asset : inject.getAssets()) {
      if (asset instanceof io.openaev.database.model.Endpoint e) {
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
