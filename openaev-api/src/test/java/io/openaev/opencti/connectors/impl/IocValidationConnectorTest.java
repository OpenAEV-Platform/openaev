package io.openaev.opencti.connectors.impl;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockSecurityCoverageConnectorConfig(
    enable = true,
    url = "https://opencti",
    token = "some-token")
@DisplayName("IocValidationConnector Integration Tests")
class IocValidationConnectorTest extends IntegrationTest {

  @Autowired private OpenCTIConnectorService openCTIConnectorService;

  private ConnectorBase iocValidationConnector() {
    Optional<ConnectorBase> connector =
        openCTIConnectorService.getIocValidationConnector(Tenant.DEFAULT_TENANT_UUID);
    assertThat(connector).isPresent();
    return connector.get();
  }

  @Test
  @DisplayName("a configured tenant gets an IOC validation connector next to the coverage one")
  void given_configuredTenant_should_registerBothConnectors() {
    ConnectorBase iocValidation = iocValidationConnector();
    ConnectorBase coverage =
        openCTIConnectorService.getConnectorBase(Tenant.DEFAULT_TENANT_UUID).orElseThrow();

    assertThat(iocValidation).isInstanceOf(IocValidationConnector.class);
    assertThat(iocValidation.getId()).isNotEqualTo(coverage.getId());
    // One tenant connects to OpenCTI: a readable name, the tenant id stays in the connector id
    assertThat(iocValidation.getName()).isEqualTo(IocValidationConnector.NAME);
    assertThat(iocValidation.shouldRegister()).isTrue();
  }

  @Test
  @DisplayName("the callback is the tenant IOC validation endpoint")
  void given_connector_should_buildTenantCallbackUri() {
    assertThat(iocValidationConnector().getListenCallbackURI())
        .contains(
            TENANT_BASE_PATH + Tenant.DEFAULT_TENANT_UUID + IocValidationConnector.CALLBACK_PATH);
  }

  @Test
  @DisplayName("both connectors authenticate as the same tenant service account")
  void given_connectors_should_shareServiceAccount() {
    ConnectorBase coverage =
        openCTIConnectorService.getConnectorBase(Tenant.DEFAULT_TENANT_UUID).orElseThrow();
    ConnectorBase iocValidation = iocValidationConnector();

    assertThat(iocValidation.getServiceAccountId()).isEqualTo(coverage.getServiceAccountId());
    assertThat(iocValidation.getToken()).isEqualTo(coverage.getToken());
    assertThat(iocValidation.getApiUrl()).isEqualTo("https://opencti/graphql");
  }
}
