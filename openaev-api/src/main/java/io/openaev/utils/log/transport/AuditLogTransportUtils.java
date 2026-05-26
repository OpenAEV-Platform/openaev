package io.openaev.utils.log.transport;

import io.openaev.engine.model.log.LogEvent;

public interface AuditLogTransportUtils {
  boolean isEnabled();

  boolean send(String message, Object level);

  boolean send(LogEvent event, Object level);

  /**
   * Execution priority among audit transports. Lower values execute first. This is scoped to audit
   * transports only (not Spring-wide ordering).
   */
  default int priority() {
    return 0;
  }
}
