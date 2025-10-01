package io.openaev.injectors.challenge;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.database.model.ExecutionTrace.getNewSuccessTrace;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.*;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.injectors.challenge.model.ChallengeContent;
import io.openaev.injectors.challenge.model.ChallengeVariable;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.model.ExecutionProcess;
import io.openaev.model.Expectation;
import io.openaev.model.expectation.ChallengeExpectation;
import io.openaev.model.expectation.ManualExpectation;
import io.openaev.service.InjectExpectationService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component(ChallengeContract.TYPE)
@RequiredArgsConstructor
public class ChallengeExecutor extends Injector {

  @Resource private OpenAEVConfig openAEVConfig;

  private final ChallengeRepository challengeRepository;
  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  @Value("${openaev.mail.imap.enabled}")
  private boolean imapEnabled;

  private String buildChallengeUri(
      ExecutionContext context, Exercise exercise, Challenge challenge) {
    String userId = context.getUser().getId();
    String challengeId = challenge.getId();
    String exerciseId = exercise.getId();
    return openAEVConfig.getBaseUrl()
        + "/challenges/"
        + exerciseId
        + "?user="
        + userId
        + "&challenge="
        + challengeId;
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection) {
    try {
      ChallengeContent content = contentConvert(injection, ChallengeContent.class);
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
        List<String> replyTos = exercise.getReplyTos();
        List<ExecutionContext> users = injection.getUsers();
        List<Document> documents =
            injection.getInjection().getInject().getDocuments().stream()
                .filter(InjectDocument::isAttached)
                .map(InjectDocument::getDocument)
                .toList();
        List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
        String message = content.buildMessage(injection, imapEnabled);
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
        // Return expectations
        List<Expectation> expectations = new ArrayList<>();
        if (!content.getExpectations().isEmpty()) {
          expectations.addAll(
              content.getExpectations().stream()
                  .flatMap(
                      (entry) ->
                          switch (entry.getType()) {
                            case MANUAL -> Stream.of((Expectation) new ManualExpectation(entry));
                            case CHALLENGE ->
                                challenges.stream()
                                    .map(
                                        challenge ->
                                            (Expectation)
                                                new ChallengeExpectation(entry, challenge));
                            default -> Stream.of();
                          })
                  .toList());
        }

        injectExpectationService.buildAndSaveInjectExpectations(injection, expectations);

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
