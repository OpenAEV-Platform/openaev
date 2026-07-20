package io.openaev.integration.impl.injectors.challenge;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.challenge.ChallengeExecutor;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.annotation.QualifiedComponent;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;

public class ChallengeInjectorIntegration extends IntegrationInMemory {
  static final String CHALLENGE_INJECTOR_NAME = "Challenges";
  public static final String CHALLENGE_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0233";

  private final ChallengeContract challengeContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final ChallengeRepository challengeRepository;
  private final UrlAccessTokenService urlAccessTokenService;

  @QualifiedComponent(identifier = {ChallengeContract.TYPE, CHALLENGE_INJECTOR_ID})
  private ChallengeExecutor challengeExecutor;

  public ChallengeInjectorIntegration(
      ComponentRequestEngine componentRequestEngine,
      ConnectorInstance connectorInstance,
      ConnectorInstanceService connectorInstanceService,
      ChallengeContract challengeContract,
      InjectorContext injectorContext,
      EmailService emailService,
      InjectorService injectorService,
      InjectExpectationService injectExpectationService,
      ChallengeRepository challengeRepository,
      UrlAccessTokenService urlAccessTokenService) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.challengeContract = challengeContract;
    this.challengeRepository = challengeRepository;
    this.emailService = emailService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
    this.urlAccessTokenService = urlAccessTokenService;
  }

  @Override
  protected void innerStart() throws Exception {
    this.challengeExecutor =
        new ChallengeExecutor(
            injectorContext,
            challengeRepository,
            emailService,
            injectExpectationService,
            urlAccessTokenService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
