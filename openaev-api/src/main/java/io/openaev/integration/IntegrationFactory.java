package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;

public interface IntegrationFactory {
  Integration spawn(ConnectorInstance instance);
}
