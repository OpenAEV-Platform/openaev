package io.openaev.secrets.provider.impl.vault.engine.gcp;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.EngineType;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class GcpEngine extends Engine {
  @Override
  public EngineType getType() {
    return EngineType.GCP;
  }

  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
