package io.openaev.utils;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.expectation.DetectionExpectation.detectionExpectationForAgent;
import static io.openaev.expectation.DetectionExpectation.detectionExpectationForAsset;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.expectation.ManualExpectation.manualExpectationForAgent;
import static io.openaev.expectation.ManualExpectation.manualExpectationForAsset;
import static io.openaev.expectation.PreventionExpectation.preventionExpectationForAgent;
import static io.openaev.expectation.PreventionExpectation.preventionExpectationForAsset;
import static io.openaev.utils.ExpectationSignatureUtils.EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAgent;
import static io.openaev.utils.VulnerabilityExpectationUtils.vulnerabilityExpectationForAsset;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForMediaPressure;

import io.openaev.database.model.*;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.expectation.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.AssetToExecute;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hibernate.Hibernate;

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
 * @see io.openaev.database.model.BaseInjectExpectation
 * @see io.openaev.expectation.PreventionExpectation
 * @see io.openaev.expectation.DetectionExpectation
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
  public static List<BaseInjectExpectation> processByValidationType(
      boolean isaNewExpectationResult,
      List<? extends TableTopInjectExpectation> childrenExpectations,
      List<? extends TableTopInjectExpectation> parentExpectations,
      Map<Team, ? extends List<? extends TableTopInjectExpectation>> playerByTeam) {
    List<BaseInjectExpectation> updatedExpectations = new ArrayList<>();

    childrenExpectations.stream()
        .findAny()
        .ifPresentOrElse(
            process -> {
              boolean isValidationAtLeastOneTarget =
                  process.isExpectationGroup(); // Without Parent expectation

              parentExpectations.forEach(
                  parentExpectation -> {
                    List<? extends TableTopInjectExpectation> toProcess =
                        playerByTeam.get(parentExpectation.getTeam());
                    int playersSize = toProcess.size();
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
                              .mapToDouble(BaseInjectExpectation::getScore)
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
        (AssetGroup assetGroup) -> {
          PreventionExpectation preventionExpectation =
              preventionExpectationForAsset(
                  expectation.getScore(),
                  expectation.getName(),
                  expectation.getDescription(),
                  assetToExecute.asset(),
                  assetGroup,
                  expectation.getExpirationTime());
          preventionExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return preventionExpectation;
        },
        (Agent agent, AssetGroup assetGroup) -> {
          PreventionExpectation preventionExpectation =
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
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getInject().getId()
                          : injectId,
                      assetToExecute.asset(),
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getParent().getId()
                          : agent.getId(),
                      valueTargetedAssetsMap));
          preventionExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return preventionExpectation;
        },
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
        (AssetGroup assetGroup) -> {
          DetectionExpectation detectionExpectation =
              detectionExpectationForAsset(
                  expectation.getScore(),
                  expectation.getName(),
                  expectation.getDescription(),
                  assetToExecute.asset(),
                  assetGroup,
                  expectation.getExpirationTime());
          detectionExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return detectionExpectation;
        },
        (Agent agent, AssetGroup assetGroup) -> {
          DetectionExpectation detectionExpectation =
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
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getInject().getId()
                          : injectId,
                      assetToExecute.asset(),
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getParent().getId()
                          : agent.getId(),
                      valueTargetedAssetsMap));
          detectionExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return detectionExpectation;
        },
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
        (AssetGroup assetGroup) -> {
          VulnerabilityExpectation vulnerabilityExpectation =
              vulnerabilityExpectationForAsset(
                  expectation.getScore(),
                  expectation.getName(),
                  expectation.getDescription(),
                  assetToExecute.asset(),
                  assetGroup,
                  expectation.getExpirationTime());
          vulnerabilityExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return vulnerabilityExpectation;
        },
        (Agent agent, AssetGroup assetGroup) -> {
          VulnerabilityExpectation vulnerabilityExpectation =
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
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getInject().getId()
                          : injectId,
                      assetToExecute.asset(),
                      OAEV_IMPLANT_CALDERA.equals(implantType)
                          ? agent.getParent().getId()
                          : agent.getId(),
                      valueTargetedAssetsMap));
          vulnerabilityExpectation.setExpectedSecurityPlatformTypes(
              expectation.getExpectedSecurityPlatformTypes());
          return vulnerabilityExpectation;
        },
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
      List<VulnerabilityInjectExpectation> expectations,
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

  public static List<ExpectationSignature> computeSignatures(
      String prefixSignature,
      String injectId,
      Asset sourceAsset,
      String agentId,
      Map<String, Endpoint> valueTargetedAssetsMap) {
    List<ExpectationSignature> signatures = new ArrayList<>();

    signatures.add(
        new ExpectationSignature(
            EXPECTATION_SIGNATURE_TYPE_PARENT_PROCESS_NAME,
            prefixSignature + injectId + "-agent-" + agentId));

    getIpsFromAsset(sourceAsset)
        .forEach(ip -> signatures.add(ExpectationSignatureUtils.createIpSignature(ip, false)));

    valueTargetedAssetsMap.forEach(
        (value, endpoint) -> {
          if (value.equals(endpoint.getHostname())) {
            signatures.add(ExpectationSignatureUtils.createHostnameSignature(value));
          } else {
            ExpectationSignature ipSignature =
                ExpectationSignatureUtils.createIpSignature(value, true);
            if (ipSignature != null) {
              signatures.add(ipSignature);
            }
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
   * @param tableTopInjectExpectation the reference expectation to match against
   * @return list of matching player expectations for the team
   */
  public static List<TableTopInjectExpectation> getPlayersExpectationsForTeam(
      @NotNull final TableTopInjectExpectation tableTopInjectExpectation) {
    return tableTopInjectExpectation.getInject().getExpectations().stream()
        .filter(TableTopInjectExpectation.class::isInstance)
        .map(TableTopInjectExpectation.class::cast)
        .filter(ExpectationUtils::isPlayerExpectation)
        .filter(e -> e.getTeam().getId().equals(tableTopInjectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(tableTopInjectExpectation.getType()))
        .filter(e -> Objects.equals(e.getName(), tableTopInjectExpectation.getName()))
        .toList();
  }

  public static boolean isPlayerExpectation(TableTopInjectExpectation e) {
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
  public static List<TableTopInjectExpectation> getTeamsExpectations(
      @NotNull final TableTopInjectExpectation injectExpectation) {
    return injectExpectation.getInject().getExpectations().stream()
        .filter(TableTopInjectExpectation.class::isInstance)
        .map(TableTopInjectExpectation.class::cast)
        .filter(ExpectationUtils::isTeamExpectation)
        .filter(e -> e.getTeam().getId().equals(injectExpectation.getTeam().getId()))
        .filter(e -> e.getType().equals(injectExpectation.getType()))
        .filter(e -> Objects.equals(e.getName(), injectExpectation.getName()))
        .toList();
  }

  private static boolean isTeamExpectation(TableTopInjectExpectation e) {
    return e.getTeam() != null && e.getUser() == null;
  }

  // -- AGENT --

  /**
   * Retrieves agent expectations for the same asset and type as the given expectation.
   *
   * <p>Filters to find agent-level expectations (those with an agent association) that match the
   * reference expectation's asset and type.
   *
   * @param technicalInjectExpectation the reference expectation to match against
   * @return list of matching agent expectations for the asset
   */
  public static List<TechnicalInjectExpectation> getAgentsExpectationsForAsset(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation) {
    return technicalInjectExpectation.getInject().getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAgentExpectation)
        .filter(
            e ->
                e.getAsset() != null
                    && technicalInjectExpectation.getAsset() != null
                    && e.getAsset().getId().equals(technicalInjectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(technicalInjectExpectation.getType()))
        .toList();
  }

  /**
   * Determines if an expectation is an agent-level expectation.
   *
   * @param e the expectation to check
   * @return {@code true} if the expectation has an agent association
   */
  public static boolean isAgentExpectation(TechnicalInjectExpectation e) {
    return e.getAgent() != null;
  }

  // -- ASSET --

  /**
   * Retrieves asset-level expectations matching the given expectation's asset and type.
   *
   * <p>Filters to find asset expectations (those with an asset but no agent) that match the
   * reference expectation's asset and type.
   *
   * @param technicalInjectExpectation the reference expectation to match against
   * @return list of matching asset-level expectations
   */
  public static List<TechnicalInjectExpectation> getAssetsExpectations(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation) {
    return technicalInjectExpectation.getInject().getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAsset().getId().equals(technicalInjectExpectation.getAsset().getId()))
        .filter(e -> e.getType().equals(technicalInjectExpectation.getType()))
        .toList();
  }

  /**
   * Retrieve all asset expectations belongings to the same asset group and the same type of
   * expectation
   *
   * @param assetGroupExpectation the reference assetGroup expectation to match with
   * @return list of technical asset expectations
   */
  public static List<TechnicalInjectExpectation> getAssetsExpectationsOfAssetGroup(
      @NotNull final TechnicalInjectExpectation assetGroupExpectation) {
    return assetGroupExpectation.getInject().getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> e.getAssetGroup() != null)
        .filter(
            e -> e.getAssetGroup().getId().equals(assetGroupExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(assetGroupExpectation.getType()))
        .toList();
  }

  /**
   * Returns all asset-level expectations for the given inject and expectation type.
   *
   * @param inject the inject whose expectations are searched
   * @param type the expectation type to match
   * @return list of matching asset-level expectations
   */
  public static List<TechnicalInjectExpectation> getAssetsExpectationsByInjectAndType(
      @NotNull final Inject inject, BaseInjectExpectation.EXPECTATION_TYPE type) {
    return inject.getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(e -> type.equals(e.getType()))
        .toList();
  }

  /**
   * Returns all asset-group-level expectations for the given inject and expectation type.
   *
   * @param inject the inject whose expectations are searched
   * @param type the expectation type to match
   * @return list of matching asset-group-level expectations
   */
  public static List<TechnicalInjectExpectation> getAssetGroupsExpectationsByInjectAndType(
      @NotNull final Inject inject, BaseInjectExpectation.EXPECTATION_TYPE type) {
    return inject.getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .filter(e -> type.equals(e.getType()))
        .toList();
  }

  /**
   * Retrieves asset expectations belonging to the same asset group as the given expectation.
   *
   * <p>Filters to find asset expectations that are part of the same asset group and have the same
   * expectation type as the reference expectation.
   *
   * @param technicalInjectExpectation the reference expectation to match against
   * @return list of matching asset expectations within the asset group
   */
  public static List<TechnicalInjectExpectation> getExpectationsAssetsForAssetGroup(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation) {
    return technicalInjectExpectation.getInject().getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetExpectation)
        .filter(
            e -> {
              AssetGroup assetGroup = e.getAssetGroup();
              AssetGroup injectGroup = technicalInjectExpectation.getAssetGroup();
              return assetGroup != null
                  && injectGroup != null
                  && assetGroup.getId().equals(injectGroup.getId());
            })
        .filter(e -> e.getType().equals(technicalInjectExpectation.getType()))
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
  public static boolean isAssetExpectation(TechnicalInjectExpectation e) {
    return e.getAsset() != null && e.getAgent() == null;
  }

  // -- ASSET GROUP --

  /**
   * Retrieves asset group-level expectations matching the given expectation's group and type.
   *
   * <p>Filters to find asset group expectations (those with only an asset group, no individual
   * asset or agent) that match the reference expectation's group and type.
   *
   * @param technicalInjectExpectation the reference expectation to match against
   * @return list of matching asset group-level expectations
   */
  public static List<TechnicalInjectExpectation> getExpectationAssetGroups(
      @NotNull final TechnicalInjectExpectation technicalInjectExpectation) {
    return technicalInjectExpectation.getInject().getExpectations().stream()
        .filter(TechnicalInjectExpectation.class::isInstance)
        .map(TechnicalInjectExpectation.class::cast)
        .filter(ExpectationUtils::isAssetGroupExpectation)
        .filter(
            e ->
                e.getAssetGroup()
                    .getId()
                    .equals(technicalInjectExpectation.getAssetGroup().getId()))
        .filter(e -> e.getType().equals(technicalInjectExpectation.getType()))
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
  public static boolean isAssetGroupExpectation(TechnicalInjectExpectation e) {
    return e.getAssetGroup() != null && e.getAsset() == null && e.getAgent() == null;
  }

  /**
   * Determine if an asset is agentless and need to have expectations created.
   *
   * <p>Agentless expectations are the asset-level expectations we create when the asset itself is
   * the validation target (no OAEV agent runs on it). This applies to:
   *
   * <ul>
   *   <li>{@link Endpoint}s targeted by an agentless (non-payload) injector that have no agent
   *   <li>Non-endpoint assets such as AI targets ({@code category = AI_TARGET}), which never carry
   *       an agent - their detection/prevention expectations are always fulfilled at the asset
   *       level by an external collector (e.g. the XTM One LLM firewall collector)
   * </ul>
   *
   * @param asset to test
   * @param inject the inject details
   * @return true when an asset-level (agentless) expectation must be created for this asset
   */
  public static boolean isAgentlessAssetExpectationNecessary(Asset asset, Inject inject) {
    if (asset == null || inject == null) {
      return false;
    }
    if (Hibernate.unproxy(asset) instanceof Endpoint endpoint) {
      // On an endpoint a payload runs through an OAEV agent, so the expectation
      // is created at the agent level. An asset-level (agentless) expectation is
      // therefore only needed for a non-executor injector (Nuclei, Nmap, HTTP...)
      // targeting an endpoint that carries no agent.
      if (injectRunsThroughAgents(inject)) {
        return false;
      }
      return endpoint.getAgents().isEmpty();
    }
    // Non-endpoint assets (AI targets, ...) never run an agent: the asset itself
    // is the validation target, fulfilled by an external collector (e.g. the XTM
    // One LLM firewall collector). They ALWAYS need an asset-level expectation,
    // regardless of whether the injector uses payloads - an AI Red Team inject is
    // payload-based yet still targets an AI asset with no agent.
    return true;
  }

  /**
   * Whether this inject executes through OAEV agents (payload contracts requiring an executor).
   *
   * <p>Resolved primarily from the injector contract's needs-executor flag because {@code
   * inject.getInjector()} is NULL for injects created by the scenario importer (XTM Hub) and for
   * legacy injects - relying on it silently disabled agentless asset-level expectations for those
   * injects. Falls back to the injector's payloads flag when no contract is resolvable, and fails
   * closed (agents-based, no agentless expectation) when neither a contract nor an injector can be
   * resolved: downstream expectation building needs the contract to locate the targeted assets.
   *
   * @param inject the inject to test
   * @return true when the inject runs through agents (expectations belong at the agent level)
   */
  private static boolean injectRunsThroughAgents(Inject inject) {
    return inject
        .getInjectorContract()
        .map(InjectorContract::getNeedsExecutorEffective)
        .orElseGet(() -> inject.getInjector() == null || inject.getInjector().isPayloads());
  }
}
