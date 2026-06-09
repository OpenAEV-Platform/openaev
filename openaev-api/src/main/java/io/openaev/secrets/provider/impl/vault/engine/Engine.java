package io.openaev.secrets.provider.impl.vault.engine;

import io.openaev.secrets.model.Credential;
import io.openaev.secrets.provider.impl.vault.EngineType;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public abstract class Engine {
  @Getter @Setter private String mountPoint;

  public abstract EngineType getType();

  public abstract List<Credential> getCredentials();
}
