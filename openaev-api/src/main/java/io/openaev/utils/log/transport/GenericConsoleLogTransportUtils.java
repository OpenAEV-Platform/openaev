package io.openaev.utils.log.transport;

import io.openaev.utils.log.LogUtils;
import java.util.logging.Level;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GenericConsoleLogTransportUtils implements GenericLogTransportUtils {

  @Getter
  @Value("${openaev.generic-logs.console.enabled:false}")
  private boolean enabled;

  public boolean send(String message, Object level) {
    try {
      Level l = LogUtils.getLogLevel(level);

      if (l == null) {
        String invalidLevel = "[LOG] Invalid level: " + level;
        LogUtils.log(log, invalidLevel, Level.SEVERE);
      } else {
        LogUtils.log(log, message, l);
      }

      return true;
    } catch (Exception e) {
      log.warn("[LOG] Failed to serialize event: {}", e.getMessage(), e);
    }

    return false;
  }
}
