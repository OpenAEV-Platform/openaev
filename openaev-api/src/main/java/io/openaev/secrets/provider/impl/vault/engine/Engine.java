package io.openaev.secrets.provider.impl.vault.engine;

import io.openaev.secrets.model.Credential;
import java.util.List;

public interface Engine {
  List<Credential> getCredentials();
}
