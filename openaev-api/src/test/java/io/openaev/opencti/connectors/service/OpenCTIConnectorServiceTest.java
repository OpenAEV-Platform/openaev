package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.QueryTypeFields;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.client.response.Response;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockSecurityCoverageConnectorConfig(
    enable = true,
    url = "some-url",
    token = "68949a7b-c1c2-4649-b3de-7db804ba02bb")
public class OpenCTIConnectorServiceTest extends IntegrationTest {

  @MockitoBean private OpenCTIClient mockOpenCTIClient;
  @Autowired OpenCTIConnectorService openCTIConnectorService;

  private Optional<ConnectorBase> getInstanceOfSecurityCoverageConnector() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> c instanceof SecurityCoverageConnector)
        .findFirst();
  }

  @BeforeEach
  public void setup() {
    reset(mockOpenCTIClient);
  }

  @Nested
  @DisplayName("Register all connectors Test")
  public class RegisterAllConnectorsTest {

    @Test
    @DisplayName("When API return is error, the connector is NOT registered")
    public void whenApiReturnIsError_connectorIsNotRegistered() throws IOException {
      ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
      connector.setRegistered(false);

      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);
      when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
          .thenReturn(ResponseFixture.getErrorResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      assertThat(connector.isRegistered()).isFalse();
    }

    @Test
    @DisplayName(
        "When Connector is known registered, the service should ping instead of registering")
    public void whenConnectorIsKnownRegistered_theServiceShouldPingInsteadOfRegistering()
        throws IOException {
      ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
      connector.setRegistered(true);

      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());
      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);

      openCTIConnectorService.registerOrPingAllConnectors();

      verify(mockOpenCTIClient, atLeastOnce()).execute(any(), any(), any(Ping.class));
      verify(mockOpenCTIClient, never()).execute(any(), any(), any(RegisterConnector.class));
    }

    @Test
    @DisplayName("When registration succeeds, connector is marked as registered")
    public void whenRegistrationSucceeds_connectorIsMarkedAsRegistered() throws IOException {
      ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
      connector.setRegistered(false);

      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);
      when(mockOpenCTIClient.execute(any(), any(), any(RegisterConnector.class)))
          .thenReturn(ResponseFixture.getOkResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      assertThat(connector.isRegistered()).isTrue();
    }
  }

  @Nested
  @DisplayName("Push STIX bundle tests")
  public class PushSTIXBundleTests {

    private Bundle createBundle() {
      return new Bundle(new Identifier("titi"), List.of());
    }

    @Nested
    @DisplayName("When connector is configured")
    public class WhenConnectorIsConfigured {

      @Test
      @DisplayName("When connector is not registered, throw exception")
      public void whenConnectorIsNotRegistered_throwException() {
        ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
        connector.setRegistered(false);

        assertThatThrownBy(
                () ->
                    openCTIConnectorService.pushSecurityCoverageStixBundle(
                        createBundle(), TenantContext.getCurrentTenant()))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining("connector hasn't registered yet");
      }

      @Test
      @DisplayName("When connector is registered and API errors, throw exception")
      public void whenConnectorIsRegisteredAndAPIErrors_throwException() throws IOException {
        ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
        connector.setRegistered(true);

        when(mockOpenCTIClient.execute(any(), any(), any()))
            .thenReturn(ResponseFixture.getErrorResponse());

        assertThatThrownBy(
                () ->
                    openCTIConnectorService.pushSecurityCoverageStixBundle(
                        createBundle(), TenantContext.getCurrentTenant()))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining("Failed to push STIX bundle");
      }

      @Test
      @DisplayName("When connector is registered and API OKs, do not throw exception")
      public void whenConnectorIsRegisteredAndAPIOKs_doNotThrowException() throws IOException {
        ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
        connector.setRegistered(true);

        when(mockOpenCTIClient.execute(any(), any(), any()))
            .thenReturn(ResponseFixture.getOkResponse());

        assertThatNoException()
            .isThrownBy(
                () ->
                    openCTIConnectorService.pushSecurityCoverageStixBundle(
                        createBundle(), TenantContext.getCurrentTenant()));
      }
    }
  }
}
