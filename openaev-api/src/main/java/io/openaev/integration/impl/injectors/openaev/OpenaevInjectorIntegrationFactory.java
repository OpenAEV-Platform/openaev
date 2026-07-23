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
import org.springframework.core.env.Environment;
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
  private final Environment env;

  public OpenaevInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenAEVImplantContract openAEVImplantContract,
      CatalogConnectorService catalogConnectorService,
      HttpClientFactory httpClientFactory,
      InjectorContext injectorContext,
      InjectExpectationService injectExpectationService,
      InjectService injectService,
      Environment env) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.openAEVImplantContract = openAEVImplantContract;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
    this.env = env;
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
        injectorService,
        openAEVImplantContract,
        injectorContext,
        injectExpectationService,
        injectService);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    String threshold = env.getProperty("inject.execution.threshold.minutes");
    if (threshold == null || threshold.isBlank()) {
      threshold = InjectsExecutionJob.DEFAULT_EXECUTION_THRESHOLD_TIME_IN_MINUTES;
    }
    int timeoutSeconds = Integer.parseInt(threshold) * 60;
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
