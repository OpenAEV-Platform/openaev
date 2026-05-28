package io.openaev.utils.log.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.audit_log.AuditLogProperties;
import io.openaev.database.model.LogTransport;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.LogUtils;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Audit log transport that writes events to the application console (stdout via SLF4J). */
@Component
@Order(1) // Ensure this runs first before file transport (priority 2) and engine transport (priority
// 3)
@RequiredArgsConstructor
@Slf4j
public class AuditConsoleLogTransportUtils implements AuditLogTransportUtils {

  private final AuditLogProperties auditLogProperties;

  @Override
  public boolean isEnabled() {
    return auditLogProperties.isTransportEnabled(LogTransport.CONSOLE);
  }

  private final ObjectMapper objectMapper;

  @Override
  public boolean send(LogEvent event, Object level) {
    try {
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
      String message = "[AUDIT] " + json;

      return send(message, level);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize event: {}", e.getMessage(), e);
      return false;
    }
  }

  @Override
  public boolean send(String message, Object level) {
    try {
      Level l = LogUtils.getLogLevel(level);

      if (l == null) {
        log.warn("[AUDIT] Invalid log level: {}, defaulting to INFO", level);
        l = Level.INFO;
      }

      LogUtils.log(log, message, l);

      return true;
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to write audit log message: {}", e.getMessage(), e);
      return false;
    }
  }
}
