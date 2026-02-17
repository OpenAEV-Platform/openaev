package io.openaev.healthcheck.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Enumeration of external service dependencies that can be health-checked.
 *
 * <p>This enum represents the various external services that OpenAEV may depend on for full
 * functionality. The health check system uses these values to verify connectivity and report
 * service status.
 *
 * <p>Available dependencies:
 *
 * <ul>
 *   <li>{@code SMTP} - Email sending service for notifications
 *   <li>{@code IMAP} - Email receiving service for response tracking
 *   <li>{@code NUCLEI} - Nuclei vulnerability scanner integration
 *   <li>{@code NMAP} - Nmap network scanner integration
 * </ul>
 */
public enum ExternalServiceDependency {
  /** SMTP email service for outbound email notifications. */
  @JsonProperty("SMTP")
  SMTP("smtp"),

  /** IMAP email service for inbound email processing. */
  @JsonProperty("IMAP")
  IMAP("imap"),

  /** Nuclei vulnerability scanner service. */
  @JsonProperty("NUCLEI")
  NUCLEI("openaev_nuclei"),

  /** Nmap network scanner service. */
  @JsonProperty("NMAP")
  NMAP("openaev_nmap"),

  /** OpenAEV Implant service. */
  @JsonProperty("OpenAEV Email")
  OPENAEV_EMAIL("openaev_email"),

  /** OpenAEV Implant service. */
  @JsonProperty("OpenAEV Implant")
  OPENAEV_IMPLANT("openaev_implant");

  private final String value;

  /**
   * Returns the string value/identifier for this dependency.
   *
   * @return the dependency identifier string
   */
  public String getValue() {
    return value;
  }

  /**
   * Constructs an external service dependency with the specified value.
   *
   * @param value the service identifier string
   */
  ExternalServiceDependency(String value) {
    this.value = value;
  }

  /**
   * Parses a injectorType to its corresponding {@link ExternalServiceDependency} enum constant.
   *
   * @param injectorType the injector type to parse (case-insensitive)
   * @return the matching {@link ExternalServiceDependency}
   * @throws IllegalArgumentException if the value is null, blank, or does not match any known
   *     dependency
   */
  public static ExternalServiceDependency[] fromInjectorType(String injectorType) {
    if (injectorType == null || injectorType.isBlank()) {
      throw new IllegalArgumentException("Injector type cannot be null or blank");
    }

    // Special cases
    if ("openaev_email".equalsIgnoreCase(injectorType)) {
      return new ExternalServiceDependency[] {SMTP, IMAP};
    }
    if ("openaev_implant".equalsIgnoreCase(injectorType)) {
      return new ExternalServiceDependency[] {};
    }

    // Default: find matching enum
    for (ExternalServiceDependency type : ExternalServiceDependency.values()) {
      if (type.value.equalsIgnoreCase(injectorType)) {
        return new ExternalServiceDependency[] {type};
      }
    }

    throw new IllegalArgumentException(
        String.format(
            "Unknown ExternalServiceDependency value: '%s'. Valid values are: %s",
            injectorType,
            java.util.Arrays.stream(ExternalServiceDependency.values())
                .map(ExternalServiceDependency::getValue)
                .collect(java.util.stream.Collectors.joining(", "))));
  }
}
