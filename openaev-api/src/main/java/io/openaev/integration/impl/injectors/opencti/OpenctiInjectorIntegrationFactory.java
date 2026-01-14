package io.openaev.integration.impl.injectors.opencti;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.opencti.OpenCTIContract;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integrations.InjectorService;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.service.FileService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class OpenctiInjectorIntegrationFactory extends IntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final OpenCTIContract openCTIContract;
  private final FileService fileService;
  private final CatalogConnectorService catalogConnectorService;
  private final InjectorContext injectorContext;
  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;

  public OpenctiInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenCTIContract openCTIContract,
      CatalogConnectorService catalogConnectorService,
      FileService fileService,
      InjectorContext injectorContext,
      OpenCTIService openCTIService,
      InjectExpectationService injectExpectationService) {
    super(connectorInstanceService, catalogConnectorService);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.openCTIContract = openCTIContract;
    this.fileService = fileService;
    this.catalogConnectorService = catalogConnectorService;
    this.openCTIService = openCTIService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    String logoFilename = "%s-logo.png".formatted(getClassName());
    fileService.uploadStream(
        FileService.CONNECTORS_LOGO_PATH,
        logoFilename,
        getClass().getResourceAsStream("/img/icon-opencti.png"));
    CatalogConnector connector = new CatalogConnector();
    connector.setTitle("OpenCTI");
    connector.setSlug(openCTIContract.TYPE);
    connector.setLogoUrl(logoFilename);
    connector.setDescription(
        """
                        Description opencti
                        """);
    connector.setShortDescription("short description opencti");
    connector.setClassName(getClassName());
    connector.setSubscriptionLink("");
    connector.setContainerType(ConnectorType.INJECTOR);
    catalogConnectorService.saveAll(List.of(connector));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new OpenctiInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        injectorService,
        openCTIContract,
        injectorContext,
        openCTIService,
        injectExpectationService);
  }
}
