package io.openaev.utils.log.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditFileLogTransportUtils implements AuditLogTransportUtils {

  @Value("${openaev.audit-logs.file.enabled:false}")
  private boolean enabled;

  /**
   * Dedicated audit logger — configured in logback-spring.xml with its own appender so it is not
   * suppressed by the root or io.openaev log level settings.
   */
  private static final Logger log = LoggerFactory.getLogger("AUDIT_LOG");

  private final ObjectMapper objectMapper;

  public boolean isEnabled() {
    return enabled;
  }

  @Async
  public CompletableFuture<Boolean> send(LogEvent event, Object level) {
    try {
      String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(event);
      String message = "[AUDIT] " + json;

      return send(message, level);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize event: {}", e.getMessage(), e);
    }

    return CompletableFuture.completedFuture(false);
  }

  @Async
  public CompletableFuture<Boolean> send(String message, Object level) {
    try {
      Level l = LogUtils.getLogLevel(level);

      if (l == null) {
        String invalidLevel = "[AUDIT] Invalid level: " + level;
        LogUtils.log(log, invalidLevel, Level.SEVERE);
      }

      LogUtils.log(log, message, l);

      return CompletableFuture.completedFuture(true);
    } catch (Exception e) {
      log.warn("[AUDIT] Failed to serialize event: {}", e.getMessage(), e);
    }

    return CompletableFuture.completedFuture(false);
  }
}
