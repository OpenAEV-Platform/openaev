package io.openaev.integration.impl.injectors.opencti;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.executors.InjectorContext;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.injectors.opencti.OpenCTIContract;
import io.openaev.injectors.opencti.OpenCTIExecutor;
import io.openaev.injectors.opencti.config.OpenCTIInjectorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.QualifiedComponent;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.opencti.service.OpenCTIService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenCTIInjectorIntegration extends Integration {
  public static final String OPENCTI_INJECTOR_NAME = "OpenCTI";
  public static final String OPENCTI_INJECTOR_ID = "2cbc77af-67f2-46af-bfd2-755d06a46da0";

  private final InjectorService injectorService;
  private final OpenCTIContract openCTIContract;
  private final InjectorContext injectorContext;
  private final OpenCTIService openCTIService;
  private final InjectExpectationService injectExpectationService;
  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstance connectorInstance;
  private final BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

  @QualifiedComponent(identifier = {OpenCTIContract.TYPE, OPENCTI_INJECTOR_ID})
  private OpenCTIExecutor openCTIExecutor;

  public OpenCTIInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      InjectorService injectorService,
      OpenCTIContract openCTIContract,
      InjectorContext injectorContext,
      OpenCTIService openCTIService,
      InjectExpectationService injectExpectationService,
      BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.openCTIContract = openCTIContract;
    this.openCTIService = openCTIService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.connectorInstanceService = connectorInstanceService;
    this.connectorInstance = connectorInstance;
    this.baseIntegrationConfigurationBuilder = baseIntegrationConfigurationBuilder;

    // Refresh the context to get the config
    try {
      refresh();
    } catch (Exception e) {
      log.error("Error during initialization of the " + OPENCTI_INJECTOR_NAME + "  Injector", e);
      throw new ExecutorException(
          e, "Error during initialization of the Injector", OPENCTI_INJECTOR_NAME);
    }
  }

  @Override
  protected void innerStart() throws Exception {
    String injectorId =
        connectorInstanceService.getConnectorInstanceConfigurationsByIdAndKey(
            connectorInstance.getId(), ConnectorType.INJECTOR.getIdKeyName());

    injectorService.registerBuiltinInjector(
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
  protected void refresh()
      throws JsonProcessingException,
          InvocationTargetException,
          NoSuchMethodException,
          InstantiationException,
          IllegalAccessException {
    OpenCTIInjectorConfig config =
        baseIntegrationConfigurationBuilder.build(OpenCTIInjectorConfig.class);
    config.fromConnectorInstanceConfigurationSet(
        this.getConnectorInstance(), OpenCTIInjectorConfig.class);
  }

  @Override
  protected void innerStop() {
    // TODO
  }
}
