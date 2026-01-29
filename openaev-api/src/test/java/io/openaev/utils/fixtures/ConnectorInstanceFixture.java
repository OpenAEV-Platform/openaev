package io.openaev.utils.fixtures;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ConnectorInstancePersisted;
import java.util.HashSet;

public class ConnectorInstanceFixture {
  public static ConnectorInstancePersisted createMigratedInstance() {
    ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
    connectorInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    connectorInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstance.setSource(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);
    return connectorInstance;
  }

  public static ConnectorInstancePersisted createDefaultConnectorInstance() {
    ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
    connectorInstance.setSource(ConnectorInstance.SOURCE.CATALOG_DEPLOYMENT);
    connectorInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    connectorInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstance.setConfigurations(new HashSet<>());
    return connectorInstance;
  }

  public static ConnectorInstanceConfiguration createConnectorInstanceConfiguration(
      String key, String value) throws JsonProcessingException {
    ConnectorInstanceConfiguration connectorInstanceConfiguration =
        new ConnectorInstanceConfiguration();
    connectorInstanceConfiguration.setKey(key);
    ObjectMapper mapper = new ObjectMapper();
    String jsonValue = mapper.writeValueAsString(value);
    connectorInstanceConfiguration.setValue(mapper.readTree(jsonValue));
    return connectorInstanceConfiguration;
  }

  public static ConnectorInstanceConfiguration createConnectorInstanceSecretConfiguration(
      String key, String value) throws JsonProcessingException {
    ConnectorInstanceConfiguration connectorInstanceConfiguration =
        new ConnectorInstanceConfiguration();
    connectorInstanceConfiguration.setKey(key);
    connectorInstanceConfiguration.setEncrypted(true);
    ObjectMapper mapper = new ObjectMapper();
    String jsonValue = mapper.writeValueAsString(value);
    connectorInstanceConfiguration.setValue(mapper.readTree(jsonValue));
    return connectorInstanceConfiguration;
  }

  public static ConnectorInstanceConfiguration createDefaultConnectorInstanceConfiguration()
      throws JsonProcessingException {
    return createConnectorInstanceConfiguration("default-key", "default-value");
  }

  public static ConnectorInstanceLog createConnectorInstanceLog(String log) {
    ConnectorInstanceLog connectorInstanceLog = new ConnectorInstanceLog();
    connectorInstanceLog.setLog(log);
    return connectorInstanceLog;
  }
}
