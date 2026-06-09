package io.openaev.secrets.provider.impl.vault.engine.aws;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.EngineType;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class AwsEngine extends Engine {
  @Override
  public EngineType getType() {
    return EngineType.AWS;
  }

  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
