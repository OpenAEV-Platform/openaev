package io.openaev.integration.impl.injectors.openaev;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.scheduler.jobs.InjectsExecutionJob;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OpenaevInjectorIntegrationFactory extends BuiltinIntegrationFactory {

  private final ComponentRequestEngine componentRequestEngine;
  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final OpenAEVImplantContract openAEVImplantContract;
  private final InjectorContext injectorContext;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  @Value(
      "${inject.execution.threshold.minutes:"
          + InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES
          + "}")
  private Integer injectExecutionThresholdMinutes;

  public OpenaevInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenAEVImplantContract openAEVImplantContract,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      InjectorContext injectorContext,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.openAEVImplantContract = openAEVImplantContract;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  protected final String getClassName() {
    return OpenaevInjectorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
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
        injectorContext,
        injectExpectationService,
        injectService);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    int timeoutSeconds = injectExecutionThresholdMinutes * 60;
    Map<String, String> executorCommands =
        OpenaevImplantCommandBuilder.buildExecutorCommands(timeoutSeconds);
    Map<String, String> executorClearCommands =
        OpenaevImplantCommandBuilder.buildExecutorClearCommands();
    injectorService.registerBuiltinInjector(
        tenantId,
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
