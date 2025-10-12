package io.openaev.opencti.connectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public abstract class ConnectorBase {
  private String url;
  private String authToken;
  private String id;
  private List<String> scope = new ArrayList<>();
  private boolean auto = false;
  private boolean autoUpdate = false;
  private boolean onlyContextual = false;
  private boolean playbookCompatible = false;
  private String listenCallbackURI;

  public abstract String getName();

  public abstract ConnectorType getType();

  public boolean shouldRegister() {
    return !StringUtils.isBlank(this.getUrl())
        && !StringUtils.isBlank(this.getAuthToken())
        && !StringUtils.isBlank(this.getId())
        && !StringUtils.isBlank(this.getListenCallbackURI())
        && !StringUtils.isBlank(this.getName())
        && this.getType() != null;
  }

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
