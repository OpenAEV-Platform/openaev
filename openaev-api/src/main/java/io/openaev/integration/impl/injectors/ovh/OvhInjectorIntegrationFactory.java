package io.openaev.integration.impl.injectors.ovh;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.injectors.ovh.config.OvhSmsInjectorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.migration.OvhInjectorConfigurationMigration;
import io.openaev.service.FileService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class OvhInjectorIntegrationFactory extends IntegrationFactory {

  private final OvhSmsContract ovhSmsContract;
  private final InjectorContext injectorContext;
  private final OvhInjectorConfigurationMigration ovhInjectorConfigurationMigration;

  private final CatalogConnectorService catalogConnectorService;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final FileService fileService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  private final ComponentRequestEngine componentRequestEngine;

  public OvhInjectorIntegrationFactory(
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ComponentRequestEngine componentRequestEngine,
      OvhSmsContract ovhSmsContract,
      InjectorContext injectorContext,
      OvhInjectorConfigurationMigration ovhInjectorConfigurationMigration,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      FileService fileService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.connectorInstanceService = connectorInstanceService;
    this.componentRequestEngine = componentRequestEngine;
    this.ovhSmsContract = ovhSmsContract;
    this.injectorContext = injectorContext;
    this.ovhInjectorConfigurationMigration = ovhInjectorConfigurationMigration;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.catalogConnectorService = catalogConnectorService;
    this.fileService = fileService;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return OvhInjectorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    ovhInjectorConfigurationMigration.migrate();
  }

  private String getLogoFilename() {
    return "%s-logo.png".formatted(ovhSmsContract.getType());
  }

  @Override
  protected void ensureCatalogLogo() throws Exception {
    ensureCatalogLogo(getLogoFilename());
  }

  private void ensureCatalogLogo(String logoFilename) throws Exception {
    fileService.uploadCatalogLogo(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-ovh-sms.png"));
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = getLogoFilename();
    ensureCatalogLogo(logoFilename);
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("OVHCloud SMS Platform");
    connector.setSlug(ovhSmsContract.getType());
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        "Send SMS messages through the OVHcloud SMS service directly from OpenAEV injects, to"
            + " drive realistic notification and crisis-communication steps in table-top"
            + " exercises. This injector is built into the platform.");
    connector.setShortDescription("Send SMS via OVHcloud for table-top exercises.");
    connector.setClassName(getClassName());
    connector.setContainerType(ConnectorType.INJECTOR);
    connector.setCatalogConnectorConfigurations(
        new OvhSmsInjectorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new OvhInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        ovhSmsContract,
        injectorContext,
        injectorService,
        injectExpectationService,
        baseIntegrationConfigurationBuilder);
  }
}
