package io.openaev.database.model;

import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConnectorInstanceInMemory extends ConnectorInstance {
  private CURRENT_STATUS_TYPE currentStatus;

  private REQUESTED_STATUS_TYPE requestedStatus;

  private Set<ConnectorInstanceConfiguration> configurations = new HashSet<>();

  private String className;

  private String id;

  @Override
  public String getHashIdentity() {
    if (className == null) {
      return "UNKNOWN";
    }
    return String.format("BUILTIN[%s]", className);
  }
}
