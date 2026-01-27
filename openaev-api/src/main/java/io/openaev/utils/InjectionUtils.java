package io.openaev.utils;

import io.openaev.database.model.Injection;
import java.time.Duration;
import java.time.Instant;

/**
 * Utility class for injection-related operations.
 *
 * <p>Provides helper methods for validating injection timing and determining eligibility for
 * execution.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Injection
 */
public class InjectionUtils {

  private InjectionUtils() {}

  /**
   * Checks if an injection is within the injectable time range.
   *
   * <p>An injection is considered injectable if its scheduled date is within the last 4 minutes but
   * before the current time. This window allows for slight delays in execution while preventing
   * execution of stale injections.
   *
   * @param injection the injection to check
   * @return {@code true} if the injection date is within the 4-minute window before now
   * @throws java.util.NoSuchElementException if the injection has no scheduled date
   */
  public static boolean isInInjectableRange(Injection injection) {
    Instant now = Instant.now();
    Instant start = now.minus(Duration.parse("PT4M"));
    Instant injectWhen = injection.getDate().orElseThrow();
    return injectWhen.isAfter(start) && injectWhen.isBefore(now);
  }
}
