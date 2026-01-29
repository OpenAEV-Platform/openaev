package io.openaev.integration.local_fixtures.regular;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.service.connector_instances.EncryptionService;
import java.util.List;

public class TestIntegrationFactory extends IntegrationFactory {
  private final FileService fileService;
  private final CatalogConnectorService catalogConnectorService;
  private final TestIntegrationConfigurationMigration testIntegrationConfigurationMigration;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final EncryptionFactory encryptionFactory;

  public TestIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      FileService fileService,
      TestIntegrationConfigurationMigration testIntegrationConfigurationMigration,
      ComponentRequestEngine componentRequestEngine,
      HttpClientFactory httpClientFactory,
      EncryptionFactory encryptionFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.fileService = fileService;
    this.catalogConnectorService = catalogConnectorService;
    this.testIntegrationConfigurationMigration = testIntegrationConfigurationMigration;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.encryptionFactory = encryptionFactory;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    testIntegrationConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(getClassName());
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-default.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Test Integration");
    connector.setSlug(getClassName());
    connector.setLogoUrl(logoFilename);
    connector.setDescription("This is a test integration.");
    connector.setShortDescription("Test integration.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://testintegration.example");
    connector.setContainerType(ConnectorType.EXECUTOR);
    connector.setCatalogConnectorConfigurations(
        new TestIntegrationConfiguration().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance) {
    EncryptionService encryptionService = null;
    if (instance instanceof ConnectorInstancePersisted) {
      encryptionService =
          encryptionFactory.getEncryptionService(
              ((ConnectorInstancePersisted) instance).getCatalogConnector());
    }
    return new TestIntegration(
        componentRequestEngine, instance, connectorInstanceService, encryptionService);
  }
}
