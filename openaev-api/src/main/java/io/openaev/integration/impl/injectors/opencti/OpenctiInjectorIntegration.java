package io.openaev.integration.impl.injectors.opencti;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.opencti.OpenCTIContract;
import io.openaev.injectors.opencti.OpenCTIExecutor;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integrations.InjectorService;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.*;

public class OpenctiInjectorIntegration extends Integration {
  public static final String OPENCTI_INJECTOR_NAME = "OpenCTI";
  public static final String OPENCTI_INJECTOR_ID = "2cbc77af-67f2-46af-bfd2-755d06a46da0";

  private final InjectorService injectorService;
  private final OpenCTIContract openCTIContract;
  private final InjectorContext injectorContext;
  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;

  @QualifiedComponent(identifier = OpenCTIContract.TYPE)
  private OpenCTIExecutor openCTIExecutor;

  public OpenctiInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenCTIContract openCTIContract,
      InjectorContext injectorContext,
      OpenCTIService openCTIService,
      InjectExpectationService injectExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.openCTIContract = openCTIContract;
    this.openCTIService = openCTIService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
  }

  @Override
  protected void innerStart() throws Exception {

    String injectorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            connectorInstance.getId(), "INJECTOR_ID");

    injectorService.register(
        injectorId,
        OPENCTI_INJECTOR_NAME,
        openCTIContract,
        true,
        "incident-response",
        null,
        null,
        false,
        new ArrayList<>());
    this.openCTIExecutor =
        new OpenCTIExecutor(injectorContext, openCTIService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
