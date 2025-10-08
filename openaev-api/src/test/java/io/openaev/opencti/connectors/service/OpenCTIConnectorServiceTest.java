package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import io.openaev.utils.fixtures.opencti.TestBeanConnector;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

@WithMockSecurityCoverageConnectorConfig(
    url = "some-url",
    authToken = "68949a7b-c1c2-4649-b3de-7db804ba02bb",
    id = "a0f2d50c-3712-47bd-8305-e2412769eb86")
public class OpenCTIConnectorServiceTest extends IntegrationTest {
  @MockBean private OpenCTIClient mockOpenCTIClient;
  @Autowired OpenCTIConnectorService openCTIConnectorService;

  private Optional<ConnectorBase> getInstanceOfTestBeanConnector() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> c instanceof TestBeanConnector)
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
      when(mockOpenCTIClient.execute(
              any(), any(), eq(new RegisterConnector(getInstanceOfTestBeanConnector().get()))))
          .thenReturn(ResponseFixture.getErrorResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      // the test connector is NOT registered
      assertThat(getInstanceOfTestBeanConnector().get().isRegistered()).isFalse();
      // other connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .filter(c -> !c.equals(getInstanceOfTestBeanConnector().get()))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }

    @Test
    @DisplayName(
        "When Connector should not register, the other connector was successfully registered")
    @DirtiesContext // because we alter an attribute of a spring-loaded connector instance
    public void whenConnectorShouldNotRegister_theOtherConnectorWasSuccessfullyRegistered()
        throws IOException {
      // make is so it appears not correctly configured
      getInstanceOfTestBeanConnector().get().setUrl(null);

      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      // the test connector is NOT registered
      assertThat(getInstanceOfTestBeanConnector().get().isRegistered()).isFalse();
      // register was not attempted
      verify(mockOpenCTIClient, never())
          .execute(any(), any(), eq(new RegisterConnector(getInstanceOfTestBeanConnector().get())));
      // other connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .filter(c -> !c.equals(getInstanceOfTestBeanConnector().get()))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }

    @Test
    @DisplayName(
        "When Connector is known registered, the service should ping instead of registering")
    public void whenConnectorIsKnownRegistered_theServiceShouldPingInsteadOfRegistering()
        throws IOException {
      openCTIConnectorService.getConnectors().forEach(c -> c.setRegistered(false));
      getInstanceOfTestBeanConnector().get().setRegistered(true);

      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());

      openCTIConnectorService.registerOrPingAllConnectors();

      verify(mockOpenCTIClient, times(1))
          .execute(any(), any(), eq(new Ping(getInstanceOfTestBeanConnector().get())));
      verify(mockOpenCTIClient, times(1)).execute(any(), any(), any(RegisterConnector.class));
      // all connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }

    @Test
    @DisplayName(
        "When Connector fails to register, the service should keep going and register the others.")
    public void whenConnectorFailsToRegister_theServiceShouldKeepGoingAndRegisterTheOthers()
        throws IOException {
      when(mockOpenCTIClient.execute(any(), any(), any()))
          .thenReturn(ResponseFixture.getOkResponse());
      when(mockOpenCTIClient.execute(
              any(), any(), eq(new RegisterConnector(getInstanceOfTestBeanConnector().get()))))
          .thenThrow(IOException.class);

      openCTIConnectorService.registerOrPingAllConnectors();

      verify(mockOpenCTIClient, times(openCTIConnectorService.getConnectors().size()))
          .execute(any(), any(), any(RegisterConnector.class));
      // the test connector is NOT registered
      assertThat(getInstanceOfTestBeanConnector().get().isRegistered()).isFalse();
      // other connectors are registered OK
      assertThat(
              openCTIConnectorService.getConnectors().stream()
                  .filter(c -> !c.equals(getInstanceOfTestBeanConnector().get()))
                  .allMatch(ConnectorBase::isRegistered))
          .isTrue();
    }
  }
}
