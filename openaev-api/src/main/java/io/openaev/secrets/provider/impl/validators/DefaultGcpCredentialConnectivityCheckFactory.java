package io.openaev.secrets.provider.impl.validators;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

/** Default {@link GcpCredentialConnectivityCheckFactory}, backed by the Google Auth library. */
@Component
public class DefaultGcpCredentialConnectivityCheckFactory
    implements GcpCredentialConnectivityCheckFactory {

  @Override
  public GoogleCredentials forServiceAccount(byte[] privateKeyJson, String scope)
      throws IOException {
    // Scoping is mandatory for a service account: an unscoped credential refuses to mint a token.
    return ServiceAccountCredentials.fromStream(new ByteArrayInputStream(privateKeyJson))
        .createScoped(List.of(scope));
  }

  @Override
  public GoogleCredentials forOAuth2(
      String clientId, String clientSecret, String refreshToken, String scope) {
    // The scope is NOT declared here on purpose: an OAuth2 grant carries the scopes the user
    // consented to at authorization time, and the token endpoint answers with exactly those. The
    // stored scope is only what the credential is meant to be used for, it cannot be re-requested.
    return UserCredentials.newBuilder()
        .setClientId(clientId)
        .setClientSecret(clientSecret)
        .setRefreshToken(refreshToken)
        .build();
  }
}
