package io.openaev.opencti.config;

import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds per-tenant OpenCTI configuration from properties of the form:
 * openaev.xtm.opencti.<tenantId>.enable / .url / .token / .api-url
 */
@Component
@Data
@ConfigurationProperties(prefix = "openaev.xtm")
public class OpenCTIConfig {

  /** Key = tenant ID, value = OpenCTI connection parameters for that tenant. */
  private Map<String, OpenCTIParamConfig> opencti;
}
