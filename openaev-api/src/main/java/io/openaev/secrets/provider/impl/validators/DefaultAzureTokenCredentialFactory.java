package io.openaev.secrets.provider.impl.validators;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import org.springframework.stereotype.Component;

/** Default {@link AzureTokenCredentialFactory}, backed by the Azure Identity SDK. */
@Component
public class DefaultAzureTokenCredentialFactory implements AzureTokenCredentialFactory {

  @Override
  public TokenCredential forServicePrincipal(
      AzureEnvironment environment, String tenantId, String clientId, String clientSecret) {
    return new ClientSecretCredentialBuilder()
        // Sovereign clouds have their own login endpoint; without this the probe would always
        // authenticate against the public cloud and reject perfectly valid credentials.
        .authorityHost(environment.getActiveDirectoryEndpoint())
        .tenantId(tenantId)
        .clientId(clientId)
        .clientSecret(clientSecret)
        .build();
  }

  @Override
  public TokenCredential forManagedIdentity(String clientId) {
    ManagedIdentityCredentialBuilder builder = new ManagedIdentityCredentialBuilder();
    if (clientId != null && !clientId.isBlank()) {
      // Set only for a user-assigned identity: leaving it out targets the system-assigned one.
      builder.clientId(clientId);
    }
    return builder.build();
  }
}
