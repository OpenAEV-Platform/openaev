package io.openaev.integration.impl.injectors.phishing;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.PhishingEmailTemplateRepository;
import io.openaev.database.repository.PhishingLandingPageRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.injectors.phishing.PhishingContract;
import io.openaev.injectors.phishing.service.PhishingLandingPageService;
import io.openaev.injectors.phishing.service.PhishingTrackingService;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PhishingInjectorIntegrationFactory extends BuiltinIntegrationFactory {
  private final PhishingContract phishingContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final PhishingTrackingService phishingTrackingService;
  private final PhishingLandingPageRepository landingPageRepository;
  private final PhishingEmailTemplateRepository emailTemplateRepository;
  private final PhishingLandingPageService landingPageService;
  private final ConnectorInstanceService connectorInstanceService;
  private final ComponentRequestEngine componentRequestEngine;

  public PhishingInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      PhishingContract phishingContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      PhishingTrackingService phishingTrackingService,
      PhishingLandingPageRepository landingPageRepository,
      PhishingEmailTemplateRepository emailTemplateRepository,
      PhishingLandingPageService landingPageService,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.phishingContract = phishingContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.phishingTrackingService = phishingTrackingService;
    this.landingPageRepository = landingPageRepository;
    this.emailTemplateRepository = emailTemplateRepository;
    this.landingPageService = landingPageService;
  }

  @Override
  protected final String getClassName() {
    return PhishingInjectorIntegrationFactory.class.getCanonicalName();
  }

  @Override
  protected void runMigrations(String tenantId) throws Exception {
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
            PhishingInjectorIntegration.PHISHING_INJECTOR_ID,
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
    return new PhishingInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        phishingContract,
        injectorContext,
        emailService,
        injectExpectationService,
        phishingTrackingService,
        landingPageRepository,
        emailTemplateRepository);
  }

  @Override
  public void registerConnectorForTenant(String tenantId) throws Exception {
    injectorService.registerBuiltinInjector(
        tenantId,
        PhishingInjectorIntegration.PHISHING_INJECTOR_ID,
        PhishingInjectorIntegration.PHISHING_INJECTOR_NAME,
        phishingContract,
        false,
        "phishing",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP));
    // Seed a platform-themed default landing page + email template so a tenant has a ready-to-use
    // phishing action immediately. Runs after the injector is registered so the seeded landing
    // page's Threat Arsenal contract can be synthesized.
    landingPageService.seedDefaultsIfEmpty();
  }
}
