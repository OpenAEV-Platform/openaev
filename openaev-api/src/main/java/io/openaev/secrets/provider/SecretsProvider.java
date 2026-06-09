package io.openaev.secrets.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.model.ConnectorType;
import io.openaev.secrets.model.Credential;
import java.io.IOException;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public abstract class SecretsProvider extends BaseConnectorEntity {
  protected SecretsProvider() {}

  @JsonProperty("secrets_provider_id")
  @Getter
  @Setter
  private String id;

  @JsonProperty("secrets_provider_name")
  @Getter
  @Setter
  private String name;

  protected SecretsProvider(String id, String name) {
    this.id = id;
    this.name = name;
    this.setType(ConnectorType.SECRETS_PROVIDER.name());
  }

  public abstract SecretsProviderType getProviderType();

  public abstract List<Credential> getSecrets() throws IOException;

  public void storeSecret(Credential credential) {
    throw new UnsupportedOperationException(
        "This secret backend does not support storing secrets.");
  }

  public static class Placeholder extends SecretsProvider {
    public Placeholder() {
      super();
    }

    @Override
    public SecretsProviderType getProviderType() {
      return SecretsProviderType.PLACEHOLDER;
    }

    @Override
    public List<Credential> getSecrets() {
      return List.of();
    }
  }
}
