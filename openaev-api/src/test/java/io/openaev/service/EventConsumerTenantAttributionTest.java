package io.openaev.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Step;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.executors.Executor;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionCallback;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.chaining.ChainingConfig;
import io.openaev.service.chaining.ExternalUpdateEvent;
import io.openaev.service.chaining.StepEvent;
import io.openaev.service.chaining.StepEventService;
import io.openaev.service.inject.BatchingInjectStatusService;
import io.openaev.service.stream.StreamClearanceService;
import io.openaev.service.stream.StreamVisibility;
import io.openaev.service.stream.StreamVisibilityService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.ExecutionTraceComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.RowTenantScope;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.Disposable;

/**
 * The event consumers and the job runner set no thread-local tenant: each unit of work runs in a
 * transaction scoped to the tenant of what it handles, every row it writes names its tenant, and
 * the entity events it publishes take the one tenant that scope pins. Each case runs on a bare
 * thread whose thread-local tenant is cleared, the way a broker or Quartz worker starts, against a
 * second tenant, with the production list of isolated tables armed.
 */
@WithMockUser(isAdmin = true)
@Import(EventConsumerTenantAttributionTest.ScopedWriterConfig.class)
@DisplayName("Event consumers and job runner attribute their work to its tenant")
class EventConsumerTenantAttributionTest extends IntegrationTest {

  private static final String CONSUMER_THREAD = "event-consumer-under-test";

  @MockitoBean private Executor executor;

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private RowTenantScope rowTenantScope;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantScopedJobRunner jobRunner;
  @Autowired private BatchingInjectStatusService batchingInjectStatusService;
  @Autowired private StepEventService stepEventService;
  @Autowired private InjectExecutionStep injectExecutionStep;
  @Autowired private ChainingConfig chainingConfig;
  @Autowired private StepRepository stepRepository;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private PermissionService permissionService;
  @Autowired private UserService userService;
  @Autowired private AttackPathAccessControl attackPathAccessControl;
  @Autowired private ObjectMapper mapper;
  @Autowired private DataSource dataSource;
  @Autowired private ConfigurableApplicationContext applicationContext;
  @Autowired private ScopedExpectationWriter scopedExpectationWriter;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private final List<BaseEvent> publishedEvents = new CopyOnWriteArrayList<>();
  private final ApplicationListener<PayloadApplicationEvent<?>> eventRecorder =
      event -> {
        if (event.getPayload() instanceof BaseEvent baseEvent
            && CONSUMER_THREAD.equals(Thread.currentThread().getName())) {
          publishedEvents.add(baseEvent);
        }
      };

  @BeforeEach
  void seedTwoTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = tenantHelper.createTenantWithCurrentUser("event-consumer-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("event-consumer-b").getId();
    TenantContext.clearCurrentTenant();
    applicationContext.addApplicationListener(eventRecorder);
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
      jdbc.update("DELETE FROM workflows WHERE tenant_id = ?", tenantId);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Entity events built off a request")
  class EventTenant {

    @Test
    @DisplayName(
        "given a row with no tenant written in a transaction scoped to one tenant, should carry"
            + " that tenant on its event")
    void given_rowWithNoTenantWrittenInAScopeOfOneTenant_should_carryThatTenantOnItsEvent()
        throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();

      // Act
      onBareThreadWithNoAmbientTenant(
          () -> tenantTx.execute(TxCtx.forTenant(tenantB), () -> scoreExpectation(expectationId)));

      // Assert
      assertThat(eventsAbout(BaseInjectExpectation.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
    }

    @Test
    @DisplayName(
        "given a row with no tenant written by a transactional method scoped to one tenant,"
            + " should carry that tenant on its event")
    void given_rowWithNoTenantWrittenByAScopedTransactionalMethod_should_carryThatTenant()
        throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();

      // Act
      onBareThreadWithNoAmbientTenant(
          () -> scopedExpectationWriter.score(TxCtx.forTenant(tenantB), expectationId));

      // Assert
      assertThat(eventsAbout(BaseInjectExpectation.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
    }

    @Test
    @DisplayName(
        "given a row with no tenant written in a transaction scoped to two tenants, should carry"
            + " the default tenant the thread falls back to")
    void given_rowWithNoTenantWrittenInAScopeOfTwoTenants_should_carryTheFallbackTenant()
        throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();

      // Act
      onBareThreadWithNoAmbientTenant(
          () ->
              tenantTx.execute(
                  TxCtx.forTenants(List.of(tenantA, tenantB)),
                  () -> scoreExpectation(expectationId)));

      // Assert
      assertThat(eventsAbout(BaseInjectExpectation.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName(
        "given the event of a row with no tenant written for another tenant, should reach that"
            + " tenant's consumer with its markings read in that tenant for every consumer")
    void given_eventOfRowWithNoTenant_should_readItsMarkingsInItsTenant() throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();
      onBareThreadWithNoAmbientTenant(
          () -> tenantTx.execute(TxCtx.forTenant(tenantB), () -> scoreExpectation(expectationId)));
      List<BaseEvent> expectationEvents = eventsAbout(BaseInjectExpectation.class);
      StreamProbe probe = new StreamProbe();

      // Act
      probe.feed(expectationEvents);

      // Assert: the stream routes a row with no tenant to the consumers of every tenant and
      // checks each one's rights, so the event tenant only decides where its markings are read
      assertThat(probe.receivedInB)
          .extracting(event -> event.getInstance().getId())
          .contains(expectationId);
      assertThat(probe.markingTenants).isNotEmpty().containsOnly(tenantB);
    }
  }

  @Nested
  @DisplayName("TenantScopedJobRunner")
  class JobRunner {

    @Test
    @DisplayName(
        "given work run in another tenant, should set no thread-local tenant and attribute the"
            + " events of its rows with no tenant to that tenant")
    void given_workRunInAnotherTenant_should_attributeItsEventsToThatTenant() throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();
      AtomicReference<Boolean> ambientDuringWork = new AtomicReference<>();

      // Act
      onBareThreadWithNoAmbientTenant(
          () ->
              jobRunner.runInTenant(
                  tenantB,
                  () -> {
                    ambientDuringWork.set(TenantContext.hasCurrentTenant());
                    scoreExpectation(expectationId);
                  }));

      // Assert
      assertThat(ambientDuringWork.get()).isFalse();
      assertThat(eventsAbout(BaseInjectExpectation.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
    }

    @Test
    @DisplayName(
        "given work that returns a value in another tenant, should attribute the events of its"
            + " rows with no tenant to that tenant")
    void given_suppliedWorkInAnotherTenant_should_attributeItsEventsToThatTenant()
        throws Exception {
      // Arrange
      String expectationId = seedInjectWithOpenExpectation(tenantB).expectationId();

      // Act
      onBareThreadWithNoAmbientTenant(
          () ->
              jobRunner.supplyInTenant(
                  tenantB,
                  () -> {
                    scoreExpectation(expectationId);
                    return expectationId;
                  }));

      // Assert
      assertThat(eventsAbout(BaseInjectExpectation.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
    }
  }

  @Nested
  @DisplayName("BatchingInjectStatusService")
  class CallbackConsumer {

    @Test
    @DisplayName(
        "given a callback of another tenant, should write its status and traces in that tenant"
            + " and stream the status to that tenant's consumer only")
    void given_callbackOfAnotherTenant_should_writeAndStreamInThatTenant() throws Exception {
      // Arrange
      String injectId = seedPendingInjectWithStoredError(tenantB);
      StreamProbe probe = new StreamProbe();

      // Act
      onBareThreadWithNoAmbientTenant(
          () ->
              batchingInjectStatusService.handleInjectExecutionCallback(
                  List.of(completeCallback(injectId))));
      probe.feed(publishedEvents);

      // Assert
      assertThat(statusOf(injectId)).isEqualTo(ExecutionStatus.PARTIAL.name());
      assertThat(traceTenants(injectId)).containsExactly(tenantB, tenantB);
      assertThat(publishedEvents)
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
      assertThat(probe.receivedInA).isEmpty();
      assertThat(probe.receivedInB)
          .filteredOn(event -> event.getInstance() instanceof Inject)
          .extracting(
              event ->
                  mapper
                      .valueToTree(event.getInstance())
                      .path("inject_status")
                      .path("status_name")
                      .asText())
          .contains(ExecutionStatus.PARTIAL.name());
    }
  }

  @Nested
  @DisplayName("StepEventService")
  class StepEvents {

    @Test
    @DisplayName(
        "given a ready step of another tenant, should create its inject in that tenant and move"
            + " the step to RUN")
    void given_readyStepOfAnotherTenant_should_createItsInjectInThatTenant() throws Exception {
      // Arrange
      doReturn(new InjectStatus()).when(executor).directExecute(any());
      String stepId = persistReadyEmailStep(tenantB);
      StepEvent event =
          StepEvent.builder()
              .stepId(stepId)
              .tenantId(tenantB)
              .retryCount(chainingConfig.getMaxRetryCount())
              .build();

      // Act
      onBareThreadWithNoAmbientTenant(() -> stepEventService.handleReadyStepEvent(event));

      // Assert
      assertThat(stepStatus(stepId)).isEqualTo("RUN");
      String injectId =
          jdbc.queryForObject(
              "SELECT step_data->>'inject_id' FROM steps WHERE step_id = ?", String.class, stepId);
      assertThat(injectId).isNotBlank();
      assertThat(
              jdbc.queryForObject(
                  "SELECT tenant_id FROM injects WHERE inject_id = ?", String.class, injectId))
          .isEqualTo(tenantB);
      assertThat(eventsAbout(Inject.class))
          .isNotEmpty()
          .extracting(BaseEvent::getTenantId)
          .containsOnly(tenantB);
    }

    @Test
    @DisplayName(
        "given an external update of another tenant whose inject has an open expectation, should"
            + " read the expectation in that tenant and leave the step running")
    void given_externalUpdateOfAnotherTenant_should_readItsExpectationsInThatTenant()
        throws Exception {
      // Arrange
      SeededInject seeded = seedInjectWithOpenExpectation(tenantB);
      String stepId = insertRunningStep(tenantB, seeded.simulationId(), seeded.injectId());
      ExternalUpdateEvent event =
          ExternalUpdateEvent.builder().stepId(stepId).tenantId(tenantB).build();

      // Act
      onBareThreadWithNoAmbientTenant(() -> stepEventService.handleExternalUpdateEvent(event));

      // Assert: the update reached the completion check, and the expectation has no result yet,
      // so the step waits for it
      assertThat(
              jdbc.queryForObject(
                  "SELECT step_output::text FROM steps WHERE step_id = ?", String.class, stepId))
          .contains("outputs");
      assertThat(stepStatus(stepId)).isEqualTo("RUN");
    }
  }

  // -- FIXTURES --

  @TestConfiguration
  static class ScopedWriterConfig {
    @Bean
    ScopedExpectationWriter scopedExpectationWriter(InjectExpectationRepository repository) {
      return new ScopedExpectationWriter(repository);
    }
  }

  /** A transactional method that takes its scope from its {@link TxCtx} argument. */
  static class ScopedExpectationWriter {
    private final InjectExpectationRepository repository;

    ScopedExpectationWriter(InjectExpectationRepository repository) {
      this.repository = repository;
    }

    @Transactional
    public void score(TxCtx ctx, String expectationId) {
      BaseInjectExpectation expectation = repository.findById(expectationId).orElseThrow();
      expectation.setScore(0.0);
      repository.save(expectation);
    }
  }

  private record SeededInject(String simulationId, String injectId, String expectationId) {}

  /**
   * A simulation of {@code tenantId} with one executed inject that has a trace and one detection
   * expectation with no result.
   */
  private SeededInject seedInjectWithOpenExpectation(String tenantId) {
    AtomicReference<SeededInject> seeded = new AtomicReference<>();
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract =
              injectorContractFixture.getWellKnownSingleEmailContract(tenantId);
          Inject inject = InjectFixture.createInject(contract, "event-consumer-probe");
          inject.setTenant(new Tenant(tenantId));
          inject.setContent(mapper.createObjectNode().put("target_selector", "assets"));
          InjectComposer.Composer injectC =
              injectComposer
                  .forInject(inject)
                  .withInjectStatus(
                      injectStatusComposer
                          .forInjectStatus(InjectStatusFixture.createSuccessStatus())
                          .withExecutionTrace(
                              executionTraceComposer.forExecutionTrace(
                                  ExecutionTrace.from(
                                      ExecutionTraceFixture.createDefaultExecutionTraceComplete(),
                                      mapper.createObjectNode()))));
          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          Exercise saved =
              exerciseComposer.forExercise(exercise).withInjects(List.of(injectC)).persist().get();
          BaseInjectExpectation expectation =
              injectExpectationComposer
                  .forExpectation(
                      InjectExpectationFixture.createDetectionInjectExpectation(
                          injectC.get(), null))
                  .persist()
                  .get();
          seeded.set(new SeededInject(saved.getId(), injectC.get().getId(), expectation.getId()));
        });
    return seeded.get();
  }

  /** Changes an expectation's score, a write of a row with no tenant. */
  private void scoreExpectation(String expectationId) {
    BaseInjectExpectation expectation =
        injectExpectationRepository.findById(expectationId).orElseThrow();
    expectation.setScore(0.0);
    injectExpectationRepository.save(expectation);
  }

  private String seedPendingInjectWithStoredError(String tenantId) {
    AtomicReference<String> injectId = new AtomicReference<>();
    rowTenantScope.write(
        tenantId,
        () -> {
          InjectorContract contract =
              injectorContractFixture.getWellKnownSingleEmailContract(tenantId);
          Inject inject = InjectFixture.createInject(contract, "event-consumer-callback");
          inject.setTenant(new Tenant(tenantId));
          InjectComposer.Composer injectC =
              injectComposer
                  .forInject(inject)
                  .withInjectStatus(
                      injectStatusComposer
                          .forInjectStatus(InjectStatusFixture.createPendingInjectStatus())
                          .withExecutionTrace(
                              executionTraceComposer.forExecutionTrace(
                                  ExecutionTraceFixture.createDefaultExecutionTraceError())));
          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          exerciseComposer.forExercise(exercise).withInjects(List.of(injectC)).persist();
          injectId.set(injectC.get().getId());
        });
    return injectId.get();
  }

  private InjectExecutionCallback completeCallback(String injectId) {
    InjectExecutionInput input = new InjectExecutionInput();
    input.setAction(InjectExecutionAction.complete);
    input.setMessage("done");
    input.setStatus("SUCCESS");
    return InjectExecutionCallback.builder()
        .injectId(injectId)
        .injectExecutionInput(input)
        .emissionDate(Instant.now().toEpochMilli())
        .build();
  }

  /** A running simulation of {@code tenantId} and its chained run, with no step yet. */
  private String insertRunningSimulationRun(String tenantId, String simulationId) {
    String runId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
            + " workflow_version, workflow_created_at, workflow_updated_at, tenant_id)"
            + " VALUES (?, 'RUN', ?, 0, now(), now(), ?)",
        runId,
        simulationId,
        tenantId);
    return runId;
  }

  /** A RUN step of a chained run of {@code tenantId}, whose inject is {@code injectId}. */
  private String insertRunningStep(String tenantId, String simulationId, String injectId) {
    String runId = insertRunningSimulationRun(tenantId, simulationId);
    String stepId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO steps (step_id, step_action_class, step_output, step_input, step_data,"
            + " step_limit_execution, step_condition_executed, step_output_parser, step_status,"
            + " step_workflow_id, tenant_id) VALUES (?, 'INJECT_EXECUTION', '{}'::jsonb,"
            + " '{}'::jsonb, ?::jsonb, 0, 'true', '{}'::jsonb, 'RUN', ?, ?)",
        stepId,
        "{\"inject_id\":\"" + injectId + "\"}",
        runId,
        tenantId);
    return stepId;
  }

  /**
   * A READY step of a chained run of {@code tenantId}, authored as production authors it: an email
   * inject on the tenant's own email contract, whose data names that contract by id only.
   */
  private String persistReadyEmailStep(String tenantId) throws Exception {
    AtomicReference<String> simulationId = new AtomicReference<>();
    AtomicReference<String> contractId = new AtomicReference<>();
    rowTenantScope.write(
        tenantId,
        () -> {
          contractId.set(injectorContractFixture.getWellKnownSingleEmailContract(tenantId).getId());
          Exercise exercise = ExerciseFixture.createDefaultExercise();
          exercise.setTenant(new Tenant(tenantId));
          simulationId.set(exerciseComposer.forExercise(exercise).persist().get().getId());
        });
    String runId = insertRunningSimulationRun(tenantId, simulationId.get());
    InjectInput injectInput =
        mapper.readValue(
            """
            {
              "inject_title": "chained email",
              "inject_description": "",
              "inject_injector_contract": "%s",
              "inject_content": { "subject": "subject", "body": "body" },
              "inject_depends_on": [],
              "inject_depends_duration": 0,
              "inject_teams": [],
              "inject_assets": [],
              "inject_asset_groups": [],
              "inject_documents": [],
              "inject_all_teams": false,
              "inject_tags": [],
              "inject_enabled": true
            }
            """
                .formatted(contractId.get()),
            InjectInput.class);
    StepsCreateInput.StepInput stepInput =
        InjectExecutionStep.getInjectAsStepsCreateInput(injectInput);
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          try {
            Workflow run = workflowRepository.findById(runId).orElseThrow();
            Step template = injectExecutionStep.create(stepInput, run).orElseThrow();
            template.setWorkflow(run);
            template.setLimitExecution(0);
            template = stepRepository.save(template);
            Step ready =
                injectExecutionStep
                    .ready(template, "{\"input\":\"do defined\"}", run)
                    .orElseThrow();
            ready.setWorkflow(run);
            ready.setLimitExecution(0);
            return stepRepository.save(ready).getId();
          } catch (ChainingException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  // -- READS --

  private String stepStatus(String stepId) {
    return jdbc.queryForObject(
        "SELECT step_status FROM steps WHERE step_id = ?", String.class, stepId);
  }

  private String statusOf(String injectId) {
    return jdbc.queryForObject(
        "SELECT status_name FROM injects_statuses WHERE status_inject = ?", String.class, injectId);
  }

  private List<String> traceTenants(String injectId) {
    return jdbc.queryForList(
        "SELECT t.tenant_id FROM execution_traces t JOIN injects_statuses s"
            + " ON s.status_id = t.execution_inject_status_id WHERE s.status_inject = ?",
        String.class,
        injectId);
  }

  /** The entity events the unit under test published, about rows of {@code type}. */
  private List<BaseEvent> eventsAbout(Class<?> type) {
    return publishedEvents.stream().filter(event -> type.isInstance(event.getInstance())).toList();
  }

  /**
   * A real stream with one consumer in each tenant, whose marking reads record the tenant they are
   * made in.
   */
  private final class StreamProbe {
    private final List<BaseEvent> receivedInA = new CopyOnWriteArrayList<>();
    private final List<BaseEvent> receivedInB = new CopyOnWriteArrayList<>();
    private final List<String> markingTenants = new CopyOnWriteArrayList<>();

    void feed(List<BaseEvent> events) {
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
      Disposable consumerOfA = subscribe(stream, tenantA, receivedInA);
      Disposable consumerOfB = subscribe(stream, tenantB, receivedInB);
      try {
        events.forEach(stream::listenDatabaseUpdate);
      } finally {
        consumerOfA.dispose();
        consumerOfB.dispose();
      }
    }

    @SuppressWarnings("unchecked")
    private Disposable subscribe(StreamService stream, String tenantId, List<BaseEvent> received) {
      return stream
          .makeDataFlux("consumer-" + tenantId + "-" + UUID.randomUUID(), tenantId)
          .subscribe(sent -> received.add(((ServerSentEvent<BaseEvent>) sent).data()));
    }
  }

  /** The unit's body, run as a broker or Quartz worker runs it. */
  private interface WorkerBody {
    void run() throws Exception;
  }

  private static void onBareThreadWithNoAmbientTenant(WorkerBody body) throws InterruptedException {
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
            CONSUMER_THREAD);
    thread.start();
    thread.join();
    if (failure.get() != null) {
      throw new AssertionError("the worker failed", failure.get());
    }
  }
}
