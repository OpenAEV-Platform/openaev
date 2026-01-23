package io.openaev.integration.impl.injectors.ovh;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.injectors.ovh.OvhSmsContract;
import io.openaev.injectors.ovh.OvhSmsExecutor;
import io.openaev.injectors.ovh.config.OvhSmsInjectorConfig;
import io.openaev.injectors.ovh.service.OvhSmsService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OvhInjectorIntegration extends Integration {
  public static final String OVH_SMS_INJECTOR_NAME = "OVHCloud SMS Platform";
  public static final String OVH_SMS_INJECTOR_ID = "e5aefbca-cf8f-4a57-9384-0503a8ffc22f";

  private final OvhSmsContract ovhSmsContract;
  private OvhSmsInjectorConfig config;
  private final InjectorContext injectorContext;
  private final ConnectorInstance connectorInstance;

  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  @QualifiedComponent(identifier = {OvhSmsContract.TYPE, OVH_SMS_INJECTOR_ID})
  private OvhSmsExecutor ovhSmsExecutor;

  public OvhInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      OvhSmsContract ovhSmsContract,
      InjectorContext injectorContext,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.ovhSmsContract = ovhSmsContract;
    this.injectorContext = injectorContext;
    this.connectorInstanceService = connectorInstanceService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.connectorInstance = connectorInstance;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the " + OVH_SMS_INJECTOR_NAME + "  Injector", e);
      throw new ExecutorException(
          e, "Error during initialization of the Injector", OVH_SMS_INJECTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String injectorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            connectorInstance.getId(), ConnectorType.INJECTOR.getIdKeyName());

    injectorService.registerBuiltinInjector(
        injectorId,
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
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    this.config = baseIntegrationConfigurationBuilder.build(OvhSmsInjectorConfig.class);
    this.config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), OvhSmsInjectorConfig.class);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
