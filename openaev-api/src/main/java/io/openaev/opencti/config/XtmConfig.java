package io.openaev.opencti.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Data
@ConfigurationProperties(prefix = "openaev.xtm")
public class XtmConfig {

  private Map<String, OpenCTIConfig> opencti;
}
