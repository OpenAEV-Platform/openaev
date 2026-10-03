package io.openaev.service.stix;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import io.openaev.database.model.IocValidationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the expectations of the injects built for one indicator into the outcome of one (indicator,
 * security platform) pair. Pure: the caller loads the expectations, this class only reads them.
 *
 * <p>Only the results reported by the pair's security platform count ({@code sourceId} equals its
 * asset id), the same filter the security coverage uses per platform. Precedence: {@link
 * IocValidationOutcome#PREVENTED} over {@link IocValidationOutcome#DETECTED} over {@link
 * IocValidationOutcome#MISSED}. A pair is decided as soon as it is prevented (nothing can outrank
 * it); otherwise only once every relevant expectation is evaluated or expired, so a detection is
 * never reported while a prevention could still arrive.
 */
public final class IocValidationOutcomes {

  private IocValidationOutcomes() {}

  /** Outcome of a pair with its reason; the reason is mainly set for misses and errors. */
  public record Evaluation(IocValidationOutcome outcome, String reason) {}

  /**
   * Evaluates one pair.
   *
   * @param expectations primary expectations of every inject built for the pair's indicator
   * @param securityPlatformId the OpenAEV security platform asset matched for the pair
   * @param finalizing whether the simulation is over: pending expectations are then decided
   * @return the evaluation, or empty while the pair cannot be decided yet
   */
  public static Optional<Evaluation> evaluate(
      Collection<BaseInjectExpectation> expectations,
      String securityPlatformId,
      boolean finalizing) {
    List<BaseInjectExpectation> relevant =
        expectations.stream()
            .filter(
                expectation ->
                    expectation.getType() == EXPECTATION_TYPE.PREVENTION
                        || expectation.getType() == EXPECTATION_TYPE.DETECTION)
            .toList();
    if (relevant.isEmpty()) {
      return finalizing
          ? Optional.of(
              new Evaluation(
                  IocValidationOutcome.ERROR,
                  "No detection or prevention expectation was created: the test did not run"))
          : Optional.empty();
    }
    if (succeeded(relevant, EXPECTATION_TYPE.PREVENTION, securityPlatformId)) {
      return Optional.of(new Evaluation(IocValidationOutcome.PREVENTED, null));
    }
    boolean allEvaluated =
        relevant.stream().allMatch(expectation -> expectation.getScore() != null);
    if (!allEvaluated && !finalizing) {
      return Optional.empty();
    }
    if (succeeded(relevant, EXPECTATION_TYPE.DETECTION, securityPlatformId)) {
      return Optional.of(new Evaluation(IocValidationOutcome.DETECTED, null));
    }
    if (!allEvaluated) {
      return Optional.of(
          new Evaluation(
              IocValidationOutcome.ERROR,
              "The simulation ended before the security platform results were evaluated"));
    }
    boolean reported =
        relevant.stream()
            .anyMatch(expectation -> !platformResults(expectation, securityPlatformId).isEmpty());
    return Optional.of(
        new Evaluation(
            IocValidationOutcome.MISSED,
            reported
                ? "The security platform reported neither a detection nor a prevention"
                : "The security platform reported nothing before the expectations expired"));
  }

  /**
   * Final request status once every pair has an outcome: {@link IocValidationStatus#COMPLETED} when
   * every pair was evaluated, {@link IocValidationStatus#FAILED} when none was, {@link
   * IocValidationStatus#PARTIAL} otherwise.
   */
  public static IocValidationStatus finalStatus(Collection<IocValidationPair> pairs) {
    long evaluated =
        pairs.stream()
            .map(IocValidationPair::getOutcome)
            .filter(Objects::nonNull)
            .filter(IocValidationOutcome::isEvaluated)
            .count();
    if (evaluated == 0) {
      return IocValidationStatus.FAILED;
    }
    return evaluated == pairs.size() ? IocValidationStatus.COMPLETED : IocValidationStatus.PARTIAL;
  }

  private static boolean succeeded(
      List<BaseInjectExpectation> expectations, EXPECTATION_TYPE type, String securityPlatformId) {
    return expectations.stream()
        .filter(expectation -> expectation.getType() == type)
        .anyMatch(
            expectation ->
                platformResults(expectation, securityPlatformId).stream()
                    .anyMatch(result -> isSuccess(result, expectation)));
  }

  private static List<InjectExpectationResult> platformResults(
      BaseInjectExpectation expectation, String securityPlatformId) {
    if (expectation.getResults() == null || securityPlatformId == null) {
      return List.of();
    }
    return expectation.getResults().stream()
        .filter(result -> securityPlatformId.equals(result.getSourceId()))
        .toList();
  }

  private static boolean isSuccess(
      InjectExpectationResult result, BaseInjectExpectation expectation) {
    if (result.getScore() == null) {
      return false;
    }
    double expected =
        expectation.getExpectedScore() == null ? 100.0 : expectation.getExpectedScore();
    return result.getScore() > 0 && result.getScore() >= expected;
  }
}
