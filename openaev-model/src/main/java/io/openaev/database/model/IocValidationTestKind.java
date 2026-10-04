package io.openaev.database.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Benign test kinds an IOC validation can run. The STIX/OpenCTI form is snake_case ({@link
 * #toStix()}), e.g. {@code dns_resolution}.
 */
public enum IocValidationTestKind {
  DNS_RESOLUTION("DNS resolution"),
  NETWORK_TRAFFIC("Network traffic"),
  HTTP_HEAD("HTTP HEAD request"),
  FILE_DROP("Benign file drop"),
  LOG_INJECTION("Benign log line");

  private final String label;

  IocValidationTestKind(String label) {
    this.label = label;
  }

  public String toStix() {
    return name().toLowerCase(Locale.ROOT);
  }

  /** Name of the test kind in messages read by people (OpenAEV request page, OpenCTI). */
  public String label() {
    return label;
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
