package io.openaev.integration.impl.injectors.challenge;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.executors.InjectorContext;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.injectors.challenge.ChallengeContract;
import io.openaev.injectors.challenge.ChallengeExecutor;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.IntegrationInMemory;
import io.openaev.integration.QualifiedComponent;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.InjectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;

public class ChallengeInjectorIntegration extends IntegrationInMemory {
  private static final String CHALLENGE_INJECTOR_NAME = "Challenges";
  public static final String CHALLENGE_INJECTOR_ID = "49229430-b5b5-431f-ba5b-f36f599b0233";

  private final ChallengeContract challengeContract;
  private final InjectorContext injectorContext;

  private final EmailService emailService;
  private final InjectorService injectorService;
  private final InjectExpectationService injectExpectationService;
  private final ChallengeRepository challengeRepository;

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
      ChallengeRepository challengeRepository) {
    super(componentRequestEngine, connectorInstance, connectorInstanceService);
    this.injectorService = injectorService;
    this.challengeContract = challengeContract;
    this.challengeRepository = challengeRepository;
    this.emailService = emailService;
    this.injectorContext = injectorContext;
    this.injectExpectationService = injectExpectationService;
  }

  @Override
  protected void innerStart() throws Exception {
    injectorService.registerBuiltinInjector(
        CHALLENGE_INJECTOR_ID,
        CHALLENGE_INJECTOR_NAME,
        challengeContract,
        false,
        "capture-the-flag",
        null,
        null,
        false,
        List.of(ExternalServiceDependency.SMTP, ExternalServiceDependency.IMAP));
    this.challengeExecutor =
        new ChallengeExecutor(
            injectorContext, challengeRepository, emailService, injectExpectationService);
  }

  @Override
  protected void innerStop() {
    // it is not possible to stop this integration
  }
}
