package io.openaev.secrets.provider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.TenantIdBase;
import lombok.Getter;
import lombok.Setter;

public abstract class SecretsProvider extends BaseConnectorEntity
    implements SecretProvider, TenantIdBase {
  protected SecretsProvider() {}

  public static final String SERVICE_NAME = "secrets-provider";

  @JsonProperty("secrets_provider_id")
  @Getter
  @Setter
  private String id;

  @JsonProperty("secrets_provider_name")
  @Getter
  @Setter
  private String name;

  @JsonIgnore @Getter @Setter private String tenantId;

  protected SecretsProvider(String id, String name) {
    this.id = id;
    this.name = name;
    this.setType(ConnectorType.SECRETS_PROVIDER.name());
  }

  public abstract SecretsProviderType getProviderType();

  // -- SecretProvider default implementations  --

  @Override
  public SecretReference store(SecretReference secretReference, SecretStoreRequest request) {
    throw new UnsupportedOperationException(
        "This secret backend does not support storing secrets.");
  }

  @Override
  public SecretReference update(SecretReference secretReference, SecretStoreRequest request) {
    throw new UnsupportedOperationException(
        "This secret backend does not support updating secrets.");
  }

  @Override
  public void delete(SecretReference secretReference) {
    throw new UnsupportedOperationException(
        "This secret backend does not support deleting secrets.");
  }

  public static class Placeholder extends SecretsProvider {
    public Placeholder() {
      super();
    }

    @Override
    public SecretsProviderType getProviderType() {
      return SecretsProviderType.PLACEHOLDER;
    }
  }
}
