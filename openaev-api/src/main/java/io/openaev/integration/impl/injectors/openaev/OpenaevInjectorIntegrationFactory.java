package io.openaev.integration.impl.injectors.openaev;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OpenaevInjectorIntegrationFactory extends BuiltinIntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final OpenAEVImplantContract openAEVImplantContract;
  private final OpenAEVConfig openAEVConfig;
  private final InjectorContext injectorContext;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  public OpenaevInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenAEVImplantContract openAEVImplantContract,
      OpenAEVConfig openAEVConfig,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      InjectorContext injectorContext,
      AssetGroupService assetGroupService,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.openAEVImplantContract = openAEVImplantContract;
    this.openAEVConfig = openAEVConfig;
    this.injectorContext = injectorContext;
    this.assetGroupService = assetGroupService;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  protected final String getClassName() {
    return OpenaevInjectorIntegrationFactory.class.getCanonicalName();
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
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID,
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
    return new OpenaevInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        injectorService,
        openAEVImplantContract,
        openAEVConfig,
        injectorContext,
        assetGroupService,
        injectExpectationService,
        injectService);
  }

  @Override
  public void registerConnectorForTenant() throws Exception {
    try {
      injectorService.injector(OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID);
    } catch (ElementNotFoundException e) {
      Map<String, String> executorCommands =
          OpenaevImplantCommandBuilder.buildExecutorCommands(openAEVConfig);
      Map<String, String> executorClearCommands =
          OpenaevImplantCommandBuilder.buildExecutorClearCommands();

      injectorService.registerBuiltinInjector(
          OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID,
          OpenaevInjectorIntegration.OPENAEV_INJECTOR_NAME,
          openAEVImplantContract,
          false,
          "simulation-implant",
          executorCommands,
          executorClearCommands,
          true,
          List.of());
    }
  }
}
