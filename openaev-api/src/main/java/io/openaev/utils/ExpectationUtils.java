package io.openaev.utils;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME;
import static io.openaev.model.expectation.DetectionExpectation.detectionExpectationForAgent;
import static io.openaev.model.expectation.DetectionExpectation.detectionExpectationForAsset;
import static io.openaev.model.expectation.ManualExpectation.manualExpectationForAgent;
import static io.openaev.model.expectation.ManualExpectation.manualExpectationForAsset;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAgent;
import static io.openaev.model.expectation.PreventionExpectation.preventionExpectationForAsset;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAgent;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAsset;
import static io.openaev.utils.inject_expectation_result.InjectExpectationResultUtils.buildForMediaPressure;

import io.openaev.database.model.*;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.model.expectation.DetectionExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.model.expectation.PreventionExpectation;
import io.openaev.model.expectation.VulnerabilityExpectation;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.AssetToExecute;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

public class ExpectationUtils {

  public static final String OAEV_IMPLANT = "oaev-implant-";
  public static final String OAEV_IMPLANT_CALDERA = "oaev-implant-caldera-";

  public static final List<EXPECTATION_TYPE> HUMAN_EXPECTATION =
      List.of(MANUAL, CHALLENGE, ARTICLE);

  private ExpectationUtils() {}

  public static List<InjectExpectation> processByValidationType(
      boolean isaNewExpectationResult,
      List<InjectExpectation> childrenExpectations,
      List<InjectExpectation> parentExpectations,
      Map<Team, List<InjectExpectation>> playerByTeam) {
    List<InjectExpectation> updatedExpectations = new ArrayList<>();

    childrenExpectations.stream()
        .findAny()
        .ifPresentOrElse(
            process -> {
              boolean isValidationAtLeastOneTarget = process.isExpectationGroup();

              parentExpectations.forEach(
                  parentExpectation -> {
                    List<InjectExpectation> toProcess =
                        playerByTeam.get(parentExpectation.getTeam());
                    int playersSize = toProcess.size(); // Without Parent expectation
                    long zeroPlayerResponses =
                        toProcess.stream()
                            .filter(exp -> exp.getScore() != null)
                            .filter(exp -> exp.getScore() == 0.0)
                            .count();
                    long nullPlayerResponses =
                        toProcess.stream().filter(exp -> exp.getScore() == null).count();

                    if (isValidationAtLeastOneTarget) { // Type atLeast
                      OptionalDouble avgAtLeastOnePlayer =
                          toProcess.stream()
                              .filter(exp -> exp.getScore() != null)
                              .filter(exp -> exp.getScore() > 0.0)
                              .mapToDouble(InjectExpectation::getScore)
                              .average();
                      if (avgAtLeastOnePlayer.isPresent()) { // Any response is positive
                        parentExpectation.setScore(avgAtLeastOnePlayer.getAsDouble());
                      } else {
                        if (zeroPlayerResponses == playersSize) { // All players had failed
                          parentExpectation.setScore(0.0);
                        } else {
                          parentExpectation.setScore(null);
                        }
                      }
                    } else { // type all
                      if (nullPlayerResponses == 0) {
                        OptionalDouble avgAllPlayer =
                            toProcess.stream().mapToDouble(InjectExpectation::getScore).average();
                        parentExpectation.setScore(avgAllPlayer.getAsDouble());
                      } else {
                        if (zeroPlayerResponses == 0) {
                          parentExpectation.setScore(null);
                        } else {
                          double sumAllPlayer =
                              toProcess.stream()
                                  .filter(exp -> exp.getScore() != null)
                                  .mapToDouble(InjectExpectation::getScore)
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
      final BiFunction<Agent, AssetGroup, T> createExpectationForAgent) {
    List<T> returnList = new ArrayList<>();

    T expectation = createExpectationForAsset.apply(assetGroup);
    List<T> expectationList =
        executedAgents.stream()
            .map(agent -> createExpectationForAgent.apply(agent, assetGroup))
            .toList();

    if (!expectationList.isEmpty()) {
      returnList.add(expectation);
      returnList.addAll(expectationList);
    }

    return returnList;
  }

  private static <T> List<T> getExpectations(
      AssetToExecute assetToExecute,
      final List<Agent> executedAgents,
      final Function<AssetGroup, T> createExpectationForAsset,
      final BiFunction<Agent, AssetGroup, T> createExpectationForAgent) {
    List<T> returnList = new ArrayList<>();

    if (assetToExecute.isDirectlyLinkedToInject()) {
      returnList.addAll(
          getExpectationForAsset(
              null, executedAgents, createExpectationForAsset, createExpectationForAgent));
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
                        createExpectationForAgent)));

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
   * @param injectId the ID of the inject
   * @return a list of prevention expectations
   */
  public static List<PreventionExpectation> getPreventionExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<io.openaev.database.model.Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      String injectId) {
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
                    valueTargetedAssetsMap)));
  }

  /**
   * Get detection expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param valueTargetedAssetsMap a map of value targeted assets
   * @param injectId the ID of the inject
   * @return a list of detection expectations
   */
  public static List<DetectionExpectation> getDetectionExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<io.openaev.database.model.Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      String injectId) {
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
                    valueTargetedAssetsMap)));
  }

  /**
   * Get manual expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @return a list of manual expectations
   */
  public static List<ManualExpectation> getManualExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<io.openaev.database.model.Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation) {
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
                expectation.getExpirationTime()));
  }

  /**
   * Get vulnerability expectations by asset
   *
   * @param implantType the type of implant (e.g., OAEV_IMPLANT_CALDERA)
   * @param assetToExecute the asset to execute the expectation on
   * @param executedAgents the list of executed agents
   * @param expectation the expectation details
   * @param valueTargetedAssetsMap a map of value targeted assets
   * @return a list of vulnerability expectations
   */
  public static List<VulnerabilityExpectation> getVulnerabilityExpectationsByAsset(
      String implantType,
      AssetToExecute assetToExecute,
      List<io.openaev.database.model.Agent> executedAgents,
      io.openaev.model.inject.form.Expectation expectation,
      Map<String, Endpoint> valueTargetedAssetsMap,
      @Nullable String injectId) {
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
                    valueTargetedAssetsMap)));
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

  // COMPUTE SIGNATURES

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

  // -- PLAYER --

  public static List<InjectExpectation> getExpectationsPlayersForTeam(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isPlayerExpectation)
        .filter(e -> e.getTeam().getId().equals(injectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  private static boolean isPlayerExpectation(InjectExpectation e) {
    return e.getUser() != null;
  }

  // -- TEAM --

  public static List<InjectExpectation> getExpectationTeams(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isTeamExpectation)
        .filter(e -> e.getTeam().getId().equals(injectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  private static boolean isTeamExpectation(InjectExpectation e) {
    return e.getTeam() != null && e.getUser() == null;
  }

  // -- AGENT --

  public static List<InjectExpectation> getExpectationsAgentsForAsset(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(e -> e.getAsset().getId().equals(injectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAgentExpectation(InjectExpectation e) {
    return e.getAgent() != null;
  }

  // -- ASSET --

  public static List<InjectExpectation> getExpectationsAssets(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAsset().getId().equals(injectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static List<InjectExpectation> getExpectationsAssetsForAssetGroup(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAssetGroup().getId().equals(injectExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAssetExpectation(InjectExpectation e) {
    return e.getAsset() != null && e.getAgent() == null;
  }

  // -- ASSET GROUP --

  public static List<InjectExpectation> getExpectationAssetGroups(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .filter(e -> e.getAssetGroup().getId().equals(injectExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  public static boolean isAssetGroupExpectation(InjectExpectation e) {
    return e.getAssetGroup() != null && e.getAsset() == null && e.getAgent() == null;
  }
}
