package io.openaev.integration.impl.injectors.challenge;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.authorisation.HttpClientFactory;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChallengeInjectorIntegrationFactory extends IntegrationFactory {

  private final ChallengeContract challengeContract;
  private final InjectorContext injectorContext;

  private final ConnectorInstanceService connectorInstanceService;
  private final InjectorService injectorService;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  private final ComponentRequestEngine componentRequestEngine;
  private final ChallengeRepository challengeRepository;

  public ChallengeInjectorIntegrationFactory(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstanceService connectorInstanceService,
      CatalogConnectorService catalogConnectorService,
      ChallengeContract challengeContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      ChallengeRepository challengeRepository,
      HttpClientFactory httpClientFactory) {
    super(connectorInstanceService, catalogConnectorService, httpClientFactory);
    this.componentRequestEngine = componentRequestEngine;
    this.connectorInstanceService = connectorInstanceService;
    this.challengeContract = challengeContract;
    this.injectorContext = injectorContext;
    this.emailService = emailService;
    this.injectorService = injectorService;
    this.injectExpectationService = injectExpectationService;
    this.challengeRepository = challengeRepository;
  }

  @Override
  protected final String getClassName() {
    return this.getClass().getCanonicalName();
  }

  @Override
  protected void runMigrations() throws Exception {
    // noop
  }

  @Override
  protected void insertCatalogEntry() throws Exception {
    // noop
  }

  @Override
  public List<ConnectorInstance> findRelatedInstances() {
    return List.of(
        connectorInstanceService.createAutostartInstance(
            ChallengeInjectorIntegration.CHALLENGE_INJECTOR_ID,
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
    return new ChallengeInjectorIntegration(
        componentRequestEngine,
        instance,
        connectorInstanceService,
        challengeContract,
        injectorContext,
        emailService,
        injectorService,
        injectExpectationService,
        challengeRepository);
  }
}
