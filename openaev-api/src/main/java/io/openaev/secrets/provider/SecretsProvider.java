package io.openaev.secrets.provider;

import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.secrets.model.Credential;
import lombok.Getter;

import java.util.List;

public abstract class SecretsProvider extends BaseConnectorEntity {
  @Getter
  private String id;

  @Getter
  private String name;

  public abstract SecretsProviderType getProviderType();

  public abstract List<Credential> getSecrets();

  public void storeSecret(Credential credential) {
    throw new UnsupportedOperationException(
        "This secret backend does not support storing secrets.");
  }
}
