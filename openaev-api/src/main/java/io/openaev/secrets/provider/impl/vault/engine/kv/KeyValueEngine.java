package io.openaev.secrets.provider.impl.vault.engine.kv;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.EngineType;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class KeyValueEngine extends Engine {
  @Override
  public EngineType getType() {
    return EngineType.KV;
  }

  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
