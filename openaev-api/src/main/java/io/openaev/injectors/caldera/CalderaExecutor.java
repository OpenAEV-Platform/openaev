package io.openaev.injectors.caldera;

import static io.openaev.database.model.Command.COMMAND_TYPE;
import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewInfoTrace;
import static io.openaev.model.expectation.DetectionExpectation.*;
import static io.openaev.model.expectation.ManualExpectation.*;
import static io.openaev.model.expectation.PreventionExpectation.*;
import static io.openaev.utils.AgentUtils.isValidAgent;
import static io.openaev.utils.ExpectationUtils.*;
import static java.time.Instant.now;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.*;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.PayloadCommandBlock;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Injector;
import io.openaev.injectors.caldera.client.model.Ability;
import io.openaev.injectors.caldera.client.model.Agent;
import io.openaev.injectors.caldera.client.model.ExploitResult;
import io.openaev.injectors.caldera.model.CalderaInjectContent;
import io.openaev.injectors.caldera.service.CalderaInjectorService;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.model.expectation.PreventionExpectation;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.time.TimeUtils;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component(CalderaContract.TYPE)
@RequiredArgsConstructor
@Slf4j
public class CalderaExecutor extends Injector {

  private static final String CALDERA_FAILED_TO_EXECUTE_THE_ABILITY_ON_AGENT =
      "Caldera failed to execute the ability on agent ";
  private static final int RETRY_NUMBER = 20;

  private final CalderaInjectorService calderaService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  @Override
  @Transactional
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {
    CalderaInjectContent content = contentConvert(injection, CalderaInjectContent.class);

    String obfuscator =
        content.getObfuscator() != null
            ? content.getObfuscator()
            : CalderaInjectContent.getDefaultObfuscator();

    Inject inject = this.injectService.inject(injection.getInjection().getInject().getId());

    List<AssetToExecute> assetToExecutes = this.injectService.resolveAllAssetsToExecute(inject);

    // Execute inject for all assetToExecutes
    if (assetToExecutes.isEmpty()) {
      execution.addTrace(
          getNewErrorTrace(
              "Found 0 asset to execute the ability on (likely this inject does not have any target or the targeted asset is inactive and has been purged)",
              ExecutionTraceAction.COMPLETE));
    }

    List<String> asyncIds = new ArrayList<>();
    List<Expectation> expectations = new ArrayList<>();
    List<Map<String, String>> additionalFields = new ArrayList<>();

    inject
        .getInjectorContract()
        .ifPresentOrElse(
            injectorContract -> {
              ObjectNode rawContent = injection.getInjection().getInject().getContent();
              ObjectNode contractContent = injectorContract.getConvertedContent();
              List<JsonNode> contractTextFields =
                  StreamSupport.stream(contractContent.get("fields").spliterator(), false)
                      .filter(
                          contractElement -> contractElement.get("type").asText().equals("text"))
                      .toList();

              if (!contractTextFields.isEmpty()) {
                contractTextFields.forEach(
                    jsonField -> {
                      String key = jsonField.get("key").asText();
                      if (rawContent.get(key) != null) {
                        Map<String, String> additionalField = new HashMap<>();
                        additionalField.put("trait", key);
                        additionalField.put("value", rawContent.get(key).asText());
                        additionalFields.add(additionalField);
                      }
                    });
              }

              String contract;
              if (injectorContract.getPayload() != null) {
                // This is a payload, need to create the ability on the fly
                List<Ability> abilities =
                    calderaService.abilities().stream()
                        .filter(
                            ability ->
                                ability.getName().equals(injectorContract.getPayload().getId()))
                        .toList();
                if (!abilities.isEmpty()) {
                  calderaService.deleteAbility(abilities.getFirst());
                }
                Ability abilityToExecute =
                    calderaService.createAbility(injectorContract.getPayload());
                contract = abilityToExecute.getAbility_id();
              } else {
                contract = injectorContract.getId();
              }

              Map<String, List<io.openaev.database.model.Agent>> executedAgentByEndpoint =
                  new HashMap<>();

              // Loop for every asset in this inject
              assetToExecutes.forEach(
                  assetToExecute -> {
                    Asset asset = assetToExecute.asset();

                    if (!(asset instanceof Endpoint)) {
                      return;
                    }

                    // We execute just one time the inject in every agent
                    if (!executedAgentByEndpoint.containsKey(asset.getId())) {
                      Endpoint endpointAgent = (Endpoint) asset;

                      List<io.openaev.database.model.Agent> executedAgents = new ArrayList<>();

                      // Loop for every validated agent in this endpoint
                      endpointAgent.getAgents().stream()
                          .filter(agent -> isValidAgent(inject, agent))
                          .forEach(
                              agent -> {
                                try {
                                  io.openaev.database.model.Agent executionAgent =
                                      this.findAndRegisterAgentForExecution(
                                          injection.getInjection().getInject(),
                                          endpointAgent,
                                          agent);
                                  if (executionAgent != null) {
                                    if (Arrays.stream(injectorContract.getPlatforms())
                                        .anyMatch(s -> s.equals(endpointAgent.getPlatform()))) {
                                      String result =
                                          this.calderaService.exploit(
                                              obfuscator,
                                              executionAgent.getExternalReference(),
                                              contract,
                                              additionalFields);
                                      if (result.contains("complete")) {
                                        execution.addTrace(
                                            getNewInfoTrace(
                                                "Request to execute the ability sent to Caldera",
                                                ExecutionTraceAction.START,
                                                agent,
                                                List.of()));
                                        ExploitResult exploitResult =
                                            this.calderaService.exploitResult(
                                                executionAgent.getExternalReference(), contract);
                                        asyncIds.add(exploitResult.getLinkId());
                                        executedAgents.add(executionAgent);
                                        execution.addTrace(
                                            getNewInfoTrace(
                                                exploitResult.getCommand(),
                                                ExecutionTraceAction.EXECUTION,
                                                agent,
                                                List.of(exploitResult.getLinkId())));
                                        execution.addTrace(
                                            getNewInfoTrace(
                                                "Caldera executed the ability on agent "
                                                    + executionAgent.getExecutedByUser()
                                                    + " using "
                                                    + executionAgent.getProcessName()
                                                    + " (paw: "
                                                    + executionAgent.getExternalReference()
                                                    + ", linkID: "
                                                    + exploitResult.getLinkId()
                                                    + ")",
                                                ExecutionTraceAction.EXECUTION,
                                                agent,
                                                List.of(exploitResult.getLinkId())));
                                      } else {
                                        execution.addTrace(
                                            getNewErrorTrace(
                                                CALDERA_FAILED_TO_EXECUTE_THE_ABILITY_ON_AGENT
                                                    + agent.getExecutedByUser()
                                                    + " ("
                                                    + result
                                                    + ")",
                                                ExecutionTraceAction.COMPLETE,
                                                agent));
                                      }
                                    } else {
                                      execution.addTrace(
                                          getNewErrorTrace(
                                              CALDERA_FAILED_TO_EXECUTE_THE_ABILITY_ON_AGENT
                                                  + agent.getExecutedByUser()
                                                  + "(platform is not compatible:"
                                                  + endpointAgent.getPlatform().name()
                                                  + ")",
                                              ExecutionTraceAction.COMPLETE,
                                              agent));
                                    }
                                  } else {
                                    execution.addTrace(
                                        getNewErrorTrace(
                                            CALDERA_FAILED_TO_EXECUTE_THE_ABILITY_ON_AGENT
                                                + agent.getExecutedByUser()
                                                + " (temporary injector not spawned correctly)",
                                            ExecutionTraceAction.COMPLETE,
                                            agent));
                                  }
                                } catch (Exception e) {
                                  execution.addTrace(
                                      getNewErrorTrace(
                                          CALDERA_FAILED_TO_EXECUTE_THE_ABILITY_ON_AGENT
                                              + agent.getExecutedByUser()
                                              + " ("
                                              + e.getMessage()
                                              + ")",
                                          ExecutionTraceAction.COMPLETE,
                                          agent));
                                  log.error(e.getMessage(), e);
                                }
                              });

                      executedAgentByEndpoint.put(asset.getId(), executedAgents);
                    }

                    // Creation of Expectations
                    computeExpectationsForAssetAndAgents(
                        expectations,
                        content,
                        assetToExecute,
                        executedAgentByEndpoint.get(asset.getId()));
                  });
            },
            () ->
                execution.addTrace(
                    getNewErrorTrace(
                        "Inject does not have a contract", ExecutionTraceAction.COMPLETE)));

    if (asyncIds.isEmpty()) {
      throw new UnsupportedOperationException(
          "Caldera failed to execute the ability due to above errors");
    }

    List<AssetGroup> assetGroups = injection.getAssetGroups();
    assetGroups.forEach(
        (assetGroup -> computeExpectationsForAssetGroup(expectations, content, assetGroup)));

    injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);
    return new ExecutionProcess(true);
  }

  @Override
  public StatusPayload getPayloadOutput(String externalId) {
    StatusPayload statusPayload = new StatusPayload();
    Ability ability = calderaService.findAbilityById(externalId);
    if (ability != null) {
      ability
          .getExecutors()
          .forEach(
              executor -> {
                PayloadCommandBlock payloadCommandBlock = new PayloadCommandBlock();
                if (executor.getCommand() != null && !executor.getCommand().isBlank()) {
                  payloadCommandBlock.setContent(executor.getCommand());
                }
                if (executor.getCleanup() != null && !executor.getCleanup().isEmpty()) {
                  payloadCommandBlock.setCleanupCommand(executor.getCleanup());
                }
                if (executor.getCommandExecutor() != null
                    && !executor.getCommandExecutor().isBlank()) {
                  payloadCommandBlock.setExecutor(executor.getCommandExecutor());
                }
                statusPayload.setPayloadCommandBlocks(
                    Collections.singletonList(payloadCommandBlock));
              });
      statusPayload.setName(ability.getName());
      statusPayload.setType(COMMAND_TYPE);
      statusPayload.setDescription(ability.getDescription());
      statusPayload.setExternalId(externalId);
    }

    return statusPayload;
  }

  // -- PRIVATE --

  private io.openaev.database.model.Agent findAndRegisterAgentForExecution(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final io.openaev.database.model.Agent agent)
      throws InterruptedException {
    io.openaev.database.model.Agent agentForExecution = null;
    if (!assetEndpoint.getType().equals("Endpoint")) {
      log.error(
          "Caldera failed to execute ability on the asset because type is not supported: {}",
          assetEndpoint.getType());
      return null;
    }
    log.info("Trying to find an available executor for {}", assetEndpoint.getName());
    for (int i = 0; i < RETRY_NUMBER; i++) {
      // Find an executor agent matching the assetEndpoint
      log.info("Listing agentsCaldera...");
      List<Agent> agentsCaldera =
          this.calderaService.agents().stream()
              .filter(
                  agentCaldera ->
                      agentCaldera.getExe_name().contains("implant")
                          && (now().toEpochMilli()
                                  - TimeUtils.toInstant(agentCaldera.getCreated()).toEpochMilli())
                              < io.openaev.helper.AgentHelper.ACTIVE_THRESHOLD
                          && (agentCaldera.getHost().equalsIgnoreCase(assetEndpoint.getHostname())
                              || agentCaldera
                                  .getHost()
                                  .split("\\.")[0]
                                  .equalsIgnoreCase(assetEndpoint.getHostname().split("\\.")[0]))
                          && Arrays.stream(assetEndpoint.getIps())
                              .anyMatch(
                                  s ->
                                      Arrays.stream(agentCaldera.getHost_ip_addrs())
                                          .toList()
                                          .contains(s)))
              .toList();
      log.info("List return with {} agents", agentsCaldera.size());

      if (!agentsCaldera.isEmpty()) {
        for (Agent agentCaldera : agentsCaldera) {
          // Check in the database if not exist
          List<io.openaev.database.model.Agent> resolvedExistingAgent =
              this.agentService.findByExternalReference(agentCaldera.getPaw());

          if (resolvedExistingAgent.isEmpty()) {
            log.info("Agent found and not present in the database, creating it...");
            io.openaev.database.model.Agent newAgent = new io.openaev.database.model.Agent();
            newAgent.setInject(inject);
            newAgent.setParent(agent);
            newAgent.setProcessName(agentCaldera.getExe_name());
            newAgent.setExecutor(agent.getExecutor());
            newAgent.setExternalReference(agentCaldera.getPaw());
            newAgent.setPrivilege(io.openaev.database.model.Agent.PRIVILEGE.admin);
            newAgent.setDeploymentMode(io.openaev.database.model.Agent.DEPLOYMENT_MODE.session);
            newAgent.setExecutedByUser(agent.getExecutedByUser());
            newAgent.setAsset(assetEndpoint);
            agentForExecution = this.agentService.createOrUpdateAgent(newAgent);
            break;
          }
        }
      }
      if (agentForExecution != null) {
        break;
      }
      Thread.sleep(5000);
    }
    return agentForExecution;
  }

  /** In case of direct asset, we have an individual expectation for the asset */
  private void computeExpectationsForAssetAndAgents(
      final List<Expectation> expectations,
      @NotNull final CalderaInjectContent content,
      @NotNull final AssetToExecute assetToExecute,
      final List<io.openaev.database.model.Agent> executedAgents) {

    if (!content.getExpectations().isEmpty()) {
      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  (expectation) ->
                      switch (expectation.getType()) {
                        case PREVENTION ->
                            getPreventionExpectationsByAsset(
                                OAEV_IMPLANT_CALDERA,
                                assetToExecute,
                                executedAgents,
                                expectation,
                                new HashMap<>(),
                                null)
                                .stream();
                        case DETECTION ->
                            getDetectionExpectationsByAsset(
                                OAEV_IMPLANT_CALDERA,
                                assetToExecute,
                                executedAgents,
                                expectation,
                                new HashMap<>(),
                                null)
                                .stream();
                        case MANUAL ->
                            getManualExpectationsByAsset(
                                OAEV_IMPLANT_CALDERA, assetToExecute, executedAgents, expectation)
                                .stream();
                        default -> Stream.of();
                      })
              .toList());
    }
  }

  /**
   * In case of asset group if expectation group -> we have an expectation for the group and one for
   * each asset if not expectation group -> we have an individual expectation for each asset
   */
  private void computeExpectationsForAssetGroup(
      @NotNull final List<Expectation> expectations,
      @NotNull final CalderaInjectContent content,
      @NotNull final AssetGroup assetGroup) {
    if (!content.getExpectations().isEmpty()) {
      expectations.addAll(
          content.getExpectations().stream()
              .flatMap(
                  (expectation) ->
                      switch (expectation.getType()) {
                        case PREVENTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  (asset) ->
                                      expectations.stream()
                                          .filter(e -> EXPECTATION_TYPE.PREVENTION == e.type())
                                          .anyMatch(
                                              (e) ->
                                                  ((PreventionExpectation) e).getAsset() != null
                                                      && ((PreventionExpectation) e)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                preventionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case DETECTION -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  (asset) ->
                                      expectations.stream()
                                          .filter(e -> EXPECTATION_TYPE.DETECTION == e.type())
                                          .anyMatch(
                                              (e) ->
                                                  ((DetectionExpectation) e).getAsset() != null
                                                      && ((DetectionExpectation) e)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                detectionExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.isExpectationGroup(),
                                    expectation.getExpirationTime()));
                          }
                          yield Stream.of();
                        }
                        case MANUAL -> {
                          // Verify that at least one asset in the group has been executed
                          List<Asset> assets =
                              this.assetGroupService.assetsFromAssetGroup(assetGroup.getId());
                          if (assets.stream()
                              .anyMatch(
                                  (asset) ->
                                      expectations.stream()
                                          .filter(e -> EXPECTATION_TYPE.MANUAL == e.type())
                                          .anyMatch(
                                              (e) ->
                                                  ((ManualExpectation) e).getAsset() != null
                                                      && ((ManualExpectation) e)
                                                          .getAsset()
                                                          .getId()
                                                          .equals(asset.getId())))) {
                            yield Stream.of(
                                manualExpectationForAssetGroup(
                                    expectation.getScore(),
                                    expectation.getName(),
                                    expectation.getDescription(),
                                    assetGroup,
                                    expectation.getExpirationTime(),
                                    expectation.isExpectationGroup()));
                          }
                          yield Stream.of();
                        }
                        default -> Stream.of();
                      })
              .toList());
    }
  }
}
