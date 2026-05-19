package io.openaev.utils.log.transport;

import io.openaev.engine.model.log.LogEvent;
import java.util.concurrent.CompletableFuture;

public interface AuditLogTransportUtils {
  boolean isEnabled();

  CompletableFuture<Boolean> send(String message, Object level);

  CompletableFuture<Boolean> send(LogEvent event, Object level);
}
