package io.openaev.opencti.connectors.impl;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.config.OpenCTIParamConfig;
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
  // TODO migrate connector and user to match with the new one
  private static final String BASE_ID = "68949a7b-c1c2-4649-b3de-7db804ba02bb";

  // need to access the base URL for overriding the callback URI
  private OpenCTIParamConfig openCTIParamConfig;
  private OpenAEVConfig mainConfig;

  @Autowired
  public void setOpenCTIParamConfig(OpenCTIParamConfig openCTIParamConfig) {
    this.openCTIParamConfig = openCTIParamConfig;
  }

  @Autowired
  public void setMainConfig(OpenAEVConfig mainConfig) {
    this.mainConfig = mainConfig;
  }

  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;
  // TODO update with tenant name at the end
  private final String name = "OpenAEV Coverage";
  @Setter private volatile String jwks;

  public SecurityCoverageConnector() {
    this.setScope(new ArrayList<>(List.of("security-coverage")));
    this.setAuto(true);
    this.setAutoUpdate(true);
  }

  @Override
  public String getId() {
    return BASE_ID + ":" + this.getTenantId();
  }

  @Override
  public String getUrl() {
    return openCTIParamConfig.getUrl();
  }

  @Override
  public String getApiUrl() {
    return openCTIParamConfig.getApiUrl();
  }

  @Override
  public String getToken() {
    return openCTIParamConfig.getToken();
  }

  @Override
  public boolean shouldRegister() {
    return Boolean.TRUE.equals(openCTIParamConfig.getEnable())
        && !StringUtils.isBlank(this.getListenCallbackURI())
        && !StringUtils.isBlank(this.getName())
        && !StringUtils.isBlank(this.getToken())
        && !StringUtils.isBlank(this.getUrl())
        && this.getType() != null;
  }

  @Override
  public String getListenCallbackURI() {
    return mainConfig.getBaseUrl() + TENANT_PREFIX + this.getTenantId() + "/stix/process-bundle";
  }
}
