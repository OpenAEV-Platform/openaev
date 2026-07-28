package io.openaev.service;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.utils.ExpectationSignatureUtils.convertToInjectExpectationSignatures;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.expireEmptyResults;
import static java.util.Optional.ofNullable;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.*;
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
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation.type());
    return expectationConverter(
        baseInjectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static BaseInjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation.type());
    if (baseInjectExpectation instanceof TableTopInjectExpectation tableTop) {
      tableTop.setTeam(team);
    }
    return expectationConverter(
        baseInjectExpectation, executableInject, expectation, expectationPropertiesConfig);
  }

  public static BaseInjectExpectation expectationConverter(
      @NotNull final Team team,
      @NotNull final User user,
      @NotNull final ExecutableInject executableInject,
      Expectation expectation,
      ExpectationPropertiesConfig expectationPropertiesConfig) {
    BaseInjectExpectation baseInjectExpectation = newExpectationByType(expectation.type());
    if (baseInjectExpectation instanceof TableTopInjectExpectation tableTop) {
      tableTop.setTeam(team);
      tableTop.setUser(user);
    }
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
      case ChannelExpectation e when expectation.type() == ARTICLE ->
          ((ArticleInjectExpectation) baseInjectExpectation).setArticle(e.getArticle());
      case io.openaev.expectation.ChallengeExpectation e when expectation.type() == CHALLENGE ->
          ((ChallengeInjectExpectation) baseInjectExpectation).setChallenge(e.getChallenge());
      case DetectionExpectation e when expectation.type() == DETECTION -> {
        TechnicalInjectExpectation tech = (TechnicalInjectExpectation) baseInjectExpectation;
        tech.setAgent(e.getAgent());
        tech.setAsset(e.getAsset());
        tech.setAssetGroup(e.getAssetGroup());
        tech.setExpectedSecurityPlatforms(e.getExpectedSecurityPlatformTypes());
        baseInjectExpectation
            .getSignatures()
            .addAll(
                convertToInjectExpectationSignatures(
                    e.getExpectationSignatures(), baseInjectExpectation));
      }
      case PreventionExpectation e when expectation.type() == PREVENTION -> {
        TechnicalInjectExpectation tech = (TechnicalInjectExpectation) baseInjectExpectation;
        tech.setAgent(e.getAgent());
        tech.setAsset(e.getAsset());
        tech.setAssetGroup(e.getAssetGroup());
        tech.setExpectedSecurityPlatforms(e.getExpectedSecurityPlatformTypes());
        baseInjectExpectation
            .getSignatures()
            .addAll(
                convertToInjectExpectationSignatures(
                    e.getExpectationSignatures(), baseInjectExpectation));
      }
      case VulnerabilityExpectation e when expectation.type() == VULNERABILITY -> {
        TechnicalInjectExpectation tech = (TechnicalInjectExpectation) baseInjectExpectation;
        tech.setAgent(e.getAgent());
        tech.setAsset(e.getAsset());
        tech.setAssetGroup(e.getAssetGroup());
        tech.setExpectedSecurityPlatforms(e.getExpectedSecurityPlatformTypes());
        baseInjectExpectation
            .getSignatures()
            .addAll(
                convertToInjectExpectationSignatures(
                    e.getExpectationSignatures(), baseInjectExpectation));
      }
      case ManualExpectation e when expectation.type() == MANUAL ->
          baseInjectExpectation.setDescription(e.getDescription());
      default -> throw new IllegalStateException("Unexpected value: " + expectation);
    }
    return baseInjectExpectation;
  }

  private static BaseInjectExpectation newExpectationByType(
      @NotNull final BaseInjectExpectation.EXPECTATION_TYPE type) {
    return switch (type) {
      case ARTICLE -> new ArticleInjectExpectation();
      case CHALLENGE -> new ChallengeInjectExpectation();
      case MANUAL -> new ManualInjectExpectation();
      case PREVENTION -> new PreventionInjectExpectation();
      case DETECTION -> new DetectionInjectExpectation();
      case VULNERABILITY -> new VulnerabilityInjectExpectation();
    };
  }

  // -- RULES OF ENGAGEMENT --
  public static double computeScore(@NotNull Double expectedScore, final boolean success) {
    return success ? expectedScore : FAILED_SCORE_VALUE;
  }

  /**
   * Guards a children-derived rollup score against clobbering a definitive direct VULNERABLE
   * verdict carried by the parent expectation's own results.
   *
   * <p>VULNERABILITY has an inverted signal polarity compared to detection/prevention: the
   * meaningful signal is a FAILURE (a scanner proved the target vulnerable), while a success ("Not
   * vulnerable") is merely the absence of a finding. Assessment injectors such as Nuclei write
   * their verdict directly on the asset/asset-group row (agent == null execution), so when the
   * untouched agent children later expire to the default "Not vulnerable", the children rollup must
   * not overwrite the proven vulnerable verdict with an absence-of-signal default.
   *
   * <p>Rows attributed to the expiration manager and rows expired to "Expired" are defaults, not
   * signals, and are ignored.
   *
   * @param expectation the parent expectation whose score is being recomputed from children
   * @param childrenScore the score derived from the children rollup
   * @return the score to persist: the worst direct vulnerable score when one exists, otherwise the
   *     children score
   */
  public static Double reconcileWithDirectVulnerableVerdict(
      @NotNull final BaseInjectExpectation expectation, @Nullable final Double childrenScore) {
    Double expectedScore = expectation.getExpectedScore();
    List<InjectExpectationResult> results = expectation.getResults();
    if (expectedScore == null || results == null || !VULNERABILITY.equals(expectation.getType())) {
      return childrenScore;
    }
    return results.stream()
        .filter(
            result ->
                !ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(result.getSourceId()))
        .filter(result -> !EXPIRED.equals(result.getResult()))
        .map(InjectExpectationResult::getScore)
        .filter(Objects::nonNull)
        .filter(score -> score < expectedScore)
        .reduce(Math::min)
        .orElse(childrenScore);
  }

  public static Double computeChildrenScore(
      boolean isGroupExpectation,
      @NotNull Double expectedScore,
      @NotNull final List<? extends BaseInjectExpectation> childrenExpectations) {
    final boolean noExpectationScore = noExpectationScore(childrenExpectations);
    final boolean allSuccess =
        allExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean anySuccess =
        anyExpectationsMatch(childrenExpectations, score -> score >= expectedScore);
    final boolean allError =
        allExpectationsMatch(childrenExpectations, score -> score < expectedScore);
    final boolean anyError =
        anyExpectationsMatch(childrenExpectations, score -> score < expectedScore);

    if (noExpectationScore) {
      return null;
    }

    Double score = null;

    if (isGroupExpectation) {
      if (anySuccess) {
        score = InjectExpectationUtils.computeScore(expectedScore, true);
      } else if (allError) {
        score = InjectExpectationUtils.computeScore(expectedScore, false);
      }
    } else {
      if (allSuccess) {
        score = InjectExpectationUtils.computeScore(expectedScore, true);
      } else if (anyError) {
        score = InjectExpectationUtils.computeScore(expectedScore, false);
      }
    }
    return score;
  }

  public static void computeScores(
      @NotNull final List<? extends BaseInjectExpectation> childrenExpectations,
      @NotNull final List<? extends BaseInjectExpectation> parentExpectations,
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
            // Children still pending: keep a definitive direct vulnerable verdict (e.g. Nuclei
            // answered the asset row itself) instead of resetting the parent to pending.
            expectation.setScore(reconcileWithDirectVulnerableVerdict(expectation, null));
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
          Double reconciledScore = reconcileWithDirectVulnerableVerdict(expectation, score);
          expectation.setScore(reconciledScore);
          // When the direct vulnerable verdict overrode the children rollup, stamping the
          // triggering result (an expiration "Not vulnerable") would contradict the verdict the
          // parent just kept: skip it, the genuine platform row already explains the score.
          if (addResult != null && Objects.equals(reconciledScore, score)) {
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
            // no result to expired, using the type's default polarity (silence means "Not
            // vulnerable" for VULNERABILITY, failure for the other types)
            if (ExpectationsExpirationManagerConfig.COLLECTOR_ID.equals(
                newResultToAdd.getSourceId())) {
              Double expiredScore =
                  VULNERABILITY.equals(expectation.getType())
                      ? expectation.getExpectedScore()
                      : Double.valueOf(FAILED_SCORE_VALUE);
              expireEmptyResults(expectation.getResults(), expiredScore, EXPIRED);
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

  private static boolean noExpectationScore(
      final List<? extends BaseInjectExpectation> expectations) {
    if (expectations == null || expectations.isEmpty()) {
      return true;
    }
    return expectations.stream().map(BaseInjectExpectation::getScore).allMatch(Objects::isNull);
  }

  private static boolean allExpectationsMatch(
      final List<? extends BaseInjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(BaseInjectExpectation::getScore)
        .allMatch(score -> score != null && predicate.test(score));
  }

  private static boolean anyExpectationsMatch(
      final List<? extends BaseInjectExpectation> expectations, final Predicate<Double> predicate) {
    if (expectations == null || expectations.isEmpty()) {
      return false;
    }
    return expectations.stream()
        .map(BaseInjectExpectation::getScore)
        .filter(Objects::nonNull)
        .anyMatch(predicate);
  }
}
