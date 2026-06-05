package io.openaev.secrets.provider.impl.local;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.SecretProvider;
import java.util.List;

public class LocalSecretProvider extends SecretProvider {
  @Override
  public List<Credential> getSecrets() {
    return List.of();
  }
}
