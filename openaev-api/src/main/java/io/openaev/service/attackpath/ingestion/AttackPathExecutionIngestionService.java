package io.openaev.service.attackpath.ingestion;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.attackpath.AttackPathIds;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
  private final AttackPathSourceTargetResolver resolver;
  private final InjectService injectService;
  private final EndpointService endpointService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantTx;

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
          executionRepository.deleteAllBySimulationId(simulationId);
          findingRepository.deleteAllBySimulationId(simulationId);
        });
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
        () -> persistExecution(getAttackPathExecution(inject, step, command)));
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
                  PrimitiveType.Hostname,
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
      } else if (targetSelector.equals("asset")) { // ASSETS
        for (Asset asset : inject.getAssets()) {
          AttackPathExecution attackPathExecution =
              setSourceInjectorTargetAsset(asset, inject, step);
          attackPathExecutions.add(attackPathExecution);
        }
      } else if (targetSelector.equals("asset_group")) { // ASSET
        for (AssetGroup assetGroup : inject.getAssetGroups()) {
          // assetsFromAssetGroup resolves static AND dynamic (filter-matched) members and unproxies
          // them; assetGroup.getAssets() only returns the statically pinned ones.
          for (Asset asset : assetGroupService.assetsFromAssetGroup(assetGroup)) {
            AttackPathExecution attackPathExecution =
                setSourceInjectorTargetAsset(asset, inject, step);
            attackPathExecutions.add(attackPathExecution);
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
                  PrimitiveType.Hostname,
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

      } else if (targetSelector.equals("asset") || targetSelector.equals("asset_group")) { // ASSET
        if (inject.getAssets().isEmpty()) return null;
        target = inject.getAssets().getFirst().getId();
        AttackPathIds.executionNode(inject.getId(), target, inject.getInjector().getId());
      }
    }
    return null;
  }
}
