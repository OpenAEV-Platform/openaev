package io.openaev.secrets.provider.impl.validators;

import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.core.management.AzureEnvironment;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import java.util.Map;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAzureCredentialConnectivityCheckFactory tests")
class DefaultAzureCredentialConnectivityCheckFactoryTest {

  private static final int TIMEOUT_SECONDS = 7;

  @Mock private HttpClientFactory httpClientFactory;
  @Mock private CloseableHttpClient httpClient;

  private DefaultAzureCredentialConnectivityCheckFactory factory;

  @BeforeEach
  void setUp() {
    when(httpClientFactory.httpClientNoRetry(any(Timeout.class))).thenReturn(httpClient);
    factory =
        new DefaultAzureCredentialConnectivityCheckFactory(httpClientFactory, TIMEOUT_SECONDS);
  }

  @Nested
  @DisplayName("Platform HTTP client")
  class PlatformHttpClient {

    @Test
    @DisplayName("The client comes from the platform factory, bounded by the probe timeout")
    void given_factory_should_useThePlatformClientWithTheTimeout() {
      // Assert
      verify(httpClientFactory).httpClientNoRetry(Timeout.ofSeconds(TIMEOUT_SECONDS));
    }

    @Test
    @DisplayName("The client is released when the factory is destroyed")
    void given_destroy_should_closeTheClient() throws IOException {
      // Act
      factory.close();

      // Assert
      verify(httpClient).close();
    }
  }

  @Nested
  @DisplayName("Credential building")
  class CredentialBuilding {

    @Test
    @DisplayName("A service principal credential is built for the public cloud")
    void given_publicCloud_should_buildServicePrincipalCredential() {
      // Act / Assert
      assertThat(
              factory.forServicePrincipal(
                  AzureEnvironment.AZURE, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET))
          .isNotNull();
    }

    @Test
    @DisplayName("A service principal credential is built for a custom authority")
    void given_customAuthority_should_buildServicePrincipalCredential() {
      // Arrange
      AzureEnvironment custom =
          new AzureEnvironment(Map.of("activeDirectoryEndpointUrl", "https://localhost:4443/"));

      // Act / Assert
      assertThat(
              factory.forServicePrincipal(
                  custom, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET))
          .isNotNull();
    }

    @Test
    @DisplayName("A system-assigned managed identity credential is built without a client id")
    void given_noClientId_should_buildSystemAssignedCredential() {
      // Act / Assert
      assertThat(factory.forManagedIdentity(null)).isNotNull();
    }

    @Test
    @DisplayName("A user-assigned managed identity credential is built with its client id")
    void given_clientId_should_buildUserAssignedCredential() {
      // Act / Assert
      assertThat(factory.forManagedIdentity(AZURE_CLIENT_ID)).isNotNull();
    }
  }

  @Nested
  @DisplayName("Authority detection")
  class AuthorityDetection {

    @ParameterizedTest(name = "authority={0}")
    @ValueSource(
        strings = {
          "https://login.microsoftonline.com/",
          "https://login.microsoftonline.com",
          "HTTPS://LOGIN.MICROSOFTONLINE.COM/",
          "https://login.chinacloudapi.cn/",
          "https://login.microsoftonline.us/",
          "https://login.microsoftonline.de/"
        })
    @DisplayName("A Microsoft cloud keeps instance discovery")
    void given_microsoftAuthority_should_beRecognised(String authority) {
      // Act / Assert
      assertThat(DefaultAzureCredentialConnectivityCheckFactory.isMicrosoftAuthority(authority))
          .isTrue();
    }

    @ParameterizedTest(name = "authority={0}")
    @ValueSource(
        strings = {
          "https://localhost:4443/",
          "https://floci.internal/",
          "https://login.microsoftonline.com.evil.example/",
          ""
        })
    @DisplayName("Any other authority is a custom endpoint")
    void given_customAuthority_should_notBeRecognised(String authority) {
      // Act / Assert
      assertThat(DefaultAzureCredentialConnectivityCheckFactory.isMicrosoftAuthority(authority))
          .isFalse();
    }

    @Test
    @DisplayName("A missing authority is not a Microsoft one")
    void given_nullAuthority_should_notBeRecognised() {
      // Act / Assert
      assertThat(DefaultAzureCredentialConnectivityCheckFactory.isMicrosoftAuthority(null))
          .isFalse();
    }
  }
}
