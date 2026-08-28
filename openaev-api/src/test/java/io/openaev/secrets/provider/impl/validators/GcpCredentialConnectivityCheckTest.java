package io.openaev.secrets.provider.impl.validators;

import static io.openaev.secrets.provider.SecretConnectionDetails.*;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.google.api.client.http.HttpResponseException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.SecretConnectionResult.OUTCOME;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the GCP credential probe.
 *
 * <p>No test here reaches the network: {@code projectId} is always null, which stops the probe
 * right after the token exchange. The resource-manager branch is deliberately NOT unit tested —
 * covering it would mean either a real outbound HTTPS call or an in-test HTTP server, and both make
 * the suite slow and flaky for a branch whose only logic (the status-code mapping) is already
 * exercised through {@link HttpResponseException} here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GcpCredentialConnectivityCheck tests")
class GcpCredentialConnectivityCheckTest {

  private static final int TIMEOUT_SECONDS = 1;
  private static final String ACCESS_TOKEN = "fake-access-token";

  @Mock private GcpCredentialConnectivityCheckFactory googleCredentialsFactory;
  @Mock private GoogleCredentials googleCredentials;

  private GcpCredentialConnectivityCheck validator;

  @BeforeEach
  void setUp() {
    validator = new GcpCredentialConnectivityCheck(googleCredentialsFactory);
    // The timeout is a @Value field: without a Spring context it stays 0, which the validator
    // would clamp to 1s anyway — set it explicitly so the intent is visible.
    ReflectionTestUtils.setField(validator, "timeoutSeconds", TIMEOUT_SECONDS);
  }

  private static byte[] keyBytes() {
    return GCP_PRIVATE_KEY_JSON.getBytes(StandardCharsets.UTF_8);
  }

  private void givenTokenIsGranted() throws IOException {
    lenient()
        .when(googleCredentials.refreshAccessToken())
        .thenReturn(
            new AccessToken(ACCESS_TOKEN, new Date(System.currentTimeMillis() + 3_600_000)));
  }

  private void givenTokenFailsWith(Throwable failure) throws IOException {
    lenient()
        .when(googleCredentials.refreshAccessToken())
        .thenAnswer(
            invocation -> {
              throw failure;
            });
  }

  private static HttpResponseException httpFailure(int statusCode) {
    return new HttpResponseException.Builder(
            statusCode, "gcp error", new com.google.api.client.http.HttpHeaders())
        .build();
  }

  /**
   * Reproduces the message the SDK formats for an OAuth error. The typed {@code OAuthException} is
   * package-private, so the error code only travels in that message — which is exactly what the
   * validator matches on.
   */
  private static IOException oauthFailure(String errorCode) {
    return new IOException(
        "Error code " + errorCode + ": must never be surfaced (project 1234, client abcd)");
  }

  @Nested
  @DisplayName("Service account validation")
  class ServiceAccountValidation {

    @Test
    @DisplayName("A service account whose token is granted is reported active")
    void given_grantedToken_should_returnActive() throws IOException {
      // Arrange
      when(googleCredentialsFactory.forServiceAccount(any(), eq(GCP_SCOPE)))
          .thenReturn(googleCredentials);
      givenTokenIsGranted();

      // Act
      SecretConnectionResult result = validator.validateServiceAccount(keyBytes(), GCP_SCOPE, null);

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.ACTIVE);
    }

    @Test
    @DisplayName("An unparsable stored key is a configuration problem, not a rejection")
    void given_unparsableKey_should_returnUnknownInvalidConfiguration() throws IOException {
      // Arrange
      when(googleCredentialsFactory.forServiceAccount(any(), any()))
          .thenThrow(new IOException("not a key file"));

      // Act
      SecretConnectionResult result =
          validator.validateServiceAccount(
              "not-json".getBytes(StandardCharsets.UTF_8), GCP_SCOPE, null);

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
    }

    @Test
    @DisplayName("A missing key file is a configuration problem checked before any network call")
    void given_missingKey_should_returnUnknownInvalidConfiguration() {
      // Arrange & Act
      SecretConnectionResult result = validator.validateServiceAccount(null, GCP_SCOPE, null);

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
      verifyNoInteractions(googleCredentialsFactory);
    }

    @Test
    @DisplayName("An empty key file is a configuration problem")
    void given_emptyKey_should_returnUnknownInvalidConfiguration() {
      // Arrange & Act
      SecretConnectionResult result =
          validator.validateServiceAccount(new byte[0], GCP_SCOPE, null);

      // Assert
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
      verifyNoInteractions(googleCredentialsFactory);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("A missing scope is a configuration problem")
    void given_missingScope_should_returnUnknownInvalidConfiguration(String scope) {
      // Arrange & Act
      SecretConnectionResult result = validator.validateServiceAccount(keyBytes(), scope, null);

      // Assert
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
      verifyNoInteractions(googleCredentialsFactory);
    }
  }

  @Nested
  @DisplayName("OAuth2 validation")
  class OAuth2Validation {

    private void givenCredentialsAreBuilt() {
      when(googleCredentialsFactory.forOAuth2(
              eq(GCP_OAUTH_CLIENT_ID),
              eq(GCP_OAUTH_CLIENT_SECRET),
              eq(GCP_OAUTH_REFRESH_TOKEN),
              eq(GCP_SCOPE)))
          .thenReturn(googleCredentials);
    }

    private SecretConnectionResult validate() {
      return validator.validateOAuth2(
          GCP_OAUTH_CLIENT_ID, GCP_OAUTH_CLIENT_SECRET, GCP_OAUTH_REFRESH_TOKEN, GCP_SCOPE, null);
    }

    @Test
    @DisplayName("A refresh token that still exchanges is reported active")
    void given_grantedToken_should_returnActive() throws IOException {
      // Arrange
      givenCredentialsAreBuilt();
      givenTokenIsGranted();

      // Act
      SecretConnectionResult result = validate();

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.ACTIVE);
    }

    @Test
    @DisplayName("A revoked or expired refresh token (invalid_grant) is a rejection")
    void given_invalidGrant_should_returnInactiveAuthRejected() throws IOException {
      // Arrange: Google answers this with HTTP 400 — a plain status mapping would miss it, and the
      // whole point of the feature is to surface a revoked token.
      givenCredentialsAreBuilt();
      givenTokenFailsWith(oauthFailure("invalid_grant"));

      // Act
      SecretConnectionResult result = validate();

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.INACTIVE);
      assertThat(result.detail()).isEqualTo(AUTH_REJECTED);
    }

    @Test
    @DisplayName("An unknown or disabled client (invalid_client) is a rejection")
    void given_invalidClient_should_returnInactiveAuthRejected() throws IOException {
      // Arrange
      givenCredentialsAreBuilt();
      givenTokenFailsWith(oauthFailure("invalid_client"));

      // Act
      SecretConnectionResult result = validate();

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.INACTIVE);
      assertThat(result.detail()).isEqualTo(AUTH_REJECTED);
    }

    @Test
    @DisplayName("Any other OAuth error is a stored-configuration problem, not a rejection")
    void given_otherOauthError_should_returnUnknownInvalidConfiguration() throws IOException {
      // Arrange
      givenCredentialsAreBuilt();
      givenTokenFailsWith(oauthFailure("invalid_scope"));

      // Act
      SecretConnectionResult result = validate();

      // Assert
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
    }

    @Test
    @DisplayName("A plain HTTP 400 is a configuration problem, never a rejection")
    void given_plainBadRequest_should_returnUnknownInvalidConfiguration() throws IOException {
      // Arrange
      givenCredentialsAreBuilt();
      givenTokenFailsWith(httpFailure(400));

      // Act
      SecretConnectionResult result = validate();

      // Assert
      assertThat(result.detail()).isEqualTo(INVALID_CONFIGURATION);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("A missing mandatory field is a configuration problem checked before the network")
    void given_missingMandatoryField_should_returnUnknownInvalidConfiguration(String blank) {
      // Arrange & Act & Assert
      assertThat(
              validator
                  .validateOAuth2(
                      blank, GCP_OAUTH_CLIENT_SECRET, GCP_OAUTH_REFRESH_TOKEN, GCP_SCOPE, null)
                  .detail())
          .isEqualTo(INVALID_CONFIGURATION);
      assertThat(
              validator
                  .validateOAuth2(
                      GCP_OAUTH_CLIENT_ID, blank, GCP_OAUTH_REFRESH_TOKEN, GCP_SCOPE, null)
                  .detail())
          .isEqualTo(INVALID_CONFIGURATION);
      assertThat(
              validator
                  .validateOAuth2(
                      GCP_OAUTH_CLIENT_ID, GCP_OAUTH_CLIENT_SECRET, blank, GCP_SCOPE, null)
                  .detail())
          .isEqualTo(INVALID_CONFIGURATION);
      assertThat(
              validator
                  .validateOAuth2(
                      GCP_OAUTH_CLIENT_ID,
                      GCP_OAUTH_CLIENT_SECRET,
                      GCP_OAUTH_REFRESH_TOKEN,
                      blank,
                      null)
                  .detail())
          .isEqualTo(INVALID_CONFIGURATION);
      verifyNoInteractions(googleCredentialsFactory);
    }
  }

  @Nested
  @DisplayName("Failure mapping")
  class FailureMapping {

    private void givenServiceAccountFailsWith(Throwable failure) throws IOException {
      when(googleCredentialsFactory.forServiceAccount(any(), any())).thenReturn(googleCredentials);
      givenTokenFailsWith(failure);
    }

    private SecretConnectionResult validate() {
      return validator.validateServiceAccount(keyBytes(), GCP_SCOPE, null);
    }

    @Test
    @DisplayName("A refused token is a rejection")
    void given_unauthorized_should_returnInactiveAuthRejected() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(httpFailure(401));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.INACTIVE);
      assertThat(result.detail()).isEqualTo(AUTH_REJECTED);
    }

    @Test
    @DisplayName("A forbidden answer means the credential no longer grants what it is stored for")
    void given_forbidden_should_returnInactiveAuthForbidden() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(httpFailure(403));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.INACTIVE);
      assertThat(result.detail()).isEqualTo(AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("A not-found answer is read like a forbidden one")
    void given_notFound_should_returnInactiveAuthForbidden() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(httpFailure(404));

      // Act & Assert
      assertThat(validate().detail()).isEqualTo(AUTH_FORBIDDEN);
    }

    @Test
    @DisplayName("Throttling is inconclusive, never a rejection")
    void given_tooManyRequests_should_returnUnknownThrottled() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(httpFailure(429));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(THROTTLED);
    }

    @Test
    @DisplayName("A Google outage must not mass-flag credentials")
    void given_serverError_should_returnUnknownUnreachable() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(httpFailure(500));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(UNREACHABLE);
    }

    @Test
    @DisplayName("A timeout is inconclusive")
    void given_timeout_should_returnUnknownTimeout() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(new SocketTimeoutException("read timed out"));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(TIMEOUT);
    }

    @Test
    @DisplayName("A plain network failure is inconclusive")
    void given_networkFailure_should_returnUnknownUnreachable() throws IOException {
      // Arrange
      givenServiceAccountFailsWith(new IOException("connection reset"));

      // Act & Assert
      SecretConnectionResult result = validate();
      assertThat(result.outcome()).isEqualTo(OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(UNREACHABLE);
    }

    @Test
    @DisplayName("No sensitive value ever reaches the returned detail")
    void given_rejection_should_notExposeAnySensitiveValue() throws IOException {
      // Arrange: detail() is persisted and exposed, so it must stay a normalized code.
      when(googleCredentialsFactory.forOAuth2(any(), any(), any(), any()))
          .thenReturn(googleCredentials);
      givenTokenFailsWith(oauthFailure("invalid_grant"));

      // Act
      SecretConnectionResult result =
          validator.validateOAuth2(
              GCP_OAUTH_CLIENT_ID,
              GCP_OAUTH_CLIENT_SECRET,
              GCP_OAUTH_REFRESH_TOKEN,
              GCP_SCOPE,
              null);

      // Assert
      assertThat(result.detail()).isEqualTo(AUTH_REJECTED);
      assertThat(result.toString())
          .doesNotContain(GCP_OAUTH_CLIENT_SECRET)
          .doesNotContain(GCP_OAUTH_REFRESH_TOKEN)
          .doesNotContain("must never be surfaced");
    }
  }
}
