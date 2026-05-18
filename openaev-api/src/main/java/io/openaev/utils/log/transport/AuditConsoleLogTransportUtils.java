package io.openaev.utils.log.transport;

import io.openaev.engine.model.log.LogEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.utils.log.LogUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditConsoleLogTransportUtils implements AuditLogTransportUtils {

    @Value("${openaev.audit-logs.console.enabled:false}")
    private boolean enabled;

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
            //TODO Or should I use System.out.println instead of the logger?

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.warn("[AUDIT] Failed to serialize event: {}", e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(false);
    }
}
