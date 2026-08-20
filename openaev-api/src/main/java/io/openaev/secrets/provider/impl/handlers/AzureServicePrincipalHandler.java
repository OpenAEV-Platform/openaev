package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.AzureEnvironments;
import io.openaev.database.model.AzureServicePrincipalSecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureServicePrincipalHandler implements SecretHandler {

  protected final NativeEncryptionService nativeEncryptionService;

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof AzureServicePrincipalSecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    AzureServicePrincipalSecret azureSecret =
        existingSecret instanceof AzureServicePrincipalSecret casted
            ? casted
            : new AzureServicePrincipalSecret();

    // A null value means "left untouched by the client", so the stored value must be kept: the form
    // strips unchanged write-only fields from the payload.
    if (request.azureEnvironment() != null) {
      AzureEnvironments.fromName(request.azureEnvironment());
      azureSecret.setAzureEnvironment(request.azureEnvironment());
    }

    if (request.azureClientId() != null) {
      azureSecret.setAzureClientId(request.azureClientId());
    }

    if (request.azureClientSecret() != null) {
      azureSecret.setAzureClientSecret(
          nativeEncryptionService.encrypt(request.azureClientSecret()));
    }

    if (request.azureTenantId() != null) {
      azureSecret.setAzureTenantId(request.azureTenantId());
    }

    if (request.azureSubscriptionId() != null) {
      azureSecret.setAzureSubscriptionId(request.azureSubscriptionId());
    }

    if (azureSecret.getAzureEnvironment() == null
        || azureSecret.getAzureClientId() == null
        || azureSecret.getAzureClientSecret() == null
        || azureSecret.getAzureTenantId() == null) {
      throw new IllegalArgumentException(
          "Azure environment, client id, client secret and tenant id are required");
    }

    return azureSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof AzureServicePrincipalSecret azureServicePrincipalSecret) {
      return SecretMetadata.forAzureServicePrincipal(
          azureServicePrincipalSecret.getAzureEnvironment(),
          azureServicePrincipalSecret.getAzureClientId());
    }
    throw new IllegalArgumentException(
        "Secret type mismatch: expected AZURE_SERVICE_PRINCIPAL secret");
  }
}
