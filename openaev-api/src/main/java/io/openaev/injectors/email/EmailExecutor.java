package io.openaev.injectors.email;

import static io.openaev.database.model.ExecutionTrace.getNewErrorTrace;
import static io.openaev.injectors.email.EmailContract.EMAIL_GLOBAL;

import io.openaev.database.model.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.executors.Injector;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.injectors.email.service.EmailService;
import io.openaev.model.ExecutionProcess;
import io.openaev.service.InjectExpectationService;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class EmailExecutor extends Injector {

  private final EmailService emailService;
  private final InjectExpectationService injectExpectationService;

  public EmailExecutor(
      InjectorContext injectorContext,
      EmailService emailService,
      InjectExpectationService injectExpectationService) {
    super(injectorContext);
    this.emailService = emailService;
    this.injectExpectationService = injectExpectationService;
  }

  private void sendMulti(
      Execution execution,
      List<ExecutionContext> users,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      String subject,
      String message,
      List<DataAttachment> attachments) {
    try {
      emailService.sendEmail(
          execution, users, from, fromName, replyTos, inReplyTo, subject, message, attachments);
    } catch (Exception e) {
      execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
    }
  }

  private void sendSingle(
      Execution execution,
      List<ExecutionContext> users,
      String from,
      String fromName,
      List<String> replyTos,
      String inReplyTo,
      boolean mustBeEncrypted,
      String subject,
      String message,
      List<DataAttachment> attachments) {
    users.forEach(
        user -> {
          try {
            emailService.sendEmail(
                execution,
                List.of(user),
                from,
                fromName,
                replyTos,
                inReplyTo,
                mustBeEncrypted,
                subject,
                message,
                attachments);
          } catch (Exception e) {
            execution.addTrace(getNewErrorTrace(e.getMessage(), ExecutionTraceAction.COMPLETE));
          }
        });
  }

  /**
   * Builds a non-platform recipient for a raw email address (e.g. one mapped from an upstream
   * finding in a chaining workflow). The wrapped {@link User} is transient (never persisted): it
   * carries a generated id - so execution traces can reference it - and the target address. Such
   * recipients have no platform account, so encryption/PGP and player-specific template variables
   * do not apply to them.
   */
  private ExecutionContext buildSyntheticRecipient(String email) {
    User user = new User();
    user.setId(UUID.randomUUID().toString());
    user.setEmail(email);
    return new ExecutionContext(user, List.of("Recipients"));
  }

  @Override
  public ExecutionProcess process(
      @NotNull final Execution execution, @NotNull final ExecutableInject injection)
      throws Exception {
    Inject inject = injection.getInjection().getInject();
    EmailContent content = injectExpectationService.contentConvert(injection, EmailContent.class);
    List<Document> documents =
        inject.getDocuments().stream()
            .filter(InjectDocument::isAttached)
            .map(InjectDocument::getDocument)
            .toList();
    List<DataAttachment> attachments = resolveAttachments(execution, injection, documents);
    String inReplyTo = content.getInReplyTo();
    String subject = content.getSubject();
    String message = content.buildMessage(injection, this.context.getOpenAEVConfig().getBaseUrl());
    boolean mustBeEncrypted = content.isEncrypted();
    // Recipients come from the inject's audience (team-resolved players) and/or raw addresses
    // mapped
    // into the "recipients" content field by a chaining MAPPER (e.g. an upstream "email" finding).
    // Raw addresses are delivered to as synthetic, non-platform recipients so a finding can drive
    // an
    // email without a team.
    List<ExecutionContext> users = new ArrayList<>(injection.getUsers());
    List<String> manualRecipients = content.getParsedRecipients();
    if (!manualRecipients.isEmpty()) {
      Set<String> existingEmails =
          users.stream()
              .map(uc -> uc.getUser() != null ? uc.getUser().getEmail() : null)
              .filter(Objects::nonNull)
              .map(email -> email.toLowerCase())
              .collect(Collectors.toSet());
      for (String address : manualRecipients) {
        if (existingEmails.add(address.toLowerCase())) {
          users.add(buildSyntheticRecipient(address));
        }
      }
      // Raw addresses have no platform account / PGP key, so encryption cannot apply to them.
      mustBeEncrypted = false;
    }
    if (users.isEmpty()) {
      throw new UnsupportedOperationException("Email needs at least one user");
    }
    Exercise exercise = injection.getInjection().getExercise();
    String from =
        exercise != null ? exercise.getFrom() : this.context.getOpenAEVConfig().getDefaultMailer();
    String fromName =
        exercise != null
            ? exercise.getFromName()
            : this.context.getOpenAEVConfig().getDefaultMailerName();
    List<String> replyTos =
        exercise != null
            ? exercise.getReplyTos()
            : new ArrayList<>(List.of(this.context.getOpenAEVConfig().getDefaultReplyTo()));
    //noinspection SwitchStatementWithTooFewBranches
    switch (inject
        .getInjectorContract()
        .map(InjectorContract::getId)
        .orElseThrow(() -> new UnsupportedOperationException("Inject does not have a contract"))) {
      case EMAIL_GLOBAL ->
          sendMulti(
              execution, users, from, fromName, replyTos, inReplyTo, subject, message, attachments);
      default ->
          sendSingle(
              execution,
              users,
              from,
              fromName,
              replyTos,
              inReplyTo,
              mustBeEncrypted,
              subject,
              message,
              attachments);
    }
    injectExpectationService.computeAndSaveExpectations(
        injection,
        content.getExpectations(),
        null,
        entry ->
            entry.getType() == BaseInjectExpectation.EXPECTATION_TYPE.MANUAL
                ? List.of(injectExpectationService.toExpectationTemplate(injection, entry))
                : List.of());

    return new ExecutionProcess(false);
  }
}
