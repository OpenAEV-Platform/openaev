package io.openaev.utils.log.transport;

import io.openaev.utils.log.LogUtils;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GenericFileLogTransportUtils implements GenericLogTransportUtils {

  @Value("${openaev.generic-logs.file.enabled:false}")
  private boolean enabled;

  /**
   * Dedicated default logger — configured in logback-spring.xml with its own appender so it is not
   * suppressed by the root or io.openaev log level settings.
   */
  private static final Logger log = LoggerFactory.getLogger("LOG");

  public boolean isEnabled() {
    return enabled;
  }

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
