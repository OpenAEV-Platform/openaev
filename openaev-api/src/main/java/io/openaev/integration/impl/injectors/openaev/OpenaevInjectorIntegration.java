package io.openaev.integration.impl.injectors.openaev;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.injectors.openaev.OpenAEVImplantExecutor;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class OpenaevInjectorIntegration extends IntegrationInMemory {
  public static final String OPENAEV_INJECTOR_NAME = "OpenAEV Implant";
  public static final String OPENAEV_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0144";

  private final InjectorContext injectorContext;
  private final InjectExpectationService injectExpectationService;
  private final InjectService injectService;

  @QualifiedComponent(identifier = {OpenAEVImplantContract.TYPE, OPENAEV_INJECTOR_ID})
  private OpenAEVImplantExecutor openAEVImplantExecutor;

  public OpenaevInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      InjectorContext injectorContext,
      InjectExpectationService injectExpectationService,
      InjectService injectService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.injectService = injectService;
  }

  @Override
  protected void innerStart() throws Exception {
    this.openAEVImplantExecutor =
        new OpenAEVImplantExecutor(injectorContext, injectExpectationService, injectService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
