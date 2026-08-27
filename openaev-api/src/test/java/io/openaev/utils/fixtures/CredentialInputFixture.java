package io.openaev.utils.fixtures;

import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_ENVIRONMENT;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_SUBSCRIPTION_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_TENANT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_REFRESH_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_SCOPE;

import io.openaev.api.credentials.form.CredentialInput;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_TYPE;
import java.util.List;

/**
 * Fixtures for {@link CredentialInput} payloads.
 *
 * <p>{@link CredentialInput} is a record with one component per provider field, so building one by
 * hand means aligning more than twenty positional arguments. These helpers keep that noise out of
 * the tests.
 */
public class CredentialInputFixture {

  private CredentialInputFixture() {}

  /** A complete, valid AZURE_SERVICE_PRINCIPAL input. */
  public static CredentialInput azureServicePrincipalInput(String name) {
    return azureInput(
        name,
        CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL,
        AZURE_ENVIRONMENT,
        AZURE_CLIENT_ID,
        AZURE_CLIENT_SECRET,
        AZURE_TENANT_ID,
        AZURE_SUBSCRIPTION_ID);
  }

  /** An AZURE_MANAGED_IDENTITY input relying on the system-assigned identity. */
  public static CredentialInput azureSystemAssignedManagedIdentityInput(String name) {
    return azureInput(
        name,
        CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY,
        AZURE_ENVIRONMENT,
        null,
        null,
        null,
        null);
  }

  /** An AZURE_MANAGED_IDENTITY input targeting a user-assigned identity. */
  public static CredentialInput azureUserAssignedManagedIdentityInput(String name) {
    return azureInput(
        name,
        CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY,
        AZURE_ENVIRONMENT,
        AZURE_CLIENT_ID,
        null,
        null,
        AZURE_SUBSCRIPTION_ID);
  }

  public static CredentialInput azureInput(
      String name,
      CREDENTIAL_AUTH_METHOD authMethod,
      String azureEnvironment,
      String azureClientId,
      String azureClientSecret,
      String azureTenantId,
      String azureSubscriptionId) {
    return new CredentialInput(
        name,
        CREDENTIAL_TYPE.CLOUD_AZURE,
        authMethod,
        "description-" + name,
        // IDENTITY
        null,
        null,
        null,
        null,
        // AWS
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        // AZURE
        azureEnvironment,
        azureClientId,
        azureClientSecret,
        azureTenantId,
        azureSubscriptionId,
        // GCP
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  /** A complete, valid GCP_SERVICE_ACCOUNT input — the key file travels as its own part. */
  public static CredentialInput gcpServiceAccountInput(String name) {
    return gcpInput(
        name,
        CREDENTIAL_AUTH_METHOD.GCP_SERVICE_ACCOUNT,
        GCP_SCOPE,
        GCP_PROJECT_ID,
        null,
        null,
        null);
  }

  /** A complete, valid GCP_OAUTH2 input. */
  public static CredentialInput gcpOAuth2Input(String name) {
    return gcpInput(
        name,
        CREDENTIAL_AUTH_METHOD.GCP_OAUTH2,
        GCP_SCOPE,
        GCP_PROJECT_ID,
        GCP_OAUTH_CLIENT_ID,
        GCP_OAUTH_CLIENT_SECRET,
        GCP_OAUTH_REFRESH_TOKEN);
  }

  public static CredentialInput gcpInput(
      String name,
      CREDENTIAL_AUTH_METHOD authMethod,
      String gcpScope,
      String gcpProjectId,
      String gcpOauthClientId,
      String gcpOauthClientSecret,
      String gcpOauthRefreshToken) {
    return new CredentialInput(
        name,
        CREDENTIAL_TYPE.CLOUD_GCP,
        authMethod,
        "description-" + name,
        // IDENTITY
        null,
        null,
        null,
        null,
        // AWS
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        // AZURE
        null,
        null,
        null,
        null,
        null,
        // GCP
        gcpScope,
        gcpProjectId,
        gcpOauthClientId,
        gcpOauthClientSecret,
        gcpOauthRefreshToken,
        List.of());
  }
}
