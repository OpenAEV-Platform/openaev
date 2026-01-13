package io.openaev.integration.local_fixtures;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestIntegrationFactoryInitThrows extends IntegrationFactory {
  private final FileService fileService;
  private final CatalogConnectorService catalogConnectorService;
  private final TestIntegrationConfigurationMigration testIntegrationConfigurationMigration;
  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  @Autowired private EncryptionFactory encryptionFactory;

  public TestIntegrationFactoryInitThrows(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      FileService fileService,
      TestIntegrationConfigurationMigration testIntegrationConfigurationMigration,
      ComponentRequestEngine componentRequestEngine,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.fileService = fileService;
    this.catalogConnectorService = catalogConnectorService;
    this.testIntegrationConfigurationMigration = testIntegrationConfigurationMigration;
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    throw new RuntimeException("%s: deliberate throw!".formatted(this.getClassName()));
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(getClassName());
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-default.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("Test Integration Init Throws");
    connector.setSlug(getClassName());
    connector.setLogoUrl(logoFilename);
    connector.setDescription("This is a test integration which throws during init.");
    connector.setShortDescription("Test integration init throws.");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://testintegration_init_throws.example");
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
