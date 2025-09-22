package io.openbas.xtmhub.collector;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@Getter
@ConfigurationProperties(prefix = "openbas.xtm.hub.collector")
public class XtmHubCollectorConfig {
  private boolean enable;

  @NotBlank private String id;

  // period between two connectivity checks, default to 1 hour in milliseconds.
  @NotBlank private Integer connectivityCheckInterval = 60 * 60 * 1000;
}
