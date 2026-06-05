package io.openaev.secrets.provider.impl.vault;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.SecretProvider;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class VaultSecretProvider extends SecretProvider {
  private List<Engine> engines;

  @Override
  public List<Credential> getSecrets() {
    return List.of();
  }

  private void populateEngineList() {
    // call vault list
  }
}
