package io.openaev.utils.fixtures;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;

public class ConnectorInstanceFixture {
  public static ConnectorInstancePersisted createMigratedInstance() {
    ConnectorInstancePersisted connectorInstance = new ConnectorInstancePersisted();
    connectorInstance.setSource(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);
    return connectorInstance;
  }
}
