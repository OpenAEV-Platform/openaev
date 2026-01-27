package io.openaev.utils;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
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

/**
 * Utility class for creating and managing inject expectations.
 *
 * <p>Provides factory methods for creating different types of expectations (Prevention, Detection,
 * Manual, Vulnerability) for various target types (Assets, Agents, Asset Groups). Also includes
 * helper methods for filtering and categorizing expectations.
 *
 * <p>Expectations are the core mechanism for evaluating the effectiveness of security controls
 * during simulations. Each expectation type has specific scoring and validation logic.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.InjectExpectation
 * @see io.openaev.model.expectation.PreventionExpectation
 * @see io.openaev.model.expectation.DetectionExpectation
 */
public class ExpectationUtils {

  /** Prefix for OpenAEV implant-based signatures. */
  public static final String OAEV_IMPLANT = "oaev-implant-";

  /** Prefix for Caldera-specific implant signatures. */
  public static final String OAEV_IMPLANT_CALDERA = "oaev-implant-caldera-";

  /** Expectation types that require human validation (manual review, challenges, articles). */
  public static final List<EXPECTATION_TYPE> HUMAN_EXPECTATION =
      List.of(MANUAL, CHALLENGE, ARTICLE);

  private ExpectationUtils() {}

  /**
   * Processes expectations based on validation type and updates parent expectations with aggregated
   * scores.
   *
   * <p>Handles two validation modes:
   *
   * <ul>
   *   <li><b>At least one target</b>: Parent succeeds if any child has a positive score
   *   <li><b>All targets</b>: Parent score is the average of all children scores
   * </ul>
   *
   * @param isaNewExpectationResult whether this is a new expectation result (adds result entry)
   * @param childrenExpectations the child expectations to aggregate from
   * @param parentExpectations the parent expectations to update with aggregated scores
   * @param playerByTeam map of teams to their player expectations
   * @return list of updated parent expectations
   */
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

  /**
   * Sets the result for vulnerability expectations based on the vulnerability assessment outcome.
   *
   * <p>Updates all provided expectations with the vulnerability result, setting the score to the
   * expected score if the vulnerability was successfully exploited, or 0.0 otherwise.
   *
   * @param expectations the vulnerability expectations to update
   * @param result the result object to populate with outcome details
   * @param vulnerabilityResult the vulnerability assessment result string
   */
  public static void setResultExpectationVulnerable(
      List<InjectExpectation> expectations,
      InjectExpectationResult result,
      String vulnerabilityResult) {

    for (InjectExpectation expectation : expectations) {
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

  /**
   * Retrieves all player expectations for the same team and type as the given expectation.
   *
   * <p>Filters expectations to find those belonging to individual players within the same team and
   * of the same expectation type.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching player expectations for the team
   */
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

  /**
   * Retrieves team-level expectations matching the given expectation's team and type.
   *
   * <p>Filters to find team expectations (those with a team but no individual user) that match the
   * reference expectation's team and type.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching team-level expectations
   */
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

  /**
   * Retrieves agent expectations for the same asset and type as the given expectation.
   *
   * <p>Filters to find agent-level expectations (those with an agent association) that match the
   * reference expectation's asset and type.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching agent expectations for the asset
   */
  public static List<InjectExpectation> getExpectationsAgentsForAsset(
      @NotNull final InjectExpectation injectExpectation) {
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

  /**
   * Determines if an expectation is an agent-level expectation.
   *
   * @param e the expectation to check
   * @return {@code true} if the expectation has an agent association
   */
  public static boolean isAgentExpectation(InjectExpectation e) {
    return e.getAgent() != null;
  }

  // -- ASSET --

  /**
   * Retrieves asset-level expectations matching the given expectation's asset and type.
   *
   * <p>Filters to find asset expectations (those with an asset but no agent) that match the
   * reference expectation's asset and type.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching asset-level expectations
   */
  public static List<InjectExpectation> getExpectationsAssets(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAsset().getId().equals(injectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  /**
   * Retrieves asset expectations belonging to the same asset group as the given expectation.
   *
   * <p>Filters to find asset expectations that are part of the same asset group and have the same
   * expectation type as the reference expectation.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching asset expectations within the asset group
   */
  public static List<InjectExpectation> getExpectationsAssetsForAssetGroup(
      @NotNull final InjectExpectation injectExpectation) {
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

  /**
   * Determines if an expectation is an asset-level expectation.
   *
   * <p>An asset expectation has an asset but no agent association (agent expectations are more
   * granular).
   *
   * @param e the expectation to check
   * @return {@code true} if the expectation is asset-level (has asset, no agent)
   */
  public static boolean isAssetExpectation(InjectExpectation e) {
    return e.getAsset() != null && e.getAgent() == null;
  }

  // -- ASSET GROUP --

  /**
   * Retrieves asset group-level expectations matching the given expectation's group and type.
   *
   * <p>Filters to find asset group expectations (those with only an asset group, no individual
   * asset or agent) that match the reference expectation's group and type.
   *
   * @param injectExpectation the reference expectation to match against
   * @return list of matching asset group-level expectations
   */
  public static List<InjectExpectation> getExpectationAssetGroups(
      @NotNull final InjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .filter(e -> e.getAssetGroup().getId().equals(injectExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .toList();
  }

  /**
   * Determines if an expectation is an asset group-level expectation.
   *
   * <p>An asset group expectation has an asset group but no individual asset or agent associations.
   *
   * @param e the expectation to check
   * @return {@code true} if the expectation is asset group-level
   */
  public static boolean isAssetGroupExpectation(InjectExpectation e) {
    return e.getAssetGroup() != null && e.getAsset() == null && e.getAgent() == null;
  }
}
