package io.openaev.utils.log.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.LogUtils;
import java.util.logging.Level;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Audit log transport that writes events to the application console (stdout via SLF4J). */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsoleLogTransportUtils implements AuditLogTransportUtils {

  @Getter
  @Value("${openaev.audit-logs.console.enabled:false}")
  private boolean enabled;

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
