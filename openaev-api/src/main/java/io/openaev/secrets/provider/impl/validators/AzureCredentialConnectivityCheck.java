package io.openaev.secrets.provider.impl.validators;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.exception.ClientAuthenticationException;
import com.azure.core.exception.HttpResponseException;
import com.azure.core.http.HttpHeaderName;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.HttpResponse;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.CredentialUnavailableException;
import io.openaev.database.model.AzureEnvironments;
import io.openaev.secrets.provider.SecretConnectionResult;
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
public class AzureCredentialConnectivityCheck {

  /** ARM's OAuth scope suffix: a token for the whole resource manager, no narrower grant. */
  private static final String DEFAULT_SCOPE_SUFFIX = ".default";

  /**
   * Stable ARM API version for a subscription GET; the payload is ignored, only the status counts.
   */
  private static final String SUBSCRIPTION_API_VERSION = "2022-12-01";

  private final AzureCredentialConnectivityCheckFactory tokenCredentialFactory;

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
   * @param authorityHostUri optional host of a sovereign or mocked cloud, used for the login and
   *     the ARM read instead of the endpoints of the stored cloud
   * @return the outcome, never null
   */
  public SecretConnectionResult validateServicePrincipal(
      String environmentName,
      String tenantId,
      String clientId,
      String clientSecret,
      String subscriptionId,
      String authorityHostUri) {
    AzureEnvironment environment;
    try {
      environment = requireEnvironment(environmentName);
    } catch (IllegalArgumentException e) {
      // Stored configuration is wrong, not the credential: it cannot be checked at all.
      return SecretConnectionResult.formatError();
    }
    if (isBlank(tenantId) || isBlank(clientId) || isBlank(clientSecret)) {
      return SecretConnectionResult.formatError();
    }

    TokenCredential credential;
    try {
      credential =
          tokenCredentialFactory.forServicePrincipal(
              environment, tenantId, clientId, clientSecret, authorityHostUri);
    } catch (IllegalArgumentException e) {
      // The SDK refuses a malformed or non-HTTPS authority host: the stored override is wrong.
      return SecretConnectionResult.formatError();
    }
    return probe(
        credential, resourceManagerEndpoint(environment, authorityHostUri), subscriptionId);
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
   * @param authorityHostUri optional host of a sovereign or mocked cloud, used for the ARM read
   *     instead of the endpoint of the stored cloud (the identity endpoint stays the one of the
   *     platform, see {@link AzureCredentialConnectivityCheckFactory#forManagedIdentity})
   * @return the outcome, never null
   */
  public SecretConnectionResult validateManagedIdentity(
      String environmentName, String clientId, String subscriptionId, String authorityHostUri) {
    AzureEnvironment environment;
    try {
      environment = requireEnvironment(environmentName);
    } catch (IllegalArgumentException e) {
      return SecretConnectionResult.formatError();
    }

    TokenCredential credential = tokenCredentialFactory.forManagedIdentity(clientId);
    return probe(
        credential, resourceManagerEndpoint(environment, authorityHostUri), subscriptionId);
  }

  private static String resourceManagerEndpoint(
      AzureEnvironment environment, String authorityHostUri) {
    return withTrailingSlash(
        isBlank(authorityHostUri) ? environment.getResourceManagerEndpoint() : authorityHostUri);
  }

  private SecretConnectionResult probe(
      TokenCredential credential, String resourceManagerEndpoint, String subscriptionId) {
    Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    String token;
    try {
      AccessToken accessToken =
          credential
              .getToken(
                  new TokenRequestContext()
                      .addScopes(resourceManagerEndpoint + DEFAULT_SCOPE_SUFFIX))
              .block(timeout);
      if (accessToken == null) {
        return SecretConnectionResult.timeout();
      }
      token = accessToken.getToken();
    } catch (RuntimeException e) {
      return mapFailure(e);
    }

    if (isBlank(subscriptionId)) {
      return SecretConnectionResult.active();
    }
    return probeSubscription(resourceManagerEndpoint, subscriptionId, token, timeout);
  }

  /**
   * Reads the subscription with the freshly obtained token. A plain GET rather than the
   * resource-manager SDK: the status code alone answers the question, and pulling {@code
   * azure-resourcemanager} in for one call would add a large dependency tree for no gain.
   *
   * <p>Sent through the transport of the credential itself: a second client (the JDK one) resolves
   * {@code localhost} to a single address and ignores {@code HTTPS_PROXY}, so it could fail on a
   * host the token call had just reached.
   */
  private SecretConnectionResult probeSubscription(
      String resourceManagerEndpoint, String subscriptionId, String token, Duration timeout) {
    HttpRequest request;
    try {
      request =
          new HttpRequest(
              HttpMethod.GET,
              resourceManagerEndpoint
                  + "subscriptions/"
                  + subscriptionId
                  + "?api-version="
                  + SUBSCRIPTION_API_VERSION);
    } catch (IllegalArgumentException e) {
      // A malformed authority host override: the stored configuration is wrong.
      return SecretConnectionResult.formatError();
    }
    request.setHeader(HttpHeaderName.AUTHORIZATION, "Bearer " + token);

    HttpResponse response = null;
    try {
      response = tokenCredentialFactory.httpClient().send(request).block(timeout);
      if (response == null) {
        return SecretConnectionResult.timeout();
      }
      return mapStatusCode(response.getStatusCode());
    } catch (RuntimeException e) {
      if (isTimeout(e)) {
        return SecretConnectionResult.timeout();
      }
      // No response at all: inconclusive. Never logged with its message, a URL may carry the
      // subscription id.
      log.debug("Azure subscription probe failed without a response");
      return SecretConnectionResult.networkError();
    } finally {
      if (response != null) {
        response.close();
      }
    }
  }

  private SecretConnectionResult mapStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return SecretConnectionResult.active();
    }
    return switch (statusCode) {
      // The token was refused: the credential is genuinely not usable.
      case 400, 401 -> SecretConnectionResult.authFailed();
      // Authenticated but not authorized, and 404 is what ARM returns for a subscription the
      // principal cannot see: in both cases the credential no longer grants what it is stored
      // for.
      case 403, 404 -> SecretConnectionResult.permissionDenied();
      // 429 -> throttled
      case 429 -> SecretConnectionResult.networkError();
      default -> SecretConnectionResult.networkError();
    };
  }

  /**
   * Turns an SDK failure into an outcome. Only an authentication rejection is conclusive; the rest
   * is treated as "could not check".
   */
  private SecretConnectionResult mapFailure(RuntimeException failure) {
    // Managed identity outside Azure, or no identity assigned: never a rejection.
    if (failure instanceof CredentialUnavailableException) {
      return SecretConnectionResult.unknown();
    }
    if (failure instanceof ClientAuthenticationException authenticationFailure) {
      Integer statusCode = statusCodeOf(authenticationFailure);
      // Entra ID answers 400 with an AADSTS code for a bad secret or an unknown application, and
      // 401 when the credential is refused outright.
      if (statusCode == null) {
        return SecretConnectionResult.authFailed();
      }
      return mapStatusCode(statusCode);
    }
    if (failure instanceof HttpResponseException httpFailure) {
      // No status means no response at all: inconclusive, so fall back to a 5xx-like reading.
      Integer statusCode = statusCodeOf(httpFailure);
      return mapStatusCode(statusCode != null ? statusCode : 503);
    }
    if (isTimeout(failure)) {
      return SecretConnectionResult.timeout();
    }
    // Everything left is unclassified — inconclusive by default, never a rejection. Message only:
    // an Azure error body would leak identifiers into the logs.
    log.debug("Azure credential probe failed with an unmapped error");
    return SecretConnectionResult.networkError();
  }

  private static Integer statusCodeOf(HttpResponseException failure) {
    return failure.getResponse() != null ? failure.getResponse().getStatusCode() : null;
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

  private static String withTrailingSlash(String endpoint) {
    return endpoint.endsWith("/") ? endpoint : endpoint + "/";
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
