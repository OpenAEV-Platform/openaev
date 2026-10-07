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
  private String tenantId;

  public abstract String getName();

  public abstract ConnectorType getType();

  public abstract String getUrl();

  public abstract String getApiUrl();

  public abstract String getId();

  public abstract String getToken();

  public abstract boolean shouldRegister();

  /**
   * Id of the technical OpenAEV user this connector authenticates as. The connectors of a tenant
   * share its OpenCTI token, hence one user, so they must agree on its identity.
   */
  @JsonIgnore
  public String getServiceAccountId() {
    return getId();
  }

  /** Display name of the technical OpenAEV user, see {@link #getServiceAccountId()}. */
  @JsonIgnore
  public String getServiceAccountName() {
    return getName();
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
