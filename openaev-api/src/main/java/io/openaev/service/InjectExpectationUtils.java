package io.openaev.service;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;
import static java.util.Optional.ofNullable;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.ExpectationPropertiesConfig;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.*;
import io.openaev.utils.StringUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class InjectExpectationUtils {

  private InjectExpectationUtils() {}

  public static final double FAILED_SCORE_VALUE = 0.0;

  // -- VALIDATION --

  /**
   * Validates that an expectation has meaningful data. An expectation without at least a name,
   * description, or positive score is considered empty/invalid and should not be persisted — see
   * OpenAEV-Platform/openaev#4891.
   */
  public static boolean isExpectationValid(BaseInjectExpectation expectation) {
    if (expectation == null) {
      return false;
    }
    return !StringUtils.isBlank(expectation.getName())
        || !StringUtils.isBlank(expectation.getDescription())
        || (expectation.getExpectedScore() != null && expectation.getExpectedScore() > 0);
  }

  // -- SCORE --

  public static double computeScore(
      @NotNull final BaseInjectExpectation expectation, final boolean success) {
    return success ? expectation.getExpectedScore() : FAILED_SCORE_VALUE;
  }

  // -- CONVERTER --

  public static BaseInjectExpectation expectationConverter(
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation);
    return expectationConverter(
        baseInjectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static BaseInjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation);
    baseInjectExpectation.setTeam(team);
    return expectationConverter(
        baseInjectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static BaseInjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final User user,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation);
    baseInjectExpectation.setTeam(team);
    baseInjectExpectation.setUser(user);
    return expectationConverter(
        baseInjectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  private static BaseInjectExpectation expectationConverter(
      @NotNull BaseInjectExpectation baseInjectExpectation,
      @NotNull final ExecutableInject executableInject,
      @NotNull final Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {

    baseInjectExpectation.setExercise(executableInject.getInjection().getExercise());
    baseInjectExpectation.setInject(executableInject.getInjection().getInject());
    baseInjectExpectation.setExpectedScore(expectation.getScore());
    baseInjectExpectation.setExpectationGroup(expectation.isExpectationGroup());
    baseInjectExpectation.setName(expectation.getName());
    baseInjectExpectation.setExpirationTime(
        ofNullable(expectation.getExpirationTime())
            .orElse(expectationPropertiesConfig.getExpirationTimeByType(expectation.type())));

    switch (expectation) {
      case ChannelExpectation e when expectation.type() == ARTICLE -> {
        baseInjectExpectation.setArticle(e.getArticle());
      }
      case ChallengeExpectation e when expectation.type() == CHALLENGE -> {
        baseInjectExpectation.setChallenge(e.getChallenge());
      }
      case Expectation ignored when expectation.type() == DOCUMENT -> {
        baseInjectExpectation.setType(DOCUMENT);
      }
      case Expectation ignored when expectation.type() == TEXT -> {
        throw new IllegalStateException(
            "TEXT expectation type is deprecated and no longer supported");
      }
      case DetectionExpectation e when expectation.type() == DETECTION -> {
        baseInjectExpectation.setDetection(e.getAgent(), e.getAsset(), e.getAssetGroup());
        baseInjectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case PreventionExpectation e when expectation.type() == PREVENTION -> {
        baseInjectExpectation.setPrevention(e.getAgent(), e.getAsset(), e.getAssetGroup());
        baseInjectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case VulnerabilityExpectation e when expectation.type() == VULNERABILITY -> {
        baseInjectExpectation.setVulnerability(e.getAgent(), e.getAsset(), e.getAssetGroup());
        baseInjectExpectation.setSignatures(e.getInjectExpectationSignatures());
      }
      case ManualExpectation e when expectation.type() == MANUAL -> {
        baseInjectExpectation.setManual(e.getAgent(), e.getAsset(), e.getAssetGroup());
        baseInjectExpectation.setDescription(e.getDescription());
      }
      default -> throw new IllegalStateException("Unexpected value: " + expectation);
    }
    return baseInjectExpectation;
  }

  private static BaseInjectExpectation newExpectationByType(
      @NotNull final Expectation expectation) {
    return switch (expectation.type()) {
      case DOCUMENT -> new DocumentInjectExpectation();
      case ARTICLE -> new ArticleInjectExpectation();
      case CHALLENGE -> new ChallengeInjectExpectation();
      case MANUAL -> new ManualInjectExpectation();
      case PREVENTION -> new PreventionInjectExpectation();
      case DETECTION -> new DetectionInjectExpectation();
      case VULNERABILITY -> new VulnerabilityInjectExpectation();
      case TEXT ->
          throw new IllegalStateException(
              "TEXT expectation type is deprecated and no longer supported");
    };
  }

  // -- RULES OF ENGAGEMENT --

  public static void computeScores(
      @NotNull final List<BaseInjectExpectation> childrenExpectations,
      @NotNull final List<BaseInjectExpectation> parentExpectations,
      @NotNull final BaseInjectExpectation baseInjectExpectation,
      @Nullable final Function<Double, InjectExpectationResult> addResult) {
    @NotNull Double expectedScore = baseInjectExpectation.getExpectedScore();
    boolean isGroup = baseInjectExpectation.isExpectationGroup();

    final boolean noExpectationScore = noExpectationScore(childrenExpectations);
    final boolean allSuccess =
        allExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean anySuccess =
        anyExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean allError =
        allExpectationsMatch(childrenExpectations, score -> score < expectedScore);
    final boolean anyError =
        anyExpectationsMatch(childrenExpectations, score -> score < expectedScore);

    parentExpectations.forEach(
        expectation -> {
          if (noExpectationScore) {
            expectation.setScore(null);
            return;
          }

          Double score = null;
          if (isGroup) {
            if (anySuccess) {
              score = InjectExpectationUtils.computeScore(expectation, true);
            } else if (allError) {
              score = InjectExpectationUtils.computeScore(expectation, false);
            }
          } else {
            if (allSuccess) {
              score = InjectExpectationUtils.computeScore(expectation, true);
            } else if (anyError) {
              score = InjectExpectationUtils.computeScore(expectation, false);
            }
          }
          expectation.setScore(score);
          if (addResult != null) {
            InjectExpectationResult newResultToAdd = addResult.apply(score);
            Optional<InjectExpectationResult> existingResult =
                expectation.getResults().stream()
                    .filter(result -> newResultToAdd.getSourceId().equals(result.getSourceId()))
                    .findFirst();
            existingResult.ifPresent(
                injectExpectationResult ->
                    expectation.getResults().remove(injectExpectationResult));
            expectation.getResults().add(newResultToAdd);

            // IF RESULT TO ADD IS EXPIRATION MANAGER => SO I EXPIRE ALL the inject expectation with
            // no result to expired
            if (ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(
                newResultToAdd.getSourceId())) {
              expireEmptyResults(expectation.getResults(), FAILED_SCORE_VALUE, EXPIRED);
            }
          }
        });
  }

  /**
   * Retrieve all asset ids from a stream of inject expectations
   *
   * @param injectExpectations stream of inject expecations to extract
   * @return distinct asset ids list
   */
  public static Set<String> extractAssetIdsFromInjectExpectationsResults(
      @NotNull final Stream<BaseInjectExpectation> injectExpectations) {
    return injectExpectations
        .flatMap(expectation -> expectation.getResults().stream())
        .map(InjectExpectationResult::getSourceAssetId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static boolean noExpectationScore(final List<BaseInjectExpectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return true;
    }
    return expectations.stream().map(BaseInjectExpectation::getScore).allMatch(Objects::isNull);
  }

  private static boolean allExpectationsMatch(
      final List<BaseInjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(BaseInjectExpectation::getScore)
        .allMatch(score -> score != null && predicate.test(score));
  }

  private static boolean anyExpectationsMatch(
      final List<BaseInjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(BaseInjectExpectation::getScore)
        .filter(Objects::nonNull)
        .anyMatch(predicate);
  }
}
