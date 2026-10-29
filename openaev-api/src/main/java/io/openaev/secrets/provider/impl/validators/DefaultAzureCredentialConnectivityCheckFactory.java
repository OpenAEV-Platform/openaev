package io.openaev.secrets.provider.impl.validators;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.HttpClient;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.AzureAuthorityHosts;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import io.openaev.authorisation.HttpClientFactory;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Default {@link AzureCredentialConnectivityCheckFactory}, backed by the Azure Identity SDK. */
@Component
public class DefaultAzureCredentialConnectivityCheckFactory
    implements AzureCredentialConnectivityCheckFactory {

  /** Authorities Entra ID instance discovery knows about: any other one is a custom endpoint. */
  private static final List<String> MICROSOFT_AUTHORITIES =
      List.of(
          AzureAuthorityHosts.AZURE_PUBLIC_CLOUD,
          AzureAuthorityHosts.AZURE_CHINA,
          AzureAuthorityHosts.AZURE_GERMANY,
          AzureAuthorityHosts.AZURE_GOVERNMENT);

  private final CloseableHttpClient apacheHttpClient;
  private final HttpClient azureHttpClient;

  public DefaultAzureCredentialConnectivityCheckFactory(
      HttpClientFactory httpClientFactory,
      @Value("${openaev.credentials.status-validation.timeout-seconds:10}") int timeoutSeconds) {
    // One client for every probe: building one per credential would leak its connection pool.
    // Proxy-aware: the SDK's own Netty transport honored HTTPS_PROXY / NO_PROXY.
    this.apacheHttpClient =
        httpClientFactory.httpClientNoRetryWithProxy(
            Timeout.ofSeconds(Math.max(1, timeoutSeconds)));
    this.azureHttpClient = new ApacheAzureHttpClient(apacheHttpClient);
  }

  @Override
  public CloseableHttpClient httpClient() {
    return apacheHttpClient;
  }

  @PreDestroy
  void close() {
    try {
      apacheHttpClient.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public TokenCredential forServicePrincipal(
      AzureEnvironment environment, String tenantId, String clientId, String clientSecret) {
    // Sovereign clouds have their own login endpoint; without this the probe would always
    // authenticate against the public cloud and reject perfectly valid credentials.
    String authorityHost = environment.getActiveDirectoryEndpoint();
    ClientSecretCredentialBuilder builder =
        new ClientSecretCredentialBuilder()
            .httpClient(azureHttpClient)
            .authorityHost(authorityHost)
            .tenantId(tenantId)
            .clientId(clientId)
            .clientSecret(clientSecret);
    if (!isMicrosoftAuthority(authorityHost)) {
      // Instance discovery queries Microsoft's own metadata endpoint, which knows nothing about a
      // custom authority and would fail the sign-in before it reaches it.
      builder.disableInstanceDiscovery();
    }
    return builder.build();
  }

  @Override
  public TokenCredential forManagedIdentity(String clientId) {
    ManagedIdentityCredentialBuilder builder =
        new ManagedIdentityCredentialBuilder().httpClient(azureHttpClient);
    if (clientId != null && !clientId.isBlank()) {
      // Set only for a user-assigned identity: leaving it out targets the system-assigned one.
      builder.clientId(clientId);
    }
    return builder.build();
  }

  static boolean isMicrosoftAuthority(String authorityHost) {
    return authorityHost != null
        && MICROSOFT_AUTHORITIES.stream()
            .anyMatch(known -> known.equalsIgnoreCase(withTrailingSlash(authorityHost)));
  }

  private static String withTrailingSlash(String endpoint) {
    return endpoint.endsWith("/") ? endpoint : endpoint + "/";
  }
}
