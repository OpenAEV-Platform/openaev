package io.openaev.opencti.connectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public abstract class ConnectorBase {
  private List<String> scope = new ArrayList<>();
  private boolean auto = false;
  private boolean autoUpdate = false;
  private boolean onlyContextual = false;
  private boolean playbookCompatible = false;
  private String listenCallbackURI;
  private volatile String jwks;

  public abstract String getName();

  public abstract ConnectorType getType();

  public abstract String getUrl();

  public abstract String getApiUrl();

  public abstract String getId();

  public abstract String getToken();

  public abstract boolean shouldRegister();

  @JsonIgnore private boolean registered = false;

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }

    if (obj.getClass() != this.getClass()) {
      return false;
    }

    return this.getId().equals(((ConnectorBase) obj).getId());
  }
}
