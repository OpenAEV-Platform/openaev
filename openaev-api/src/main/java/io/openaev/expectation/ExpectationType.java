package io.openaev.expectation;

import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import jakarta.validation.constraints.NotNull;

public enum ExpectationType {
  DETECTION("Detected", "Pending", "Not Detected"),
  HUMAN_RESPONSE("Successful", "Pending", "Failed"),
  PREVENTION("Prevented", "Pending", "Not Prevented"),
  VULNERABILITY("Not vulnerable", "Pending", "Vulnerable");

  public final String successLabel;
  public final String pendingLabel;
  public final String failureLabel;

  public static final String SUCCESS_ID = "SUCCESS";
  public static final String PENDING_ID = "PENDING";
  public static final String FAILED_ID = "FAILED";

  ExpectationType(String successLabel, String pendingLabel, String failureLabel) {
    this.successLabel = successLabel;
    this.pendingLabel = pendingLabel;
    this.failureLabel = failureLabel;
  }

  public static ExpectationType of(String value) {
    switch (value.toLowerCase()) {
      case "manual":
      case "article":
      case "challenge":
        return ExpectationType.HUMAN_RESPONSE;
      default:
        return valueOf(value);
    }
  }

  public static String label(
      @NotNull final EXPECTATION_TYPE type,
      @NotNull final Double expectedScore,
      @NotNull final Double actualScore) {
    ExpectationType expectationType =
        switch (type) {
          case DETECTION -> ExpectationType.DETECTION;
          case PREVENTION -> ExpectationType.PREVENTION;
          case VULNERABILITY -> ExpectationType.VULNERABILITY;
          default -> ExpectationType.HUMAN_RESPONSE;
        };
    if (actualScore >= expectedScore) {
      return expectationType.successLabel;
    } else {
      return expectationType.failureLabel;
    }
  }
}
