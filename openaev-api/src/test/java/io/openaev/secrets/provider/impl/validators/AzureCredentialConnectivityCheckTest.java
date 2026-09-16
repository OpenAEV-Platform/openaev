package io.openaev.secrets.provider.impl.validators;

import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.AUTH_FAILED;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.FORMAT_ERROR;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.NETWORK_ERROR;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.TIMEOUT;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.UNKNOWN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.CredentialUnavailableException;
import io.openaev.secrets.provider.SecretConnectionResult;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeoutException;
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
import reactor.core.publisher.Mono;

/**
 * Unit tests for the Azure credential probe.
 *
 * <p>No test here reaches the network: {@code subscriptionId} is always null, which stops the probe
 * right after the token exchange. The ARM subscription branch is deliberately NOT unit tested —
 * covering it would mean either a real outbound HTTPS call or an in-test HTTP server, and both make
 * the suite slow and flaky for a branch whose only logic (the status-code mapping) is already
 * exercised through {@link ClientAuthenticationException} and {@link HttpResponseException} here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AzureCredentialValidator tests")
class AzureCredentialConnectivityCheckTest {

  private static final String UNSUPPORTED_ENVIRONMENT = "AzureNotACloud";
  private static final int TIMEOUT_SECONDS = 1;

  @Mock private AzureCredentialConnectivityCheckFactory tokenCredentialFactory;
  @Mock private TokenCredential tokenCredential;

  private AzureCredentialConnectivityCheck validator;

  @BeforeEach
  void setUp() {
    validator = new AzureCredentialConnectivityCheck(tokenCredentialFactory);
    // The timeout is a @Value field: without a Spring context it stays 0, which the validator
    // would clamp to 1s anyway — set it explicitly so the intent is visible.
    ReflectionTestUtils.setField(validator, "timeoutSeconds", TIMEOUT_SECONDS);
  }

  /** Makes the mocked credential answer with a usable token. */
  private void givenTokenIsGranted() {
    lenient()
        .when(tokenCredential.getToken(any()))
        .thenReturn(
            Mono.just(new AccessToken("fake-access-token", OffsetDateTime.now().plusHours(1))));
  }

  /** Makes the mocked credential fail the token exchange. */
  private void givenTokenFailsWith(Throwable failure) {
    lenient().when(tokenCredential.getToken(any())).thenReturn(Mono.error(failure));
  }

  private static HttpResponseException httpFailure(int statusCode) {
    HttpResponse response = org.mockito.Mockito.mock(HttpResponse.class);
    lenient().when(response.getStatusCode()).thenReturn(statusCode);
    return new HttpResponseException("azure error", response);
  }

  @Nested
  @DisplayName("Service principal validation")
  class ServicePrincipalValidation {

    @Test
    @DisplayName("A service principal whose token is granted is reported active")
    void given_grantedToken_should_returnActive() {
      // Arrange
      when(tokenCredentialFactory.forServicePrincipal(
              any(), eq(AZURE_TENANT_ID), eq(AZURE_CLIENT_ID), eq(AZURE_CLIENT_SECRET)))
          .thenReturn(tokenCredential);
      givenTokenIsGranted();

      // Act
      SecretConnectionResult result =
          validator.validateServicePrincipal(
              AZURE_ENVIRONMENT, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(result.wasChecked()).isTrue();
      assertThat(result.statusToPersist()).contains(ACTIVE);
    }

    @Test
    @DisplayName("The probe targets the resolved cloud, not a hardcoded one")
    void given_sovereignCloud_should_buildCredentialForThatCloud() {
      // Arrange
      when(tokenCredentialFactory.forServicePrincipal(any(), any(), any(), any()))
          .thenReturn(tokenCredential);
      givenTokenIsGranted();

      // Act
      validator.validateServicePrincipal(
          AZURE_ENVIRONMENT, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);

      // Assert
      verify(tokenCredentialFactory)
          .forServicePrincipal(
              eq(AzureEnvironment.AZURE),
              eq(AZURE_TENANT_ID),
              eq(AZURE_CLIENT_ID),
              eq(AZURE_CLIENT_SECRET));
    }

    @Test
    @DisplayName("An unsupported cloud name is a configuration problem, not a rejection")
    void given_unsupportedEnvironment_should_returnUnknownInvalidConfiguration() {
      // Arrange / Act
      SecretConnectionResult result =
          validator.validateServicePrincipal(
              UNSUPPORTED_ENVIRONMENT, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);

      // Assert
      assertThat(result.status()).isEqualTo(FORMAT_ERROR);
      assertThat(result.wasChecked()).isTrue();
      verifyNoInteractions(tokenCredentialFactory);
    }

    @ParameterizedTest(name = "environment=\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("A blank cloud name is a configuration problem")
    void given_blankEnvironment_should_returnUnknownInvalidConfiguration(String environment) {
      // Arrange / Act
      SecretConnectionResult result =
          validator.validateServicePrincipal(
              environment, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);

      // Assert
      assertThat(result.status()).isEqualTo(FORMAT_ERROR);
      assertThat(result.wasChecked()).isTrue();
      verifyNoInteractions(tokenCredentialFactory);
    }

    @ParameterizedTest(name = "missing field #{index}")
    @NullAndEmptySource
    @DisplayName("A missing tenant id, client id or client secret is a configuration problem")
    void given_missingMandatoryField_should_returnUnknownInvalidConfiguration(String missing) {
      // Arrange / Act
      SecretConnectionResult missingTenant =
          validator.validateServicePrincipal(
              AZURE_ENVIRONMENT, missing, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);
      SecretConnectionResult missingClient =
          validator.validateServicePrincipal(
              AZURE_ENVIRONMENT, AZURE_TENANT_ID, missing, AZURE_CLIENT_SECRET, null);
      SecretConnectionResult missingSecret =
          validator.validateServicePrincipal(
              AZURE_ENVIRONMENT, AZURE_TENANT_ID, AZURE_CLIENT_ID, missing, null);

      // Assert
      assertThat(missingTenant.status()).isEqualTo(FORMAT_ERROR);
      assertThat(missingClient.status()).isEqualTo(FORMAT_ERROR);
      assertThat(missingSecret.status()).isEqualTo(FORMAT_ERROR);
      assertThat(missingTenant.wasChecked()).isTrue();
      assertThat(missingClient.wasChecked()).isTrue();
      assertThat(missingSecret.wasChecked()).isTrue();
      verifyNoInteractions(tokenCredentialFactory);
    }
  }

  @Nested
  @DisplayName("Managed identity validation")
  class ManagedIdentityValidation {

    @Test
    @DisplayName("A system-assigned identity is probed without any client id")
    void given_systemAssignedIdentity_should_probeWithoutClientId() {
      // Arrange
      when(tokenCredentialFactory.forManagedIdentity(isNull())).thenReturn(tokenCredential);
      givenTokenIsGranted();

      // Act
      SecretConnectionResult result =
          validator.validateManagedIdentity(AZURE_ENVIRONMENT, null, null);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(result.wasChecked()).isTrue();
      verify(tokenCredentialFactory).forManagedIdentity(isNull());
    }

    @Test
    @DisplayName("A user-assigned identity is probed with its client id")
    void given_userAssignedIdentity_should_probeWithClientId() {
      // Arrange
      when(tokenCredentialFactory.forManagedIdentity(eq(AZURE_CLIENT_ID)))
          .thenReturn(tokenCredential);
      givenTokenIsGranted();

      // Act
      SecretConnectionResult result =
          validator.validateManagedIdentity(AZURE_ENVIRONMENT, AZURE_CLIENT_ID, null);

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(result.wasChecked()).isTrue();
      verify(tokenCredentialFactory).forManagedIdentity(eq(AZURE_CLIENT_ID));
    }

    @Test
    @DisplayName("An unreachable IMDS never flags the identity as inactive")
    void given_imdsUnavailable_should_returnUnknownUnreachable() {
      // Arrange — this is what happens on every deployment running outside Azure.
      when(tokenCredentialFactory.forManagedIdentity(any())).thenReturn(tokenCredential);
      givenTokenFailsWith(
          new CredentialUnavailableException("ManagedIdentityCredential is unavailable"));

      // Act
      SecretConnectionResult result =
          validator.validateManagedIdentity(AZURE_ENVIRONMENT, null, null);

      // Assert
      assertThat(result.status()).isEqualTo(UNKNOWN);
      assertThat(result.wasChecked()).isTrue();
      assertThat(result.statusToPersist()).contains(UNKNOWN);
    }

    @Test
    @DisplayName("An unsupported cloud name is a configuration problem")
    void given_unsupportedEnvironment_should_returnUnknownInvalidConfiguration() {
      // Arrange / Act
      SecretConnectionResult result =
          validator.validateManagedIdentity(UNSUPPORTED_ENVIRONMENT, null, null);

      // Assert
      assertThat(result.status()).isEqualTo(FORMAT_ERROR);
      assertThat(result.wasChecked()).isTrue();
      verifyNoInteractions(tokenCredentialFactory);
    }
  }

  @Nested
  @DisplayName("Failure mapping")
  class FailureMapping {

    @BeforeEach
    void stubFactory() {
      lenient()
          .when(tokenCredentialFactory.forServicePrincipal(any(), any(), any(), any()))
          .thenReturn(tokenCredential);
    }

    @Test
    @DisplayName("An authentication rejection is the only conclusive failure")
    void given_authenticationRejected_should_returnInactive() {
      // Arrange
      givenTokenFailsWith(new ClientAuthenticationException("AADSTS7000215", null));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(AUTH_FAILED);
      assertThat(result.wasChecked()).isTrue();
      assertThat(result.statusToPersist()).contains(AUTH_FAILED);
    }

    @Test
    @DisplayName("A throttled probe leaves the previous status untouched")
    void given_throttled_should_returnUnknownThrottled() {
      // Arrange
      givenTokenFailsWith(httpFailure(429));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(NETWORK_ERROR);
      assertThat(result.wasChecked()).isTrue();
      assertThat(result.statusToPersist()).contains(NETWORK_ERROR);
    }

    @Test
    @DisplayName("A provider outage leaves the previous status untouched")
    void given_serverError_should_returnUnknownUnreachable() {
      // Arrange
      givenTokenFailsWith(httpFailure(503));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(NETWORK_ERROR);
      assertThat(result.wasChecked()).isTrue();
    }

    @Test
    @DisplayName("A timeout leaves the previous status untouched")
    void given_timeout_should_returnUnknownTimeout() {
      // Arrange
      givenTokenFailsWith(new RuntimeException(new TimeoutException("no answer")));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(TIMEOUT);
      assertThat(result.wasChecked()).isTrue();
    }

    @Test
    @DisplayName("A probe that never answers times out instead of hanging the run")
    void given_neverAnsweringProvider_should_returnUnknownTimeout() {
      // Arrange — a Mono that never emits is what a hung endpoint looks like.
      lenient()
          .when(tokenCredential.getToken(any()))
          .thenReturn(Mono.never().cast(AccessToken.class).delaySubscription(Duration.ZERO));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(TIMEOUT);
      assertThat(result.wasChecked()).isTrue();
    }

    @Test
    @DisplayName("An unmapped failure is inconclusive, never a rejection")
    void given_unmappedFailure_should_returnUnknownUnreachable() {
      // Arrange
      givenTokenFailsWith(new IllegalStateException("something unexpected"));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(NETWORK_ERROR);
      assertThat(result.wasChecked()).isTrue();
    }

    @Test
    @DisplayName("No failure detail ever carries the client secret")
    void given_failureCarryingTheSecret_should_notLeakItInTheDetail() {
      // Arrange — a provider error echoing the secret back is exactly the leak to prevent.
      givenTokenFailsWith(
          new ClientAuthenticationException(
              "Invalid client secret provided: " + AZURE_CLIENT_SECRET, null));

      // Act
      SecretConnectionResult result = validateServicePrincipal();

      // Assert
      assertThat(result.status()).isEqualTo(AUTH_FAILED);
      assertThat(result.wasChecked()).isTrue();
    }

    private SecretConnectionResult validateServicePrincipal() {
      return validator.validateServicePrincipal(
          AZURE_ENVIRONMENT, AZURE_TENANT_ID, AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, null);
    }
  }
}
