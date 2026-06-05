package io.openaev.secrets.provider.impl.vault.engine.kv;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.engine.Engine;

import java.util.List;

public class KeyValueEngine implements Engine {
  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
