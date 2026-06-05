package io.openaev.secrets.provider.impl.vault.engine.aws;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.engine.Engine;
import java.util.List;

public class AwsEngine implements Engine {
  @Override
  public List<Credential> getCredentials() {
    return List.of();
  }
}
