package io.openaev.secrets.provider.impl.vault;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;
import io.openaev.secrets.provider.impl.vault.client.VaultClient;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.io.IOException;
import java.util.List;

public class VaultSecretsProvider extends SecretsProvider {
  private VaultClient client;
  private List<Engine> engines;

  public VaultSecretsProvider(VaultClient client, VaultSecretProviderConfig config) {
    super(config.getId(), config.getName());
    this.client = client;
  }

  @Override
  public SecretsProviderType getProviderType() {
    return SecretsProviderType.VAULT;
  }

  @Override
  public List<Credential> getSecrets() throws IOException {
    populateEngineList();
    return List.of();
  }

  private void populateEngineList() throws IOException {
    client.enabledEngines();
  }
}
