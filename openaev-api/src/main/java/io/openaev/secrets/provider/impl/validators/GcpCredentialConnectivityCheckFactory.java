package io.openaev.secrets.provider.impl.validators;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;

/**
 * Builds the Google SDK credential objects used to probe a stored GCP credential.
 *
 * <p>This interface exists purely as a seam, exactly like {@link
 * AzureCredentialConnectivityCheckFactory}. {@code ServiceAccountCredentials} and {@code
 * UserCredentials} are final-ish builder products that reach the network on first use, so a
 * validator calling them directly cannot be unit tested. Injecting this factory lets {@code
 * GcpCredentialConnectivityCheck} be driven against a stub for every branch — success, rejection,
 * timeout, throttling — without a Google project.
 */
public interface GcpCredentialConnectivityCheckFactory {

  /**
   * Builds a credential from an uploaded service account key file.
   *
   * @param privateKeyJson the decrypted key file content
   * @param scope the OAuth scope the credential is stored for
   * @return a credential able to request tokens for that service account
   * @throws IOException when the key material cannot be parsed — a stored-configuration problem,
   *     never a rejection
   */
  GoogleCredentials forServiceAccount(byte[] privateKeyJson, String scope) throws IOException;

  /**
   * Builds a credential from an OAuth 2.0 installed-application grant.
   *
   * @param clientId the OAuth client id
   * @param clientSecret the decrypted OAuth client secret
   * @param refreshToken the decrypted refresh token
   * @param scope the OAuth scope the credential is stored for
   * @return a credential able to exchange the refresh token for an access token
   */
  GoogleCredentials forOAuth2(
      String clientId, String clientSecret, String refreshToken, String scope);
}
