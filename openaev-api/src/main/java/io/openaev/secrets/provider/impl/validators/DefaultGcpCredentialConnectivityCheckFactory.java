package io.openaev.secrets.provider.impl.validators;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;

/** Default {@link GcpCredentialConnectivityCheckFactory}, backed by the Google Auth library. */
@Component
public class DefaultGcpCredentialConnectivityCheckFactory
    implements GcpCredentialConnectivityCheckFactory {

  private final SSLContext sslContext;

  /** Carries the token exchange of every credential built here over the platform's TLS trust. */
  private final HttpTransportFactory transportFactory;

  public DefaultGcpCredentialConnectivityCheckFactory(X509TrustManager trustManager) {
    // Only the TLS trust of the library's own transport is swapped for the platform's, extra
    // trusted certs included.
    try {
      // TLS 1.3, with the TLS 1.2 the JDK still enables by default for a client: no older protocol.
      SSLContext context = SSLContext.getInstance("TLSv1.3");
      context.init(null, new TrustManager[] {trustManager}, null);
      this.sslContext = context;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Cannot build the TLS context of the GCP probes", e);
    }
    NetHttpTransport transport =
        new NetHttpTransport.Builder().setSslSocketFactory(sslContext.getSocketFactory()).build();
    this.transportFactory = () -> transport;
  }

  @Override
  public GoogleCredentials forServiceAccount(
      byte[] privateKeyJson, String scope, String customResourceManagerUri) throws IOException {
    ServiceAccountCredentials.Builder builder =
        ServiceAccountCredentials.fromStream(new ByteArrayInputStream(privateKeyJson)).toBuilder()
            .setHttpTransportFactory(transportFactory);
    if (isPresent(customResourceManagerUri)) {
      builder.setTokenServerUri(EndpointUrls.requireHttpUri(customResourceManagerUri));
    }
    // Scoping is mandatory for a service account: an unscoped credential refuses to mint a token.
    return builder.build().createScoped(List.of(scope));
  }

  @Override
  public GoogleCredentials forOAuth2(
      String clientId,
      String clientSecret,
      String refreshToken,
      String scope,
      String customTokenUri) {
    // The scope is NOT declared here on purpose: an OAuth2 grant carries the scopes the user
    // consented to at authorization time, and the token endpoint answers with exactly those. The
    // stored scope is only what the credential is meant to be used for, it cannot be re-requested.
    UserCredentials.Builder builder =
        UserCredentials.newBuilder()
            .setClientId(clientId)
            .setClientSecret(clientSecret)
            .setRefreshToken(refreshToken)
            .setHttpTransportFactory(transportFactory);
    if (isPresent(customTokenUri)) {
      builder.setTokenServerUri(EndpointUrls.requireHttpUri(customTokenUri));
    }
    return builder.build();
  }

  @Override
  public SSLContext sslContext() {
    return sslContext;
  }

  private static boolean isPresent(String value) {
    return value != null && !value.isBlank();
  }
}
