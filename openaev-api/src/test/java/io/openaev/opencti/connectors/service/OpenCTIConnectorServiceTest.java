package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.types.Identifier;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import io.openaev.utils.fixtures.opencti.TestBeanConnector;
import io.openaev.utils.mockConfig.WithMockSecurityCoverageConnectorConfig;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@WithMockSecurityCoverageConnectorConfig(
    url = "some-url",
    authToken = "68949a7b-c1c2-4649-b3de-7db804ba02bb",
    id = "a0f2d50c-3712-47bd-8305-e2412769eb86")
public class OpenCTIConnectorServiceTest {
  @MockBean private OpenCTIClient mockOpenCTIClient;
  @Autowired OpenCTIConnectorService openCTIConnectorService;

  private Optional<ConnectorBase> getInstanceOfTestBeanConnector() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> c instanceof TestBeanConnector)
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
  @SpringBootTest
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

        assertThatThrownBy(
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "Cannot push STIX bundle via connector %s to OpenCTI at %s: connector hasn't registered yet. Try again later."
                    .formatted(connector.getName(), connector.getApiUrl()));
      }

      @Test
      @DisplayName("When connector is registered and API errors, throw exception")
      @DirtiesContext // we force register a global connector
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
      @DirtiesContext // we force register a global connector
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

    @Nested
    @SpringBootTest
    @DisplayName("When connector is NOT configured")
    @WithMockSecurityCoverageConnectorConfig // null config
    @DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
    public class WhenConnectorIsNOTConfigured {
      @Test
      @DisplayName("throw exception")
      @DirtiesContext // we change a property
      public void whenConnectorIsNotRegistered_throwException() {
        getInstanceOfSecurityCoverageConnector().get().setUrl(null);

        assertThatThrownBy(
                () -> openCTIConnectorService.pushSecurityCoverageStixBundle(createBundle()))
            .isInstanceOf(ConnectorError.class)
            .hasMessage(
                "No instance of Security Coverage connector is currently active to send security coverage bundles.");
      }
    }
  }
}
