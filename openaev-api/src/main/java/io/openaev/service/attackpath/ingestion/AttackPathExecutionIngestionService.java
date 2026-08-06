package io.openaev.service.attackpath.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionCollector;
import io.openaev.database.model.attackpath.AttackPathExecutionRemediation;
import io.openaev.database.repository.DetectionRemediationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionCollectorRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRemediationRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.InjectExpectationTraceService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.dto.AttackPathAlertDTO;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Attack-path ingestion — Phase A (issue 5048, #203). At RUN, create one EXECUTION row per resolved
 * edge from the run's source/target resolution, on the store columns the read already consumes. The
 * rows carry the inject's tenant (the write runs under it, see {@link #onRun}). #204/#202 update
 * these rows later (Phase B), found by the queryable {@code (step_id, agent_id)} written here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttackPathExecutionIngestionService {

  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathExecutionCollectorRepository executionCollectorRepository;
  private final AttackPathFindingRepository findingRepository;
  private final AttackPathExecutionRemediationRepository executionRemediationRepository;
  private final DetectionRemediationRepository detectionRemediationRepository;
  private final AttackPathSourceTargetResolver resolver;
  private final InjectService injectService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationTraceService injectExpectationTraceService;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final TenantScopedTransaction tenantTx;
  private final AttackPathVersionService versionService;
  private final ObjectMapper objectMapper;

  /**
   * Argument types that can name an endpoint, so a Command payload carrying exactly one of them
   * targets that value rather than the endpoint it runs on.
   */
  private static final Set<PrimitiveType> TARGET_ARGUMENT_TYPES =
      Set.of(
          PrimitiveType.IPv4,
          PrimitiveType.IPv6,
          PrimitiveType.TargetedAsset,
          PrimitiveType.Host,
          PrimitiveType.Domain,
          PrimitiveType.IpSubnet);

  /**
   * Values that name the executing machine itself. A command targeting one of these did not reach a
   * second endpoint, so it must stay attached to the endpoint it ran on: treating them as
   * discovered targets both hides the real endpoint and merges every endpoint's local executions
   * into one shared node, since the target key would be the literal value for all of them.
   */
  private static final Set<String> SELF_TARGET_VALUES =
      Set.of("localhost", "127.0.0.1", "::1", "0.0.0.0", "*");

  /**
   * The trimmed remote target a raw value names, or {@code null} when it names the executing
   * machine itself (or nothing at all).
   *
   * <p>Every remote-target resolution goes through here, so the value that gets persisted (and
   * keyed into {@link AttackPathIds#executionNode}) is normalized exactly once: trimmed, so two
   * authorings of the same target differing only by stray whitespace converge on one node, while
   * the authored case is kept for display — only the self-target comparison lowercases.
   */
  @Nullable
  private static String remoteTargetOrNull(@Nullable String rawValue) {
    if (rawValue == null) {
      return null;
    }
    String value = rawValue.trim();
    if (value.isEmpty() || SELF_TARGET_VALUES.contains(value.toLowerCase())) {
      return null;
    }
    return value;
  }

  /**
   * The endpoint a Command payload targets, or {@code null} when it targets the endpoint it runs
   * on.
   *
   * <p>Reads the value <b>this inject actually uses</b> — the inject's own content overrides the
   * payload's default — because the default is authored once on the payload while the value is
   * chosen per inject. Using the default made every inject of a payload whose {@code host} argument
   * defaults to {@code localhost} land on a discovered "localhost" node instead of its endpoint. A
   * blank (or JSON null) override does not name a target, so it falls back to the default rather
   * than suppressing it.
   *
   * <p>Returns {@code null} when the payload declares several endpoint-ish arguments (ambiguous,
   * the pre-existing rule) or when the value names the executing machine.
   */
  @Nullable
  private String resolveCommandTargetValue(Inject inject, Command payloadCommand) {
    List<PayloadArgument> endpointArguments =
        payloadCommand.getArguments().stream()
            .filter(arg -> TARGET_ARGUMENT_TYPES.contains(arg.getType()))
            .toList();
    if (endpointArguments.size() != 1) {
      return null;
    }
    PayloadArgument argument = endpointArguments.getFirst();
    JsonNode override =
        inject.getContent() == null ? null : inject.getContent().get(argument.getKey());
    // A JSON null node must not read as the literal string "null" (asText would).
    String overrideValue = override == null || override.isNull() ? null : override.asText();
    String value =
        overrideValue != null && !overrideValue.isBlank()
            ? overrideValue
            : argument.getDefaultValue();
    return remoteTargetOrNull(value);
  }

  /**
   * Clears a simulation's attack-path rows (on simulation reset and delete). As a writer of a
   * tenant-active table it goes through Hibernate under a v2 scope, never raw JDBC (MT v2 /
   * TenantNonOrmAccessArchTest): {@code executeNew} opens a tenant-scoped REQUIRES_NEW transaction
   * (the callers run inside their own tx), so the delete runs under a real scope instead of
   * fail-closing to zero rows. The {@code attackpath_execution_finding} links ride the ON DELETE
   * CASCADE from both parents, so deleting executions and findings clears the links too.
   *
   * <p>The simulation's version counter goes with the rows (#6647, spec 002): a client still
   * polling with an old {@code since} then finds no counter, and the delta read answers that with a
   * resync, which is how the contract expresses a deletion. Keeping the counter instead would leave
   * the client convinced it was up to date on an emptied graph.
   */
  public void deleteAllBySimulationId(@NotBlank String simulationId, @NotBlank String tenantId) {
    executeTenantScoped(
        tenantId,
        () -> {
          executionRemediationRepository.deleteAllBySimulationId(simulationId);
          executionCollectorRepository.deleteAllBySimulationId(simulationId, tenantId);
          executionRepository.deleteAllBySimulationId(simulationId);
          findingRepository.deleteAllBySimulationId(simulationId);
          versionService.deleteBySimulationId(simulationId, tenantId);
        });
  }

  public void updateTerminalView(Inject inject) {

    Map<String, StringBuilder> executionTracesAttackPath =
        getExecutionTracesByEndpointIndex(inject);
    executeTenantScoped(
        inject.getTenant().getId(),
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
   *
   * <p>This is the only path that writes verdicts onto the projection (#6647, spec 002, FR5), and
   * it carries the two properties the real-time delta depends on. The simulation's attack-path
   * version is bumped once per step event and stamped on every row the updates touch — without that
   * stamp a changed verdict never reaches a polling client, which is the whole point of the
   * feature. And the update itself is guarded on the three status columns, so replaying the same
   * expectation result — which the chaining engine does on every execution event, by design —
   * matches zero rows and tells no client anything changed.
   *
   * <p>Like the other ingestion writers, the bump and the updates share one {@code executeNew}
   * transaction, so a version a client can observe is never ahead of the rows backing it. An empty
   * result set never opens it: nothing written, nothing to version.
   */
  public void updateExpectationByExecutionIndex(
      Inject inject, Map<String, ExecutionExpectationResults> expectationResults) {
    if (expectationResults.isEmpty() || inject.getExercise() == null) {
      return; // nothing to write, so nothing to version; and the projection is simulation-scoped
    }
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> {
          long version = versionService.bump(simulationId, tenantId);
          int changed = 0;
          for (Map.Entry<String, ExecutionExpectationResults> entry :
              expectationResults.entrySet()) {
            ExecutionExpectationResults expectation = entry.getValue();
            String preventionStatus =
                resolveHighestPriorityResult(expectation.prevention(), ExpectationType.PREVENTION);
            String detectionStatus =
                resolveHighestPriorityResult(expectation.detection(), ExpectationType.DETECTION);
            String vulnerabilityStatus =
                resolveHighestPriorityResult(
                    expectation.vulnerability(), ExpectationType.VULNERABILITY);
            changed +=
                executionRepository.updateExpectationStatusByExecutionId(
                    entry.getKey(),
                    preventionStatus,
                    detectionStatus,
                    vulnerabilityStatus,
                    tenantId,
                    version);
          }
          // Only a write that touched rows is worth telling a client about. The engine replays
          // these
          // results on every execution event, so nudging on a replay would flood the stream's
          // shared
          // executor for nothing — see AttackPathVersionService#publishChanged.
          if (changed > 0) {
            versionService.publishChanged(simulationId, tenantId, version);
          }
        });
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
    String simulationId = inject.getExercise().getId();
    String tenantId = inject.getTenant().getId();
    tenantTx.executeNew(
        TxCtx.forTenant(tenantId),
        () -> {
          // The remediation snapshot rides the same transaction but never bumps the graph
          // version: it only writes to the separate snapshot table, which the delta contract
          // does not observe (#6647, spec 002).
          persistExecutionRemediations(inject, step);
          List<AttackPathExecution> rows = getAttackPathExecution(inject, step, command);
          if (rows.isEmpty()) {
            return; // nothing written, so nothing to version: never bump on an empty write
          }
          // Bump and stamp inside this transaction, so the version a client can observe is never
          // ahead of the rows backing it (#6647, spec 002).
          long version = versionService.bump(simulationId, tenantId);
          rows.forEach(row -> row.setRowVersion(version));
          persistExecution(rows);
          // New rows always change the graph, so this one always nudges.
          versionService.publishChanged(simulationId, tenantId, version);
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
        case NETWORK_TRAFFIC -> { // AGENT -> DISCOVERED (the destination it reached)
          NetworkTraffic networkTraffic = (NetworkTraffic) inject.getPayload().get();
          String destination = remoteTargetOrNull(networkTraffic.getIpDst());
          for (Agent agent : agentsAndAssetsAgentless.agents()) {
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            AttackPathExecution attackPathExecution = new AttackPathExecution();
            attackPathExecution.setGlobalInformation(step, inject);
            attackPathExecution.setSourceAgentInformation(agent, endpoint);
            if (destination != null) {
              attackPathExecution.setId(
                  AttackPathIds.executionNode(inject.getId(), destination, agent.getId()));
              attackPathExecution.setTargetDiscoveredInformation(destination);
            } else {
              attackPathExecution.setId(
                  AttackPathIds.executionNode(inject.getId(), endpoint.getId(), agent.getId()));
              attackPathExecution.setTargetAssetInformation(endpoint);
            }
            attackPathExecutions.add(attackPathExecution);
          }
        }
        case COMMAND -> {
          Command payloadCommand = (Command) inject.getPayload().get();
          String targetArgIdentified = resolveCommandTargetValue(inject, payloadCommand);

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
        // Safety net, not dead code: this switch is a statement, so a payload type added to the
        // enum
        // without a branch here compiles and silently writes no row at all - which is how
        // NETWORK_TRAFFIC stayed invisible on the graph. Any unknown type falls back to the
        // endpoint
        // it ran on, the one edge that is always true of an agent-based execution.
        default -> {
          log.warn(
              "Attack path: payload type '{}' has no target resolution; attaching inject {} to the "
                  + "endpoint it ran on",
              payloadType,
              inject.getId());
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
      }

    } else { // INJECTOR ->
      // Human-in-the-loop injects (email, SMS, ...) can only target TEAMS, never assets, so they
      // fell through every asset/manual branch below and wrote nothing - the autonomous phishing
      // step rendered an empty attack path. Emit the team and each ENABLED recipient (injector ->
      // team -> persons) so the step lands on the graph and its findings attach to those nodes.
      if (!inject.getTeams().isEmpty()) {
        attackPathExecutions.addAll(getTeamTargetExecutions(inject, step));
      }

      JsonNode selectorNode =
          inject.getContent() == null ? null : inject.getContent().get("target_selector");
      String targetSelector = selectorNode == null ? null : selectorNode.asText();
      if (targetSelector == null) {
        return attackPathExecutions; // e.g. a team-only inject: nothing more to resolve
      }

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

  /**
   * Attack-path rows for a team-targeted (human-in-the-loop) inject: one TEAM node targeted by the
   * injector, and one PERSON node per ENABLED recipient hanging off the team (injector -> team ->
   * persons). Recipients are the players enabled on THIS simulation ({@code exercise_teams_users}),
   * not merely team members, mirroring what the email/SMS executor actually delivers to - so the
   * graph shows exactly who was reached and findings (e.g. harvested credentials) attach per
   * person.
   */
  private List<AttackPathExecution> getTeamTargetExecutions(Inject inject, Step step) {
    List<AttackPathExecution> rows = new ArrayList<>();
    String simulationId = inject.getExercise() == null ? null : inject.getExercise().getId();
    String injectorId = inject.getInjector() == null ? "injector" : inject.getInjector().getId();
    for (Team teamRef : inject.getTeams()) {
      Team team = (Team) org.hibernate.Hibernate.unproxy(teamRef);
      AttackPathExecution teamRow = new AttackPathExecution();
      teamRow.setId(AttackPathIds.executionNode(inject.getId(), team.getId(), injectorId));
      teamRow.setGlobalInformation(step, inject);
      teamRow.setSourceInjectorInformation(inject.getInjector());
      teamRow.setTargetTeamInformation(team.getId(), team.getName());
      rows.add(teamRow);

      if (simulationId == null) {
        continue;
      }
      List<User> recipients =
          team.getExerciseTeamUsers().stream()
              .filter(
                  etu ->
                      etu.getExercise() != null && simulationId.equals(etu.getExercise().getId()))
              .map(ExerciseTeamUser::getUser)
              .filter(Objects::nonNull)
              .distinct()
              .toList();
      for (User user : recipients) {
        AttackPathExecution personRow = new AttackPathExecution();
        personRow.setId(AttackPathIds.executionNode(inject.getId(), user.getId(), team.getId()));
        personRow.setGlobalInformation(step, inject);
        personRow.setSourceTeamInformation(team.getId(), team.getName());
        personRow.setTargetPersonInformation(user.getId(), personLabel(user));
        rows.add(personRow);
      }
    }
    return rows;
  }

  private static String personLabel(User user) {
    if (user.getEmail() != null && !user.getEmail().isBlank()) {
      return user.getEmail();
    }
    return user.getId();
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
        case NETWORK_TRAFFIC -> { // AGENT -> DISCOVERED (the destination it reached)
          NetworkTraffic networkTraffic = (NetworkTraffic) inject.getPayload().get();
          String destination = remoteTargetOrNull(networkTraffic.getIpDst());
          if (destination != null) {
            return AttackPathIds.executionNode(inject.getId(), destination, target);
          }
          io.openaev.database.model.Endpoint endpoint =
              (io.openaev.database.model.Endpoint)
                  org.hibernate.Hibernate.unproxy(agent.getAsset());
          return AttackPathIds.executionNode(inject.getId(), endpoint.getId(), target);
        }
        case COMMAND -> { // AGENT ->
          Command payloadCommand = (Command) inject.getPayload().get();
          String targetArgIdentified = resolveCommandTargetValue(inject, payloadCommand);

          if (targetArgIdentified != null) { // DISCOVERED
            return AttackPathIds.executionNode(inject.getId(), targetArgIdentified, target);

          } else { // ASSET
            io.openaev.database.model.Endpoint endpoint =
                (io.openaev.database.model.Endpoint)
                    org.hibernate.Hibernate.unproxy(agent.getAsset());
            return AttackPathIds.executionNode(inject.getId(), endpoint.getId(), target);
          }
        }
        // Mirrors the write side's fallback, so an unknown payload type still resolves to the id
        // that was actually persisted for it.
        default -> {
          io.openaev.database.model.Endpoint endpoint =
              (io.openaev.database.model.Endpoint)
                  org.hibernate.Hibernate.unproxy(agent.getAsset());
          return AttackPathIds.executionNode(inject.getId(), endpoint.getId(), target);
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
            groupedResults.prevention().addAll(expectationResultsForStatus(expectation));
            return;
          }
          if (expectation instanceof DetectionInjectExpectation) {
            groupedResults.detection().addAll(expectationResultsForStatus(expectation));
            return;
          }
          if (expectation instanceof VulnerabilityInjectExpectation) {
            groupedResults.vulnerability().addAll(expectationResultsForStatus(expectation));
          }
        });

    return expectationByEndpointIndex;
  }

  /** Persists one snapshot row per execution x collector-result line used by execution detail. */
  public void upsertExecutionCollectors(Inject inject, List<BaseInjectExpectation> expectations) {
    if (inject.getTenant() == null || inject.getExercise() == null) {
      return;
    }
    executeTenantScoped(
        inject.getTenant().getId(),
        () -> {
          List<AttackPathExecutionCollector> rows = new ArrayList<>();
          Set<String> touchedExecutionIds = new HashSet<>();
          for (BaseInjectExpectation expectation : expectations) {
            if (!(expectation instanceof TechnicalInjectExpectation technical)
                || expectation.getType() == null) {
              continue;
            }
            String executionId =
                technical.getAgent() != null
                    ? getExecutionIndex(inject, technical.getAgent().getId())
                    : getExecutionIndex(inject, null);
            if (executionId == null) {
              continue;
            }
            touchedExecutionIds.add(executionId);

            List<InjectExpectationResult> expectationResults =
                expectation.getResults() == null ? List.of() : expectation.getResults();
            for (InjectExpectationResult result : expectationResults) {
              String sourceKey =
                  result.getSourceId() != null && !result.getSourceId().isBlank()
                      ? result.getSourceId()
                      : (result.getSourceName() != null && !result.getSourceName().isBlank()
                          ? result.getSourceName()
                          : "unknown");
              String statusLabel = resolveCollectorStatusLabel(expectation, result);
              AttackPathExecutionCollector row = new AttackPathExecutionCollector();
              row.setId(
                  AttackPathIds.executionCollectorRow(
                      executionId, expectation.getType().name(), sourceKey));
              row.setTenant(inject.getTenant());
              row.setSimulationId(inject.getExercise().getId());
              row.setExecutionId(executionId);
              row.setExpectationType(expectation.getType().name());
              row.setSourceId(result.getSourceId());
              row.setSourceType(resolveCollectorSourceType(result));
              row.setSourceName(result.getSourceName());
              row.setSourceAssetId(result.getSourceAssetId());
              row.setResultStatusLabel(statusLabel);
              row.setDetectionTime(result.getDate());
              row.setAlerts(buildAlertsNode(expectation.getId(), result));
              row.setResultScore(result.getScore());
              row.setResultDate(result.getDate());
              rows.add(row);
            }
          }
          if (touchedExecutionIds.isEmpty()) {
            return;
          }
          executionCollectorRepository.deleteAllByExecutionIdInAndTenantId(
              new ArrayList<>(touchedExecutionIds), inject.getTenant().getId());
          if (!rows.isEmpty()) {
            executionCollectorRepository.saveAll(rows);
          } else {
            log.debug(
                "Attack-path collector upsert produced no rows for inject {} (tenant {}, expectations {})",
                inject.getId(),
                inject.getTenant().getId(),
                expectations.size());
          }
        });
  }

  private void executeTenantScoped(String tenantId, Runnable work) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      tenantTx.executeNew(TxCtx.forTenant(tenantId), work);
      return;
    }
    tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  private String resolveCollectorStatusLabel(
      BaseInjectExpectation expectation, InjectExpectationResult result) {
    if (result.getResult() != null && !result.getResult().isBlank()) {
      return result.getResult();
    }
    ExpectationType expectationType = toExpectationType(expectation);
    if (expectationType != null
        && expectationType.pendingLabel != null
        && !expectationType.pendingLabel.isBlank()) {
      return expectationType.pendingLabel;
    }
    return "Unknown";
  }

  private List<InjectExpectationResult> expectationResultsForStatus(
      BaseInjectExpectation expectation) {
    if (expectation.getResults() != null && !expectation.getResults().isEmpty()) {
      return expectation.getResults();
    }
    InjectExpectationResult pending = new InjectExpectationResult();
    ExpectationType type = toExpectationType(expectation);
    if (type != null && type.pendingLabel != null && !type.pendingLabel.isBlank()) {
      pending.setResult(type.pendingLabel);
    } else {
      pending.setResult("Pending");
    }
    return List.of(pending);
  }

  private ExpectationType toExpectationType(BaseInjectExpectation expectation) {
    if (expectation instanceof PreventionInjectExpectation) {
      return ExpectationType.PREVENTION;
    }
    if (expectation instanceof DetectionInjectExpectation) {
      return ExpectationType.DETECTION;
    }
    if (expectation instanceof VulnerabilityInjectExpectation) {
      return ExpectationType.VULNERABILITY;
    }
    return null;
  }

  private JsonNode buildAlertsNode(String expectationId, InjectExpectationResult result) {
    List<AttackPathAlertDTO> alerts = extractAlerts(expectationId, result);
    try {
      return objectMapper.valueToTree(alerts);
    } catch (Exception e) {
      return objectMapper.createArrayNode();
    }
  }

  private List<AttackPathAlertDTO> extractAlerts(
      String expectationId, InjectExpectationResult result) {
    if (expectationId == null || expectationId.isBlank()) {
      return List.of();
    }
    String sourceId = result.getSourceId();
    if (sourceId == null || sourceId.isBlank()) {
      return List.of();
    }
    for (String traceSourceId : resolveTraceSourceIds(sourceId)) {
      List<InjectExpectationTrace> traces =
          injectExpectationTraceService.getInjectExpectationTracesFromCollector(
              expectationId, traceSourceId);
      if (!traces.isEmpty()) {
        return mapTracesToAlerts(traces);
      }
    }
    return List.of();
  }

  private Set<String> resolveTraceSourceIds(String sourceId) {
    Set<String> sourceIds = new LinkedHashSet<>();
    sourceIds.add(sourceId);
    securityPlatformRepository
        .findByExternalReference(sourceId)
        .map(SecurityPlatform::getId)
        .ifPresent(sourceIds::add);
    return sourceIds;
  }

  private List<AttackPathAlertDTO> mapTracesToAlerts(List<InjectExpectationTrace> traces) {
    List<AttackPathAlertDTO> alerts = new ArrayList<>();
    for (InjectExpectationTrace trace : traces) {
      String title =
          trace.getAlertName() == null || trace.getAlertName().isBlank()
              ? "Alert"
              : trace.getAlertName();
      String date = trace.getAlertDate() == null ? null : trace.getAlertDate().toString();
      alerts.add(new AttackPathAlertDTO(trace.getId(), title, date, trace.getAlertLink()));
    }
    return alerts;
  }

  private String resolveCollectorSourceType(InjectExpectationResult result) {
    if (result.getSourcePlatform() != null
        && !result.getSourcePlatform().isBlank()
        && isBusinessSecurityPlatformType(result.getSourcePlatform())) {
      return result.getSourcePlatform();
    }
    if (result.getSourceType() != null && !result.getSourceType().isBlank()) {
      return result.getSourceType();
    }
    return AssetType.Values.SECURITY_PLATFORM_TYPE;
  }

  private boolean isBusinessSecurityPlatformType(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    try {
      SecurityPlatform.SECURITY_PLATFORM_TYPE.valueOf(value.trim().toUpperCase(Locale.ROOT));
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
