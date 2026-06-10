package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Comcheck.COMCHECK_STATUS.EXPIRED;
import static io.openaev.database.specification.ComcheckStatusSpecification.thatNeedExecution;
import static io.openaev.injector_contract.variables.VariableHelper.COMCHECK;
import static java.time.Instant.now;
import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.ComcheckStatusRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.ManagerFactory;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@Slf4j
@RequiredArgsConstructor
public class ComchecksExecutionJob implements Job {
  private final OpenAEVConfig openAEVConfig;
  private final ComcheckRepository comcheckRepository;
  private final ComcheckStatusRepository comcheckStatusRepository;

  private final InjectorContractRepository injectorContractRepository;
  private final ExecutionContextService executionContextService;

  private final ManagerFactory managerFactory;

  private final ObjectMapper mapper;

  @Lazy @Autowired private ComchecksExecutionJob proxySelf;

  private Inject buildComcheckEmail(Comcheck comCheck) {
    Inject emailInject = new Inject();
    InjectorContract contract =
        injectorContractRepository.findById(EmailContract.EMAIL_DEFAULT).orElseThrow();
    emailInject.setInjectorContract(contract);
    emailInject.setInjector(contract.getFirstInjector());
    emailInject.setExercise(comCheck.getExercise());
    ObjectNode content = mapper.createObjectNode();
    content.set("subject", mapper.convertValue(comCheck.getSubject(), JsonNode.class));
    content.set("body", mapper.convertValue(comCheck.getMessage(), JsonNode.class));
    content.set("expectationType", mapper.convertValue("none", JsonNode.class));
    emailInject.setContent(content);
    return emailInject;
  }

  private ComcheckContext buildComcheckLink(ComcheckStatus status) {
    ComcheckContext comcheckContext = new ComcheckContext();
    String comCheckLink = openAEVConfig.getBaseUrl() + "/comcheck/" + status.getId();
    comcheckContext.setUrl("<a href='" + comCheckLink + "'>" + comCheckLink + "</a>");
    return comcheckContext;
  }

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    TxCtx ctx = TxCtx.noTenant();
    proxySelf.txExecute(ctx);
  }

  @Transactional
  public void txExecute(TxCtx ctx) throws JobExecutionException {
    Instant now = now();
    try {
      // 01. Manage expired comchecks.
      List<Comcheck> toExpired = comcheckRepository.thatMustBeExpired(now);
      comcheckRepository.saveAll(
          toExpired.stream().peek(comcheck -> comcheck.setState(EXPIRED)).toList());
      // 02. Send all required statuses
      List<ComcheckStatus> allStatuses = comcheckStatusRepository.findAll(thatNeedExecution());
      Map<Comcheck, List<ComcheckStatus>> byComchecks =
          allStatuses.stream().collect(groupingBy(ComcheckStatus::getComcheck));
      byComchecks.entrySet().stream()
          .parallel()
          .forEach(
              entry -> {
                Comcheck comCheck = entry.getKey();
                // Send the email to users
                Exercise exercise = comCheck.getExercise();
                List<ComcheckStatus> comcheckStatuses = entry.getValue();
                List<ExecutionContext> userInjectContexts =
                    comcheckStatuses.stream()
                        .map(
                            comcheckStatus -> {
                              ExecutionContext injectContext =
                                  this.executionContextService.executionContext(
                                      comcheckStatus.getUser(), exercise, "Comcheck");
                              injectContext.put(
                                  COMCHECK,
                                  buildComcheckLink(
                                      comcheckStatus)); // Add specific inject variable for comcheck
                              // link
                              return injectContext;
                            })
                        .toList();
                Inject emailInject = buildComcheckEmail(comCheck);
                ExecutableInject injection =
                    new ExecutableInject(false, true, emailInject, userInjectContexts);
                io.openaev.executors.Injector emailExecutor =
                    this.managerFactory.getManager().requestEmailInjector();
                Execution execution = emailExecutor.executeInjection(ctx, injection);
                // Save the status sent date
                List<String> usersSuccessfullyNotified =
                    execution.getTraces().stream()
                        .filter(
                            executionTrace ->
                                ExecutionTraceStatus.EXECUTED.equals(executionTrace.getStatus()))
                        .flatMap(t -> t.getIdentifiers().stream())
                        .toList();
                List<ComcheckStatus> statusToUpdate =
                    comcheckStatuses.stream()
                        .filter(
                            comcheckStatus ->
                                usersSuccessfullyNotified.contains(
                                    comcheckStatus.getUser().getId()))
                        .toList();
                if (!statusToUpdate.isEmpty()) {
                  comcheckStatusRepository.saveAll(
                      statusToUpdate.stream()
                          .peek(comcheckStatus -> comcheckStatus.setLastSent(now))
                          .toList());
                }
              });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }
}
