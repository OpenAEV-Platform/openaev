package io.openaev.integration.impl.injectors.prowler;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.injectors.prowler.ProwlerContract;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Registers the Prowler injector catalog placeholder for every tenant. See {@link ProwlerContract}
 * for why this injector has no executable contracts yet.
 */
@Service
public class ProwlerInjectorIntegrationFactory extends BuiltinIntegrationFactory {
  private final ProwlerContract prowlerContract;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final ComponentRequestEngine componentRequestEngine;

  public ProwlerInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ProwlerContract prowlerContract,
      InjectorService injectorService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.prowlerContract = prowlerContract;
    this.injectorService = injectorService;
  }

  @Override
  protected final String getClassName() {
    return ProwlerInjectorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances(String tenantId) {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            ProwlerInjectorIntegration.PROWLER_INJECTOR_ID,
            this.getClassName(),
            ConnectorType.INJECTOR));
  }

  @Override
  public Integration spawn(ConnectorInstance instance)
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    return new ProwlerInjectorIntegration(
        componentRequestEngine, instance, connectorInstanceService);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    injectorService.registerBuiltinInjector(
        tenantId,
        ProwlerInjectorIntegration.PROWLER_INJECTOR_ID,
        ProwlerInjectorIntegration.PROWLER_INJECTOR_NAME,
        prowlerContract,
        false,
        "misconfiguration_scanner",
        null,
        null,
        false,
        List.of());
  }
}
