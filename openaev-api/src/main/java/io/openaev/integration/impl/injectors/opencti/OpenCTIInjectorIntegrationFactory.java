package io.openaev.integration.impl.injectors.opencti;

import static io.openaev.integration.impl.injectors.opencti.OpenCTIInjectorIntegration.OPENCTI_INJECTOR_NAME;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.opencti.OpenCTIContract;
import io.openaev.injectors.opencti.config.OpenCTIInjectorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.migration.OpenCTIInjectorConfigurationMigration;
import io.openaev.opencti.service.OpenCTIService;
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
public class OpenCTIInjectorIntegrationFactory extends IntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final OpenCTIContract openCTIContract;
  private final FileService fileService;
  private final CatalogConnectorService catalogConnectorService;
  private final InjectorContext injectorContext;
  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;
  private final OpenCTIInjectorConfigurationMigration openctiInjectorConfigurationMigration;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  public OpenCTIInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenCTIContract openCTIContract,
      CatalogConnectorService catalogConnectorService,
      FileService fileService,
      InjectorContext injectorContext,
      OpenCTIService openCTIService,
      InjectExpectationService injectExpectationService,
      OpenCTIInjectorConfigurationMigration openctiInjectorConfigurationMigration,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.openCTIContract = openCTIContract;
    this.fileService = fileService;
    this.catalogConnectorService = catalogConnectorService;
    this.openCTIService = openCTIService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.openctiInjectorConfigurationMigration = openctiInjectorConfigurationMigration;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    openctiInjectorConfigurationMigration.migrate();
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(openCTIContract.TYPE);
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-opencti.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(OPENCTI_INJECTOR_NAME);
    connector.setSlug(openCTIContract.TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
                        Allow OAEV to create report and case based on OpenCTI injector
                        """);
    connector.setShortDescription("Allow OAEV to create report and case based on OpenCTI injector");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("https://filigran.io/platforms/opencti/");
    connector.setContainerType(ConnectorType.INJECTOR);
    connector.setCatalogConnectorConfigurations(
        new OpenCTIInjectorConfig().toCatalogConfigurationSet(connector));
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new OpenCTIInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        injectorService,
        openCTIContract,
        injectorContext,
        openCTIService,
        injectExpectationService,
        baseIntegrationConfigurationBuilder);
  }
}
