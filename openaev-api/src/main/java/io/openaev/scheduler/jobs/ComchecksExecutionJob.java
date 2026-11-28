package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Comcheck.COMCHECK_STATUS.EXPIRED;
import static io.openaev.database.specification.ComcheckStatusSpecification.thatNeedExecution;
import static io.openaev.injector_contract.variables.VariableHelper.COMCHECK;
import static java.time.Instant.now;
import static java.util.stream.Collectors.groupingBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawComcheckTenant;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.ComcheckStatusRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.ManagerFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@DisallowConcurrentExecution
@Slf4j
@RequiredArgsConstructor
public class ComchecksExecutionJob implements Job {
  private final OpenAEVConfig openAEVConfig;
  private final ApplicationContext context;
  private final ComcheckRepository comcheckRepository;
  private final ComcheckStatusRepository comcheckStatusRepository;

  private final InjectorContractRepository injectorContractRepository;
  private final InjectorRepository injectorRepository;
  private final ExecutionContextService executionContextService;

  private final ManagerFactory managerFactory;

  private final ObjectMapper mapper;

  private final TenantScopedTransaction tenantTx;

  private Inject buildComcheckEmail(Comcheck comCheck, String tenantId) {
    // The contract and the injector both exist once per tenant under the same id: resolve this
    // tenant's own, explicitly by (id, tenant).
    Inject emailInject = new Inject();
    InjectorContract contract =
        injectorContractRepository
            .findByContractIdAndTenant(EmailContract.EMAIL_DEFAULT, tenantId)
            .orElseThrow();
    emailInject.setInjectorContract(contract);
    emailInject.setInjector(
        injectorRepository
            .findFirstByContractsCompositeIdIdAndTenantId(contract.getId(), tenantId)
            .orElseThrow());
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

  /** Everything needed to send one comcheck email batch, materialized inside the DB transaction. */
  record ComcheckSendTask(
      ExecutableInject injection, List<ComcheckStatus> statuses, String tenantId) {}

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    Instant now = now();
    try {
      // Phase 1 (DB transaction): expire comchecks and materialize everything needed for sending.
      // The SMTP sends deliberately happen outside the transaction: holding a DB transaction open
      // during network I/O ties up connections for the duration of the sends.
      List<ComcheckSendTask> tasks = prepareSendTasks(now);

      // Phase 2 (no DB transaction around the sends): send the emails
      Objects.requireNonNullElse(tasks, List.<ComcheckSendTask>of()).stream()
          .parallel()
          .forEach(
              task -> {
                io.openaev.executors.Injector emailExecutor =
                    this.managerFactory.getManager(task.tenantId()).requestEmailInjector();
                Execution execution = emailExecutor.executeInjection(task.injection());
                // Save the status sent date
                List<String> usersSuccessfullyNotified =
                    execution.getTraces().stream()
                        .filter(
                            executionTrace ->
                                ExecutionTraceStatus.EXECUTED.equals(executionTrace.getStatus()))
                        .flatMap(t -> t.getIdentifiers().stream())
                        .toList();
                List<ComcheckStatus> statusToUpdate =
                    task.statuses().stream()
                        .filter(
                            comcheckStatus ->
                                usersSuccessfullyNotified.contains(
                                    comcheckStatus.getUser().getId()))
                        .toList();
                if (!statusToUpdate.isEmpty()) {
                  // A status carries no tenant: written in its simulation's tenant, its event
                  // goes to the consumers of that tenant.
                  tenantTx.execute(
                      TxCtx.forTenant(task.tenantId()),
                      () ->
                          comcheckStatusRepository.saveAll(
                              statusToUpdate.stream()
                                  .peek(comcheckStatus -> comcheckStatus.setLastSent(now))
                                  .toList()));
                }
              });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }

  /**
   * Expires the outdated comchecks, then builds the send tasks. The comchecks of both steps are
   * gathered across tenants, with only their ids and their simulation's tenant leaving that read;
   * each tenant's comchecks are then expired, and its tasks built, in a transaction scoped to that
   * tenant. A comcheck carries no tenant, so the tenant of that scope is the tenant its event goes
   * to, and no comcheck is ever read under another tenant's scope.
   */
  @VisibleForTesting
  List<ComcheckSendTask> prepareSendTasks(Instant now) {
    // 01. Expire the outdated comchecks.
    tenantTx
        .execute(
            TxCtx.allTenants(),
            () -> byTenant(comcheckRepository.findComchecksToExpireWithTenant(now)))
        .forEach((tenantId, comcheckIds) -> expireInTenant(tenantId, comcheckIds, now));
    // 02. Gather the comchecks that have statuses to send, then build each tenant's tasks.
    Map<String, List<String>> comcheckIdsByTenant =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> byTenant(comcheckStatusRepository.findComchecksToSendWithTenant()));
    List<ComcheckSendTask> tasks = new ArrayList<>();
    comcheckIdsByTenant.forEach(
        (tenantId, comcheckIds) ->
            tasks.addAll(
                tenantTx.execute(
                    TxCtx.forTenant(tenantId), () -> buildSendTasks(tenantId, comcheckIds))));
    return tasks;
  }

  /**
   * Groups comcheck ids by their simulation's tenant. A projection, not the entities: loading a
   * simulation outside the scope fails and would stop the preparation for every tenant. A comcheck
   * with no simulation goes with the default tenant: the API creates every comcheck in a simulation
   * and deletes it with it, so only a row older than tenants has none, and every such row belongs
   * to the default tenant.
   */
  private static Map<String, List<String>> byTenant(List<RawComcheckTenant> comchecks) {
    Map<String, List<String>> comcheckIdsByTenant = new LinkedHashMap<>();
    comchecks.forEach(
        comcheck ->
            comcheckIdsByTenant
                .computeIfAbsent(
                    Objects.requireNonNullElse(comcheck.getTenant_id(), Tenant.DEFAULT_TENANT_UUID),
                    key -> new ArrayList<>())
                .add(comcheck.getComcheck_id()));
    return comcheckIdsByTenant;
  }

  /**
   * Expires, in a transaction scoped to {@code tenantId}, those of {@code comcheckIds} that are
   * running with an end date past {@code now}, read again under a lock: a comcheck a player
   * finished, or whose end date moved, after the gather stays as it is.
   */
  @VisibleForTesting
  void expireInTenant(String tenantId, List<String> comcheckIds, Instant now) {
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          List<Comcheck> toExpire =
              comcheckRepository.findRunningToExpireForUpdate(comcheckIds, now);
          toExpire.forEach(comcheck -> comcheck.setState(EXPIRED));
          comcheckRepository.saveAll(toExpire);
        });
  }

  private List<ComcheckSendTask> buildSendTasks(String tenantId, List<String> comcheckIds) {
    List<ComcheckStatus> statuses =
        comcheckStatusRepository.findAll(
            thatNeedExecution()
                .and((root, query, cb) -> root.get("comcheck").get("id").in(comcheckIds)));
    Map<Comcheck, List<ComcheckStatus>> byComchecks =
        statuses.stream().collect(groupingBy(ComcheckStatus::getComcheck));
    return byComchecks.entrySet().stream()
        .map(
            entry -> {
              Comcheck comCheck = entry.getKey();
              Exercise exercise = comCheck.getExercise();
              List<ComcheckStatus> comcheckStatuses = entry.getValue();
              List<ExecutionContext> userInjectContexts =
                  comcheckStatuses.stream()
                      .map(
                          comcheckStatus -> {
                            ExecutionContext injectContext =
                                this.executionContextService.executionContext(
                                    comcheckStatus.getUser(), exercise, "Comcheck");
                            // Add specific inject variable for comcheck link
                            injectContext.put(COMCHECK, buildComcheckLink(comcheckStatus));
                            return injectContext;
                          })
                      .toList();
              // Initialised here, inside the tenant's scope: the send runs after the commit and
              // reads the simulation from the inject.
              Hibernate.initialize(exercise);
              Inject emailInject = buildComcheckEmail(comCheck, tenantId);
              return new ComcheckSendTask(
                  new ExecutableInject(false, true, emailInject, userInjectContexts),
                  comcheckStatuses,
                  tenantId);
            })
        .toList();
  }
}
