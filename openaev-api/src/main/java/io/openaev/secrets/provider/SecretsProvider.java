package io.openaev.secrets.provider;

import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.secrets.model.Credential;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public abstract class SecretsProvider extends BaseConnectorEntity {
  @Getter @Setter private String id;

  @Getter @Setter private String name;

  protected SecretsProvider() { }

  protected SecretsProvider(String id, String name) {
    this.id = id;
    this.name = name;
  }

  public abstract SecretsProviderType getProviderType();

  public abstract List<Credential> getSecrets();

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
