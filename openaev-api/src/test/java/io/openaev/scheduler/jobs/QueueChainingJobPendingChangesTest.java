package io.openaev.scheduler.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepDelayQueue;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.StepDelayQueueRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.StepComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * {@link QueueChainingJob} processes the due entries of several tenants in one transaction, moving
 * its scope from one entry's tenant to the next. Each entry's changes must be written under its own
 * tenant before the job moves on, on the handled-failure path as on the success path: an update
 * flushed under the next entry's tenant matches no row once {@code steps} is active.
 *
 * <p>The production active tables are armed, {@code steps} among them, which is what makes an
 * update run under the wrong scope fail. Not {@code @Transactional}: the job opens and commits its
 * own transaction, so the rows are read back through JDBC, outside the inspector, and swept in
 * {@link #cleanup()}.
 */
@SpringBootTest
@DisplayName("Queue chaining job: each entry's changes are written in its own tenant")
class QueueChainingJobPendingChangesTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private QueueChainingJob job;
  @Autowired private WorkflowComposer workflowComposer;
  @Autowired private StepComposer stepComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private StepDelayQueueRepository stepDelayQueueRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private StepRepository stepRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @MockitoSpyBean private StepService stepService;
  @MockitoSpyBean private WorkflowService workflowService;
  @MockitoSpyBean private QueueChainingService queueChainingService;

  private final List<String> committedTenantIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    // The chaining tables are armed: with no scope the reads below find nothing and leave the rows
    // behind.
    tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          stepDelayQueueRepository.deleteAll(stepDelayQueueRepository.findAll());
          workflowRepository.deleteAll(
              workflowRepository.findAllById(
                  workflowComposer.generatedItems.stream().map(Workflow::getId).toList()));
          stepRepository.deleteAll(
              stepRepository.findAllById(
                  stepComposer.generatedItems.stream().map(Step::getId).toList()));
          scenarioRepository.deleteAll(
              scenarioRepository.findAllById(
                  scenarioComposer.generatedItems.stream().map(Scenario::getId).toList()));
          return null;
        });
    workflowComposer.reset();
    stepComposer.reset();
    scenarioComposer.reset();
    tenantRepository.deleteAllById(committedTenantIds);
    committedTenantIds.clear();
  }

  private Tenant persistTenant() {
    Tenant tenant =
        tenantRepository.save(
            TenantFixture.getTenant(
                "queue-chaining-flush-" + UUID.randomUUID().toString().substring(0, 8)));
    committedTenantIds.add(tenant.getId());
    return tenant;
  }

  /** A due entry of a scenario-backed run of {@code tenant}; returns its step template's id. */
  private String persistDueEntryOf(Tenant tenant) {
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(tenant);
    Step stepTemplate = StepFixture.getDefaultStepTemplate();
    Workflow run =
        workflowComposer
            .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
            .withStep(stepComposer.forStep(stepTemplate))
            .withScenario(scenarioComposer.forScenario(scenario))
            .persist()
            .get();
    stepDelayQueueRepository.save(
        StepDelayQueue.builder()
            .input("{}")
            .now(Instant.now())
            .goal(Instant.now().minusSeconds(60))
            .delay(0L)
            .stepTemplate(stepTemplate)
            .workflowRun(run)
            .build());
    return stepTemplate.getId();
  }

  private String storedStatus(String stepId) {
    return new JdbcTemplate(dataSource)
        .queryForObject("SELECT step_status FROM steps WHERE step_id = ?", String.class, stepId);
  }

  @Nested
  @DisplayName("Publish failure")
  class PublishFailure {

    @Test
    @DisplayName(
        "given the publish failing for entries of two tenants should end each step in its own"
            + " tenant and commit the pass")
    void given_publishFailingForEntriesOfTwoTenants_should_endEachStepInItsOwnTenant()
        throws Exception {
      // Arrange: whichever entry the pop returns first, its step is ended while the job is still
      // in its tenant, and the job then moves to the other tenant.
      String stepOfTenantA = persistDueEntryOf(persistTenant());
      String stepOfTenantB = persistDueEntryOf(persistTenant());
      doAnswer(invocation -> List.of((Step) invocation.getArgument(0)))
          .when(stepService)
          .createReadySteps(any(), any(), any(), anyInt());
      doThrow(new IOException("broker down")).when(queueChainingService).readyStep(any(), any());
      doAnswer(invocation -> invocation.getArgument(0))
          .when(workflowService)
          .evaluateWorkflowProgress(any());

      // Act and assert: the pass commits
      assertThatCode(() -> job.execute(null)).doesNotThrowAnyException();

      // Assert
      assertThat(storedStatus(stepOfTenantA)).isEqualTo(StepStatus.END.name());
      assertThat(storedStatus(stepOfTenantB)).isEqualTo(StepStatus.END.name());
    }
  }
}
