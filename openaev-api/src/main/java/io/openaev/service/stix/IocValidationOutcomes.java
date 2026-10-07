package io.openaev.service.stix;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.IocValidationOutcome;
import io.openaev.database.model.IocValidationPair;
import io.openaev.database.model.IocValidationStatus;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the expectations of the injects built for one indicator into the outcome of one (indicator,
 * security platform) pair. Pure: the caller loads the expectations, this class only reads them.
 *
 * <p>Only the results reported for the pair's security platform count: {@code sourceAssetId} equals
 * its asset id ({@code sourceId} names the collector), falling back to {@code sourceId} for results
 * entered for the platform itself. Precedence: {@link IocValidationOutcome#PREVENTED} over {@link
 * IocValidationOutcome#DETECTED} over {@link IocValidationOutcome#MISSED}. A pair is decided as
 * soon as it is prevented (nothing can outrank it); otherwise only once the platform's own results
 * of every relevant expectation are scored (an expiration scores them too), so a detection is never
 * reported while a prevention could still arrive. The expectation score is shared by every platform
 * of a multi-platform request and never decides a pair before the simulation ends.
 */
public final class IocValidationOutcomes {

  private static final Set<ExecutionStatus> RAN_STATUSES =
      Set.of(ExecutionStatus.EXECUTED, ExecutionStatus.PARTIAL);

  private IocValidationOutcomes() {}

  /** Outcome of a pair with its reason; the reason is mainly set for misses and errors. */
  public record Evaluation(IocValidationOutcome outcome, String reason) {}

  /**
   * Evaluates one pair.
   *
   * @param expectations leaf technical expectations (agents, agentless assets) of every inject
   *     built for the pair's indicator: the rows carrying the collectors' per-source results
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
        relevant.stream().allMatch(expectation -> evaluatedFor(expectation, securityPlatformId));
    if (!allEvaluated && !finalizing) {
      return Optional.empty();
    }
    if (succeeded(relevant, EXPECTATION_TYPE.DETECTION, securityPlatformId)) {
      return Optional.of(new Evaluation(IocValidationOutcome.DETECTED, null));
    }
    boolean windowClosed =
        relevant.stream()
            .allMatch(
                expectation ->
                    evaluatedFor(expectation, securityPlatformId)
                        || expectation.getScore() != null);
    if (!windowClosed) {
      return Optional.of(
          new Evaluation(
              IocValidationOutcome.ERROR,
              "The simulation ended before the security platform results were evaluated"));
    }
    if (testDidNotRun(relevant, finalizing)) {
      return Optional.of(
          new Evaluation(
              IocValidationOutcome.ERROR,
              "The test did not run successfully on the endpoint: the expectations expired"
                  + " without proving the security platform missed it"));
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

  // An expiration scores every unanswered expectation, including those of an inject whose execution
  // failed: a miss needs one inject of the pair executed, fully or on part of its targets.
  private static boolean testDidNotRun(
      List<BaseInjectExpectation> expectations, boolean finalizing) {
    List<ExecutionStatus> statuses =
        expectations.stream()
            .map(BaseInjectExpectation::getInject)
            .filter(Objects::nonNull)
            .distinct()
            .map(inject -> inject.getStatus().map(InjectStatus::getName).orElse(null))
            .toList();
    if (statuses.isEmpty()) {
      return false;
    }
    if (statuses.stream().anyMatch(RAN_STATUSES::contains)) {
      return false;
    }
    return finalizing || statuses.stream().allMatch(status -> status == ExecutionStatus.ERROR);
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

  // Several collectors can report for one security platform, each with its own seeded result: the
  // platform has evaluated an expectation once every one of them is scored.
  private static boolean evaluatedFor(
      BaseInjectExpectation expectation, String securityPlatformId) {
    List<InjectExpectationResult> results = platformResults(expectation, securityPlatformId);
    return !results.isEmpty() && results.stream().allMatch(result -> result.getScore() != null);
  }

  private static List<InjectExpectationResult> platformResults(
      BaseInjectExpectation expectation, String securityPlatformId) {
    if (expectation.getResults() == null || securityPlatformId == null) {
      return List.of();
    }
    // Collector results name the collector in sourceId and its security platform in sourceAssetId.
    return expectation.getResults().stream()
        .filter(
            result ->
                securityPlatformId.equals(
                    result.getSourceAssetId() != null
                        ? result.getSourceAssetId()
                        : result.getSourceId()))
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
