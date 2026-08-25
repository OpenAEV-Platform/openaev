package io.openaev.secrets.provider.impl.validators;

import static io.openaev.secrets.provider.SecretConnectionDetails.*;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.CredentialUnavailableException;
import io.openaev.database.model.AzureEnvironments;
import io.openaev.secrets.provider.SecretConnectionResult;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Probes Azure credentials to tell whether they are still usable.
 *
 * <p>Two-step probe. Requesting a token proves the credential itself is alive, which is the
 * question the status column answers. When a subscription id is stored, a scoped ARM read is added:
 * a service principal whose secret is valid but whose role assignment was removed is useless in
 * practice, and only the second call surfaces it.
 *
 * <p>The mapping of failures is the delicate part, and it is deliberately asymmetric: only an
 * explicit rejection by Entra ID or ARM yields {@code INACTIVE}. Everything else — timeout,
 * throttling, IMDS unavailability, 5xx, plain network errors — yields an inconclusive result, so a
 * transient Azure outage cannot mass-flag a tenant's credentials as broken overnight.
 *
 * <p>Nothing sensitive is logged or returned: client secrets and tokens never leave this class, and
 * Azure error payloads (which embed tenant, application and principal identifiers, and whose {@code
 * AADSTS} text is itself a disclosure) are reduced to the normalized codes of {@code
 * SecretValidationDetails}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AzureCredentialValidator {

  /** ARM's OAuth scope suffix: a token for the whole resource manager, no narrower grant. */
  private static final String DEFAULT_SCOPE_SUFFIX = ".default";

  /**
   * Stable ARM API version for a subscription GET; the payload is ignored, only the status counts.
   */
  private static final String SUBSCRIPTION_API_VERSION = "2022-12-01";

  private final AzureTokenCredentialFactory tokenCredentialFactory;

  @Value("${openaev.credentials.status-validation.timeout-seconds:10}")
  private int timeoutSeconds;

  /**
   * Checks a service principal (application + client secret).
   *
   * @param environmentName the stored Azure cloud name
   * @param tenantId the Entra ID tenant hosting the application
   * @param clientId the application id
   * @param clientSecret the decrypted client secret
   * @param subscriptionId optional subscription to additionally probe with an ARM read
   * @return the outcome, never null
   */
  public SecretConnectionResult validateServicePrincipal(
      String environmentName,
      String tenantId,
      String clientId,
      String clientSecret,
      String subscriptionId) {
    AzureEnvironment environment;
    try {
      environment = requireEnvironment(environmentName);
    } catch (IllegalArgumentException e) {
      // Stored configuration is wrong, not the credential: it cannot be checked at all.
      return SecretConnectionResult.unknown(INVALID_CONFIGURATION);
    }
    if (isBlank(tenantId) || isBlank(clientId) || isBlank(clientSecret)) {
      return SecretConnectionResult.unknown(INVALID_CONFIGURATION);
    }

    TokenCredential credential =
        tokenCredentialFactory.forServicePrincipal(environment, tenantId, clientId, clientSecret);
    return probe(credential, environment, subscriptionId);
  }

  /**
   * Checks a managed identity.
   *
   * <p>Only conclusive when the platform itself runs inside Azure: elsewhere IMDS is unreachable
   * and the probe returns an inconclusive result rather than flagging the credential.
   *
   * @param environmentName the stored Azure cloud name
   * @param clientId the user-assigned identity's client id, or null for the system-assigned one
   * @param subscriptionId optional subscription to additionally probe with an ARM read
   * @return the outcome, never null
   */
  public SecretConnectionResult validateManagedIdentity(
      String environmentName, String clientId, String subscriptionId) {
    AzureEnvironment environment;
    try {
      environment = requireEnvironment(environmentName);
    } catch (IllegalArgumentException e) {
      return SecretConnectionResult.unknown(INVALID_CONFIGURATION);
    }

    TokenCredential credential = tokenCredentialFactory.forManagedIdentity(clientId);
    return probe(credential, environment, subscriptionId);
  }

  private SecretConnectionResult probe(
      TokenCredential credential, AzureEnvironment environment, String subscriptionId) {
    Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    String token;
    try {
      AccessToken accessToken =
          credential
              .getToken(new TokenRequestContext().addScopes(armScope(environment)))
              .block(timeout);
      if (accessToken == null) {
        return SecretConnectionResult.unknown(TIMEOUT);
      }
      token = accessToken.getToken();
    } catch (RuntimeException e) {
      return mapFailure(e);
    }

    if (isBlank(subscriptionId)) {
      return SecretConnectionResult.active();
    }
    return probeSubscription(environment, subscriptionId, token, timeout);
  }

  /**
   * Reads the subscription with the freshly obtained token. A plain HTTPS GET rather than the
   * resource-manager SDK: the status code alone answers the question, and pulling {@code
   * azure-resourcemanager} in for one call would add a large dependency tree for no gain.
   */
  private SecretConnectionResult probeSubscription(
      AzureEnvironment environment, String subscriptionId, String token, Duration timeout) {
    String endpoint = withTrailingSlash(environment.getResourceManagerEndpoint());
    URI uri =
        URI.create(
            endpoint
                + "subscriptions/"
                + subscriptionId
                + "?api-version="
                + SUBSCRIPTION_API_VERSION);

    try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build()) {
      HttpRequest request =
          HttpRequest.newBuilder(uri)
              .GET()
              .timeout(timeout)
              .header("Authorization", "Bearer " + token)
              .build();
      HttpResponse<Void> response =
          httpClient.send(request, HttpResponse.BodyHandlers.discarding());
      return mapStatusCode(response.statusCode());
    } catch (java.net.http.HttpTimeoutException e) {
      return SecretConnectionResult.unknown(TIMEOUT);
    } catch (IOException e) {
      return SecretConnectionResult.unknown(UNREACHABLE);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SecretConnectionResult.unknown(UNREACHABLE);
    }
  }

  private SecretConnectionResult mapStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return SecretConnectionResult.active();
    }
    return switch (statusCode) {
      // The token was refused: the credential is genuinely not usable.
      case 401 -> SecretConnectionResult.inactive(AUTH_REJECTED);
      // Authenticated but not authorized, and 404 is what ARM returns for a subscription the
      // principal cannot see: in both cases the credential no longer grants what it is stored
      // for.
      case 403, 404 -> SecretConnectionResult.inactive(AUTH_FORBIDDEN);
      case 429 -> SecretConnectionResult.unknown(THROTTLED);
      default -> SecretConnectionResult.unknown(UNREACHABLE);
    };
  }

  /**
   * Turns an SDK failure into an outcome. Only an authentication rejection is conclusive; the rest
   * is treated as "could not check".
   */
  private SecretConnectionResult mapFailure(RuntimeException failure) {
    // Managed identity outside Azure, or no identity assigned: never a rejection.
    if (failure instanceof CredentialUnavailableException) {
      return SecretConnectionResult.unknown(UNREACHABLE);
    }
    if (failure instanceof ClientAuthenticationException authenticationFailure) {
      Integer statusCode = statusCodeOf(authenticationFailure);
      // Entra ID answers 400 with an AADSTS code for a bad secret or an unknown application, and
      // 401 when the credential is refused outright.
      if (statusCode == null || statusCode == 400 || statusCode == 401) {
        return SecretConnectionResult.inactive(AUTH_REJECTED);
      }
      return mapStatusCode(statusCode);
    }
    if (failure instanceof HttpResponseException httpFailure) {
      // No status means no response at all: inconclusive, so fall back to a 5xx-like reading.
      return mapStatusCode(statusCodeOf(httpFailure, 503));
    }
    if (isTimeout(failure)) {
      return SecretConnectionResult.unknown(TIMEOUT);
    }
    // Everything left is unclassified — inconclusive by default, never a rejection. Message only:
    // an Azure error body would leak identifiers into the logs.
    log.debug("Azure credential probe failed with an unmapped error: {}", failure.getMessage());
    return SecretConnectionResult.unknown(UNREACHABLE);
  }

  private static Integer statusCodeOf(HttpResponseException failure) {
    return failure.getResponse() != null ? failure.getResponse().getStatusCode() : null;
  }

  private static int statusCodeOf(HttpResponseException failure, int fallback) {
    Integer statusCode = statusCodeOf(failure);
    return statusCode != null ? statusCode : fallback;
  }

  /**
   * Reactor signals a blocking timeout as an {@code IllegalStateException}, and the SDK can also
   * surface a {@link TimeoutException} wrapped in a runtime exception. Both mean "no answer in
   * time", never "rejected".
   */
  private static boolean isTimeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof TimeoutException) {
        return true;
      }
      String message = current.getMessage();
      if (current instanceof IllegalStateException
          && message != null
          && message.contains("Timeout on blocking read")) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }

  private static AzureEnvironment requireEnvironment(String environmentName) {
    AzureEnvironment environment = AzureEnvironments.fromName(environmentName);
    if (environment == null) {
      throw new IllegalArgumentException("Azure environment is required");
    }
    return environment;
  }

  private static String armScope(AzureEnvironment environment) {
    return withTrailingSlash(environment.getResourceManagerEndpoint()) + DEFAULT_SCOPE_SUFFIX;
  }

  private static String withTrailingSlash(String endpoint) {
    return endpoint.endsWith("/") ? endpoint : endpoint + "/";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
