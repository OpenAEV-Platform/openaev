package io.openaev.utils.log.dispatcher;

import io.openaev.engine.model.log.LogEvent;
import io.openaev.utils.log.transport.AuditLogTransportUtils;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditLogTransportDispatcherUtils {

  private final List<AuditLogTransportUtils> transports;

  public AuditLogTransportDispatcherUtils(List<AuditLogTransportUtils> transports) {
    this.transports = transports.stream()
        .sorted(Comparator.comparingInt(AuditLogTransportUtils::priority))
        .toList();
  }

  public boolean dispatch(LogEvent event, Object level) {
    return dispatchInternal((transport, l) -> transport.send(event, l), level);
  }

  public boolean dispatch(String message, Object level) {
    return dispatchInternal((transport, l) -> transport.send(message, l), level);
  }

  private boolean dispatchInternal(
      BiFunction<AuditLogTransportUtils, Object, Boolean> sendFn, Object level) {
    boolean allSucceeded = true;
    for (AuditLogTransportUtils transport : transports) {
      if (!transport.isEnabled()) {
        continue;
      }
      try {
        if (!sendFn.apply(transport, level)) {
          allSucceeded = false;
        }
      } catch (Exception e) {
        log.error(
            "Audit log transport [{}] failed: {}",
            transport.getClass().getSimpleName(),
            e.getMessage(),
            e);
        allSucceeded = false;
      }
    }
    return allSucceeded;
  }
}
