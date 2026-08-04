package io.openaev.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Startup run mode for the platform.
 *
 * <p>This is a boot-time setting used to control how background processing is initialized.
 */
@Component
@ConfigurationProperties(prefix = "openaev")
@Data
public class RunModeConfig {

  private RunMode runMode = RunMode.NORMAL;

  public enum RunMode {
    NORMAL,
    SAFE
  }
}
