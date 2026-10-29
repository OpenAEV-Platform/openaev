package io.openaev.secrets.provider.impl.validators;

import static io.openaev.utils.fixtures.GcpServiceAccountKeyFixture.GCP_KEY_FILE_TOKEN_URI;
import static io.openaev.utils.fixtures.GcpServiceAccountKeyFixture.createServiceAccountKeyJson;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DefaultGcpCredentialConnectivityCheckFactory tests")
class DefaultGcpCredentialConnectivityCheckFactoryTest {

  /** Where the Google Auth library sends an OAuth 2.0 refresh when told nothing else. */
  private static final String LIBRARY_DEFAULT_TOKEN_HOST = "oauth2.googleapis.com";

  private final DefaultGcpCredentialConnectivityCheckFactory factory =
      new DefaultGcpCredentialConnectivityCheckFactory();

  @Nested
  @DisplayName("Service account")
  class ServiceAccount {

    @Test
    @DisplayName("A custom resource manager URI replaces the token server of the key file")
    void given_customResourceManagerUri_should_useItAsTokenServer() throws IOException {
      // Arrange & Act
      GoogleCredentials credentials =
          factory.forServiceAccount(
              createServiceAccountKeyJson(GCP_KEY_FILE_TOKEN_URI),
              GCP_SCOPE,
              GCP_CUSTOM_RESOURCE_MANAGER_URI);

      // Assert
      assertThat(((ServiceAccountCredentials) credentials).getTokenServerUri())
          .isEqualTo(URI.create(GCP_CUSTOM_RESOURCE_MANAGER_URI));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Without an override the token server of the key file is kept")
    void given_noOverride_should_keepTheKeyFileTokenServer(String customResourceManagerUri)
        throws IOException {
      // Arrange & Act
      GoogleCredentials credentials =
          factory.forServiceAccount(
              createServiceAccountKeyJson(GCP_KEY_FILE_TOKEN_URI),
              GCP_SCOPE,
              customResourceManagerUri);

      // Assert
      assertThat(((ServiceAccountCredentials) credentials).getTokenServerUri())
          .isEqualTo(URI.create(GCP_KEY_FILE_TOKEN_URI));
    }
  }

  @Nested
  @DisplayName("OAuth 2.0")
  class OAuth2 {

    private UserCredentials build(String customTokenUri) {
      return (UserCredentials)
          factory.forOAuth2(
              GCP_OAUTH_CLIENT_ID,
              GCP_OAUTH_CLIENT_SECRET,
              GCP_OAUTH_REFRESH_TOKEN,
              GCP_SCOPE,
              customTokenUri);
    }

    @Test
    @DisplayName("A custom token URI replaces the library's token server")
    void given_customTokenUri_should_useItAsTokenServer() {
      // Arrange & Act
      UserCredentials credentials = build(GCP_CUSTOM_TOKEN_URI);

      // Assert
      assertThat(credentials.toBuilder().getTokenServerUri())
          .isEqualTo(URI.create(GCP_CUSTOM_TOKEN_URI));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Without an override the library's default token server is kept")
    void given_noOverride_should_keepTheLibraryDefault(String customTokenUri) {
      // Arrange & Act
      UserCredentials credentials = build(customTokenUri);

      // Assert
      assertThat(credentials.toBuilder().getTokenServerUri().getHost())
          .isEqualTo(LIBRARY_DEFAULT_TOKEN_HOST);
    }
  }
}
