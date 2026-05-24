package io.openaev.config.audit_log;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
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

  @Getter
  @Value("${openaev.audit-logs.enabled:true}")
  private boolean enabled;

  @Getter
  @Value("${openaev.audit-logs.include-reads:false}")
  private boolean includeReads;

  @Value("${openaev.audit-logs.transports:console,file,engine}")
  private Set<String> transports;

  @PostConstruct
  void validate() {
    // Normalize: trim whitespace from each transport name (handles "console, file" with spaces)
    if (transports != null) {
      transports =
          transports.stream()
              .map(String::trim)
              .filter(s -> !s.isEmpty())
              .collect(Collectors.toSet());
    }

    if (!enabled) {
      return;
    }

    if (transports == null || transports.isEmpty()) {
      log.warn(
          "[AUDIT CONFIG] Audit logging is enabled but no transports are configured "
              + "(openaev.audit-logs.transports is empty). No audit events will be emitted.");
      return;
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

  /** Returns whether a specific transport is active. */
  public boolean isTransportEnabled(String transportName) {
    return enabled && transports != null && transports.contains(transportName);
  }
}
