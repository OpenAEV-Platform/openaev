package io.openaev.service;

import static io.openaev.config.OpenAEVAnonymous.ANONYMOUS;
import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.email.model.EmailContent;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailingService {

  @Resource protected ObjectMapper mapper;

  private final UserRepository userRepository;
  private final ResultsMetricCollector resultsMetricCollector;
  private final InjectorContractRepository injectorContractRepository;
  private final ExecutionContextService executionContextService;
  private final ManagerFactory managerFactory;

  private Injector resolveFirstInjector(InjectorContract injectorContract) {
    if (injectorContract.getInjectors() == null || injectorContract.getInjectors().isEmpty()) {
      throw new IllegalStateException(
          "Email injector contract has no linked injector: " + injectorContract.getId());
    }
    return injectorContract.getInjectors().getFirst();
  }

  /**
   * Turns a failed or partially failed delivery into an exception. The email injector swallows
   * every error into {@link Execution} traces, so this is the only place where a caller can learn
   * that nothing was actually sent.
   */
  private void raiseOnFailedDelivery(Execution execution, String contractId, int recipientCount) {
    ExecutionStatus status = execution.getStatus();
    if (!ExecutionStatus.ERROR.equals(status) && !ExecutionStatus.PARTIAL.equals(status)) {
      return;
    }
    String errors =
        execution.getTraces().stream()
            .filter(trace -> trace.getStatus().isError())
            .map(ExecutionTrace::getMessage)
            .collect(Collectors.joining("; "));
    throw new IllegalStateException(
        String.format(
            "Email delivery %s for contract %s (%d recipient(s)): %s",
            status, contractId, recipientCount, errors));
  }

  private String resolveInjectorType(InjectorContract injectorContract, Injector firstInjector) {
    if (firstInjector.getType() == null) {
      throw new IllegalStateException(
          "Email injector contract has no linked injector type: " + injectorContract.getId());
    }
    return firstInjector.getType();
  }

  public void sendEmail(
      String subject, String body, List<User> users, Optional<Exercise> exercise, String tenantId) {
    EmailContent emailContent = new EmailContent();
    emailContent.setSubject(subject);
    emailContent.setBody(body);

    Inject inject = new Inject();
    InjectorContract emailContract =
        this.injectorContractRepository
            .findById(new InjectorContractId(EmailContract.EMAIL_DEFAULT, tenantId))
            .orElseThrow(ElementNotFoundException::new);
    inject.setInjectorContract(emailContract);
    Injector firstInjector = resolveFirstInjector(emailContract);
    String injectorType = resolveInjectorType(emailContract, firstInjector);
    inject.setInjector(firstInjector);

    inject
        .getInjectorContract()
        .ifPresent(
            injectorContract -> {
              inject.setContent(this.mapper.valueToTree(emailContent));

              // When resetting the password, the user is not logged in (anonymous),
              // so there's no need to add the user to the inject.
              if (!ANONYMOUS.equals(currentUser().getId())) {
                inject.setUser(
                    this.userRepository
                        .findById(currentUser().getId())
                        .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
              }

              exercise.ifPresent(inject::setExercise);

              List<ExecutionContext> userInjectContexts =
                  users.stream()
                      .distinct()
                      .map(
                          user ->
                              this.executionContextService.executionContext(
                                  user, inject, "Direct execution"))
                      .toList();
              // Telemetry: the email injector sends one individual email per distinct
              // recipient (attempts semantics, before delivery).
              resultsMetricCollector.recordEmailsSent(userInjectContexts.size());
              ExecutableInject injection =
                  new ExecutableInject(false, true, inject, userInjectContexts);
              io.openaev.executors.Injector executor =
                  managerFactory.getManager(tenantId).requestInjectorExecutorByType(injectorType);
              // Injector.execute() never propagates: it catches every failure into execution
              // traces. Discarding the returned Execution would make a failed delivery (SMTP
              // down, misconfigured mailer, encryption error) indistinguishable from a success,
              // which is how notification emails went missing with no trace in the logs.
              Execution execution = executor.executeInjection(injection);
              raiseOnFailedDelivery(execution, injectorContract.getId(), userInjectContexts.size());
            });
  }

  public void sendEmail(
      String subject, String body, List<User> users, Optional<Exercise> exercise) {
    String tenantId = exercise.map(ex -> ex.getTenant().getId()).orElse(DEFAULT_TENANT_UUID);
    sendEmail(subject, body, users, exercise, tenantId);
  }

  public void sendEmail(String subject, String body, List<User> users) {
    sendEmail(subject, body, users, Optional.empty(), DEFAULT_TENANT_UUID);
  }

  /**
   * Sends an email in the context of a specific tenant (e.g., notifications). The tenantId is used
   * to resolve the correct email integration from the per-tenant Manager.
   *
   * @param subject email subject
   * @param body email body
   * @param users recipients
   * @param tenantId the tenant whose email integration to use
   */
  public void sendEmail(String subject, String body, List<User> users, String tenantId) {
    sendEmail(subject, body, users, Optional.empty(), tenantId);
  }
}
