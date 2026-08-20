package io.openaev.integration.impl.injectors.phishing;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.injectors.phishing.PhishingExecutor;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class PhishingInjectorIntegration extends IntegrationInMemory {
  static final String PHISHING_INJECTOR_NAME = "Phishing";
  public static final String PHISHING_INJECTOR_ID = PhishingContract.PHISHING_INJECTOR_ID;

  private final PhishingContract phishingContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;
  private final PhishingTrackingService phishingTrackingService;
  private final PhishingLandingPageRepository landingPageRepository;
  private final PhishingEmailTemplateRepository emailTemplateRepository;

  @QualifiedComponent(identifier = {PhishingContract.TYPE, PHISHING_INJECTOR_ID})
  private PhishingExecutor phishingExecutor;

  public PhishingInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      PhishingContract phishingContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectExpectationService injectExpectationService,
      PhishingTrackingService phishingTrackingService,
      PhishingLandingPageRepository landingPageRepository,
      PhishingEmailTemplateRepository emailTemplateRepository) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.phishingContract = phishingContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
    this.phishingTrackingService = phishingTrackingService;
    this.landingPageRepository = landingPageRepository;
    this.emailTemplateRepository = emailTemplateRepository;
  }

  @Override
  protected void innerStart() throws Exception {
    this.phishingExecutor =
        new PhishingExecutor(
            injectorContext,
            emailService,
            injectExpectationService,
            phishingTrackingService,
            landingPageRepository,
            emailTemplateRepository);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
