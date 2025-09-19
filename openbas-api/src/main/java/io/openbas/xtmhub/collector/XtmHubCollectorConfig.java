package io.openbas.xtmhub.collector;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Setter
@Component
@ConfigurationProperties(prefix = "openbas.xtm.hub.collector")
public class XtmHubCollectorConfig {
  @Getter private boolean enable;

  @Getter @NotBlank private String id;

  @Getter @NotBlank private Integer connectivityCheckInterval = 60 * 60 * 1000;
}
