package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Article;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.LessonsCategory;
import io.openaev.database.model.LessonsQuestion;
import io.openaev.database.model.Objective;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.TenantIdBase;
import io.openaev.database.model.User;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.ManagerFactory;
import io.openaev.service.PermissionService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.StreamService;
import io.openaev.service.UserService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepEvent;
import io.openaev.service.queue.BatchQueueService;
import io.openaev.service.stream.StreamClearanceService;
import io.openaev.service.stream.StreamMarkingRules;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.Disposable;

/**
 * The scheduler jobs write and read each item in the item's own tenant with no ambient tenant on
 * the thread. Each job runs on a bare thread, as a Quartz worker does: no transaction, no request,
 * no ambient tenant, so {@link TenantContext} answers the default tenant to anyone who reads it.
 * The items live in a second tenant, so a row, a lookup or an event that falls back to the default
 * tenant shows as a wrong tenant or a missing row.
 *
 * <p>Not {@code @Transactional}: the jobs open their own transactions, and the rows they read are
 * committed first. The context runs with the production activation list.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Scheduler jobs write and read each item in the item's own tenant")
class SchedulerJobTenantAttributionTest extends IntegrationTest {

  private static final String EVERY_MINUTE_CRON = "0 * * * * *";
  private static final String JOB_THREAD = "scheduler-job-under-test";

  @Autowired private ScenarioExecutionJob scenarioExecutionJob;
  @Autowired private AtomicTestingExecutionJob atomicTestingExecutionJob;
  @Autowired private AgentInactivityMonitorJob agentInactivityMonitorJob;
  @Autowired private ManagerFactory managerFactory;
  @Autowired private TenantService tenantService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private QueueChainingJob queueChainingJob;
  @Autowired private QueueChainingService queueChainingService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ConfigurableApplicationContext applicationContext;
  @Autowired private UserService userService;
  @Autowired private AttackPathAccessControl attackPathAccessControl;
  @Autowired private PermissionService permissionService;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private StreamMarkingRules streamMarkingRules;
  @MockitoSpyBean private ScenarioToExerciseService scenarioToExerciseService;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private final List<BaseEvent> publishedEvents = new CopyOnWriteArrayList<>();
  private final ApplicationListener<PayloadApplicationEvent<?>> eventRecorder =
      event -> {
        if (event.getPayload() instanceof BaseEvent baseEvent
            && JOB_THREAD.equals(Thread.currentThread().getName())) {
          publishedEvents.add(baseEvent);
        }
      };

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    tenantB = seedTenant();
    applicationContext.addApplicationListener(eventRecorder);
  }

  private String seedTenant() {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "scheduler-attribution-" + tenantId);
    return tenantId;
  }

  @AfterEach
  void sweep() {
    applicationContext
        .getBean(
            AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
            ApplicationEventMulticaster.class)
        .removeApplicationListener(eventRecorder);
    publishedEvents.clear();
    for (String tenantId : List.of(tenantA, tenantB)) {
      jdbc.update("DELETE FROM agents WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
      jdbc.update(
          "DELETE FROM injects_statuses WHERE status_inject IN"
              + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
          tenantId);
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
      String ofTheTenant =
          " IN (SELECT scenario_id FROM scenarios WHERE tenant_id = ?) OR %s IN"
              + " (SELECT exercise_id FROM exercises WHERE tenant_id = ?)";
      jdbc.update(
          "DELETE FROM lessons_questions WHERE lessons_question_category IN"
              + " (SELECT lessons_category_id FROM lessons_categories WHERE"
              + " lessons_category_scenario"
              + ofTheTenant.formatted("lessons_category_exercise")
              + ")",
          tenantId,
          tenantId);
      jdbc.update(
          "DELETE FROM lessons_categories WHERE lessons_category_scenario"
              + ofTheTenant.formatted("lessons_category_exercise"),
          tenantId,
          tenantId);
      jdbc.update(
          "DELETE FROM objectives WHERE objective_scenario"
              + ofTheTenant.formatted("objective_exercise"),
          tenantId,
          tenantId);
      jdbc.update(
          "DELETE FROM articles WHERE article_channel IN"
              + " (SELECT channel_id FROM channels WHERE tenant_id = ?)",
          tenantId);
      jdbc.update("DELETE FROM channels WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM scenarios WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM teams WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
  }

  @Nested
  @DisplayName("ScenarioExecutionJob")
  class ScheduledScenario {

    @Test
    @DisplayName(
        "given a due recurring scenario of another tenant, should create its simulation and the"
            + " simulation's injects in that tenant")
    void given_dueRecurringScenarioOfAnotherTenant_should_createItsSimulationInThatTenant()
        throws Exception {
      // Arrange
      String scenarioId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO scenarios (scenario_id, scenario_name, scenario_recurrence,"
              + " scenario_recurrence_start, scenario_recurrence_end, tenant_id)"
              + " VALUES (?, ?, ?, ?, ?, ?)",
          scenarioId,
          "scheduler-attribution",
          EVERY_MINUTE_CRON,
          Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)),
          Timestamp.from(Instant.now().plus(1, ChronoUnit.DAYS)),
          tenantB);
      jdbc.update(
          "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
              + " inject_depends_duration, inject_scenario, tenant_id)"
              + " VALUES (?, ?, false, true, 0, ?, ?)",
          UUID.randomUUID().toString(),
          "scheduler-attribution",
          scenarioId,
          tenantB);
      insertChildrenWithNoTenantColumn(scenarioId, tenantB);

      // Act
      onBareThreadWithNoAmbientTenant(() -> scenarioExecutionJob.execute(null));

      // Assert
      List<String> simulationTenants =
          jdbc.queryForList(
              "SELECT e.tenant_id FROM exercises e JOIN scenarios_exercises se"
                  + " ON se.exercise_id = e.exercise_id WHERE se.scenario_id = ?",
              String.class,
              scenarioId);
      assertThat(simulationTenants).containsExactly(tenantB);
      List<String> injectTenants =
          jdbc.queryForList(
              "SELECT i.tenant_id FROM injects i JOIN scenarios_exercises se"
                  + " ON se.exercise_id = i.inject_exercise WHERE se.scenario_id = ?",
              String.class,
              scenarioId);
      assertThat(injectTenants).containsExactly(tenantB);
      assertThat(publishedEvents)
          .anySatisfy(event -> assertThat(rowTenantOf(event)).isEqualTo(tenantB))
          .extracting(event -> Hibernate.getClassLazy(event.getInstance()).getSimpleName())
          .contains(
              Objective.class.getSimpleName(),
              Article.class.getSimpleName(),
              LessonsCategory.class.getSimpleName(),
              LessonsQuestion.class.getSimpleName());
      assertNoConsumerReadsTheTenantOfTheEvents();
    }

    /**
     * An objective, an article and a lessons category with its question on the scenario: rows with
     * no tenant column, which the conversion copies to the simulation.
     */
    private void insertChildrenWithNoTenantColumn(String scenarioId, String tenantId) {
      String channelId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO channels (channel_id, channel_name, channel_type, tenant_id)"
              + " VALUES (?, 'scheduler-attribution', 'newspaper', ?)",
          channelId,
          tenantId);
      jdbc.update(
          "INSERT INTO objectives (objective_id, objective_title, objective_scenario)"
              + " VALUES (?, 'scheduler-attribution', ?)",
          UUID.randomUUID().toString(),
          scenarioId);
      jdbc.update(
          "INSERT INTO articles (article_id, article_name, article_channel, article_scenario)"
              + " VALUES (?, 'scheduler-attribution', ?, ?)",
          UUID.randomUUID().toString(),
          channelId,
          scenarioId);
      String categoryId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO lessons_categories (lessons_category_id, lessons_category_name,"
              + " lessons_category_order, lessons_category_scenario)"
              + " VALUES (?, 'scheduler-attribution', 0, ?)",
          categoryId,
          scenarioId);
      jdbc.update(
          "INSERT INTO lessons_questions (lessons_question_id, lessons_question_content,"
              + " lessons_question_order, lessons_question_category)"
              + " VALUES (?, 'scheduler-attribution', 0, ?)",
          UUID.randomUUID().toString(),
          categoryId);
    }

    @Test
    @DisplayName(
        "given a scenario of one tenant that fails to convert, should clear that tenant's"
            + " outdated recurrence and create the due simulation of the other tenant")
    void given_failingScenarioInOneTenant_should_runItsCleanUpAndTheOtherTenants()
        throws Exception {
      // Arrange
      String failingOfA = insertRecurringScenario(tenantA, 1);
      String outdatedOfA = insertRecurringScenario(tenantA, -1);
      String dueOfB = insertRecurringScenario(tenantB, 1);
      doThrow(new IllegalStateException("conversion failed"))
          .when(scenarioToExerciseService)
          .toExercise(
              argThat((Scenario scenario) -> failingOfA.equals(scenario.getId())),
              any(),
              any(Boolean.class),
              any());

      // Act
      onBareThreadWithNoAmbientTenant(() -> scenarioExecutionJob.execute(null));

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT scenario_recurrence FROM scenarios WHERE scenario_id = ?",
                  String.class,
                  outdatedOfA))
          .isNull();
      assertThat(
              jdbc.queryForList(
                  "SELECT e.tenant_id FROM exercises e JOIN scenarios_exercises se"
                      + " ON se.exercise_id = e.exercise_id WHERE se.scenario_id = ?",
                  String.class,
                  dueOfB))
          .containsExactly(tenantB);
    }

    /** A recurring scenario due every minute, whose recurrence ends {@code daysToEnd} from now. */
    private String insertRecurringScenario(String tenantId, int daysToEnd) {
      String scenarioId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO scenarios (scenario_id, scenario_name, scenario_recurrence,"
              + " scenario_recurrence_start, scenario_recurrence_end, tenant_id)"
              + " VALUES (?, ?, ?, ?, ?, ?)",
          scenarioId,
          "scheduler-attribution",
          EVERY_MINUTE_CRON,
          Timestamp.from(Instant.now().minus(2, ChronoUnit.DAYS)),
          Timestamp.from(Instant.now().plus(daysToEnd, ChronoUnit.DAYS)),
          tenantId);
      return scenarioId;
    }
  }

  @Nested
  @DisplayName("QueueChainingJob")
  class QueueChaining {

    private BatchQueueService<StepEvent> brokerReadyQueue;
    private final List<StepEvent> publishedReadyEvents = new CopyOnWriteArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void captureTheReadyQueue() throws Exception {
      brokerReadyQueue =
          (BatchQueueService<StepEvent>)
              ReflectionTestUtils.getField(queueChainingService, "readyQueueService");
      BatchQueueService<StepEvent> captor = mock(BatchQueueService.class);
      doAnswer(
              invocation -> {
                publishedReadyEvents.add(invocation.getArgument(0));
                return null;
              })
          .when(captor)
          .publish(any());
      queueChainingService.setReadyQueueService(captor);
    }

    @AfterEach
    void restoreTheReadyQueue() {
      queueChainingService.setReadyQueueService(brokerReadyQueue);
    }

    @Test
    @DisplayName(
        "given due entries of two tenants in one pass, should ready the step of the one in its"
            + " tenant and end the other with its team photo read in its own tenant")
    void given_dueEntriesOfTwoTenants_should_workEachRunInItsOwnTenant() throws Exception {
      // Arrange: the run of A has a step template with no condition, which its entry readies. The
      // run of B waits on a step template that never ran, so its entry readies nothing, and the
      // evaluation that follows the commit ends the run and freezes its end photo.
      ChainedRun runOfA = insertChainedRunWithDueEntry(tenantA);
      ChainedRun runOfB = insertChainedRunWithDueEntry(tenantB);
      String waitsForever = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO conditions (condition_id, condition_type, condition_value,"
              + " condition_workflow_id, tenant_id) VALUES (?, 'DEPEND_ON', ?, ?, ?)",
          waitsForever,
          UUID.randomUUID().toString(),
          runOfB.templateId(),
          tenantB);
      jdbc.update(
          "INSERT INTO conditions_steps (condition_step_id, condition_id, step_id, is_root)"
              + " VALUES (?, ?, ?, true)",
          UUID.randomUUID().toString(),
          waitsForever,
          runOfB.stepTemplateId());
      String teamId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, 'blue team', ?)",
          teamId,
          tenantB);
      jdbc.update(
          "INSERT INTO workflow_scope_rules (workflow_scope_rule_id, workflow_id,"
              + " workflow_scope_rule_selected_mode, workflow_scope_rule_source,"
              + " workflow_scope_rule_value, workflow_scope_rule_value_type,"
              + " workflow_scope_rule_snapshot_start, tenant_id)"
              + " VALUES (?, ?, 'ALLOWLIST', 'TEAM', ?, 'TEAM_ID',"
              + " '{\"label\":\"blue team\"}'::jsonb, ?)",
          UUID.randomUUID().toString(),
          runOfB.runId(),
          teamId,
          tenantB);

      // Act
      onBareThreadWithNoAmbientTenant(() -> queueChainingJob.execute(null));

      // Assert: A's step is readied and published in A, B's run gets no step
      List<Map<String, Object>> readyOfA =
          jdbc.queryForList(
              "SELECT step_id, tenant_id FROM steps WHERE step_workflow_id = ?"
                  + " AND step_status = 'READY'",
              runOfA.runId());
      assertThat(readyOfA).hasSize(1);
      assertThat(readyOfA.getFirst()).containsEntry("tenant_id", tenantA);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM steps WHERE step_workflow_id = ? AND step_status <>"
                      + " 'TEMPLATE'",
                  Long.class,
                  runOfB.runId()))
          .isZero();
      List<StepEvent> readyEventsOfTheseRuns =
          publishedReadyEvents.stream()
              .filter(
                  event -> List.of(runOfA.runId(), runOfB.runId()).contains(event.getWorkflowId()))
              .toList();
      assertThat(readyEventsOfTheseRuns).hasSize(1);
      assertThat(readyEventsOfTheseRuns.getFirst().getStepId())
          .isEqualTo(readyOfA.getFirst().get("step_id"));
      assertThat(readyEventsOfTheseRuns.getFirst().getTenantId()).isEqualTo(tenantA);
      // Assert: A's run goes on, B's run ends with its team read in B
      assertThat(workflowStatus(runOfA.runId())).isEqualTo("RUN");
      assertThat(workflowStatus(runOfB.runId())).isEqualTo("END");
      Map<String, Object> endPhoto =
          jdbc.queryForMap(
              "SELECT workflow_scope_rule_snapshot_end->>'label' AS label,"
                  + " workflow_scope_rule_snapshot_end->>'deleted' AS deleted"
                  + " FROM workflow_scope_rules WHERE workflow_id = ?",
              runOfB.runId());
      assertThat(endPhoto.get("label")).isEqualTo("blue team");
      assertThat(endPhoto.get("deleted")).isNotEqualTo("true");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM steps_delay_queue WHERE steps_delay_queue_workflow_run_id"
                      + " IN (?, ?)",
                  Long.class,
                  runOfA.runId(),
                  runOfB.runId()))
          .isZero();
      assertThat(publishedEvents).isNotEmpty();
      assertNoConsumerReadsTheTenantOfTheEvents();
    }

    private String workflowStatus(String workflowId) {
      return jdbc.queryForObject(
          "SELECT workflow_status FROM workflows WHERE workflow_id = ?", String.class, workflowId);
    }

    /**
     * A running simulation of {@code tenantId} with its template workflow, one step template, its
     * run, and an entry of the delay queue due now for that step template and run.
     */
    private ChainedRun insertChainedRunWithDueEntry(String tenantId) {
      String simulationId = UUID.randomUUID().toString();
      String templateId = UUID.randomUUID().toString();
      String runId = UUID.randomUUID().toString();
      String stepTemplateId = UUID.randomUUID().toString();
      jdbc.update(
          "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
              + " VALUES (?, 'chained simulation', 'RUNNING', ?)",
          simulationId,
          tenantId);
      jdbc.update(
          "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
              + " workflow_version, workflow_created_at, workflow_updated_at, tenant_id)"
              + " VALUES (?, 'TEMPLATE', ?, 0, now(), now(), ?)",
          templateId,
          simulationId,
          tenantId);
      jdbc.update(
          "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
              + " workflow_template_id, workflow_version, workflow_created_at,"
              + " workflow_updated_at, tenant_id)"
              + " VALUES (?, 'RUN', ?, ?, 0, now(), now(), ?)",
          runId,
          simulationId,
          templateId,
          tenantId);
      jdbc.update(
          "INSERT INTO steps (step_id, step_action_class, step_output, step_input, step_data,"
              + " step_limit_execution, step_condition_executed, step_output_parser, step_status,"
              + " step_workflow_id, tenant_id) VALUES (?, 'INJECT_EXECUTION', '{}'::jsonb,"
              + " '{}'::jsonb, '{}'::jsonb, 1, 'true', '{}'::jsonb, 'TEMPLATE', ?, ?)",
          stepTemplateId,
          templateId,
          tenantId);
      jdbc.update(
          "INSERT INTO steps_delay_queue (steps_delay_queue_input, steps_delay_queue_now,"
              + " steps_delay_queue_goal, steps_delay_queue_delay,"
              + " steps_delay_queue_step_template_id, steps_delay_queue_workflow_run_id,"
              + " tenant_id) VALUES ('{}', now(), now() - interval '1 minute', 0, ?, ?, ?)",
          stepTemplateId,
          runId,
          tenantId);
      return new ChainedRun(templateId, runId, stepTemplateId);
    }
  }

  private record ChainedRun(String templateId, String runId, String stepTemplateId) {}

  @Nested
  @DisplayName("AtomicTestingExecutionJob")
  class RecurringAtomicTesting {

    @Test
    @DisplayName(
        "given a due recurring atomic testing of another tenant, should relaunch it in that"
            + " tenant")
    void given_dueRecurringAtomicTestingOfAnotherTenant_should_relaunchItInThatTenant()
        throws Exception {
      // Arrange
      String title = "scheduler-attribution-" + UUID.randomUUID();
      Inject atomicTesting = InjectFixture.getDefaultInject();
      atomicTesting.setTitle(title);
      atomicTesting.setRecurrence(EVERY_MINUTE_CRON);
      atomicTesting.setRecurrenceEnd(Instant.now().plus(10, ChronoUnit.DAYS));
      atomicTesting.setTenant(new Tenant(tenantB));
      String originalId =
          tenantTx.execute(
              TxCtx.forTenant(tenantB), () -> injectRepository.save(atomicTesting).getId());

      // Act
      onBareThreadWithNoAmbientTenant(() -> atomicTestingExecutionJob.execute(null));

      // Assert
      List<String> relaunched =
          jdbc.queryForList(
              "SELECT tenant_id FROM injects WHERE inject_title = ? AND inject_id <> ?",
              String.class,
              title,
              originalId);
      assertThat(relaunched).containsExactly(tenantB);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM injects WHERE inject_id = ?", Long.class, originalId))
          .isZero();
      assertThat(publishedEvents)
          .anySatisfy(event -> assertThat(rowTenantOf(event)).isEqualTo(tenantB));
      assertNoConsumerReadsTheTenantOfTheEvents();
    }

    @Test
    @DisplayName(
        "given a relaunch of another tenant, should stream its queued status to that tenant's"
            + " consumer with the markings read in that tenant, and nothing to the other tenant's")
    void given_relaunchOfAnotherTenant_should_streamItsStatusToThatTenantOnly() throws Exception {
      // Arrange
      String originalId = saveRecurringAtomicTesting(tenantB);
      onBareThreadWithNoAmbientTenant(() -> atomicTestingExecutionJob.execute(null));
      List<BaseEvent> relaunchEvents = eventsAbout(Inject.class);
      List<String> markingTenants = new CopyOnWriteArrayList<>();
      StreamVisibilityService markings = mock(StreamVisibilityService.class);
      when(markings.visibilityOrUnresolved(any(), any()))
          .thenAnswer(
              invocation -> {
                markingTenants.add(invocation.getArgument(1));
                return StreamVisibility.UNFILTERED;
              });
      StreamService stream =
          new StreamService(
              permissionService,
              userService,
              attackPathAccessControl,
              mock(StreamClearanceService.class),
              markings,
              mapper);
      List<BaseEvent> receivedInA = new CopyOnWriteArrayList<>();
      List<BaseEvent> receivedInB = new CopyOnWriteArrayList<>();
      Disposable consumerOfA = subscribe(stream, tenantA, receivedInA);
      Disposable consumerOfB = subscribe(stream, tenantB, receivedInB);

      // Act
      try {
        relaunchEvents.forEach(stream::listenDatabaseUpdate);
      } finally {
        consumerOfA.dispose();
        consumerOfB.dispose();
      }

      // Assert
      assertThat(receivedInA).isEmpty();
      assertThat(receivedInB)
          .filteredOn(event -> !originalId.equals(event.getInstance().getId()))
          .extracting(
              event ->
                  mapper
                      .valueToTree(event.getInstance())
                      .path("inject_status")
                      .path("status_name")
                      .asText())
          .contains(ExecutionStatus.QUEUING.name());
      assertThat(markingTenants).isNotEmpty().containsOnly(tenantB);
    }

    @SuppressWarnings("unchecked")
    private Disposable subscribe(StreamService stream, String tenantId, List<BaseEvent> received) {
      return stream
          .makeDataFlux("consumer-" + tenantId + "-" + UUID.randomUUID(), tenantId)
          .subscribe(sent -> received.add(((ServerSentEvent<BaseEvent>) sent).data()));
    }

    private String saveRecurringAtomicTesting(String tenantId) {
      Inject atomicTesting = InjectFixture.getDefaultInject();
      atomicTesting.setTitle("scheduler-attribution-" + UUID.randomUUID());
      atomicTesting.setRecurrence(EVERY_MINUTE_CRON);
      atomicTesting.setRecurrenceEnd(Instant.now().plus(10, ChronoUnit.DAYS));
      atomicTesting.setTenant(new Tenant(tenantId));
      return tenantTx.execute(
          TxCtx.forTenant(tenantId), () -> injectRepository.save(atomicTesting).getId());
    }
  }

  @Nested
  @DisplayName("AgentInactivityMonitorJob")
  class StaleAgent {

    @Test
    @DisplayName(
        "given a stale active agent of another tenant, should mark it inactive in that tenant")
    void given_staleActiveAgentOfAnotherTenant_should_markItInactiveInThatTenant()
        throws Exception {
      // Arrange
      String agentId =
          tenantTx.execute(
              TxCtx.forTenant(tenantB),
              () -> {
                Endpoint endpoint =
                    EndpointFixture.createEndpoint("scheduler-attribution-" + UUID.randomUUID());
                endpoint.setTenant(new Tenant(tenantB));
                endpointRepository.save(endpoint);
                Agent agent = AgentFixture.createDefaultAgentService();
                agent.setAsset(endpoint);
                agent.setTenant(endpoint.getTenant());
                agent.setStatus(AgentStatus.ACTIVE);
                agent.setLastSeen(Instant.now().minus(Duration.ofHours(2)));
                return agentRepository.save(agent).getId();
              });

      // Act
      onBareThreadWithNoAmbientTenant(() -> agentInactivityMonitorJob.execute(null));

      // Assert
      assertThat(
              jdbc.queryForObject(
                  "SELECT agent_status FROM agents WHERE agent_id = ?", String.class, agentId))
          .isEqualTo(AgentStatus.INACTIVE.name());
      assertThat(
              jdbc.queryForObject(
                  "SELECT tenant_id FROM agents WHERE agent_id = ?", String.class, agentId))
          .isEqualTo(tenantB);
      assertThat(eventsAbout(Agent.class))
          .filteredOn(event -> agentId.equals(event.getInstance().getId()))
          .isNotEmpty()
          .allSatisfy(event -> assertThat(rowTenantOf(event)).isEqualTo(tenantB));
      assertNoConsumerReadsTheTenantOfTheEvents();
    }
  }

  @Nested
  @DisplayName("ManagerIntegrationsSyncJob")
  class IntegrationsSync {

    private static final Duration SYNC_BOUND = Duration.ofSeconds(60);

    private ExecutorService syncExecutor;
    private ManagerIntegrationsSyncJob job;

    /**
     * The job's code with an executor the test owns, so the test can wait for the sync of every
     * tenant the job hands off, and not only for the tenant it asserts on.
     */
    @BeforeEach
    void ownTheSyncExecutor() {
      syncExecutor = Executors.newFixedThreadPool(2);
      job = new ManagerIntegrationsSyncJob(managerFactory, tenantService, tenantTx, syncExecutor);
    }

    /**
     * Runs before the outer sweep deletes the tenants, also when the test fails, so no sync writes
     * into a tenant being deleted or into the next test.
     */
    @AfterEach
    void awaitEverySync() throws InterruptedException {
      if (!awaitEverySubmittedSync()) {
        syncExecutor.shutdownNow();
        throw new AssertionError("a tenant sync outlived " + SYNC_BOUND);
      }
    }

    @Test
    @DisplayName(
        "given a tenant with no integration yet, should register its built-in injectors in that"
            + " tenant")
    void given_tenantWithNoIntegrationYet_should_registerItsBuiltInInjectorsInThatTenant()
        throws Exception {
      // Arrange: the second tenant, seeded with no integration

      // Act: the job hands each tenant to the executor; wait for every tenant's pass
      onBareThreadWithNoAmbientTenant(() -> job.execute(null));
      assertThat(awaitEverySubmittedSync()).as("every tenant sync ends").isTrue();

      // Assert
      assertThat(emailInjectorCount(tenantB)).isEqualTo(1L);
      assertThat(emailInjectorCount(DEFAULT_TENANT_UUID)).isEqualTo(1L);
    }

    private boolean awaitEverySubmittedSync() throws InterruptedException {
      syncExecutor.shutdown();
      return syncExecutor.awaitTermination(SYNC_BOUND.toSeconds(), TimeUnit.SECONDS);
    }

    private long emailInjectorCount(String tenantId) {
      return jdbc.queryForObject(
          "SELECT count(*) FROM injectors WHERE tenant_id = ? AND injector_type = ?",
          Long.class,
          tenantId,
          EmailContract.TYPE);
    }
  }

  /** The tenant of the row an entity event is about, or null for a row with no tenant. */
  private static String rowTenantOf(BaseEvent event) {
    if (event.getInstance() instanceof TenantBase row && row.getTenant() != null) {
      return row.getTenant().getId();
    }
    if (event.getInstance() instanceof DualScopeBase row && row.getTenant() != null) {
      return row.getTenant().getId();
    }
    return event.getInstance() instanceof TenantIdBase row ? row.getTenantId() : null;
  }

  /**
   * The events of a job carry the tenant of the thread, the default one, not the tenant of their
   * row. Three consumers read that tenant: the stream for the markings of a row with no tenant
   * whose type it filters by markings, and for the fields of a user, and the notification engine
   * for the deletion of a grant-managed root. None of the events the job under test published is of
   * these kinds.
   */
  private void assertNoConsumerReadsTheTenantOfTheEvents() {
    assertThat(publishedEvents)
        .allSatisfy(
            event -> {
              assertThat(
                      rowTenantOf(event) != null
                          || streamMarkingRules
                              .forEntity(Hibernate.getClassLazy(event.getInstance()))
                              .isEmpty())
                  .as(
                      "the stream reads the markings of this %s in the tenant of its event",
                      Hibernate.getClassLazy(event.getInstance()).getSimpleName())
                  .isTrue();
              assertThat(event.getInstance()).isNotInstanceOf(User.class);
              assertThat(StreamService.isGrantManagedRootDeletion(event)).isFalse();
            });
  }

  /** The entity events the job under test published, about rows of {@code type}. */
  private List<BaseEvent> eventsAbout(Class<?> type) {
    return publishedEvents.stream().filter(event -> type.isInstance(event.getInstance())).toList();
  }

  /** The job's body, run as a Quartz worker runs it: a fresh thread with no ambient tenant. */
  private interface JobBody {
    void run() throws Exception;
  }

  private static void onBareThreadWithNoAmbientTenant(JobBody body) throws InterruptedException {
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread thread =
        new Thread(
            () -> {
              try {
                TenantContext.clearCurrentTenant();
                assertThat(TenantContext.hasCurrentTenant()).isFalse();
                assertThat(TenantContext.getCurrentTenant()).isEqualTo(DEFAULT_TENANT_UUID);
                body.run();
              } catch (Throwable t) {
                failure.set(t);
              }
            },
            JOB_THREAD);
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the job failed", failure.get());
    }
  }
}
