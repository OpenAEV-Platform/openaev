package io.openaev.opencti.connectors.impl;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;

import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import io.openaev.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
public class SecurityCoverageConnector extends ConnectorBase {
  /**
   * Namespace UUID used to derive deterministic connector IDs via UUID v5. DO NOT CHANGE — OpenCTI
   * connector registrations depend on this value. Changing it would orphan all existing
   * registrations.
   */
  private static final UUID NAMESPACE = UUID.fromString("68949a7b-c1c2-4649-b3de-7db804ba02bb");

  @Setter private OpenCTIConfig openCTIConfig;
  @Setter private OpenAEVConfig openAEVConfig;

  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;
  @Setter private volatile String jwks;

  public SecurityCoverageConnector() {
    this.setScope(new ArrayList<>(List.of("security-coverage")));
    this.setAuto(true);
    this.setAutoUpdate(true);
  }

  @Override
  public String getName() {
    return "OpenAEV Coverage - " + this.getTenantId();
  }

  @Override
  public String getId() {
    return UUID.nameUUIDFromBytes((NAMESPACE + ":" + this.getTenantId()).getBytes()).toString();
  }

  @Override
  public String getUrl() {
    return openCTIConfig.getUrl();
  }

  @Override
  public String getApiUrl() {
    return openCTIConfig.getApiUrl();
  }

  @Override
  public String getToken() {
    return openCTIConfig.getToken();
  }

  @Override
  public boolean shouldRegister() {
    return openCTIConfig != null
        && Boolean.TRUE.equals(openCTIConfig.getEnable())
        && !StringUtils.isBlank(this.getTenantId())
        && !StringUtils.isBlank(openCTIConfig.getUrl())
        && !StringUtils.isBlank(openCTIConfig.getToken())
        && openAEVConfig != null;
  }

  @Override
  public String getListenCallbackURI() {
    return openAEVConfig.getBaseUrl()
        + TENANT_BASE_PATH
        + this.getTenantId()
        + "/stix/process-bundle";
  }
}
