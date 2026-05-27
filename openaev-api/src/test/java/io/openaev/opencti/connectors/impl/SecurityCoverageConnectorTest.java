package io.openaev.opencti.connectors.impl;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockSecurityCoverageConnectorConfig(
    enable = true,
    url = "https://opencti",
    token = "some-token")
@DisplayName("SecurityCoverageConnector Integration Tests")
public class SecurityCoverageConnectorTest extends IntegrationTest {

  @Autowired private OpenCTIConnectorService openCTIConnectorService;

  private SecurityCoverageConnector getConnector() {
    Optional<ConnectorBase> connector =
        openCTIConnectorService.getConnectorBase(TenantContext.getCurrentTenant());
    assertThat(connector).isPresent();
    return (SecurityCoverageConnector) connector.get();
  }

  @Nested
  @DisplayName("Remote URL override")
  class RemoteUrlOverride {

    @Test
    @DisplayName("it appends the graphql endpoint to the url")
    void given_fqdn_should_appendGraphqlEndpoint() {
      // Act & Assert
      SecurityCoverageConnector connector = getConnector();
      assertThat(connector.getApiUrl()).isEqualTo("https://opencti/graphql");
    }
  }

  @Nested
  @DisplayName("Connector properties")
  class ConnectorProperties {

    @Test
    @DisplayName("connector has correct tenantId from config map")
    void given_connector_should_haveTenantId() {
      SecurityCoverageConnector connector = getConnector();
      assertThat(connector.getTenantId()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("connector shouldRegister returns true when properly configured")
    void given_validConfig_should_register() {
      SecurityCoverageConnector connector = getConnector();
      assertThat(connector.shouldRegister()).isTrue();
    }

    @Test
    @DisplayName("connector getId is deterministic based on namespace + tenantId")
    void given_connector_should_haveDeterministicId() {
      SecurityCoverageConnector connector = getConnector();
      String id = connector.getId();
      assertThat(id).isNotNull().isNotBlank();
      // Calling again should give same result (deterministic)
      assertThat(connector.getId()).isEqualTo(id);
    }
  }

  @Nested
  @DisplayName("Listen Callback URI override")
  class ListenCallbackURIOverride {

    @Test
    @DisplayName("it builds the URI with the tenant prefix")
    void given_connector_should_buildTenantCallbackUri() {
      // Act & Assert
      SecurityCoverageConnector connector = getConnector();
      assertThat(connector.getListenCallbackURI())
          .contains(TENANT_BASE_PATH + Tenant.DEFAULT_TENANT_UUID + "/stix/process-bundle");
    }
  }
}
