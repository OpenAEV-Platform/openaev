package io.openaev.secrets.provider.impl.local;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.SecretsProviderType;

import java.util.List;

public class LocalSecretsProvider extends SecretsProvider {
  @Override
  public SecretsProviderType getProviderType() {
    return SecretsProviderType.LOCAL;
  }

  @Override
  public List<Credential> getSecrets() {
    return List.of();
  }
}
