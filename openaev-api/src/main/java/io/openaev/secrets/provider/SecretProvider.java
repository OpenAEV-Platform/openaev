package io.openaev.secrets.provider;

import io.openaev.secrets.model.Credential;
import java.util.List;

public abstract class SecretProvider {
  public abstract List<Credential> getSecrets();

  public void storeSecret(Credential credential) {
    throw new UnsupportedOperationException(
        "This secret backend does not support storing secrets.");
  }
}
