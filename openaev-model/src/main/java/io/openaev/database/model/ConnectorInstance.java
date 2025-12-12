package io.openaev.database.model;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class ConnectorInstance {
  public enum CURRENT_STATUS_TYPE {
    started,
    stopped
  }

  public enum REQUESTED_STATUS_TYPE {
    starting,
    stopping
  }

  public enum SOURCE {
    PROPERTIES_MIGRATION,
    CATALOG_DEPLOYMENT,
    OTHER
  }

  public abstract CURRENT_STATUS_TYPE getCurrentStatus();

  public abstract void setCurrentStatus(CURRENT_STATUS_TYPE newStatus);

  public abstract REQUESTED_STATUS_TYPE getRequestedStatus();

  public abstract void setRequestedStatus(REQUESTED_STATUS_TYPE newStatus);

  public abstract Set<ConnectorInstanceConfiguration> getConfigurations();

  public abstract void setConfigurations(Set<ConnectorInstanceConfiguration> newConfigurations);
}
