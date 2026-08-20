package io.openaev.secrets.provider.impl.handlers;

import io.openaev.database.model.AzureEnvironments;
import io.openaev.database.model.AzureManagedIdentitySecret;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.secrets.provider.SecretMetadata;
import io.openaev.secrets.provider.SecretStoreRequest;
import org.springframework.stereotype.Component;

/**
 * Handles Azure managed identity secrets.
 *
 * <p>Unlike the other cloud handlers, this one holds no encrypted value: Azure creates and rotates
 * the credentials of a managed identity itself, so only the cloud to target and the identity to
 * assume are stored.
 */
@Component
public class AzureManagedIdentityHandler implements SecretHandler {

  @Override
  public boolean supports(Secret secret) {
    return secret instanceof AzureManagedIdentitySecret;
  }

  @Override
  public boolean supports(SecretReference reference) {
    return reference instanceof CredentialSecretReference credential
        && credential.getCredentialAuthMethod()
            == CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY;
  }

  @Override
  public Secret buildOrUpdate(Secret existingSecret, SecretStoreRequest request) {
    AzureManagedIdentitySecret azureSecret =
        existingSecret instanceof AzureManagedIdentitySecret casted
            ? casted
            : new AzureManagedIdentitySecret();

    // A null value means "left untouched by the client", so the stored value must be kept: the form
    // strips unchanged write-only fields from the payload.
    if (request.azureEnvironment() != null) {
      AzureEnvironments.fromName(request.azureEnvironment());
      azureSecret.setAzureEnvironment(request.azureEnvironment());
    }

    // Left empty for a system-assigned identity, set to target a user-assigned one.
    if (request.azureClientId() != null) {
      azureSecret.setAzureClientId(request.azureClientId());
    }

    if (request.azureSubscriptionId() != null) {
      azureSecret.setAzureSubscriptionId(request.azureSubscriptionId());
    }

    if (azureSecret.getAzureEnvironment() == null) {
      throw new IllegalArgumentException("Azure environment is required");
    }

    return azureSecret;
  }

  @Override
  public SecretMetadata toMetadata(Secret secret) {
    if (secret instanceof AzureManagedIdentitySecret azureManagedIdentitySecret) {
      return SecretMetadata.forAzureManagedIdentity(
          azureManagedIdentitySecret.getAzureEnvironment(),
          azureManagedIdentitySecret.getAzureClientId());
    }
    throw new IllegalArgumentException(
        "Secret type mismatch: expected AZURE_MANAGED_IDENTITY secret");
  }
}
