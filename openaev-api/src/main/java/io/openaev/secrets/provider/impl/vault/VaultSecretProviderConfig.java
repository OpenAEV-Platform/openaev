package io.openaev.secrets.provider.impl.vault;

import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class VaultSecretProviderConfig extends BaseIntegrationConfiguration {
  @IntegrationConfigKey(
      key = "SECRETS_PROVIDER_NAME",
      description =
          """
            Name of the builtin Vault secrets provider
            """,
      isRequired = true)
  @NotBlank
  private String name = "HashiCorp Vault Secrets Provider";

  @IntegrationConfigKey(
      key = "SECRETS_PROVIDER_VAULT_URL",
      description =
          """
            URL to the HashiCorp Vault instance
            """,
      isRequired = true)
  @NotBlank
  private String url;

  @IntegrationConfigKey(
      key = "SECRETS_PROVIDER_VAULT_AUTH_TOKEN",
      description =
          """
            Token for authenticating to Vault
            """,
      isRequired = true)
  @NotBlank
  private String authToken;
}
