package io.openaev.integration;

import io.openaev.database.model.ConnectorInstance;
import java.util.List;

public interface IntegrationFactory {
  List<Integration> initialise();

  Integration spawn(ConnectorInstance instance);
}
