package io.openaev.secrets.provider.impl.vault;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class VaultSecretsProvider extends SecretsProvider {
  private List<Engine> engines;

  @Override
  public SecretsProviderType getProviderType() {
    return SecretsProviderType.VAULT;
  }

  @Override
  public List<Credential> getSecrets() {
    return List.of();
  }

  private void populateEngineList() {
    // call vault list
  }
}
