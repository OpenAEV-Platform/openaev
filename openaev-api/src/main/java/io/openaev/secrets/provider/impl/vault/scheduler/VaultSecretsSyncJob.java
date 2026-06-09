package io.openaev.secrets.provider.impl.vault.scheduler;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.VaultSecretsProvider;
import java.io.IOException;
import java.util.List;

public class VaultSecretsSyncJob implements Runnable {
  private final VaultSecretsProvider vaultSecretsProvider;

  public VaultSecretsSyncJob(VaultSecretsProvider provider) {
    this.vaultSecretsProvider = provider;
  }

  @Override
  public void run() {
    try {
      List<Credential> creds = vaultSecretsProvider.getSecrets();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    // persist
  }
}
