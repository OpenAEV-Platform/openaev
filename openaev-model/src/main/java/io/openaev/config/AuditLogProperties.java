package io.openaev.config;

import io.openaev.database.model.LogTransport;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogProperties {

  @Getter
  @Value("${openaev.audit-logs.halt-on-failure:false}")
  private boolean haltOnFailure;

  @Value("${openaev.audit-logs.transports:}")
  private Set<String> transports;

  @Getter
  @Value(
      "${openaev.audit-logs.engine.retention-days:${openaev.audit-logs.file.retention-days:365}}")
  private int engineRetentionDays;

  @Getter
  @Value(
      "${openaev.audit-logs.engine.rollover-max-size:${openaev.audit-logs.file.rollover-max-size:5gb}}")
  private String engineRolloverMaxSize;

  @Getter
  @Value(
      "${openaev.audit-logs.engine.rollover-max-age:${openaev.audit-logs.file.rollover-max-age:30d}}")
  private String engineRolloverMaxAge;

  @PostConstruct
  void validate() {
    if (!isEnabled()) {
      return;
    }

    // Normalize: trim whitespace from each transport name (handles "console, file" with spaces)
    transports =
        transports.stream()
            .map(String::trim)
            .map(name -> name.toLowerCase(Locale.ROOT))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());

    Set<String> unknown =
        transports.stream().filter(t -> !isLogTransport(t)).collect(Collectors.toSet());

    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "[AUDIT CONFIG] Invalid transport(s) in openaev.audit-logs.transports: "
              + unknown
              + ". Valid values are: "
              + Arrays.toString(LogTransport.values()));
    }
  }

  /**
   * Audit logging is enabled when at least one transport is configured. An empty or absent {@code
   * openaev.audit-logs.transports} value disables it.
   */
  public boolean isEnabled() {
    return transports != null && !transports.isEmpty();
  }

  /** Returns whether a specific transport is active. */
  public boolean isTransportEnabled(LogTransport logTransport) {
    return transports != null && transports.contains(logTransport.name().toLowerCase(Locale.ROOT));
  }

  private boolean isLogTransport(String value) {
    return value != null
        && Arrays.stream(LogTransport.values())
            .anyMatch(transport -> transport.name().equalsIgnoreCase(value));
  }
}
