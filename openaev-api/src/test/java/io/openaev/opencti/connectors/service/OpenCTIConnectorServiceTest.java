package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
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
import io.openaev.utils.fixtures.opencti.TestBeanConnector;
import io.openaev.utils.fixtures.opencti.TestBeanConnectorShouldRegister;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockSecurityCoverageConnectorConfig(
    enable = true,
    url = "some-url",
    token = "68949a7b-c1c2-4649-b3de-7db804ba02bb")
public class OpenCTIConnectorServiceTest extends IntegrationTest {

  @MockBean private OpenCTIClient mockOpenCTIClient;
  @Autowired OpenCTIConnectorService openCTIConnectorService;

  private Optional<ConnectorBase> getInstanceOfTestBeanConnector() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> c instanceof TestBeanConnector)
        .findFirst();
  }

  private Optional<ConnectorBase> getInstanceOfTestBeanShouldRegisterConnector() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> c instanceof TestBeanConnectorShouldRegister)
        .findFirst();
  }

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
    @DisplayName(
        "When API return is error payload for single connector, the other connector was successfully registered")
    public void
        whenApiReturnIsErrorPayloadForSingleConnector_theOtherConnectorWasSuccessfullyRegistered()
            throws IOException {

      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());
      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);
      ConnectorBase connector = getInstanceOfTestBeanConnector().get();
      when(mockOpenCTIClient.execute(any(), any(), eq(new RegisterConnector(connector))))
          .thenReturn(ResponseFixture.getErrorResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      // the test connector is NOT registered
      assertThat(connector.isRegistered()).isFalse();
      // other connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .filter(c -> !(c instanceof TestBeanConnector))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }

    @Test
    @DisplayName(
        "When Connector should not register, the other connector was successfully registered")
    public void whenConnectorShouldNotRegister_theOtherConnectorWasSuccessfullyRegistered()
        throws IOException {
      // make is so it appears not correctly configured
      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());
      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);

      openCTIConnectorService.registerOrPingAllConnectors();

      // the test connector is NOT registered
      ConnectorBase connector = getInstanceOfTestBeanConnector().get();
      assertThat(connector.isRegistered()).isFalse();
      // register was not attempted
      verify(mockOpenCTIClient, never())
          .execute(any(), any(), eq(new RegisterConnector(connector)));
      // other connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .filter(c -> !(c instanceof TestBeanConnector))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }

    @Test
    @DisplayName(
        "When Connector is known registered, the service should ping instead of registering")
    public void whenConnectorIsKnownRegistered_theServiceShouldPingInsteadOfRegistering()
        throws IOException {
      openCTIConnectorService.getConnectors().forEach(c -> c.setRegistered(false));
      ConnectorBase connectorBase = getInstanceOfTestBeanShouldRegisterConnector().get();
      connectorBase.setRegistered(true);

      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());
      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);

      openCTIConnectorService.registerOrPingAllConnectors();

      verify(mockOpenCTIClient, atLeastOnce())
          .execute(
              any(),
              any(),
              argThat(
                  arg ->
                      arg instanceof Ping
                          && ((Ping) arg).getConnector()
                              instanceof TestBeanConnectorShouldRegister));
      verify(mockOpenCTIClient, times(1)).execute(any(), any(), any(RegisterConnector.class));
      // all connectors are registered OK
      List<ConnectorBase> connectors = getConnectorsShouldRegister();

      assertThat(connectors.stream().allMatch(ConnectorBase::isRegistered)).isTrue();
    }

    @Test
    @DisplayName(
        "When Connector fails to register, the service should keep going and register the others.")
    public void whenConnectorFailsToRegister_theServiceShouldKeepGoingAndRegisterTheOthers()
        throws IOException {
      openCTIConnectorService.getConnectors().forEach(c -> c.setRegistered(false));
      Response jwksSchemaResponse = ResponseFixture.getSchemaResponseWithJwks();
      when(mockOpenCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
          .thenReturn(jwksSchemaResponse);
      when(mockOpenCTIClient.execute(
              any(),
              any(),
              argThat(
                  arg ->
                      arg instanceof RegisterConnector
                          && ((RegisterConnector) arg).getConnector()
                              instanceof SecurityCoverageConnector)))
          .thenReturn(ResponseFixture.getOkResponse());

      ConnectorBase connectorBase = getInstanceOfTestBeanShouldRegisterConnector().get();
      when(mockOpenCTIClient.execute(
              any(),
              any(),
              argThat(
                  arg ->
                      arg instanceof RegisterConnector
                          && ((RegisterConnector) arg).getConnector()
                              instanceof TestBeanConnectorShouldRegister)))
          .thenThrow(IOException.class);

      openCTIConnectorService.registerOrPingAllConnectors();

      List<ConnectorBase> connectors = getConnectorsShouldRegister();

      verify(mockOpenCTIClient, times(connectors.size()))
          .execute(any(), any(), any(RegisterConnector.class));
      // the test connector is NOT registered
      assertThat(connectorBase.isRegistered()).isFalse();
      // other connectors are registered OK
      assertThat(
              connectors.stream()
                  .filter(c -> !(c instanceof TestBeanConnectorShouldRegister))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }
  }

  @NotNull
  private List<ConnectorBase> getConnectorsShouldRegister() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(
            c ->
                c instanceof SecurityCoverageConnector
                    || c instanceof TestBeanConnectorShouldRegister)
        .toList();
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
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "Cannot push STIX bundle via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
                    .formatted(connector.getName(), connector.getApiUrl()));
      }

      @Test
      @DisplayName("When connector is registered and API errors, throw exception")
      public void whenConnectorIsRegisteredAndAPIErrors_throwException() throws IOException {
        ConnectorBase connector = getInstanceOfSecurityCoverageConnector().get();
        connector.setRegistered(true);

        when(mockOpenCTIClient.execute(any(), any(), any()))
            .thenReturn(ResponseFixture.getErrorResponse());

        assertThatThrownBy(
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()))
            .isInstanceOf(ConnectorError.class)
            .hasMessageContaining(
                "Failed to push STIX bundle via connector %s to OpenCTI at %s"
                    .formatted(connector.getName(), connector.getApiUrl()));
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
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()));
      }
    }
  }
}
