package io.openaev.database.model;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectorInstanceInMemory extends ConnectorInstance {
  private CURRENT_STATUS_TYPE currentStatus;

  private REQUESTED_STATUS_TYPE requestedStatus;

  private Set<ConnectorInstanceConfiguration> configurations;

  private String className;
}
