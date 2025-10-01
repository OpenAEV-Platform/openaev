package io.openaev.helper;

import io.openaev.database.model.InjectExpectation.EXPECTATION_STATUS;
import org.jetbrains.annotations.Nullable;

public class InjectExpectationHelper {

  private InjectExpectationHelper() {}

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
