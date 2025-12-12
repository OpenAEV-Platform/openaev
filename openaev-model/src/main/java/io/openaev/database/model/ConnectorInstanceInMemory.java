package io.openaev.database.model;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;

public class ConnectorInstanceInMemory extends ConnectorInstance {
  @Getter @Setter private CURRENT_STATUS_TYPE currentStatus;

  @Getter @Setter private REQUESTED_STATUS_TYPE requestedStatus;

  @Getter @Setter private Set<ConnectorInstanceConfiguration> configurations;
}
