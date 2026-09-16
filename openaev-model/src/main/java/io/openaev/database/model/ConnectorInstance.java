package io.openaev.database.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Optional;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
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

  @EqualsAndHashCode.Include
  public abstract String getId();

  public abstract void setId(String newId);

  public abstract CURRENT_STATUS_TYPE getCurrentStatus();

  public abstract void setCurrentStatus(CURRENT_STATUS_TYPE newStatus);

  public abstract REQUESTED_STATUS_TYPE getRequestedStatus();

  public abstract void setRequestedStatus(REQUESTED_STATUS_TYPE newStatus);

  public abstract Set<ConnectorInstanceConfiguration> getConfigurations();

  public abstract void setConfigurations(Set<ConnectorInstanceConfiguration> newConfigurations);

  public abstract String getClassName();

  public abstract String getHashIdentity();

  public Optional<String> configurationValue(String key) {
    Set<ConnectorInstanceConfiguration> configurations = getConfigurations();
    if (configurations == null) {
      return Optional.empty();
    }
    return configurations.stream()
        .filter(configuration -> key.equals(configuration.getKey()))
        .map(ConnectorInstanceConfiguration::getValue)
        .filter(value -> value != null && value.isTextual())
        .map(JsonNode::asText)
        .findFirst();
  }
}
