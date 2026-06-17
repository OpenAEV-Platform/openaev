package io.openaev.utils;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.model.expectation.DetectionExpectation.detectionExpectationForAgent;
import static io.openaev.model.expectation.DetectionExpectation.detectionExpectationForAsset;
import static io.openaev.model.expectation.ManualExpectation.manualExpectationForAgent;
import static io.openaev.model.expectation.ManualExpectation.manualExpectationForAsset;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAgent;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAsset;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAgent;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAsset;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForMediaPressure;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.database.model.Team;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.model.expectation.PreventionExpectation;
import io.openaev.model.expectation.VulnerabilityExpectation;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.AssetToExecute;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class ExpectationUtils {

  public static final String OAEV_IMPLANT = "oaev-implant-";
  public static final String OAEV_IMPLANT_CALDERA = "oaev-implant-caldera-";

  public static final List<EXPECTATION_TYPE> HUMAN_EXPECTATION =
      List.of(MANUAL, CHALLENGE, ARTICLE);

  private ExpectationUtils() {}

  public static List<BaseInjectExpectation> processByValidationType(
      boolean isaNewExpectationResult,
      List<BaseInjectExpectation> childrenExpectations,
      List<BaseInjectExpectation> parentExpectations,
      Map<Team, List<BaseInjectExpectation>> playerByTeam) {
    List<BaseInjectExpectation> updatedExpectations = new ArrayList<>();

    childrenExpectations.stream()
        .findAny()
        .ifPresentOrElse(
            process -> {
              boolean isValidationAtLeastOneTarget = process.isExpectationGroup();

              parentExpectations.forEach(
                  parentExpectation -> {
                    List<BaseInjectExpectation> toProcess =
                        playerByTeam.get(parentExpectation.getTeam());
                    int playersSize = toProcess.size();
                    long zeroPlayerResponses =
                        toProcess.stream()
                            .filter(exp -> exp.getScore() != null)
                            .filter(exp -> exp.getScore() == 0.0)
                            .count();
                    long nullPlayerResponses =
                        toProcess.stream().filter(exp -> exp.getScore() == null).count();

                    if (isValidationAtLeastOneTarget) {
                      OptionalDouble avgAtLeastOnePlayer =
                          toProcess.stream()
                              .filter(exp -> exp.getScore() != null)
                              .filter(exp -> exp.getScore() > 0.0)
                              .mapToDouble(BaseInjectExpectation::getScore)
                              .average();
                      if (avgAtLeastOnePlayer.isPresent()) {
                        parentExpectation.setScore(avgAtLeastOnePlayer.getAsDouble());
                      } else {
                        if (zeroPlayerResponses == playersSize) {
                          parentExpectation.setScore(0.0);
                        } else {
                          parentExpectation.setScore(null);
                        }
                      }
                    } else {
                      if (nullPlayerResponses == 0) {
                        OptionalDouble avgAllPlayer =
                            toProcess.stream()
                                .mapToDouble(BaseInjectExpectation::getScore)
                                .average();
                        parentExpectation.setScore(avgAllPlayer.getAsDouble());
                      } else {
                        if (zeroPlayerResponses == 0) {
                          parentExpectation.setScore(null);
                        } else {
                          double sumAllPlayer =
                              toProcess.stream()
                                  .filter(exp -> exp.getScore() != null)
                                  .mapToDouble(BaseInjectExpectation::getScore)
                                  .sum();
                          parentExpectation.setScore(sumAllPlayer / playersSize);
                        }
                      }
                    }

                    if (isaNewExpectationResult) {
                      InjectExpectationResult result = buildForMediaPressure(process);
                      parentExpectation.getResults().add(result);
                    }

                    parentExpectation.setUpdatedAt(Instant.now());
                    updatedExpectations.add(parentExpectation);
                  });
            },
            ElementNotFoundException::new);

    return updatedExpectations;
  }

  private static <T> List<T> getExpectationForAsset(
      final AssetGroup assetGroup,
      final List<Agent> executedAgents,
      final Function<AssetGroup, T> createExpectationForAsset,
      final BiFunction<Agent, AssetGroup, T> createExpectationForAgent,
      final boolean isAgentless) {
    List<T> returnList = new ArrayList<>();

    T expectation = createExpectationForAsset.apply(assetGroup);
    List<T> expectationList =
        executedAgents.stream()
            .map(agent -> createExpectationForAgent.apply(agent, assetGroup))
            .toList();

    if (!expectationList.isEmpty() || isAgentless) {
      returnList.add(expectation);
      returnList.addAll(expectationList);
    }

    return returnList;
  }

  private static <T> List<T> getExpectations(
      AssetToExecute assetToExecute,
      final List<Agent> executedAgents,
      final Function<AssetGroup, T> createExpectationForAsset,
      final BiFunction<Agent, AssetGroup, T> createExpectationForAgent,
      final boolean isAgentless) {
    List<T> returnList = new ArrayList<>();

    if (assetToExecute.isDirectlyLinkedToInject()) {
      returnList.addAll(
          getExpectationForAsset(
              null,
              executedAgents,
              createExpectationForAsset,
              createExpectationForAgent,
              isAgentless));
    }

    assetToExecute
        .assetGroups()
        .forEach(
            assetGroup ->
                returnList.addAll(
                    getExpectationForAsset(
                        assetGroup,
                        executedAgents,
                        createExpectationForAsset,
                        createExpectationForAgent,
                        isAgentless)));

    return returnList;
  }

  /**
   * Get prevention expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param valueTargetedAssetsMap a map of value targeted assets
   * @param inject the inject details
   * @return a list of prevention expectations
   */
  public static List<PreventionExpectation> getPreventionExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      Inject inject) {
    String injectId = inject != null ? inject.getId() : null;
    return getExpectations(
        assetToExecute,
        executedAgents,
        (AssetGroup assetGroup) ->
            preventionExpectationForAsset(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime()),
        (Agent agent, AssetGroup assetGroup) ->
            preventionExpectationForAgent(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getParent() : agent,
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime(),
                computeSignatures(
                    implantType,
                    OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getInject().getId() : injectId,
                    assetToExecute.asset(),
                    OAEV_IMPLANT_CALDERA.equals(implantType)
                        ? agent.getParent().getId()
                        : agent.getId(),
                    valueTargetedAssetsMap)),
        isAgentlessAssetExpectationNecessary(assetToExecute.asset(), inject));
  }

  /**
   * Get detection expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param valueTargetedAssetsMap a map of value targeted assets
   * @param inject the inject details
   * @return a list of detection expectations
   */
  public static List<DetectionExpectation> getDetectionExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      Inject inject) {
    String injectId = inject != null ? inject.getId() : null;
    return getExpectations(
        assetToExecute,
        executedAgents,
        (AssetGroup assetGroup) ->
            detectionExpectationForAsset(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime()),
        (Agent agent, AssetGroup assetGroup) ->
            detectionExpectationForAgent(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getParent() : agent,
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime(),
                computeSignatures(
                    implantType,
                    OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getInject().getId() : injectId,
                    assetToExecute.asset(),
                    OAEV_IMPLANT_CALDERA.equals(implantType)
                        ? agent.getParent().getId()
                        : agent.getId(),
                    valueTargetedAssetsMap)),
        isAgentlessAssetExpectationNecessary(assetToExecute.asset(), inject));
  }

  /**
   * Get manual expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param inject the inject details
   * @return a list of manual expectations
   */
  public static List<ManualExpectation> getManualExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<io.openaev.database.model.Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Inject inject) {
    return getExpectations(
        assetToExecute,
        executedAgents,
        (AssetGroup assetGroup) ->
            manualExpectationForAsset(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime()),
        (Agent agent, AssetGroup assetGroup) ->
            manualExpectationForAgent(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getParent() : agent,
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime()),
        isAgentlessAssetExpectationNecessary(assetToExecute.asset(), inject));
  }

  /**
   * Get vulnerability expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param valueTargetedAssetsMap a map of value targeted assets
   * @param inject the inject details
   * @return a list of vulnerability expectations
   */
  public static List<VulnerabilityExpectation> getVulnerabilityExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      Inject inject) {
    String injectId = inject != null ? inject.getId() : null;
    return getExpectations(
        assetToExecute,
        executedAgents,
        (AssetGroup assetGroup) ->
            vulnerabilityExpectationForAsset(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime()),
        (Agent agent, AssetGroup assetGroup) ->
            vulnerabilityExpectationForAgent(
                expectation.getScore(),
                expectation.getName(),
                expectation.getDescription(),
                OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getParent() : agent,
                assetToExecute.asset(),
                assetGroup,
                expectation.getExpirationTime(),
                computeSignatures(
                    implantType,
                    OAEV_IMPLANT_CALDERA.equals(implantType) ? agent.getInject().getId() : injectId,
                    assetToExecute.asset(),
                    OAEV_IMPLANT_CALDERA.equals(implantType)
                        ? agent.getParent().getId()
                        : agent.getId(),
                    valueTargetedAssetsMap)),
        isAgentlessAssetExpectationNecessary(assetToExecute.asset(), inject));
  }

  private static List<String> getIpsFromAsset(Asset asset) {
    if (asset instanceof Endpoint endpoint) {
      return Stream.concat(
              endpoint.getIps() != null ? Stream.of(endpoint.getIps()) : Stream.empty(),
              endpoint.getSeenIp() != null ? Stream.of(endpoint.getSeenIp()) : Stream.empty())
          .toList();
    }
    return Collections.emptyList();
  }

  public static void setResultExpectationVulnerable(
      List<BaseInjectExpectation> expectations,
      InjectExpectationResult result,
      String vulnerabilityResult) {

    for (BaseInjectExpectation expectation : expectations) {
      double score =
          VULNERABILITY.successLabel.equals(vulnerabilityResult)
              ? expectation.getExpectedScore()
              : 0.0;

      result.setResult(vulnerabilityResult);
      result.setScore(score);
      expectation.setScore(score);
      expectation.setResults(List.of(result));
    }
  }

  private static List<InjectExpectationSignature> computeSignatures(
      String prefixSignature,
      String injectId,
      Asset sourceAsset,
      String agentId,
      Map<String, Endpoint> valueTargetedAssetsMap) {
    List<InjectExpectationSignature> signatures = new ArrayList<>();

    signatures.add(
        new InjectExpectationSignature(
            EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME,
            prefixSignature + injectId + "-agent-" + agentId));

    getIpsFromAsset(sourceAsset)
        .forEach(ip -> signatures.add(InjectExpectationSignature.createIpSignature(ip, false)));

    valueTargetedAssetsMap.forEach(
        (value, endpoint) -> {
          if (value.equals(endpoint.getHostname())) {
            signatures.add(InjectExpectationSignature.createHostnameSignature(value));
          } else {
            signatures.add(InjectExpectationSignature.createIpSignature(value, true));
          }
        });

    return signatures;
  }

  public static List<BaseInjectExpectation> getExpectationsPlayersForTeam(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isPlayerExpectation)
        .filter(e -> e.getTeam().getId().equals(injectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .filter(e -> Objects.equals(e.getName(), injectExpectation.getName()))
        .toList();
  }

  private static boolean isPlayerExpectation(BaseInjectExpectation e) {
    return e.getUser() != null;
  }

  public static List<BaseInjectExpectation> getExpectationTeams(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isTeamExpectation)
        .filter(e -> e.getTeam().getId().equals(injectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .filter(e -> Objects.equals(e.getName(), injectExpectation.getName()))
        .toList();
  }

  private static boolean isTeamExpectation(BaseInjectExpectation e) {
    return e.getTeam() != null && e.getUser() == null;
  }

  public static List<BaseInjectExpectation> getExpectationsAgentsForAsset(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(
            e ->
                e.getAsset() != null
                    && injectExpectation.getAsset() != null
                    && e.getAsset().getId().equals(injectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAgentExpectation(BaseInjectExpectation e) {
    return e.getAgent() != null;
  }

  public static List<BaseInjectExpectation> getExpectationsAssets(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAsset().getId().equals(injectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static List<BaseInjectExpectation> getExpectationsAssetsForAssetGroup(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(
            e -> {
              AssetGroup assetGroup = e.getAssetGroup();
              AssetGroup injectGroup = injectExpectation.getAssetGroup();
              return assetGroup != null
                  && injectGroup != null
                  && assetGroup.getId().equals(injectGroup.getId());
            })
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAssetExpectation(BaseInjectExpectation e) {
    return e.getAsset() != null && e.getAgent() == null;
  }

  public static List<BaseInjectExpectation> getExpectationAssetGroups(
      @NotNull final BaseInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .filter(e -> e.getAssetGroup().getId().equals(injectExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAssetGroupExpectation(BaseInjectExpectation e) {
    return e.getAssetGroup() != null && e.getAsset() == null && e.getAgent() == null;
  }

  /**
   * Determine if an asset is agentless and need to have expectations created
   *
   * @param asset to test
   * @param inject the inject details
   * @return true if is agentless and injector payload, false if not
   */
  private static boolean isAgentlessAssetExpectationNecessary(Asset asset, Inject inject) {
    return inject != null
        && inject.getInjector() != null
        && !inject.getInjector().isPayloads()
        && asset instanceof Endpoint endpoint
        && endpoint.getAgents().isEmpty();
  }
}
