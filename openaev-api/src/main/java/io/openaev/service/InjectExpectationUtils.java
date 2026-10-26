package io.openaev.service;

import static io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService.EXPIRED;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.*;
import static java.util.Optional.ofNullable;

import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.expectation.*;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.StringUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InjectExpectationUtils {

  private InjectExpectationUtils() {}

  public static final double FAILED_SCORE_VALUE = 0.0;

  // -- VALIDATION --

  /**
   * Returns whether expectation initialization should run for the given executable inject.
   *
   * <p>Expectations are initialized for scheduled exercise injects, direct atomic testing runs and
   * chaining executions. Other direct runs are ignored.
   */
  public static boolean shouldInitializeExpectation(ExecutableInject executableInject) {
    boolean isAtomicTesting = executableInject.getInjection().getInject().isAtomicTesting();
    boolean isExerciseInject = !executableInject.isDirect();
    boolean isChainingExecution = executableInject.isChainingExecution();
    return isExerciseInject || isAtomicTesting || isChainingExecution;
  }

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
   * vulnerable") is merely the absence of a finding, so the worst vulnerable score wins.
   *
   * <p>A vulnerability leaf applies this rule to its own results: a finding settles it at once,
   * even while other sources are pending or after its expiration. Verdicts are written on the
   * leaves only, so a parent row carries a direct result only from legacy data (assessment
   * injectors such as Nuclei used to answer the asset / asset-group row itself): the rule keeps
   * such a proven vulnerable verdict from being overwritten by the children rollup.
   *
   * <p>Rows attributed to the expiration manager and rows expired to "Expired" are defaults, not
   * signals, and are ignored.
   *
   * @param expectation the parent expectation whose score is being recomputed from children, or the
   *     vulnerability leaf whose score is computed from its results
   * @param childrenScore the score derived from the children rollup, or from the leaf results
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

  public static void setCommonFields(
      @NotNull BaseInjectExpectation baseInjectExpectation,
      @NotNull Expectation formExpectation,
      @NotNull Exercise exercise,
      @NotNull Inject inject,
      @NotNull ExpectationPropertiesConfig expectationPropertiesConfig) {
    baseInjectExpectation.setExercise(exercise);
    baseInjectExpectation.setInject(inject);
    baseInjectExpectation.setExpectedScore(
        Objects.requireNonNullElse(formExpectation.getScore(), 100.0));
    baseInjectExpectation.setExpectationGroup(formExpectation.isExpectationGroup());
    baseInjectExpectation.setName(formExpectation.getName());
    baseInjectExpectation.setOrder(formExpectation.getOrder());
    baseInjectExpectation.setDescription(formExpectation.getDescription());
    baseInjectExpectation.setExpirationTime(
        ofNullable(formExpectation.getExpirationTime())
            .orElse(
                expectationPropertiesConfig.getExpirationTimeByType(formExpectation.getType())));
  }

  // -- EXPECTED SECURITY PLATFORMS --

  /**
   * Restricts the tenant's security-platform collectors to those matching an expectation's expected
   * platform types. When the expectation declares no expected type, every collector is kept (legacy
   * behaviour). Expected types that have no connected collector are logged so the misconfiguration
   * is visible instead of the expectation silently hanging until expiration.
   *
   * @param collectors all connected security-platform collectors of the tenant
   * @param expectedTypes the expectation's expected security platform types (may be null/empty)
   * @return the collectors expected to answer this expectation
   */
  public static List<Collector> filterCollectorsForExpectation(
      final List<Collector> collectors,
      final List<SecurityPlatform.SECURITY_PLATFORM_TYPE> expectedTypes) {
    if (expectedTypes == null || expectedTypes.isEmpty()) {
      return collectors;
    }
    List<Collector> matching =
        collectors.stream()
            .filter(
                c ->
                    c.getSecurityPlatform() != null
                        && expectedTypes.contains(
                            c.getSecurityPlatform().getSecurityPlatformType()))
            .toList();
    Set<SecurityPlatform.SECURITY_PLATFORM_TYPE> connectedTypes =
        collectors.stream()
            .map(Collector::getSecurityPlatform)
            .filter(Objects::nonNull)
            .map(SecurityPlatform::getSecurityPlatformType)
            .collect(Collectors.toSet());
    expectedTypes.stream()
        .filter(type -> !connectedTypes.contains(type))
        .forEach(
            type ->
                log.warn(
                    "Expectation expects security platform type {} but no connected collector of that type exists; it will only be finalized by the expiration manager",
                    type));
    return matching;
  }

  /**
   * Expiration ordering guarantee: when specific security platforms are expected, make sure the
   * expectation's expiration is long enough for the real collectors to answer first (at least two
   * of their poll cycles), so the expiration manager only ever acts as a fallback for genuinely
   * unanswered expectations.
   *
   * @param expectation the technical expectation being seeded
   * @param expectedCollectors the collectors expected to answer it
   */
  public static void applyExpirationOrderingGuarantee(
      final TechnicalInjectExpectation expectation, final List<Collector> expectedCollectors) {
    if (expectedCollectors.isEmpty()) {
      return;
    }
    long maxPeriodSeconds =
        expectedCollectors.stream().mapToLong(Collector::getPeriod).max().orElse(0L);
    long floor = maxPeriodSeconds * 2L;
    if (expectation.getExpirationTime() == null || expectation.getExpirationTime() < floor) {
      expectation.setExpirationTime(floor);
    }
  }
}
