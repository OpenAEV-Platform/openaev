package io.openaev.utils.fixtures;

import io.openaev.database.model.CatalogConnector;

public class CatalogConnectorFixture {
  public static CatalogConnector createCatalogConnectorWithClassName(String className) {
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(className);
    connector.setSlug(className);
    connector.setClassName(className);
    return connector;
  }
}
