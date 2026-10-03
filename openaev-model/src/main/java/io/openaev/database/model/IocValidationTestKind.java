package io.openaev.database.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Benign test kinds an IOC validation can run. The STIX/OpenCTI form is snake_case ({@link
 * #toStix()}), e.g. {@code dns_resolution}.
 */
public enum IocValidationTestKind {
  DNS_RESOLUTION,
  NETWORK_TRAFFIC,
  HTTP_HEAD,
  FILE_DROP,
  LOG_INJECTION;

  public String toStix() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Parses the snake_case contract value; unknown or blank values yield an empty optional. */
  public static Optional<IocValidationTestKind> fromStix(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
