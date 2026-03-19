package io.openaev.integration.impl.injectors.manual;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.injectors.manual.ManualExecutor;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class ManualInjectorIntegration extends IntegrationInMemory {
  private static final String MANUAL_INJECTOR_NAME = "Manual";
  public static final String MANUAL_INJECTOR_ID = "6981a39d-e219-4016-a235-cf7747994abc";

  private final ManualContract manualContract;
  private final InjectorContext injectorContext;

  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;

  @QualifiedComponent(identifier = {ManualContract.TYPE, MANUAL_INJECTOR_ID})
  private ManualExecutor manualExecutor;

  public ManualInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ManualContract manualContract,
      InjectorContext injectorContext,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.manualContract = manualContract;
    this.injectorContext = injectorContext;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.registerBuiltinInjector(
        MANUAL_INJECTOR_ID,
        MANUAL_INJECTOR_NAME,
        manualContract,
        true,
        "generic",
        null,
        null,
        false,
        List.of());
    this.manualExecutor = new ManualExecutor(injectorContext, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
