package io.openaev.secrets.provider.impl.vault.engine;

import io.openaev.secrets.model.Credential;
import java.util.List;

public class KeyValueEngine implements Engine {
  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
