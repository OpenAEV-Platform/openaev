package io.openaev.collectors.expectations_expiration_manager.utils;

import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.expectation.ExpectationType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class ExpectationUtils {

  private ExpectationUtils() {}

  /**
   * Checks if an expectation has expired based on its expiration time setting.
   *
   * @param expectation the expectation to check
   * @return true if the expectation has exceeded its expiration time
   */
  public static boolean isExpired(@NotNull final InjectExpectation expectation) {
    // expirationTime is stored in seconds, convert to minutes for comparison
    long expirationTimeInMinutes = expectation.getExpirationTime() / 60;
    Instant expirationThreshold = Instant.now().minus(expirationTimeInMinutes, ChronoUnit.MINUTES);
    return expectation.getCreatedAt().isBefore(expirationThreshold);
  }

  public static String computeSuccessMessage(@NotNull final EXPECTATION_TYPE expectationType) {
    return switch (expectationType) {
      case DETECTION -> ExpectationType.DETECTION.successLabel;
      case PREVENTION -> ExpectationType.PREVENTION.successLabel;
      case VULNERABILITY -> ExpectationType.VULNERABILITY.successLabel;
      default -> ExpectationType.HUMAN_RESPONSE.successLabel;
    };
  }

  public static String computeFailedMessage(@NotNull final EXPECTATION_TYPE expectationType) {
    return switch (expectationType) {
      case DETECTION -> ExpectationType.DETECTION.failureLabel;
      case PREVENTION -> ExpectationType.PREVENTION.failureLabel;
      default -> ExpectationType.HUMAN_RESPONSE.failureLabel;
    };
  }
}
