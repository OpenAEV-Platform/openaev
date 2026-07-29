package io.openaev.service.attackpath.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.attackpath.AttackPathIds;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Attack-path ingestion — Phase A (issue 5048, #203). At RUN, create one EXECUTION row per resolved
 * edge from the run's source/target resolution, on the store columns the read already consumes. The
 * rows carry the inject's tenant (the write runs under it, see {@link #onRun}). #204/#202 update
 * these rows later (Phase B), found by the queryable {@code (step_id, agent_id)} written here.
 */
@Service
@RequiredArgsConstructor
public class AttackPathExecutionIngestionService {

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingRepository findingRepository;
  private final AttackPathExecutionRemediationRepository executionRemediationRepository;
  private final DetectionRemediationRepository detectionRemediationRepository;
  private final AttackPathSourceTargetResolver resolver;
  private final InjectService injectService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;
  private final ObjectMapper objectMapper;

  /**
   * Clears a simulation's attack-path rows (on simulation reset and delete). As a writer of a
   * tenant-active table it goes through Hibernate under a v2 scope, never raw JDBC (MT v2 /
   * TenantNonOrmAccessArchTest): {@code executeNew} opens a tenant-scoped REQUIRES_NEW transaction
   * (the callers run inside their own tx), so the delete runs under a real scope instead of
   * fail-closing to zero rows. The {@code attackpath_execution_finding} links ride the ON DELETE
   * CASCADE from both parents, so deleting executions and findings clears the links too.
   */
  public void deleteAllBySimulationId(@NotBlank String simulationId, @NotBlank String tenantId) {
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> {
          executionRemediationRepository.deleteAllBySimulationId(simulationId);
          executionRepository.deleteAllBySimulationId(simulationId);
          findingRepository.deleteAllBySimulationId(simulationId);
        });
  }

  public void updateTerminalView(Inject inject) {

    Map<String, StringBuilder> executionTracesAttackPath =
        getExecutionTracesByEndpointIndex(inject);
    tenantTx.executeNew(
        TxCtx.forTenant(inject.getTenant().getId()),
        () -> {
          for (String executionIndex : executionTracesAttackPath.keySet()) {
            executionRepository.updateTerminalViewByExecutionIndex(
                executionIndex,
                executionTracesAttackPath.get(executionIndex).toString(),
                inject.getTenant().getId());
          }
        });
  }

  /**
   * Updates the prevention/detection/vulnerability status columns of the execution rows identified
   * by their index. For each list, selects the {@link InjectExpectationResult} whose {@code result}
   * label has the highest priority (success > partial > pending > failure) according to the
   * corresponding {@link ExpectationType} labels, then persists that label as the status value.
   */
  public void updateExpectationByExecutionIndex(
      Inject inject, Map<String, ExecutionExpectationResults> expectationResults) {
    tenantTx.executeNew(
        TxCtx.forTenant(inject.getTenant().getId()),
        () ->
            expectationResults.forEach(
                (index, expectation) -> {
                  String preventionStatus =
                      resolveHighestPriorityResult(
                          expectation.prevention(), ExpectationType.PREVENTION);
                  String detectionStatus =
                      resolveHighestPriorityResult(
                          expectation.detection(), ExpectationType.DETECTION);
                  String vulnerabilityStatus =
                      resolveHighestPriorityResult(
                          expectation.vulnerability(), ExpectationType.VULNERABILITY);
                  executionRepository.updateExpectationStatusByExecutionId(
                      index,
                      preventionStatus,
                      detectionStatus,
                      vulnerabilityStatus,
                      inject.getTenant().getId());
                }));
  }

  /**
   * Among the given results, picks the one whose {@code result} label maps to the highest-priority
   * outcome for the supplied type (success=3 > partial=2 > pending=1 > failure=0). Returns {@code
   * null} when the list is empty.
   */
  private static String resolveHighestPriorityResult(
      List<InjectExpectationResult> results, ExpectationType type) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    return results.stream()
        .max(Comparator.comparingInt(r -> resultPriority(r.getResult(), type)))
        .map(InjectExpectationResult::getResult)
        .orElse(null);
  }

  /**
   * Maps a result label to a numeric priority so that success beats partial, partial beats pending,
   * and pending beats failure. Unknown labels fall back to the lowest priority (0).
   */
  private static int resultPriority(String result, ExpectationType type) {
    if (result == null) {
      return 0;
    }
    if (result.equals(type.successLabel)) {
      return 3;
    }
    if (result.equals(type.partialLabel)) {
      return 2;
    }
    if (result.equals(type.pendingLabel)) {
      return 1;
    }
    return 0; // failureLabel or unknown
  }

  /** The run context shared by all of a run's execution rows. */
  public record ExecutionContext(
      String simulationId,
      String stepId,
      String stepTemplateId,
      String injectExecId,
      Instant executedAt,
      String payloadName) {}

  /**
   * Records a run's attack-path rows. As a background writer of a tenant-active table it goes
   * through the tenant primitive, never {@code @Transactional} (activate-tenant-table skill, Phase
   * 5b): {@code executeNew} opens its own tenant-scoped REQUIRES_NEW transaction, so the write
   * carries the inject's tenant and commits independently of the run — an attack-path failure can
   * never roll the real inject execution back, and the resolution runs under the inject's scope.
   * The caller recovers around this boundary (a try/catch in InjectExecutionStep), never inside it.
   */
  public void onRun(Inject inject, Step step, String command) {
    if (inject.getExercise() == null) {
      return; // the attack path is simulation-scoped: no simulation, nothing to record
    }
    tenantTx.executeNew(
        TxCtx.forTenant(inject.getTenant().getId()),
        () -> {
          persistExecution(getAttackPathExecution(inject, step, command));
          persistExecutionRemediations(inject, step);
        });
  }

  private void persistExecutionRemediations(Inject inject, Step step) {
    if (step.getId() == null || inject.getPayload().isEmpty()) {
      return;
    }

    List<DetectionRemediationRepository.SnapshotRow> remediationRows =
        detectionRemediationRepository.findSnapshotRowsByPayloadId(
            inject.getPayload().get().getId());
    if (remediationRows.isEmpty()) {
      return;
    }

    List<AttackPathExecutionRemediation> snapshots =
        remediationRows.stream().map(row -> toExecutionRemediation(step.getId(), row)).toList();
    executionRemediationRepository.saveAll(snapshots);
  }

  private static AttackPathExecutionRemediation toExecutionRemediation(
      String stepId, DetectionRemediationRepository.SnapshotRow row) {
    AttackPathExecutionRemediation remediation = new AttackPathExecutionRemediation();
    remediation.setId(
        AttackPathIds.executionRemediationRow(
            stepId, row.getCollectorType(), row.getSecurityPlatformId()));
    remediation.setStepId(stepId);
    remediation.setValues(row.getValues());
    remediation.setAuthorRule(row.getAuthorRule());
    remediation.setCollectorType(row.getCollectorType());
    remediation.setSecurityPlatformId(row.getSecurityPlatformId());
    return remediation;
  }

  public void persistExecution(List<AttackPathExecution> attackPathExecutions) {
    executionRepository.saveAll(attackPathExecutions);
  }

  public List<AttackPathExecution> getAttackPathExecution(
      Inject inject, Step step, String command) {

    if (inject.getInjectorContract().isEmpty()) return List.of();
    boolean needExecutor = inject.getInjectorContract().get().getNeedsExecutor();
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        injectService.getAgentsAndAgentlessAssetsByInject(inject);
    List<AttackPathExecution> attackPathExecutions = new ArrayList<>();

    if (needExecutor) {

      if (inject.getPayload().isEmpty()) return List.of();
      PayloadType payloadType = PayloadType.fromString(inject.getPayload().get().getType());

      switch (payloadType) {
        case EXECUTABLE, FILE_DROP, AI_ATTACK -> { // AGENT -> ASSET
          for (Agent agent : agentsAndAssetsAgentless.agents()) {
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            AttackPathExecution attackPathExecution = new AttackPathExecution();
            attackPathExecution.setId(
                AttackPathIds.executionNode(inject.getId(), endpoint.getId(), agent.getId()));
            attackPathExecution.setGlobalInformation(step, inject);
            attackPathExecution.setSourceAgentInformation(agent, endpoint);
            attackPathExecution.setTargetAssetInformation(endpoint);
            attackPathExecutions.add(attackPathExecution);
          }
        }
        case DNS_RESOLUTION -> {
          DnsResolution dnsResolution = (DnsResolution) inject.getPayload().get();
          for (Agent agent : agentsAndAssetsAgentless.agents()) {
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            AttackPathExecution attackPathExecution = new AttackPathExecution();
            attackPathExecution.setId(
                AttackPathIds.executionNode(
                    inject.getId(), dnsResolution.getHostname(), agent.getId()));
            attackPathExecution.setGlobalInformation(step, inject);
            attackPathExecution.setSourceAgentInformation(agent, endpoint);
            attackPathExecution.setTargetDiscoveredInformation(dnsResolution.getHostname());
            attackPathExecution.setTargetHostname(dnsResolution.getHostname());
            attackPathExecutions.add(attackPathExecution);
          }
        }
        case COMMAND -> {
          Set<PrimitiveType> typeEndpoint =
              Set.of(
                  PrimitiveType.IPv4,
                  PrimitiveType.IPv6,
                  PrimitiveType.TargetedAsset,
                  PrimitiveType.Host,
                  PrimitiveType.Domain,
                  PrimitiveType.IpSubnet);
          Command payloadCommand = (Command) inject.getPayload().get();
          String targetArgIdentified = null;
          List<String> targetArg =
              payloadCommand.getArguments().stream()
                  .filter(arg -> typeEndpoint.contains(arg.getType()))
                  .map(PayloadArgument::getDefaultValue)
                  .toList();
          // IF MORE THAN 1 ARGS CAN MATCH AN ENDPOINT type WE DO NOT USED IT
          if (targetArg.size() == 1) {
            targetArgIdentified = targetArg.getFirst();
          }

          for (Agent agent : agentsAndAssetsAgentless.agents()) { // AGENT ->
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            AttackPathExecution attackPathExecution = new AttackPathExecution();
            attackPathExecution.setGlobalInformation(step, inject);
            attackPathExecution.setSourceAgentInformation(agent, endpoint);
            attackPathExecution.setCommand(command);

            if (targetArgIdentified != null) {
              attackPathExecution.setId(
                  AttackPathIds.executionNode(inject.getId(), targetArgIdentified, agent.getId()));
              attackPathExecution.setTargetDiscoveredInformation(targetArgIdentified);
              attackPathExecutions.add(attackPathExecution);
            } else {
              attackPathExecution.setId(
                  AttackPathIds.executionNode(inject.getId(), endpoint.getId(), agent.getId()));
              attackPathExecution.setTargetAssetInformation(endpoint);
              attackPathExecutions.add(attackPathExecution);
            }
          }
        }
      }

    } else { // INJECTOR ->
      String targetSelector = inject.getContent().get("target_selector").asText();

      if (targetSelector.equals("manual")) { // DISCOVERY
        String[] targets = inject.getContent().get("targets").asText().split(",");
        for (String injectorTargets : targets) {
          AttackPathExecution attackPathExecution = new AttackPathExecution();
          attackPathExecution.setId(
              AttackPathIds.executionNode(
                  inject.getId(), injectorTargets, inject.getInjector().getId()));
          attackPathExecution.setGlobalInformation(step, inject);
          attackPathExecution.setSourceInjectorInformation(inject.getInjector());
          attackPathExecution.setTargetDiscoveredInformation(injectorTargets);
          attackPathExecutions.add(attackPathExecution);
        }
      } else if (targetSelector.equals("assets") || targetSelector.equals("asset_group")) {
        // The injector "assets" selector (the only stored value; the contract choices are
        // {assets, manual}) targets both the inject's direct assets and its asset groups. Resolve
        // both, exactly as the executor path does via getAgentsAndAgentlessAssetsByInject.
        for (Asset asset : inject.getAssets()) {
          attackPathExecutions.add(setSourceInjectorTargetAsset(asset, inject, step));
        }
        for (AssetGroup assetGroup : inject.getAssetGroups()) {
          // assetsFromAssetGroup resolves static AND dynamic (filter-matched) members and unproxies
          // them; assetGroup.getAssets() only returns the statically pinned ones.
          for (Asset asset : assetGroupService.assetsFromAssetGroup(assetGroup)) {
            attackPathExecutions.add(setSourceInjectorTargetAsset(asset, inject, step));
          }
        }
      }
    }
    return attackPathExecutions;
  }

  private AttackPathExecution setSourceInjectorTargetAsset(Asset asset, Inject inject, Step step) {
    Endpoint endpoint = endpointService.getEndpoint(asset.getId(), inject.getTenant().getId());
    AttackPathExecution attackPathExecution = new AttackPathExecution();
    attackPathExecution.setId(
        AttackPathIds.executionNode(inject.getId(), asset.getId(), inject.getInjector().getId()));
    attackPathExecution.setSourceInjectorInformation(inject.getInjector());
    attackPathExecution.setGlobalInformation(step, inject);
    attackPathExecution.setTargetAssetInformation(endpoint);
    return attackPathExecution;
  }

  /**
   * String target can be AGENT (EXECUTOR) ASSET (INJECTOR) DISCOVERED (PAYLOAD COMMAND & INJECTOR)
   */
  public String getExecutionIndex(Inject inject, String target) {
    if (inject.getInjectorContract().isEmpty()) return null;
    boolean needExecutor = inject.getInjectorContract().get().getNeedsExecutor();
    AgentsAndAssetsAgentless agentsAndAssetsAgentless =
        injectService.getAgentsAndAgentlessAssetsByInject(inject);

    if (needExecutor) {

      if (inject.getPayload().isEmpty()) return null;
      PayloadType payloadType = PayloadType.fromString(inject.getPayload().get().getType());

      String finalTarget = target;
      Agent agent =
          agentsAndAssetsAgentless.agents().stream()
              .filter(targets -> targets.getId().equals(finalTarget))
              .findFirst()
              .orElse(null);
      if (agent == null) return null;

      switch (payloadType) {
        case EXECUTABLE, FILE_DROP, AI_ATTACK -> { // AGENT -> ASSET
          io.openaev.database.model.Endpoint endpoint =
              (io.openaev.database.model.Endpoint)
                  org.hibernate.Hibernate.unproxy(agent.getAsset());
          return AttackPathIds.executionNode(inject.getId(), endpoint.getId(), target);
        }
        case DNS_RESOLUTION -> { // AGENT -> DISCOVERED
          DnsResolution dnsResolution = (DnsResolution) inject.getPayload().get();
          return AttackPathIds.executionNode(inject.getId(), dnsResolution.getHostname(), target);
        }
        case COMMAND -> { // AGENT ->
          Set<PrimitiveType> typeEndpoint =
              Set.of(
                  PrimitiveType.IPv4,
                  PrimitiveType.IPv6,
                  PrimitiveType.TargetedAsset,
                  PrimitiveType.Host,
                  PrimitiveType.Domain,
                  PrimitiveType.IpSubnet);
          Command payloadCommand = (Command) inject.getPayload().get();
          String targetArgIdentified = null;
          List<String> targetArg =
              payloadCommand.getArguments().stream()
                  .filter(arg -> typeEndpoint.contains(arg.getType()))
                  .map(PayloadArgument::getDefaultValue)
                  .toList();
          // IF MORE THAN 1 ARGS CAN MATCH AN ENDPOINT type WE DO NOT USED IT
          if (targetArg.size() == 1) {
            targetArgIdentified = targetArg.getFirst();
          }

          if (targetArgIdentified != null) { // DISCOVERED
            return AttackPathIds.executionNode(inject.getId(), targetArgIdentified, target);

          } else { // ASSET
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            return AttackPathIds.executionNode(inject.getId(), endpoint.getId(), target);
          }
        }
      }

    } else { // INJECTOR -> 1 Execution by target
      String targetSelector = inject.getContent().get("target_selector").asText();

      if (targetSelector.equals("manual")) { // DISCOVERY
        String[] targets = inject.getContent().get("targets").asText().split(",");
        if (targets.length < 1) return null;

        target = inject.getContent().get("targets").asText().split(",")[0];
        return AttackPathIds.executionNode(inject.getId(), target, inject.getInjector().getId());

      } else if (targetSelector.equals("assets") || targetSelector.equals("asset_group")) { // ASSET
        if (inject.getAssets().isEmpty()) return null;
        target = inject.getAssets().getFirst().getId();
        return AttackPathIds.executionNode(inject.getId(), target, inject.getInjector().getId());
      }
    }
    return null;
  }

  /**
   * Mirrors the frontend {@code parseTraceOutput} in {@code TerminalView.tsx}: tries to JSON-parse
   * the raw message and extracts {@code stdout} / {@code stderr}. Falls back to treating the full
   * message as stdout when it is not valid JSON (plain-text implants, legacy traces).
   */
  private record TraceOutput(String stdout, String stderr) {}

  private TraceOutput parseTraceMessage(@Nullable String message) {
    if (message == null || message.isBlank()) {
      return new TraceOutput("", "");
    }
    try {
      JsonNode node = objectMapper.readTree(message);
      String stdout = node.path("stdout").asText("");
      String stderr = node.path("stderr").asText("");
      return new TraceOutput(stdout, stderr);
    } catch (Exception e) {
      return new TraceOutput(message, "");
    }
  }

  private Map<String, StringBuilder> getExecutionTracesByEndpointIndex(Inject inject) {
    Map<String, StringBuilder> tracesByEndpointSource = new HashMap<>();
    if (inject.getStatus().isEmpty()) return tracesByEndpointSource;
    if (inject.getInjectorContract().isEmpty()) return tracesByEndpointSource;

    InjectStatus status = inject.getStatus().get();
    List<ExecutionTrace> executionTraces = status.getTraces();

    if (inject.getInjectorContract().get().getNeedsExecutor()) {
      executionTraces.forEach(
          executionTrace -> {
            if (executionTrace.getAgent() == null || executionTrace.getAgent().getAsset() == null) {
              return;
            }
            if (!executionTrace.getAction().equals(ExecutionTraceAction.EXECUTION)) {
              return;
            }
            String agentId = getExecutionIndex(inject, executionTrace.getAgent().getId());
            if (agentId == null) {
              return;
            }

            StringBuilder agentTraces =
                tracesByEndpointSource.computeIfAbsent(agentId, k -> new StringBuilder());

            TraceOutput parsed = parseTraceMessage(executionTrace.getMessage());
            // A trace with no timestamp must not render a literal "null" at the start of the line.
            String prefix = executionTrace.getTime() != null ? executionTrace.getTime() + " " : "";
            if (!parsed.stdout().isBlank()) {
              agentTraces.append(prefix).append(parsed.stdout()).append("\n");
            }
            if (!parsed.stderr().isBlank()) {
              agentTraces.append(prefix).append(parsed.stderr()).append("\n");
            }
          });
    } else {
      executionTraces.forEach(
          executionTrace -> {
            String index = getExecutionIndex(inject, null);
            if (index == null) {
              return;
            }
            StringBuilder injectorTraces =
                tracesByEndpointSource.computeIfAbsent(index, k -> new StringBuilder());
            // A trace with no timestamp must not render a literal "null" at the start of the line.
            if (executionTrace.getTime() != null) {
              injectorTraces.append(executionTrace.getTime()).append(" ");
            }
            injectorTraces
                .append(executionTrace.getStatus().name())
                .append(" ")
                .append(executionTrace.getMessage())
                .append("\n");
          });
    }
    return tracesByEndpointSource;
  }

  public record ExecutionExpectationResults(
      List<InjectExpectationResult> prevention,
      List<InjectExpectationResult> detection,
      List<InjectExpectationResult> vulnerability) {

    private static ExecutionExpectationResults empty() {
      return new ExecutionExpectationResults(
          new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  public Map<String, ExecutionExpectationResults> getExpectationByEndpointIndex(
      Inject inject, List<BaseInjectExpectation> expectations) {
    Map<String, ExecutionExpectationResults> expectationByEndpointIndex = new HashMap<>();

    expectations.forEach(
        expectation -> {
          if (!(expectation instanceof TechnicalInjectExpectation technical)) {
            return;
          }
          String index =
              technical.getAgent() != null
                  ? getExecutionIndex(inject, technical.getAgent().getId())
                  : getExecutionIndex(inject, null);
          if (index == null) {
            return;
          }

          ExecutionExpectationResults groupedResults =
              expectationByEndpointIndex.computeIfAbsent(
                  index, k -> ExecutionExpectationResults.empty());

          if (expectation instanceof PreventionInjectExpectation) {
            groupedResults.prevention().addAll(expectation.getResults());
            return;
          }
          if (expectation instanceof DetectionInjectExpectation) {
            groupedResults.detection().addAll(expectation.getResults());
            return;
          }
          if (expectation instanceof VulnerabilityInjectExpectation) {
            groupedResults.vulnerability().addAll(expectation.getResults());
          }
        });

    return expectationByEndpointIndex;
  }
}
