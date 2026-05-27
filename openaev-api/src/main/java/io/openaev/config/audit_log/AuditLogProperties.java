package io.openaev.config.audit_log;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogProperties {

  public static final String TRANSPORT_CONSOLE = "console";
  public static final String TRANSPORT_FILE = "file";
  public static final String TRANSPORT_ENGINE = "engine";

  private static final Set<String> VALID_TRANSPORTS =
      Set.of(TRANSPORT_CONSOLE, TRANSPORT_FILE, TRANSPORT_ENGINE);

  @Value("${openaev.audit-logs.transports:}")
  private Set<String> transports;

  @PostConstruct
  void validate() {
    if (!isEnabled()) {
      return;
    }

    // Normalize: trim whitespace from each transport name (handles "console, file" with spaces)
    if (transports != null) {
      transports =
          transports.stream()
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toSet());
    }

    Set<String> unknown =
        transports.stream().filter(t -> !VALID_TRANSPORTS.contains(t)).collect(Collectors.toSet());

    if (!unknown.isEmpty()) {
      throw new IllegalStateException(
          "[AUDIT CONFIG] Invalid transport(s) in openaev.audit-logs.transports: "
              + unknown
              + ". Valid values are: "
              + VALID_TRANSPORTS);
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
  public boolean isTransportEnabled(String transportName) {
    return transports != null && transports.contains(transportName);
  }
}
