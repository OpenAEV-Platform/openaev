package io.openaev.integration.impl.injectors.ovh;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.injectors.ovh.OvhSmsExecutor;
import io.openaev.injectors.ovh.config.OvhSmsInjectorConfig;
import io.openaev.injectors.ovh.service.OvhSmsService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integrations.InjectorService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class OvhInjectorIntegration extends Integration {
  public static final String OVH_SMS_INJECTOR_NAME = "OVHCloud SMS Platform";
  public static final String OVH_SMS_INJECTOR_ID = "e5aefbca-cf8f-4a57-9384-0503a8ffc22f";

  private final OvhSmsContract ovhSmsContract;
  private final OvhSmsInjectorConfig config;
  private final InjectorContext injectorContext;

  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;

  @QualifiedComponent(identifier = OvhSmsContract.TYPE)
  private OvhSmsExecutor ovhSmsExecutor;

  public OvhInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      OvhSmsInjectorConfig config,
      OvhSmsContract ovhSmsContract,
      InjectorContext injectorContext,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.config = config;
    this.ovhSmsContract = ovhSmsContract;
    this.injectorContext = injectorContext;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
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
    OvhSmsService ovhSmsService = new OvhSmsService(this.config);
    this.ovhSmsExecutor =
        new OvhSmsExecutor(injectorContext, ovhSmsService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
