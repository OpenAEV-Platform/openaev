package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Id;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class ConnectorInstance {

  @Id
  @JsonProperty("connector_instance_id")
  private String id;

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

  @EqualsAndHashCode.Include
  public abstract String getId();

  public abstract CURRENT_STATUS_TYPE getCurrentStatus();

  public abstract void setCurrentStatus(CURRENT_STATUS_TYPE newStatus);

  public abstract REQUESTED_STATUS_TYPE getRequestedStatus();

  public abstract void setRequestedStatus(REQUESTED_STATUS_TYPE newStatus);

  public abstract Set<ConnectorInstanceConfiguration> getConfigurations();

  public abstract void setConfigurations(Set<ConnectorInstanceConfiguration> newConfigurations);

  public abstract String getClassName();

  public abstract String getHashIdentity();
}
