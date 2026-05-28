package io.openaev.utils.log.transport;

import io.openaev.engine.model.log.LogEvent;

public interface AuditLogTransportUtils {
  boolean isEnabled();

  boolean send(String message, Object level);

  boolean send(LogEvent event, Object level);
}
