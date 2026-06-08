package io.openaev.secrets.provider.impl.vault;

import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.integration.configuration.IntegrationConfigKey;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Data
public class VaultSecretProviderConfig extends BaseIntegrationConfiguration {
  @IntegrationConfigKey(
      key = "SECRETS_PROVIDER_ID",
      description =
          """
          Name of the builtin Vault secrets provider
          """,
      isRequired = true)
  @NotBlank
  private String id = UUID.randomUUID().toString();

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
      isRequired = true,
      valueFormat = CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_FORMAT.PASSWORD)
  @NotBlank
  private String authToken;

  @IntegrationConfigKey(
      key = "SECRETS_PROVIDER_VAULT_SYNC_INTERVAL",
      jsonType = CatalogConnectorConfiguration.CONNECTOR_CONFIGURATION_TYPE.INTEGER,
      description =
          """
                  Secrets refresh interval (in seconds)
                  """)
  @Getter
  @NotBlank
  private Integer secretsRefreshInterval = 1800;
}
