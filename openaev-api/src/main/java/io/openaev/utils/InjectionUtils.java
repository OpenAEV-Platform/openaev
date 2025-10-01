package io.openaev.utils;

import io.openaev.database.model.Injection;
import java.time.Duration;
import java.time.Instant;

public class InjectionUtils {

  private InjectionUtils() {}

  public static boolean isInInjectableRange(Injection injection) {
    Instant now = Instant.now();
    Instant start = now.minus(Duration.parse("PT4M"));
    Instant injectWhen = injection.getDate().orElseThrow();
    return injectWhen.isAfter(start) && injectWhen.isBefore(now);
  }
}
