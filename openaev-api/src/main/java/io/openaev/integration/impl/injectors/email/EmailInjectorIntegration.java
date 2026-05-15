package io.openaev.integration.impl.injectors.email;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.executors.InjectorContext;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.email.EmailExecutor;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class EmailInjectorIntegration extends IntegrationInMemory {
  private static final String EMAIL_INJECTOR_NAME = "Email";
  public static final String EMAIL_INJECTOR_ID = "41b4dd55-5bd1-4614-98cd-9e3770753306";

  private final EmailContract emailContract;
  private final InjectorContext injectorContext;

  private final InjectorService injectorService;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  @QualifiedComponent(identifier = {EmailContract.TYPE, EMAIL_INJECTOR_ID})
  private EmailExecutor emailExecutor;

  public EmailInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance instance,
      ConnectorInstanceService connectorInstanceService,
      EmailContract emailContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService) {
    super(componentRequestEngine, instance, connectorInstanceService);
    this.emailContract = emailContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.registerBuiltinInjector(
        EMAIL_INJECTOR_ID,
        EMAIL_INJECTOR_NAME,
        emailContract,
        false,
        "communication",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.IMAP));
    this.emailExecutor = new EmailExecutor(injectorContext, emailService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
