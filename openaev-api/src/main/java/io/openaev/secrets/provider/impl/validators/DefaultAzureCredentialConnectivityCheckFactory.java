package io.openaev.secrets.provider.impl.validators;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import javax.net.ssl.X509TrustManager;
import org.springframework.stereotype.Component;
import reactor.netty.http.Http11SslContextSpec;
import reactor.netty.tcp.SslProvider;

/** Default {@link AzureCredentialConnectivityCheckFactory}, backed by the Azure Identity SDK. */
@Component
public class DefaultAzureCredentialConnectivityCheckFactory
    implements AzureCredentialConnectivityCheckFactory {

  /** Shared by every credential built here, so the Netty resources are set up only once. */
  private final HttpClient httpClient;

  public DefaultAzureCredentialConnectivityCheckFactory(X509TrustManager trustManager) {
    // The SDK's own Netty transport, proxy settings included (HTTPS_PROXY / NO_PROXY and the JVM
    // properties): only its TLS trust is swapped for the platform's, extra trusted certs included.
    SslProvider.GenericSslContextSpec<?> tls =
        Http11SslContextSpec.forClient().configure(builder -> builder.trustManager(trustManager));
    this.httpClient =
        new NettyAsyncHttpClientBuilder(
                reactor.netty.http.client.HttpClient.create().secure(spec -> spec.sslContext(tls)))
            .build();
  }

  @Override
  public TokenCredential forServicePrincipal(
      AzureEnvironment environment,
      String tenantId,
      String clientId,
      String clientSecret,
      String authorityHostUri) {
    ClientSecretCredentialBuilder builder =
        new ClientSecretCredentialBuilder()
            .httpClient(httpClient)
            .tenantId(tenantId)
            .clientId(clientId)
            .clientSecret(clientSecret);
    if (authorityHostUri == null || authorityHostUri.isBlank()) {
      // Sovereign clouds have their own login endpoint; without this the probe would always
      // authenticate against the public cloud and reject perfectly valid credentials.
      return builder.authorityHost(environment.getActiveDirectoryEndpoint()).build();
    }
    // Microsoft does not know a custom authority: instance discovery would ask the public cloud to
    // validate it, and the token request would fail before reaching that authority.
    return builder.authorityHost(authorityHostUri).disableInstanceDiscovery().build();
  }

  @Override
  public TokenCredential forManagedIdentity(String clientId) {
    ManagedIdentityCredentialBuilder builder =
        new ManagedIdentityCredentialBuilder().httpClient(httpClient);
    if (clientId != null && !clientId.isBlank()) {
      // Set only for a user-assigned identity: leaving it out targets the system-assigned one.
      builder.clientId(clientId);
    }
    return builder.build();
  }
}
