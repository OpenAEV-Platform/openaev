package io.openaev.secrets.provider.impl.validators;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;

/**
 * Builds the Azure SDK credential objects used to probe a stored credential.
 *
 * <p>This interface exists purely as a seam. {@code ClientSecretCredentialBuilder} and {@code
 * ManagedIdentityCredentialBuilder} are final builders producing final types that reach the network
 * on first use, so a validator calling them directly cannot be unit tested. Injecting this factory
 * lets {@code AzureCredentialValidator} be tested against a stub for every branch — success,
 * rejection, timeout, throttling — without an Azure tenant.
 */
public interface AzureCredentialConnectivityCheckFactory {

  /**
   * Builds a credential for an application (service principal) authenticating with a client secret.
   *
   * @param environment the target Azure cloud
   * @param tenantId the Entra ID tenant hosting the application
   * @param clientId the application id
   * @param clientSecret the decrypted client secret
   * @return a credential able to request tokens for that application
   */
  TokenCredential forServicePrincipal(
      AzureEnvironment environment, String tenantId, String clientId, String clientSecret);

  /**
   * Builds a credential for a managed identity.
   *
   * <p>Only usable from inside Azure: the credential resolves through IMDS, which is unreachable
   * from anywhere else. A validator must therefore treat a failure here as inconclusive, never as a
   * rejection.
   *
   * @param clientId the user-assigned identity's client id, or null for the system-assigned one
   * @return a credential able to request tokens for that identity
   */
  TokenCredential forManagedIdentity(String clientId);
}
