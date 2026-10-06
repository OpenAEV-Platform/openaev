package io.openaev.opencti.connectors.impl;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;

import io.openaev.config.OpenAEVConfig;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.ConnectorType;
import io.openaev.utils.StringUtils;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * Per-tenant OpenCTI connector receiving IOC validation requests (scope {@code
 * ioc-validation-request}) and pushing their results back. Registered next to the {@link
 * SecurityCoverageConnector}, with its own id namespace so both coexist in OpenCTI.
 */
@Getter
public class IocValidationConnector extends ConnectorBase {

  public static final String NAME = "OpenAEV IOC Validation";
  public static final String SCOPE = "ioc-validation-request";
  public static final String CALLBACK_PATH = "/stix/process-ioc-validation";

  /**
   * Namespace UUID used to derive the deterministic connector id. DO NOT CHANGE: OpenCTI connector
   * registrations depend on this value, and it must differ from the coverage connector namespace.
   */
  static final UUID NAMESPACE = UUID.fromString("5b8f0b6e-3f49-4d63-9a0c-7c2d1e4f8a61");

  @Setter private OpenCTIConfig openCTIConfig;
  @Setter private OpenAEVConfig openAEVConfig;

  /**
   * Shown after {@link #NAME} when several tenants connect to OpenCTI, null when only one does. The
   * tenant id stays in {@link #getId()}, never in the name people read.
   */
  @Setter private String tenantName;

  private final ConnectorType type = ConnectorType.INTERNAL_ENRICHMENT;

  public IocValidationConnector() {
    this.setScope(new ArrayList<>(List.of(SCOPE)));
    this.setAuto(false);
    this.setAutoUpdate(false);
  }

  @Override
  public String getName() {
    return tenantName == null ? NAME : NAME + " - " + tenantName;
  }

  @Override
  public String getId() {
    return UUID.nameUUIDFromBytes(
            (NAMESPACE + ":" + this.getTenantId()).getBytes(StandardCharsets.UTF_8))
        .toString();
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
    return openAEVConfig.getBaseUrl() + TENANT_BASE_PATH + this.getTenantId() + CALLBACK_PATH;
  }

  /** Reuses the coverage connector user: both authenticate with the same tenant token. */
  @Override
  public String getServiceAccountId() {
    return coverageConnectorIdentity().getServiceAccountId();
  }

  @Override
  public String getServiceAccountName() {
    return coverageConnectorIdentity().getServiceAccountName();
  }

  private SecurityCoverageConnector coverageConnectorIdentity() {
    SecurityCoverageConnector coverageConnector = new SecurityCoverageConnector();
    coverageConnector.setTenantId(this.getTenantId());
    return coverageConnector;
  }
}
