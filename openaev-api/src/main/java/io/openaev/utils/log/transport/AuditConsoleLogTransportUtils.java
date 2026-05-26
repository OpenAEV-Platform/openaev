package io.openaev.utils.log.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.LogUtils;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsoleLogTransportUtils implements AuditLogTransportUtils {

  @Getter
  @Value("${openaev.audit-logs.console.enabled:false}")
  private boolean enabled;

  private final ObjectMapper objectMapper;

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
