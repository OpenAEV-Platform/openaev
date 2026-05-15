package io.openaev.opencti.connectors.impl;

import io.openaev.api.stix_process.StixApi;
import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import io.openaev.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@ConfigurationProperties(prefix = "openaev.xtm.opencti.connector.security-coverage")
public class SecurityCoverageConnector extends ConnectorBase {
  private final String id = "68949a7b-c1c2-4649-b3de-7db804ba02bb";

  // need to access the base URL for overriding the callback URI
  private OpenCTIConfig openctiConfig;
  private OpenAEVConfig mainConfig;

  @Autowired
  public void setOpenctiConfig(OpenCTIConfig openCTIConfig) {
    this.openctiConfig = openCTIConfig;
  }

  @Autowired
  public void setMainConfig(OpenAEVConfig mainConfig) {
    this.mainConfig = mainConfig;
  }

  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;
  private final String name = "OpenAEV Coverage";
  @Setter private volatile String jwks;

  public SecurityCoverageConnector() {
    this.setScope(new ArrayList<>(List.of("security-coverage")));
    this.setAuto(true);
    this.setAutoUpdate(true);
  }

  @Override
  public String getUrl() {
    return openctiConfig.getUrl();
  }

  @Override
  public String getApiUrl() {
    return openctiConfig.getApiUrl();
  }

  @Override
  public String getToken() {
    return openctiConfig.getToken();
  }

  @Override
  public boolean shouldRegister() {
    return openctiConfig.getEnable()
        && !StringUtils.isBlank(this.getListenCallbackURI())
        && !StringUtils.isBlank(this.getName())
        && !StringUtils.isBlank(this.getToken())
        && !StringUtils.isBlank(this.getUrl())
        && this.getType() != null;
  }

  public String getListenCallbackURI() {
    return mainConfig.getBaseUrl() + StixApi.STIX_URI + "/process-bundle";
  }
}
