package io.openaev.helper;

import io.openaev.database.model.InjectExpectation.EXPECTATION_STATUS;
import org.jetbrains.annotations.Nullable;

/**
 * Helper class for inject expectation calculations and status determination.
 *
 * <p>This utility class provides methods for computing expectation statuses based on scores,
 * supporting the evaluation of detection and prevention expectations during simulations.
 *
 * @see io.openaev.database.model.InjectExpectation
 */
public class InjectExpectationHelper {

  private InjectExpectationHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Computes the expectation status based on the actual and expected scores.
   *
   * <p>The status is determined as follows:
   *
   * <ul>
   *   <li>{@code UNKNOWN} - if no expected score is defined
   *   <li>{@code PENDING} - if no actual score has been recorded yet
   *   <li>{@code SUCCESS} - if the actual score meets or exceeds the expected score
   *   <li>{@code FAILED} - if the actual score is below the expected score
   * </ul>
   *
   * @param score the actual score achieved (may be null if not yet evaluated)
   * @param expectedScore the expected/target score (may be null if not defined)
   * @return the computed {@link EXPECTATION_STATUS}
   */
  public static EXPECTATION_STATUS computeStatus(
      @Nullable final Double score, @Nullable final Double expectedScore) {
    if (expectedScore == null) {
      return EXPECTATION_STATUS.UNKNOWN;
    }
    if (score == null) {
      return EXPECTATION_STATUS.PENDING;
    }
    if (score >= expectedScore) {
      return EXPECTATION_STATUS.SUCCESS;
    }
    return EXPECTATION_STATUS.FAILED;
  }
}
