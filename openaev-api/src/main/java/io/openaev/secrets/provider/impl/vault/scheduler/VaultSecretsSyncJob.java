package io.openaev.secrets.provider.impl.vault.scheduler;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.VaultSecretsProvider;
import java.util.List;

public class VaultSecretsSyncJob implements Runnable {
  private final VaultSecretsProvider vaultSecretsProvider;

  public VaultSecretsSyncJob(VaultSecretsProvider provider) {
    this.vaultSecretsProvider = provider;
  }

  @Override
  public void run() {
    List<Credential> creds = vaultSecretsProvider.getSecrets();
    // persist
  }
}
