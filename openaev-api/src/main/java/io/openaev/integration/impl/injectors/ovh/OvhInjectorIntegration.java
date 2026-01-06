package io.openaev.integration.impl.injectors.ovh;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.injectors.ovh.OvhSmsExecutor;
import io.openaev.injectors.ovh.service.OvhSmsService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integrations.InjectorService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class OvhInjectorIntegration extends Integration {
  private static final String OVH_SMS_INJECTOR_NAME = "OVHCloud SMS Platform";
  private static final String OVH_SMS_INJECTOR_ID = "e5aefbca-cf8f-4a57-9384-0503a8ffc22f";

  private final OvhSmsContract ovhSmsContract;
  private final InjectorContext injectorContext;

  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final OvhSmsService smsService;

  @QualifiedComponent(identifier = OvhSmsContract.TYPE)
  private OvhSmsExecutor ovhSmsExecutor;

  public OvhInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      OvhSmsContract ovhSmsContract,
      InjectorContext injectorContext,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      OvhSmsService smsService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.ovhSmsContract = ovhSmsContract;
    this.injectorContext = injectorContext;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.smsService = smsService;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.register(
        OVH_SMS_INJECTOR_ID,
        OVH_SMS_INJECTOR_NAME,
        ovhSmsContract,
        true,
        "communication",
        null,
        null,
        false,
        List.of());
    this.ovhSmsExecutor = new OvhSmsExecutor(injectorContext, smsService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
