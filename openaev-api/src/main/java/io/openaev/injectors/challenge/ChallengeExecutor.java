package io.openaev.injectors.challenge;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewSuccessTrace;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.expectation.ChallengeExpectation;
import io.openaev.expectation.Expectation;
import io.openaev.expectation.ManualExpectation;
import io.openaev.injector_contract.variables.contract.UserContract;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.injectors.challenge.model.ChallengeVariable;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;

public class ChallengeExecutor extends Injector {

  private final ChallengeRepository challengeRepository;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;
  private final UrlAccessTokenService urlAccessTokenService;

  public ChallengeExecutor(
      InjectorContext context,
      ChallengeRepository challengeRepository,
      EmailService emailService,
      InjectExpectationService injectExpectationService,
      UrlAccessTokenService urlAccessTokenService) {
    super(context);
    this.challengeRepository = challengeRepository;
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
    this.urlAccessTokenService = urlAccessTokenService;
  }

  private String buildChallengeUri(
      ExecutionContext executionContext, Exercise exercise, Challenge challenge) {
    UserContract user = executionContext.getUser();
    String challengeId = challenge.getId();
    String url =
        this.context.getOpenAEVConfig().getBaseUrl()
            + "/"
            + exercise.getTenant().getId()
            + "/challenges/"
            + exercise.getId()
            + "?challenge="
            + challengeId;
    return urlAccessTokenService.generateTokenUrl(exercise, user, url);
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection) {
    try {
      ChallengeContent content =
          injectExpectationService.contentConvert(injection, ChallengeContent.class);
      List<Challenge> challenges =
          fromIterable(challengeRepository.findAllById(content.getChallenges()));
      if (challenges.isEmpty()) {
        throw new UnsupportedOperationException("Inject needs at least one challenge");
      }
      String contract =
          injection
              .getInjection()
              .getInject()
              .getInjectorContract()
              .map(InjectorContract::getId)
              .orElseThrow(
                  () -> new UnsupportedOperationException("Inject does not have a contract"));

      if (contract.equals(CHALLENGE_PUBLISH)) {
        // Challenge publishing is only linked to execution date of this inject.
        String challengeNames =
            challenges.stream().map(Challenge::getName).collect(Collectors.joining(","));
        String publishedMessage = "Challenges (" + challengeNames + ") marked as published";
        execution.addTrace(getNewSuccessTrace(publishedMessage, ExecutionTraceAction.COMPLETE));
        // Send the publication message.
        Exercise exercise = injection.getInjection().getExercise();
        String from = exercise.getFrom();
        String fromName = exercise.getFromName();
        List<String> replyTos = exercise.getReplyTos();
        List<ExecutionContext> users = injection.getUsers();
        List<Document> documents =
            injection.getInjection().getInject().getDocuments().stream()
                .filter(InjectDocument::isAttached)
                .map(InjectDocument::getDocument)
                .toList();
        List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
        String message =
            content.buildMessage(injection, this.context.getOpenAEVConfig().getBaseUrl());
        boolean encrypted = content.isEncrypted();
        users.forEach(
            userInjectContext -> {
              try {
                // Put the challenges variables in the injection context
                List<ChallengeVariable> challengeVariables =
                    challenges.stream()
                        .map(
                            challenge ->
                                new ChallengeVariable(
                                    challenge.getId(),
                                    challenge.getName(),
                                    buildChallengeUri(userInjectContext, exercise, challenge)))
                        .toList();
                userInjectContext.put("challenges", challengeVariables);
                // Send the email.
                emailService.sendEmail(
                    execution,
                    List.of(userInjectContext),
                    from,
                    fromName,
                    replyTos,
                    content.getInReplyTo(),
                    encrypted,
                    content.getSubject(),
                    message,
                    attachments);
              } catch (Exception e) {
                execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
              }
            });

        injectExpectationService.computeAndSaveExpectations(
            injection,
            content.getExpectations(),
            null,
            entry ->
                switch (entry.getType()) {
                  case MANUAL ->
                      List.of(injectExpectationService.toExpectationTemplate(injection, entry));
                  case CHALLENGE ->
                      challenges.stream()
                          .map(
                              challenge -> {
                                ChallengeInjectExpectation template =
                                    (ChallengeInjectExpectation)
                                        injectExpectationService.toExpectationTemplate(
                                            injection, entry);
                                template.setChallenge(challenge);
                                template.setName(challenge.getName());
                                return (BaseInjectExpectation) template;
                              })
                          .toList();
                  default -> List.of();
                });

        return new ExecutionProcess(false);
      } else {
        throw new UnsupportedOperationException("Unknown contract " + contract);
      }
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    }
    return new ExecutionProcess(false);
  }
}
