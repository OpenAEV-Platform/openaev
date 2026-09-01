package io.openaev.secrets.provider.impl.validators;

import com.google.api.client.http.HttpResponseException;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.openaev.secrets.provider.SecretConnectionResult;
import java.io.IOException;
import java.net.SocketTimeoutException;
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
 * Probes GCP credentials to tell whether they are still usable.
 *
 * <p>Two-step probe, mirroring {@link AzureCredentialConnectivityCheck}. Minting a fresh access
 * token proves the credential itself is alive, which is the question the status column answers.
 * When a project id is stored, a resource-manager read is added: a service account whose key still
 * signs, or a refresh token that still exchanges, but whose IAM binding was removed is useless in
 * practice, and only the second call surfaces it.
 *
 * <p>The mapping of failures is deliberately asymmetric: only an explicit rejection by Google
 * yields {@code AUTH_FAILED} or {@code PERMISSION_DENIED}. Everything else — timeout, throttling,
 * 5xx, plain network errors — yields an inconclusive status ({@code TIMEOUT}, {@code
 * NETWORK_ERROR}, {@code FORMAT_ERROR}), so a transient Google outage cannot mass-flag a tenant's
 * credentials as broken overnight.
 *
 * <p>Nothing sensitive is logged or returned: key material, client secrets, refresh tokens and
 * access tokens never leave this class, and Google error payloads (which embed project, client and
 * principal identifiers, and whose {@code error_description} is itself a disclosure) are reduced to
 * the normalized statuses of {@code SecretReference.SECRET_STATUS}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GcpCredentialConnectivityCheck {

  /**
   * Cloud Resource Manager v1 project read; the payload is ignored, only the status counts. v1 is
   * used rather than v3 because it accepts the human-readable project id directly, which is what is
   * stored on the credential.
   */
  private static final String PROJECT_ENDPOINT =
      "https://cloudresourcemanager.googleapis.com/v1/projects/";

  /** Google answers a revoked or expired refresh token with HTTP 400 and this error code. */
  private static final String ERROR_INVALID_GRANT = "invalid_grant";

  /** Google answers an unknown or disabled OAuth client with HTTP 400 and this error code. */
  private static final String ERROR_INVALID_CLIENT = "invalid_client";

  private final GcpCredentialConnectivityCheckFactory googleCredentialsFactory;

  @Value("${openaev.credentials.status-validation.timeout-seconds:10}")
  private int timeoutSeconds;

  /**
   * Checks a service account key file.
   *
   * @param privateKeyJson the decrypted key file content
   * @param scope the OAuth scope the credential is stored for
   * @param projectId optional project to additionally probe with a resource-manager read
   * @return the outcome, never null
   */
  public SecretConnectionResult validateServiceAccount(
      byte[] privateKeyJson, String scope, String projectId) {
    if (privateKeyJson == null || privateKeyJson.length == 0 || isBlank(scope)) {
      return SecretConnectionResult.formatError();
    }

    GoogleCredentials credentials;
    try {
      credentials = googleCredentialsFactory.forServiceAccount(privateKeyJson, scope);
    } catch (IOException | RuntimeException e) {
      // Malformed or unparsable stored key: the configuration is broken, so the credential cannot
      // be checked at all — never "rejected".
      return SecretConnectionResult.formatError();
    }
    return probe(credentials, projectId);
  }

  /**
   * Checks an OAuth 2.0 grant by exchanging the stored refresh token for a fresh access token.
   *
   * @param clientId the OAuth client id
   * @param clientSecret the decrypted OAuth client secret
   * @param refreshToken the decrypted refresh token
   * @param scope the OAuth scope the credential is stored for
   * @param projectId optional project to additionally probe with a resource-manager read
   * @return the outcome, never null
   */
  public SecretConnectionResult validateOAuth2(
      String clientId, String clientSecret, String refreshToken, String scope, String projectId) {
    if (isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken) || isBlank(scope)) {
      return SecretConnectionResult.formatError();
    }

    GoogleCredentials credentials;
    try {
      credentials = googleCredentialsFactory.forOAuth2(clientId, clientSecret, refreshToken, scope);
    } catch (RuntimeException e) {
      return SecretConnectionResult.formatError();
    }
    return probe(credentials, projectId);
  }

  private SecretConnectionResult probe(GoogleCredentials credentials, String projectId) {
    Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    String token;
    try {
      AccessToken accessToken = credentials.refreshAccessToken();
      if (accessToken == null || isBlank(accessToken.getTokenValue())) {
        return SecretConnectionResult.networkError();
      }
      token = accessToken.getTokenValue();
    } catch (IOException | RuntimeException e) {
      return mapFailure(e);
    }

    if (isBlank(projectId)) {
      return SecretConnectionResult.active();
    }
    return probeProject(projectId, token, timeout);
  }

  /**
   * Reads the project with the freshly obtained token. A plain HTTPS GET rather than the
   * resource-manager SDK: the status code alone answers the question, and pulling the full GCP
   * resource-manager client in for one call would add a large dependency tree for no gain.
   */
  private SecretConnectionResult probeProject(String projectId, String token, Duration timeout) {
    URI uri = URI.create(PROJECT_ENDPOINT + projectId);

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
      return SecretConnectionResult.timeout();
    } catch (IOException e) {
      return SecretConnectionResult.networkError();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return SecretConnectionResult.networkError();
    }
  }

  private SecretConnectionResult mapStatusCode(int statusCode) {
    if (statusCode >= 200 && statusCode < 300) {
      return SecretConnectionResult.active();
    }
    return switch (statusCode) {
      // The token was refused: the credential is genuinely not usable.
      case 401 -> SecretConnectionResult.authFailed();
      // Authenticated but not authorized, and 404 is what the resource manager returns for a
      // project the principal cannot see: in both cases the credential no longer grants what it is
      // stored for.
      case 403, 404 -> SecretConnectionResult.permissionDenied();
      // 429 is Google's quota/rate-limit answer (RESOURCE_EXHAUSTED): the credential itself was
      // never judged, so it must stay inconclusive and never be flagged.
      case 429 -> SecretConnectionResult.networkError();
      // 5xx and anything else say nothing about the credential either.
      default -> SecretConnectionResult.networkError();
    };
  }

  /**
   * Turns a token-exchange failure into an outcome. Only an authentication rejection is conclusive;
   * the rest is treated as "could not check".
   */
  private SecretConnectionResult mapFailure(Exception failure) {
    // The OAuth2 error code is matched on the exception message rather than read from a typed
    // getter: the SDK's OAuthException is package-private, so its error code is only reachable
    // through the message it formats ("Error code invalid_grant: ...").
    String errorCode = findOAuthErrorCode(failure);
    if (errorCode != null) {
      return SecretConnectionResult.authFailed();
    }
    HttpResponseException httpFailure = findCause(failure, HttpResponseException.class);
    if (httpFailure != null) {
      // A 400 carrying no recognized OAuth error code says nothing about the credential itself: it
      // is the stored configuration that Google could not make sense of.
      return httpFailure.getStatusCode() == 400
          ? SecretConnectionResult.formatError()
          : mapStatusCode(httpFailure.getStatusCode());
    }
    if (isOtherOAuthError(failure)) {
      // An OAuth error that is neither invalid_grant nor invalid_client is a stored-configuration
      // problem, never a rejection.
      return SecretConnectionResult.formatError();
    }
    if (isTimeout(failure)) {
      return SecretConnectionResult.timeout();
    }
    // Everything left is unclassified — inconclusive by default, never a rejection. Message only:
    // a Google error body would leak project, client and principal identifiers into the logs.
    log.debug("GCP credential probe failed with an unmapped error: {}", failure.getMessage());
    return SecretConnectionResult.networkError();
  }

  /**
   * Finds the rejection error codes in the failure chain, which a plain status-code mapping gets
   * wrong: Google answers a revoked or expired refresh token ({@code invalid_grant}), and an
   * unknown or disabled client ({@code invalid_client}), with HTTP 400. Both are genuine
   * rejections, and reading a 400 generically as {@code NETWORK_ERROR} would mean a revoked token
   * is never surfaced — which is the whole point of the feature.
   */
  private static String findOAuthErrorCode(Throwable failure) {
    String messages = messageChain(failure);
    if (messages.contains(ERROR_INVALID_GRANT)) {
      return ERROR_INVALID_GRANT;
    }
    return messages.contains(ERROR_INVALID_CLIENT) ? ERROR_INVALID_CLIENT : null;
  }

  /** Any other OAuth error: a stored-configuration problem, never a rejection. */
  private static boolean isOtherOAuthError(Throwable failure) {
    return messageChain(failure).contains("error code ");
  }

  private static String messageChain(Throwable failure) {
    StringBuilder messages = new StringBuilder();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current.getMessage() != null) {
        messages.append(current.getMessage().toLowerCase(java.util.Locale.ROOT)).append('\n');
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return messages.toString();
  }

  private static <T extends Throwable> T findCause(Throwable failure, Class<T> type) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (type.isInstance(current)) {
        return type.cast(current);
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return null;
  }

  /**
   * The transport surfaces "no answer in time" either as a {@link SocketTimeoutException} or as a
   * {@link TimeoutException} wrapped in the SDK's own exception. Both mean "no answer", never
   * "rejected".
   */
  private static boolean isTimeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof SocketTimeoutException
          || current instanceof TimeoutException
          || current instanceof java.net.http.HttpTimeoutException) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
